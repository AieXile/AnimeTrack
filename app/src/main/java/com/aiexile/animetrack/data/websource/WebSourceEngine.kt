package com.aiexile.animetrack.data.websource

import com.aiexile.animetrack.model.WebSourceEntry
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourceRule
import com.aiexile.animetrack.model.WebSourcePage
import com.aiexile.animetrack.model.WebSourceChannel
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 规则字段缺失，无法完成搜索或选集。 */
class WebSourceRuleInvalidException : Exception("invalid rule")

/**
 * 数据源的「搜索 → 选集」链路。
 *
 * 直接网页入口先读取选集数据，媒体提取失败时复用 WebView 嗅探。
 */
class WebSourceEngine(private val http: WebSourceHttpClient) {

    suspend fun page(
        url: String,
        userAgent: String,
        referer: String?,
        rule: WebSourceRule?
    ): WebSourcePage = withContext(Dispatchers.Default) {
        val response = http.getPage(url, userAgent, referer)
        var parsed = WebPageParser.parse(response.bytes, response.url).copy(selectedEpisodeUrl = url)
        parsed.detailUrl?.let { detail ->
            try {
                val detailResponse = http.getPage(detail, userAgent, referer ?: response.url)
                val full = WebPageParser.parse(detailResponse.bytes, detailResponse.url)
                if (full.channels.isNotEmpty()) parsed = full.copy(selectedEpisodeUrl = url,
                    detailUrl = detailResponse.url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                // 详情页不可用时仍可选择播放页中已取得的当前线路。
            }
        }
        if (parsed.channels.isEmpty() && rule?.isUsable == true &&
            rule.baseUrl.toHttpUrlOrNull()?.host == response.url.toHttpUrlOrNull()?.host) {
            val episodes = WebSourceParser.parseEpisodes(response.bytes, rule, response.url)
            parsed = parsed.copy(channels = episodes.groupBy { it.channelIndex }.values.map {
                WebSourceChannel(null, it)
            })
        }
        parsed
    }

    suspend fun media(url: String, userAgent: String, referer: String?): String? =
        resolveMedia(url, userAgent, referer).firstOrNull()?.url

    suspend fun resolveMedia(url: String, userAgent: String, referer: String?): List<com.aiexile.animetrack.model.WebMediaSource> =
        withContext(Dispatchers.Default) {
            val response = http.getPage(url, userAgent, referer)
            WebPageParser.parse(response.bytes, response.url).mediaSources
        }

    suspend fun search(rule: WebSourceRule, keyword: String): List<WebSourceEntry> =
        withContext(Dispatchers.Default) {
            if (!rule.isUsable) throw WebSourceRuleInvalidException()
            if (rule.isApiSearch) {
                val call = WebSourceApiParser.buildSearchCall(rule, keyword)
                return@withContext WebSourceApiParser.parseSearch(
                    http.executeApi(call, rule),
                    rule
                )
            }
            val searchUrl = WebSourceParser.buildSearchUrl(rule, keyword)
            // 跟随跳转后以最终地址为基准解析相对链接
            val response = http.getPage(searchUrl, rule.effectiveUserAgent, rule.effectiveReferer)
            WebSourceParser.parseEntries(response.bytes, rule, response.url)
        }

    suspend fun episodes(rule: WebSourceRule, entry: WebSourceEntry): List<WebSourceEpisode> =
        withContext(Dispatchers.Default) {
            if (!rule.isUsable) throw WebSourceRuleInvalidException()
            if (rule.isApiChapter) {
                val call = WebSourceApiParser.buildChapterCall(rule, entry.url)
                return@withContext WebSourceApiParser.parseEpisodes(
                    http.executeApi(call, rule),
                    rule,
                    entry.url
                )
            }
            val html = http.getBytes(entry.url, rule)
            WebSourceParser.parseEpisodes(html, rule, entry.url)
        }

    /** 导入规则时从链接抓取原始 JSON。 */
    suspend fun fetchRuleSource(url: String): String = http.getText(url)
}
