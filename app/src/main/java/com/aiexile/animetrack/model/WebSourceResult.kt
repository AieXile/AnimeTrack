package com.aiexile.animetrack.model

/** 搜索结果页的一个条目（番剧）。 */
data class WebSourceEntry(
    val name: String,
    val url: String
)

/** 详情页的一集；channelIndex 为线路序号（规则只给出一条线路时为空）。 */
data class WebSourceEpisode(
    val name: String,
    val url: String,
    val channelIndex: Int? = null
)
