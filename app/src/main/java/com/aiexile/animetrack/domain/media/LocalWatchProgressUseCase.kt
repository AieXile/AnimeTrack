package com.aiexile.animetrack.domain.media

import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.sync.BangumiSyncManager
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.first

/** 连续观看进度的纯规则，便于单元测试。 */
object WatchProgressRules {
    /**
     * 从第 1 集开始连续完成的最大集数。
     * 跳看第 8 集不会把前 7 集算作已看，避免进度虚高。
     */
    fun contiguousCompleted(completed: Set<Int>): Int {
        if (completed.isEmpty()) return 0
        var count = 0
        while (completed.contains(count + 1)) count++
        return count
    }

    /**
     * 有效观看是否达到完成阈值。
     * 不能只看当前播放位置：直接拖到片尾不应算看完。
     */
    fun isCompleted(
        positionMs: Long,
        durationMs: Long,
        watchedMs: Long,
        positionThreshold: Double = 0.95,
        watchedThreshold: Double = 0.90
    ): Boolean {
        if (durationMs <= 0) return false
        // 自然播完或覆盖足够时长：有效观看达标
        if (watchedMs >= durationMs * watchedThreshold) return true
        // 用户快进片头/片尾：只有确实看过大部分内容才算完成，
        // 直接拖到片尾（watchedMs 很低）不推进追番进度
        return positionMs >= durationMs * positionThreshold &&
            watchedMs >= durationMs * 0.60
    }
}

/**
 * 单集播放完成 → 追番进度。
 *
 * 只有用户开启「播放完成后更新追番进度」且文件已确认关联番剧、集数明确时才生效。
 * 观看进度按连续完成集数推进，状态/完结语义复用详情页既有规则。
 */
class LocalWatchProgressUseCase(
    private val localMediaRepository: LocalMediaRepository,
    private val animeRepository: AnimeRepository,
    private val settingsRepository: SettingsRepository,
    private val bangumiSyncManager: BangumiSyncManager
) {
    suspend fun onEpisodeCompleted(
        animeId: Int,
        seasonNumber: Int?,
        episodeNumber: Int
    ) {
        if (!settingsRepository.playerUpdateProgressOnComplete.first()) return
        if (episodeNumber <= 0) return

        localMediaRepository.recordEpisodeCompleted(animeId, seasonNumber, episodeNumber)

        val anime = animeRepository.getAnimeById(animeId) ?: return
        val completed = localMediaRepository.getCompletedEpisodes(animeId)
        val target = WatchProgressRules.contiguousCompleted(completed)
        if (target <= anime.watchedEpisodes) return

        // 只推进到连续完成的最大集数；跳集由规则本身兜底，不把中间集当作已看
        var updated = anime.copy(
            watchedEpisodes = target,
            lastProgressAt = System.currentTimeMillis()
        )
        if (anime.status == AnimeStatus.PLANNED) {
            updated = updated.copy(status = AnimeStatus.WATCHING)
        }
        if (settingsRepository.autoCompleteEnabled.first() &&
            anime.totalEpisodes > 0 &&
            target >= anime.totalEpisodes &&
            anime.status != AnimeStatus.COMPLETED
        ) {
            updated = updated.copy(
                status = AnimeStatus.COMPLETED,
                isFinished = anime.airingStatusOverride?.let { !it } ?: true,
                finishDate = anime.finishDate ?: System.currentTimeMillis()
            )
        }

        animeRepository.updateAnime(updated)
        updated.bangumiId?.let { bangumiId ->
            bangumiSyncManager.pushProgressThenStatus(
                bangumiId,
                updated.watchedEpisodes,
                updated.status
            )
        }
    }
}
