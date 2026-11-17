package com.aiexile.animetrack.model

/** 一个网页的原生选集数据；保留入口地址以定位用户打开的集数。 */
data class WebSourcePage(
    val title: String,
    val url: String,
    val channels: List<WebSourceChannel>,
    val selectedEpisodeUrl: String = url,
    val detailUrl: String? = null,
    val mediaUrl: String? = null,
    val mediaSources: List<WebMediaSource> = emptyList()
)

data class WebSourceChannel(
    val name: String?,
    val episodes: List<WebSourceEpisode>
)
