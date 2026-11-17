package com.aiexile.animetrack.data.websource

import com.aiexile.animetrack.model.WebSourceEntry
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourceRule
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.w3c.dom.Node
import java.net.URLEncoder

/**
 * 规则驱动的 HTML 解析，只做纯计算，便于单元测试。
 *
 * 关键词清洗沿用 Animeko 的策略：去掉「剧场版 / OVA / 总集篇」等标记与括号内容，
 * 避免搜索续作时被剧场版或特典挤到后面。
 */
object WebSourceParser {

    private const val MAX_ENTRIES = 60
    private const val MAX_EPISODES = 500

    private val brackets = Regex("[\\[【(（].*?[\\]】)）]")
    private val movieMarkers = Regex(
        "剧场版|劇場版|OVA|OAD|总集篇|總集篇|特别篇|特別篇",
        RegexOption.IGNORE_CASE
    )
    private val whitespace = Regex("\\s+")

    fun buildSearchUrl(rule: WebSourceRule, keyword: String): String {
        val encoded = URLEncoder.encode(cleanKeyword(keyword), Charsets.UTF_8.name())
        return rule.searchUrl
            .replace(WebSourceRule.KEYWORD_AT, encoded)
            .replace(WebSourceRule.KEYWORD_BRACED, encoded)
    }

    fun cleanKeyword(keyword: String): String = keyword
        .replace(brackets, " ")
        .replace(movieMarkers, " ")
        .replace(whitespace, " ")
        .trim()

    fun parseEntries(
        html: ByteArray,
        rule: WebSourceRule,
        searchUrl: String
    ): List<WebSourceEntry> {
        val document = XPathHtml.parse(html, searchUrl)
        // 以实际请求到的地址为基准：站点换域名/跳转后，规则里的 baseUrl 可能已过期，
        // 仍用它拼相对链接会落到旧域名。
        val base = searchUrl.ifBlank { rule.baseUrl }
        val seen = HashSet<String>()
        return XPathHtml.select(document, rule.searchList)
            .asSequence()
            .mapNotNull { item ->
                val name = XPathHtml.text(XPathHtml.select(item, rule.searchName).firstOrNull())
                    .ifBlank { XPathHtml.text(item) }
                if (name.isBlank()) return@mapNotNull null

                val link = XPathHtml.select(item, rule.searchResult)
                    .firstOrNull()
                    ?.let(::hrefOrDescendant)
                val url = resolve(base, link) ?: return@mapNotNull null

                WebSourceEntry(name, url)
            }
            .filter { seen.add(it.url) }
            .take(MAX_ENTRIES)
            .toList()
    }

    fun parseEpisodes(
        html: ByteArray,
        rule: WebSourceRule,
        detailUrl: String
    ): List<WebSourceEpisode> {
        val document = XPathHtml.parse(html, detailUrl)
        val base = detailUrl.ifBlank { rule.baseUrl }
        val roads = if (rule.chapterRoads.isBlank()) {
            listOf(document)
        } else {
            XPathHtml.select(document, rule.chapterRoads)
        }

        val seen = HashSet<String>()
        val episodes = mutableListOf<WebSourceEpisode>()
        roads.forEachIndexed { index, road ->
            // 线路容器通常把标题和整段剧集放在一起，取整段文本会把剧集名拼进线路名；
            // 只保留序号，由界面本地化渲染为「线路 N」。
            val channelIndex = if (roads.size > 1) index else null
            XPathHtml.select(road, rule.chapterResult).forEach { node ->
                val name = XPathHtml.text(node)
                if (name.isBlank()) return@forEach
                val url = resolve(base, hrefOrDescendant(node)) ?: return@forEach
                if (!seen.add(url)) return@forEach
                episodes += WebSourceEpisode(name, url, channelIndex)
            }
        }
        return episodes.take(MAX_EPISODES)
    }

    /** 选择器可能落在容器上（如 `<li>`），链接在其内部 `<a>` 上。 */
    private fun hrefOrDescendant(node: Node): String? {
        XPathHtml.href(node)?.let { return it }
        return XPathHtml.select(node, ".//a").firstOrNull()?.let(XPathHtml::href)
    }

    fun resolve(base: String, href: String?): String? {
        val link = href?.trim().orEmpty()
        if (link.isEmpty()) return null
        val baseUrl = base.toHttpUrlOrNull() ?: return null
        val normalized = if (link.startsWith("//")) "${baseUrl.scheme}:$link" else link
        return runCatching { baseUrl.resolve(normalized)?.toString() }.getOrNull()
    }
}
