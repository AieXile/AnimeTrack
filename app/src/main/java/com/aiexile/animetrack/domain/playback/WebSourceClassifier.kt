package com.aiexile.animetrack.domain.playback

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 网页资源类型判断；只看 URL，不发起额外请求。 */
object WebSourceClassifier {
    enum class Kind { HLS, DASH, VIDEO, UNKNOWN }

    private val videoExtensions = setOf("mp4", "webm", "mkv", "mov", "flv", "m4v")

    fun classify(url: String): Kind {
        val parsed = url.toHttpUrlOrNull() ?: return Kind.UNKNOWN
        val path = parsed.encodedPath.lowercase()
        val full = (path + "?" + (parsed.query ?: "")).lowercase()
        val extension = path.substringAfterLast('.', "")
        return when {
            extension == "m3u8" || full.contains("m3u8") || full.contains("/hls/") -> Kind.HLS
            extension == "mpd" || full.contains("/manifest") -> Kind.DASH
            extension in videoExtensions -> Kind.VIDEO
            else -> Kind.UNKNOWN
        }
    }

    /**
     * 结合响应类型判断：许多播放器请求的清单地址没有扩展名，
     * 只能靠 Content-Type（application/vnd.apple.mpegurl / dash+xml）识别。
     */
    fun classify(url: String, contentType: String?): Kind {
        val type = contentType?.lowercase().orEmpty()
        if (type.contains("mpegurl")) return Kind.HLS
        if (type.contains("dash+xml")) return Kind.DASH
        val byUrl = classify(url)
        if (byUrl != Kind.UNKNOWN) return byUrl
        if (type.startsWith("video/") || type.contains("mp4")) return Kind.VIDEO
        return Kind.UNKNOWN
    }

    fun isCandidate(url: String): Boolean = classify(url) != Kind.UNKNOWN

    fun isCandidate(url: String, contentType: String?): Boolean =
        classify(url, contentType) != Kind.UNKNOWN

    fun mimeType(url: String): String? = when (classify(url)) {
        Kind.HLS -> "application/x-mpegURL"
        Kind.DASH -> "application/dash+xml"
        Kind.VIDEO -> "video/mp4"
        Kind.UNKNOWN -> null
    }

    fun label(url: String): String = when (classify(url)) {
        Kind.HLS -> "HLS"
        Kind.DASH -> "DASH"
        Kind.VIDEO -> "视频"
        Kind.UNKNOWN -> "未知"
    }
}
