package com.aiexile.animetrack.data.player.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 当前网页播放会话的请求头。
 *
 * 只对当前会话的页面/媒体域名附加 Cookie；UA 与 Referer 作为通用头，
 * 避免把某个站点的 Cookie 发给完全无关的主机。
 */
object WebPlaybackSession {

    private data class Defaults(
        val userAgent: String? = null,
        val referer: String? = null,
        val origin: String? = null
    )

    /** 候选资源在 WebView 中实际使用的请求头，按主机保存 */
    @Volatile
    private var capturedByHost: Map<String, Map<String, String>> = emptyMap()

    private val hopByHopHeaders = setOf(
        "host", "connection", "keep-alive", "transfer-encoding", "upgrade",
        "content-length", "range", "accept-encoding"
    )

    @Volatile
    private var defaults = Defaults()

    @Volatile
    private var cookiesByHost: Map<String, String> = emptyMap()

    /** 播放前准备会话；pageUrl 为空时只使用默认 UA。 */
    fun prepare(
        context: Context,
        pageUrl: String?,
        mediaUrl: String,
        capturedHeaders: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        referer: String? = null
    ) {
        clear()
        val effectiveUserAgent = capturedHeaders.valueOf("User-Agent")
            ?: userAgent?.takeIf { it.isNotBlank() }
            ?: runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        val page = pageUrl?.toHttpUrlOrNull()
        // 已捕获请求时尊重其 Referer/Origin 的缺省策略，包括明确不发送这些头的页面。
        // 未捕获到请求（DOM/JS 发现直链）时，才使用规则配置与页面作为兜底。
        val effectiveReferer = if (capturedHeaders.isNotEmpty()) {
            capturedHeaders.valueOf("Referer")
        } else {
            referer?.takeIf { it.isNotBlank() } ?: pageUrl?.takeIf { it.isNotBlank() }
        }
        val origin = if (capturedHeaders.isNotEmpty()) {
            capturedHeaders.valueOf("Origin")
        } else {
            page?.let { "${it.scheme}://${it.host}" }
        }
        defaults = Defaults(
            userAgent = effectiveUserAgent,
            referer = effectiveReferer,
            origin = origin
        )

        val pageHost = page?.host
        val mediaHost = mediaUrl.toHttpUrlOrNull()?.host
        if (mediaHost != null && capturedHeaders.isNotEmpty()) {
            capturedByHost = mapOf(
                mediaHost to capturedHeaders.filterKeys {
                    it.lowercase() !in hopByHopHeaders
                }
            )
        }
        val cookies = mutableMapOf<String, String>()
        if (pageHost != null) {
            runCatching { CookieManager.getInstance().getCookie(pageUrl) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { cookies[pageHost] = it }
        }
        if (mediaHost != null && mediaHost != pageHost) {
            runCatching { CookieManager.getInstance().getCookie(mediaUrl) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { cookies[mediaHost] = it }
        }
        cookiesByHost = cookies
    }

    /** 按主机组装请求头；捕获头优先，头名大小写不敏感。 */
    internal fun buildHeaders(
        userAgent: String?,
        referer: String?,
        cookiesByHost: Map<String, String>,
        host: String?,
        origin: String? = null,
        captured: Map<String, String> = emptyMap()
    ): Map<String, String> = buildMap {
        captured.filterKeys { it.lowercase() !in hopByHopHeaders }.forEach { (name, value) ->
            if (keys.none { it.equals(name, ignoreCase = true) }) put(name, value)
        }
        fun fallback(name: String, value: String?) {
            if (!value.isNullOrBlank() && keys.none { it.equals(name, ignoreCase = true) }) {
                put(name, value)
            }
        }
        fallback("User-Agent", userAgent)
        if (captured.isEmpty()) {
            fallback("Referer", referer)
            fallback("Origin", origin)
        }
        fallback("Cookie", host?.let(cookiesByHost::get))
    }

    fun headersFor(host: String?): Map<String, String> {
        return buildHeaders(
            userAgent = defaults.userAgent,
            referer = defaults.referer,
            cookiesByHost = cookiesByHost,
            host = host,
            origin = defaults.origin,
            captured = host?.let { capturedByHost[it] }.orEmpty()
        )
    }

    private fun Map<String, String>.valueOf(name: String): String? =
        entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun clear() {
        defaults = Defaults()
        cookiesByHost = emptyMap()
        capturedByHost = emptyMap()
    }
}
