package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.media.LocalMediaMatchState
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.sync.BangumiSyncManager
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 连续观看进度的纯规则，便于单元测试。 */
object WatchProgressRules {
    /**
     * 在已有观看数基础上推进：已看集数视为完成，从 current+1 起连续完成才继续推进。
     * 跳看第 8 集不会把中间集算作已看，已有进度也不会被重置。
     */
    fun advanceCount(completed: Set<Int>, current: Int): Int {
        var count = current.coerceAtLeast(0)
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
 * 本地与网页播放共用：完成记录都写入同一张 `local_episode_progress`，
 * 只有用户开启「播放完成后更新追番进度」且番剧、集数明确时才生效。
 * 观看进度按已有进度继续推进，状态/完结语义复用详情页既有规则。
 */
class LocalWatchProgressUseCase internal constructor(
    private val localMediaRepository: LocalMediaRepository,
    private val animeRepository: AnimeRepository,
    private val updateProgressEnabled: Flow<Boolean>,
    private val autoCompleteEnabled: Flow<Boolean>,
    private val pushProgressThenStatus: suspend (Int, Int, AnimeStatus) -> Unit
) {
    constructor(
        localMediaRepository: LocalMediaRepository,
        animeRepository: AnimeRepository,
        settingsRepository: SettingsRepository,
        bangumiSyncManager: BangumiSyncManager
    ) : this(
        localMediaRepository,
        animeRepository,
        settingsRepository.playerUpdateProgressOnComplete,
        settingsRepository.autoCompleteEnabled,
        bangumiSyncManager::pushProgressThenStatus
    )

    private val completionMutex = Mutex()

    internal suspend fun onLocalEpisodeCompleted(mediaId: Long): Boolean {
        if (!updateProgressEnabled.first()) return false
        val file = localMediaRepository.getMediaById(mediaId) ?: return false
        if (file.matchState != LocalMediaMatchState.CONFIRMED || !LocalPlaybackQueue.isEpisode(file)) return false
        val episode = file.episodeNumber ?: return false
        val animeId = file.matchedAnimeId ?: localMediaRepository.findTrackedAnimeIdForArchive(mediaId) ?: return false
        return recordCompletion(animeId, file.seasonNumber, episode)
    }

    suspend fun onEpisodeCompleted(
        animeId: Int,
        seasonNumber: Int?,
        episodeNumber: Int
    ): Boolean {
        if (episodeNumber <= 0 || !updateProgressEnabled.first()) return false
        return recordCompletion(animeId, seasonNumber, episodeNumber)
    }

    private suspend fun recordCompletion(animeId: Int, seasonNumber: Int?, episodeNumber: Int): Boolean =
        completionMutex.withLock {
            val anime = animeRepository.getAnimeById(animeId) ?: return@withLock false
            localMediaRepository.recordEpisodeCompleted(animeId, seasonNumber, episodeNumber)

            val completed = localMediaRepository.getCompletedEpisodes(animeId)
            val target = WatchProgressRules.advanceCount(completed, anime.watchedEpisodes)
            if (target <= anime.watchedEpisodes) return@withLock true

            // 只推进到连续完成的最大集数；跳集由规则本身兜底，不把中间集当作已看
            var updated = anime.copy(
                watchedEpisodes = target,
                lastProgressAt = System.currentTimeMillis()
            )
            if (anime.status == AnimeStatus.PLANNED) {
                updated = updated.copy(status = AnimeStatus.WATCHING)
            }
            if (autoCompleteEnabled.first() &&
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
                pushProgressThenStatus(
                    bangumiId,
                    updated.watchedEpisodes,
                    updated.status
                )
            }
            true
        }
}
