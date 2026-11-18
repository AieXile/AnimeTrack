package com.aiexile.animetrack.data.player.web

/** 网页解析页选中资源后交给播放器的一次性请求。 */
data class PendingWebPlaybackRequest(
    val url: String,
    val mimeType: String?,
    val title: String?,
    val pageUrl: String?,
    /** WebView 捕获到的原始请求头（Referer / Origin / Cookie / UA 等） */
    val headers: Map<String, String> = emptyMap(),
    /** 规则/解析 WebView 的配置，独立于实际捕获的媒体请求头。 */
    val userAgent: String? = null,
    val referer: String? = null,
    /** 先进入播放器，再解析该网页；url 此时是剧集页面而非媒体地址。 */
    val resolvePage: Boolean = false,
    val queue: com.aiexile.animetrack.domain.playback.WebPlaybackQueue? = null,
    /** 历史续播先打开播放器，再获取原网页队列；失败仍可单集解析。 */
    val restoreQueue: Boolean = false,
    val progressMediaId: String? = null,
    val seriesUrl: String? = null,
    /** 从番剧详情页进入时绑定的番剧；为空表示全局网页历史，不推进追番进度。 */
    val animeId: Int? = null,
    val animeTitle: String? = null
)

object PendingWebPlayback {
    @Volatile
    private var request: PendingWebPlaybackRequest? = null

    fun set(value: PendingWebPlaybackRequest) {
        request = value
    }

    fun consume(): PendingWebPlaybackRequest? {
        val current = request
        request = null
        return current
    }

    fun clear() {
        request = null
    }
}
