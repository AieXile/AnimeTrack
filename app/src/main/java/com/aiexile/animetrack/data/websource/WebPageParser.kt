package com.aiexile.animetrack.data.websource

import com.aiexile.animetrack.data.player.WebSourceClassifier
import com.aiexile.animetrack.model.WebSourceChannel
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourcePage
import com.aiexile.animetrack.model.WebMediaSource
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.Base64

/** 无导入规则时识别常见播放列表；仅扫描剧集容器，不扫描推荐区域。 */
object WebPageParser {
    private val playlistSelectors = listOf(
        ".anime-detail-episodes", ".anime-episode-grid", ".module-play-list",
        ".anthology-list-box", ".stui-content__playlist", ".myui-content__list",
        ".playlist:has(a[href])"
    )

    fun parse(html: ByteArray, pageUrl: String): WebSourcePage {
        val document = Jsoup.parse(ByteArrayInputStream(html), null, pageUrl)
        val containers = playlistSelectors.asSequence()
            .map { document.select(it) }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val names = document.select(".anime-detail-source-name")
        val currentName = document.selectFirst(".anime-source-list .active .anime-source-name")
            ?.text() ?: document.selectFirst(".anime-episode-title strong")?.text()
        val channels = containers.mapIndexedNotNull { index, container ->
            val episodes = container.select("a[href]").mapNotNull { link ->
                val url = link.absUrl("href").toHttpUrlOrNull()?.toString()
                    ?: return@mapNotNull null
                val name = link.text().trim()
                if (name.isBlank()) return@mapNotNull null
                WebSourceEpisode(name, url, index)
            }.distinctBy { it.url }.take(2000)
            if (episodes.isEmpty()) null else WebSourceChannel(
                names.getOrNull(index)?.text()?.takeIf { it.isNotBlank() }
                    ?: currentName?.takeIf { containers.size == 1 },
                episodes
            )
        }
        val detail = document.selectFirst("a.anime-back-detail[href]")
            ?: document.selectFirst(".anime-breadcrumb a[href*=/acgdetail/]")
        val detailUrl = detail?.absUrl("href")?.toHttpUrlOrNull()
            ?.takeIf { it.host == pageUrl.toHttpUrlOrNull()?.host && it.toString() != pageUrl }
            ?.toString()
        val heading = document.selectFirst("h1")?.clone()?.apply { select("strong").remove() }
            ?.text()?.trim().orEmpty()
        val title = heading.ifBlank {
            document.selectFirst("meta[property=og:title]")?.attr("content")
                ?.takeIf { it.isNotBlank() } ?: document.title().ifBlank { pageUrl }
        }
        val configuredMedia = document.select("script").asSequence().mapNotNull { script ->
            playerMedia(script.data(), pageUrl)
        }.firstOrNull()
        val declaredSources = document.select("video[src], video source[src]").mapNotNull { source ->
            val url = source.absUrl("src").takeIf(::isDirectMedia) ?: return@mapNotNull null
            val label = listOf("label", "data-quality", "data-res", "res", "size", "title")
                .firstNotNullOfOrNull { source.attr(it).takeIf(String::isNotBlank) }
            WebMediaSource(url, label, source.attr("type").takeIf { it.startsWith("video/") || it.contains("mpegurl") })
        } + document.select("script").flatMap { declaredMedia(it.data(), pageUrl) }
        val sources = (listOfNotNull(configuredMedia?.let { WebMediaSource(it) }) + declaredSources)
            .groupBy { it.url }.values.map { group -> group.firstOrNull { it.label != null } ?: group.first() }
        val media = configuredMedia ?: sources.firstOrNull()?.url
        return WebSourcePage(title, pageUrl, channels, detailUrl = detailUrl,
            mediaUrl = media, mediaSources = sources)
    }

    /** 只读取明确的播放器 sources/quality 数组，不执行 JS，也不把播放线路当清晰度。 */
    private fun declaredMedia(script: String, pageUrl: String): List<WebMediaSource> {
        return Regex("[\"']?(?:sources|quality|qualities)[\"']?\\s*[:=]\\s*\\[")
            .findAll(script).flatMap { match ->
                val start = script.indexOf('[', match.range.first)
                val json = balancedJson(script, start, '[', ']') ?: return@flatMap emptySequence()
                runCatching {
                    JsonParser.parseString(json).asJsonArray.mapNotNull { item ->
                        if (!item.isJsonObject) return@mapNotNull null
                        val value = item.asJsonObject
                        val raw = listOf("file", "url", "src").firstNotNullOfOrNull { key ->
                            value.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                        } ?: return@mapNotNull null
                        val url = WebSourceParser.resolve(pageUrl, raw)?.takeIf(::isDirectMedia)
                            ?: return@mapNotNull null
                        val label = listOf("label", "name", "quality", "res", "size")
                            .firstNotNullOfOrNull { key -> value.get(key)?.takeIf { it.isJsonPrimitive }?.asString }
                        WebMediaSource(url, label)
                    }.asSequence()
                }.getOrDefault(emptySequence())
            }.toList()
    }

    private fun balancedJson(script: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until script.length) {
            val char = script[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                open -> depth++
                close -> if (--depth == 0) return script.substring(start, i + 1)
            }
        }
        return null
    }

    /** MacCMS 播放配置是 JSON 数据，不执行站点脚本；失败时交给 WebView。 */
    private fun playerMedia(script: String, pageUrl: String): String? {
        val match = Regex("(?:var\\s+)?player_[\\w]+\\s*=\\s*\\{").find(script) ?: return null
        val start = script.indexOf('{', match.range.first)
        var depth = 0
        var quoted = false
        var escaped = false
        var end = -1
        for (i in start until script.length) {
            val char = script[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) { end = i + 1; break }
            }
        }
        if (end < 0) return null
        return runCatching {
            val config = JsonParser.parseString(script.substring(start, end)).asJsonObject
            var value = config.get("url")?.asString ?: return null
            val encryption = config.get("encrypt")?.asInt ?: 0
            if (encryption == 2) value = String(Base64.getDecoder().decode(value), Charsets.UTF_8)
            if (encryption == 1 || encryption == 2) {
                value = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
            }
            WebSourceParser.resolve(pageUrl, value)?.takeIf(::isDirectMedia)
        }.getOrNull()
    }

    private fun isDirectMedia(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        // 不把包含 m3u8 参数的 iframe/解析服务地址误当成媒体。
        return WebSourceClassifier.classify(parsed.newBuilder().query(null).build().toString()) !=
            WebSourceClassifier.Kind.UNKNOWN
    }
}
