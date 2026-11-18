package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourcePage
import java.util.Locale

/** 网页队列只保存剧集页面，切集时再解析短时有效的媒体地址。 */
data class WebPlaybackQueue(
    val page: WebSourcePage,
    val channelIndex: Int,
    val episodeIndex: Int
) {
    val episodes get() = page.channels.getOrNull(channelIndex)?.episodes.orEmpty()
    val episode get() = episodes.getOrNull(episodeIndex)
    val seriesUrl get() = page.detailUrl ?: page.channels.firstOrNull()?.episodes?.firstOrNull()?.url ?: page.url

    fun select(index: Int): WebPlaybackQueue? =
        if (index in episodes.indices) copy(episodeIndex = index) else null

    /** 不按列表下标猜测对应集，防止某条线路缺集或倒序时误播。 */
    fun switchChannel(index: Int): WebPlaybackQueue? {
        val current = episode ?: return null
        if (episodes.count { identity(it) == identity(current) } != 1) return null
        val candidates = page.channels.getOrNull(index)?.episodes ?: return null
        val matches = candidates.withIndex().filter { identity(it.value) == identity(current) }
        return matches.singleOrNull()?.let { copy(channelIndex = index, episodeIndex = it.index) }
    }

    /** 一次计算整条线路，选集页刷新进度时避免为每集重复扫描整个列表。 */
    val mediaKeys: List<String> by lazy {
        val identities = episodes.map(::identity)
        val counts = identities.groupingBy { it }.eachCount()
        episodes.mapIndexed { index, episode ->
            val id = identities[index]
            if (counts[id] == 1) "web:$seriesUrl|$id" else "web:${episode.url}"
        }
    }

    fun mediaKey(): String = mediaKeys.getOrNull(episodeIndex) ?: "web:$seriesUrl"

    companion object {
        /** 纯集号名称 → 数字；"第 3 集" / "EP03" / "3" 可识别，其他名称返回 null。 */
        fun episodeNumberOf(name: String): Int? {
            val normalized = name.lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
            return Regex("^(?:第|ep\\.?|episode)?0*(\\d+)(?:集|话|話)?$")
                .matchEntire(normalized)?.groupValues?.get(1)?.toIntOrNull()
        }

        fun from(page: WebSourcePage, episodeUrl: String): WebPlaybackQueue? {
            page.channels.forEachIndexed { channel, source ->
                val index = source.episodes.indexOfFirst { it.url == episodeUrl }
                if (index >= 0) return WebPlaybackQueue(page, channel, index)
            }
            return null
        }

        private fun identity(episode: WebSourceEpisode): String {
            val name = episode.name.lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
            val number = episodeNumberOf(episode.name)
            return number?.let { "episode:$it" } ?: "name:$name"
        }
    }
}
