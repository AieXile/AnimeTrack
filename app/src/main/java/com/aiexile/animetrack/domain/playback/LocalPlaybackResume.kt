package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.model.PlaybackProgress

/**
 * 详情页「继续观看」目标集的选择规则。
 *
 * 优先级：
 * 1. 有未完成进度、且最近播放过的剧集——用户上次看到一半的那一集；
 * 2. 第一个尚未完成的剧集——按季/集顺序；
 * 3. 全部看完时回到第一集（重看）。
 *
 * 只处理已确认关联的剧集列表，不访问数据库与播放器。
 */
object LocalPlaybackResume {

    fun mediaKey(mediaId: Long): String = "local:$mediaId"

    fun pick(
        episodes: List<LocalMediaEntity>,
        progress: Map<String, PlaybackProgress>,
        completedEpisodes: Set<Int>
    ): LocalMediaEntity? {
        if (episodes.isEmpty()) return null

        val inProgress = episodes
            .mapNotNull { media ->
                val item = progress[mediaKey(media.id)] ?: return@mapNotNull null
                if (item.completed || item.positionMs <= 0L) return@mapNotNull null
                media to item.updatedAt
            }
            .maxByOrNull { it.second }
        if (inProgress != null) return inProgress.first

        return episodes.firstOrNull { media ->
            val episode = media.episodeNumber
            episode == null || episode !in completedEpisodes
        } ?: episodes.first()
    }
}
