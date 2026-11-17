package com.aiexile.animetrack.model

/** 源站明确声明的清晰度地址；无标签的网络候选不推测清晰度。 */
data class WebMediaSource(
    val url: String,
    val label: String? = null,
    val mimeType: String? = null
)
