package com.aiexile.animetrack.ui.player.coordinator

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.domain.playback.LocalPlaybackQueue
import com.aiexile.animetrack.domain.playback.LocalWatchProgressUseCase
import com.aiexile.animetrack.domain.playback.WatchProgressRules
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.ui.player.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal sealed interface EpisodeCompletionTarget {
    data class Local(val file: LocalMediaEntity) : EpisodeCompletionTarget
    data class Web(val animeId: Int, val episodeNumber: Int) : EpisodeCompletionTarget
}

/**
 * 负责播放器进度轮询、连续有效观看时长统计、周期性与退场落盘，以及单集完播推进追番进度。
 */
internal class PlaybackProgressTracker(
    private val playerRepository: PlayerRepository,
    private val localWatchProgressUseCase: LocalWatchProgressUseCase,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val getController: () -> MediaController?,
    private val getCurrentMediaId: () -> String?,
    private val getRememberPosition: () -> Boolean,
    private val resolveCompletionTarget: (mediaId: String) -> EpisodeCompletionTarget?,
    private val onSavedProgressUpdated: (Map<String, PlaybackProgress>) -> Unit
) {
    companion object {
        private const val TAG = "PlaybackProgressTracker"
        const val POSITION_UPDATE_INTERVAL_MS = 500L

        /** 播放中周期性落盘进度，避免进程被杀时丢失整段观看记录 */
        private const val POSITION_PERSIST_INTERVAL_MS = 15_000L

        fun maxContinuousWatchDeltaMs(speed: Float): Long =
            (POSITION_UPDATE_INTERVAL_MS * speed.coerceAtLeast(1f) * 4f).toLong().coerceAtLeast(3_000L)
    }

    /** 有效观看累计时长（仅统计连续播放，拖动产生的大跳变不计入） */
    var watchedMs: Long = 0L
        private set

    /** 上一次采样位置；-1 表示本段播放尚无有效采样 */
    private var lastSampledPositionMs: Long = -1L
    private var lastSampledDurationMs: Long = 0L
    private var lastPersistAt: Long = 0L
    private var positionUpdateJob: Job? = null

    var savedProgress: Map<String, PlaybackProgress> = emptyMap()
        private set

    /** 已上报完成的 番剧:集数，避免重复推进进度 */
    private val completedEpisodeKeys = mutableSetOf<String>()

    /**
     * 独立于 viewModelScope 的持久化作用域：
     * viewModelScope 在 onCleared 前即被关闭，其内启动的保存协程不会执行；
     * 此作用域 fire-and-forget，不手动取消，让退出时的最后一次写入自然完成。
     */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeSavedProgress() {
        scope.launch {
            playerRepository.progress.collect { records ->
                savedProgress = records
                onSavedProgressUpdated(records)
            }
        }
    }

    fun startPositionUpdates() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = scope.launch {
            while (isActive) {
                updatePositionState()
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun resetSampling(initialWatchedMs: Long = 0L) {
        watchedMs = initialWatchedMs
        lastSampledPositionMs = -1L
        lastSampledDurationMs = 0L
    }

    suspend fun seedWatchedMsFromSaved(mediaId: String) {
        if (!getRememberPosition()) return
        val saved = playerRepository.progress.first()[mediaId]
        if (saved != null && !saved.completed) {
            watchedMs = maxOf(watchedMs, saved.watchedMs)
        }
    }

    fun updatePositionState() {
        if (uiState.value.isPreparingMedia) return
        val c = getController() ?: return
        val hasDuration = c.duration > 0
        val position = if (hasDuration) c.currentPosition else 0L
        val duration = if (hasDuration) c.duration else 0L

        if (c.isPlaying) {
            // 只累计连续播放的时长（随倍速放宽采样间隔抖动容忍）；大跨度拖动不视为有效观看
            val maxDelta = maxContinuousWatchDeltaMs(c.playbackParameters.speed)
            val delta = position - lastSampledPositionMs
            if (lastSampledPositionMs >= 0 && delta in 1..maxDelta) {
                watchedMs += delta
            }
            val now = System.currentTimeMillis()
            if (now - lastPersistAt >= POSITION_PERSIST_INTERVAL_MS) {
                lastPersistAt = now
                saveCurrentPosition()
            }
        }
        lastSampledPositionMs = position
        lastSampledDurationMs = duration

        uiState.update {
            it.copy(
                currentPositionMs = position,
                durationMs = duration,
                bufferedPositionMs = if (hasDuration) c.bufferedPosition else 0
            )
        }
    }

    /** 按「记住播放位置」开关读取已存进度（无记录/开关关闭返回 0） */
    suspend fun readSavedPosition(mediaId: String): Long {
        if (!getRememberPosition()) return 0L
        return try {
            playerRepository.getPlaybackPosition(mediaId)?.coerceAtLeast(0L) ?: 0L
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogManager.w(TAG, "Failed to read saved position", e)
            0L
        }
    }

    /**
     * 按「记住播放位置」开关保存当前进度（幂等，可在暂停/播完/退出时多次调用）。
     * 仅在 STATE_READY 后才取位置，避免 IDLE/BUFFERING 期间读到无效的 0。
     */
    fun saveCurrentPosition() {
        val mediaId = getCurrentMediaId() ?: return
        if (!getRememberPosition()) return
        val c = getController() ?: return
        if (uiState.value.isPreparingMedia || c.currentMediaItem?.mediaId != mediaId) return
        if (c.playbackState != Player.STATE_READY) return
        val position = c.currentPosition
        val duration = c.duration
        if (duration <= 0 || position <= 0) return
        val completed = WatchProgressRules.isCompleted(position, duration, watchedMs)
        persistPlaybackProgress(mediaId, position, duration, completed)
    }

    /** 切集/退出时用最近一次采样落盘；没有有效采样则跳过 */
    fun persistSampledPosition() {
        if (!getRememberPosition()) return
        val mediaId = getCurrentMediaId() ?: return
        val position = lastSampledPositionMs
        if (position <= 0 || lastSampledDurationMs <= 0) return
        val duration = lastSampledDurationMs
        persistPlaybackProgress(
            mediaId,
            position,
            duration,
            WatchProgressRules.isCompleted(position, duration, watchedMs)
        )
    }

    /**
     * 播放列表自动切集时 ExoPlayer 不会为上一项触发 STATE_ENDED，
     * 必须在这里用采样值结算：完成则清续播点并上报，未完成则保留进度。
     */
    fun finalizeCurrentForTransition() {
        val mediaId = getCurrentMediaId() ?: return
        val duration = lastSampledDurationMs
        if (duration <= 0) return
        val position = lastSampledPositionMs.coerceAtLeast(0)
        val completed = WatchProgressRules.isCompleted(position, duration, watchedMs)
        persistPlaybackProgress(mediaId, position, duration, completed)
    }

    /** 播放结束：完成判定基于有效观看时长，直接拖到尾不算看完 */
    fun finishCurrentMedia() {
        val mediaId = getCurrentMediaId() ?: return
        val c = getController() ?: return
        val duration = c.duration
        if (duration <= 0) return
        val position = c.currentPosition
        val maxDelta = maxContinuousWatchDeltaMs(c.playbackParameters.speed)
        val tailDelta = position - lastSampledPositionMs
        if (lastSampledPositionMs >= 0 && tailDelta in 1..maxDelta) {
            watchedMs += tailDelta
            lastSampledPositionMs = position
        }
        val completed = WatchProgressRules.isCompleted(position, duration, watchedMs)
        if (!completed) {
            AppLogManager.i(
                TAG,
                "Playback ended without completion: mediaId=$mediaId, pos=$position, dur=$duration, watched=$watchedMs"
            )
        }
        persistPlaybackProgress(mediaId, position, duration, completed)
    }

    private fun persistPlaybackProgress(
        mediaId: String,
        position: Long,
        duration: Long,
        completed: Boolean
    ) {
        val observedAt = System.currentTimeMillis()
        val watched = watchedMs
        val remember = getRememberPosition()
        // 在主线程同步读取当前队列/番剧上下文并触发单集完成，避免自动连播切集后状态被覆盖
        if (completed) notifyEpisodeCompleted(mediaId)
        persistScope.launch {
            try {
                if (remember && position > 0) {
                    playerRepository.savePlaybackPosition(mediaId, position, duration, completed, observedAt, watched)
                } else if (completed) {
                    playerRepository.clearPlaybackPosition(mediaId)
                }
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to save playback record", e)
            }
        }
    }

    private fun notifyEpisodeCompleted(mediaId: String) {
        when (val target = resolveCompletionTarget(mediaId)) {
            is EpisodeCompletionTarget.Local -> notifyLocalEpisodeCompleted(target.file)
            is EpisodeCompletionTarget.Web -> notifyAnimeEpisodeCompleted(target.animeId, null, target.episodeNumber)
            null -> Unit
        }
    }

    private fun notifyLocalEpisodeCompleted(file: LocalMediaEntity) {
        val animeId = file.matchedAnimeId ?: return
        val episode = file.episodeNumber ?: return
        if (!file.isUserOverride || !LocalPlaybackQueue.isEpisode(file) || file.episodeEndNumber != null) {
            AppLogManager.i(
                TAG,
                "Skip local progress update: mediaId=${file.id}, userOverride=${file.isUserOverride}, endEp=${file.episodeEndNumber}"
            )
            return
        }
        notifyAnimeEpisodeCompleted(animeId, file.seasonNumber, episode)
    }

    private fun notifyAnimeEpisodeCompleted(animeId: Int, seasonNumber: Int?, episode: Int) {
        val dedupeKey = "$animeId:$episode"
        if (!completedEpisodeKeys.add(dedupeKey)) return
        persistScope.launch {
            try {
                localWatchProgressUseCase.onEpisodeCompleted(animeId, seasonNumber, episode)
            } catch (e: Exception) {
                completedEpisodeKeys.remove(dedupeKey)
                AppLogManager.e(TAG, "Failed to update watch progress", e)
            }
        }
    }
}