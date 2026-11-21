package com.aiexile.animetrack.ui.player

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.service.PlaybackSessionCommands
import com.aiexile.animetrack.data.player.web.PendingWebPlaybackRequest
import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository
import com.aiexile.animetrack.data.player.web.WebPlaybackSession
import com.aiexile.animetrack.data.websource.WebSourceEngine
import com.aiexile.animetrack.data.websource.WebSourceRuleRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.playback.LocalWatchProgressUseCase
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.WebMediaSource
import com.aiexile.animetrack.ui.player.coordinator.EpisodeCompletionTarget
import com.aiexile.animetrack.ui.player.coordinator.LocalPlaybackCoordinator
import com.aiexile.animetrack.ui.player.coordinator.PlaybackProgressTracker
import com.aiexile.animetrack.ui.player.coordinator.PlaybackSessionCoordinator
import com.aiexile.animetrack.ui.player.coordinator.WebPlaybackCoordinator
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 播放器页面控制器：手里握的是 MediaController（遥控器），
 * 真正的 ExoPlayer 常驻 [com.aiexile.animetrack.data.player.service.PlaybackService] 后台服务中。
 *
 * 页面只负责发指令与编排各播放子协调器；
 * 进度追踪、会话控制、网页解析与本地队列分别委托给 `ui.player.coordinator` 下的协调器处理。
 */
@UnstableApi
class PlayerViewModel(
    private val application: Application,
    /** 进入播放器的番剧 id；0 表示本地文件 / 全局网页入口，不关联追番数据 */
    private val animeId: Int,
    private val animeRepository: AnimeRepository,
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository,
    private val localMediaRepository: LocalMediaRepository,
    private val localWatchProgressUseCase: LocalWatchProgressUseCase,
    private val webSourceEngine: WebSourceEngine,
    private val webPlaybackHistoryRepository: WebPlaybackHistoryRepository,
    private val webSourceRuleRepository: WebSourceRuleRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /**
     * 播放页身份卡使用的番剧记录：封面、追番进度。
     * 全局网页入口（animeId = 0）没有关联番剧，恒为 null。
     */
    val trackedAnime: StateFlow<Anime?> =
        if (animeId > 0) {
            animeRepository.observeAnimeById(animeId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        } else {
            MutableStateFlow<Anime?>(null).asStateFlow()
        }

    private var currentMediaId: String? = null
    private var lastPlayRequest: PlayRequest? = null

    /** 长按加速前的原始播放速度 */
    private var speedBeforeLongPress: Float = 1f

    /** 设置项内存缓存：避免触发路径上的异步 IO，保证即时生效 */
    private var longPressSpeedCache: Float = 2f
    private var defaultSpeedCache: Float = 1f
    private var rememberPositionCache: Boolean = true
    private var autoLandscapeCache: Boolean = false

    private val localCoordinator: LocalPlaybackCoordinator
    private val webCoordinator: WebPlaybackCoordinator

    private val progressTracker: PlaybackProgressTracker = PlaybackProgressTracker(
        playerRepository = playerRepository,
        localWatchProgressUseCase = localWatchProgressUseCase,
        scope = viewModelScope,
        uiState = _uiState,
        getController = { sessionCoordinator.controller },
        getCurrentMediaId = { currentMediaId },
        getRememberPosition = { rememberPositionCache },
        resolveCompletionTarget = { mediaId ->
            localCoordinator.findLocalFile(mediaId)?.let { EpisodeCompletionTarget.Local(it) }
                ?: webCoordinator.resolveCompletionTarget(mediaId)
        },
        onSavedProgressUpdated = { records ->
            webCoordinator.publishEpisodeProgress(records)
        }
    )

    private val sessionCoordinator: PlaybackSessionCoordinator = PlaybackSessionCoordinator(
        application = application,
        animeId = animeId,
        animeRepository = animeRepository,
        scope = viewModelScope,
        uiState = _uiState,
        progressTracker = progressTracker,
        getDefaultSpeed = { defaultSpeedCache },
        getAutoLandscape = { autoLandscapeCache },
        onControllerConnected = {
            sessionCoordinator.syncWebNavigation(webCoordinator.webQueue)
        },
        findLocalFile = { mediaId -> localCoordinator.findLocalFile(mediaId) },
        hasLocalQueue = { localCoordinator.localQueue.isNotEmpty() },
        localDisplayTitle = { file -> localCoordinator.localDisplayTitle(file) }
    )

    init {
        webCoordinator = WebPlaybackCoordinator(
            application = application,
            entryAnimeId = animeId,
            webSourceEngine = webSourceEngine,
            webPlaybackHistoryRepository = webPlaybackHistoryRepository,
            webSourceRuleRepository = webSourceRuleRepository,
            scope = viewModelScope,
            uiState = _uiState,
            sessionCoordinator = sessionCoordinator,
            progressTracker = progressTracker,
            getCurrentMediaId = { currentMediaId },
            setCurrentMediaId = { currentMediaId = it },
            getNormalPlaybackSpeed = ::normalPlaybackSpeed,
            onBeginPlaybackRequest = ::beginPlaybackRequest,
            onResetForNewSource = ::resetEpisodeState,
            onRecordPlayRequest = { lastPlayRequest = it }
        )

        localCoordinator = LocalPlaybackCoordinator(
            application = application,
            entryAnimeId = animeId,
            animeRepository = animeRepository,
            settingsRepository = settingsRepository,
            localMediaRepository = localMediaRepository,
            scope = viewModelScope,
            uiState = _uiState,
            sessionCoordinator = sessionCoordinator,
            progressTracker = progressTracker,
            setCurrentMediaId = { currentMediaId = it },
            onBeginPlaybackRequest = ::beginPlaybackRequest,
            onResetForNewSource = ::resetEpisodeState,
            onRecordPlayRequest = { lastPlayRequest = it }
        )
    }

    val webParseRequest: StateFlow<PendingWebPlaybackRequest?> = webCoordinator.webParseRequest

    /** 仅在 isControllerReady=true 之后访问；供 PlayerView / 轨道选择弹窗使用 */
    val player: Player
        get() = checkNotNull(sessionCoordinator.controller) { "PlaybackService not connected yet" }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _uiState.update {
                it.copy(
                    playWhenReady = player.playWhenReady,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                progressTracker.startPositionUpdates()
            } else {
                progressTracker.stopPositionUpdates()
                // 任一来源的暂停（通知栏/音频焦点丢失/片尾自动暂停）都落一次进度，
                // 不再只依赖手动暂停与页面退出两条路径（幂等，多次写入无害）
                progressTracker.saveCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _uiState.update { it.copy(error = null) }
                    sessionCoordinator.restoreStartPositionIfNeeded()
                    sessionCoordinator.updateFullscreenOrientation()
                }
                Player.STATE_ENDED -> {
                    progressTracker.finishCurrentMedia()
                    if (webCoordinator.hasQueue && _uiState.value.autoPlayNext &&
                        player.playWhenReady && hasNextEpisode() && !_uiState.value.isPreparingMedia
                    ) {
                        webCoordinator.selectEpisode(_uiState.value.currentEpisodeIndex + 1, fromStart = true)
                    }
                }
            }
            progressTracker.updatePositionState()
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLogManager.e(TAG, "Playback error", error)
            _uiState.update { it.copy(error = error.message ?: "播放出错", isBuffering = false) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _uiState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            // 记录当前选中视频轨的容器分辨率，供「自动横屏」判断使用。
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_VIDEO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSelected(i)) continue
                    val f = group.getTrackFormat(i)
                    if (f.width > 0 && f.height > 0) {
                        sessionCoordinator.videoFormatWidth = f.width
                        sessionCoordinator.videoFormatHeight = f.height
                    }
                }
            }
            sessionCoordinator.updateFullscreenOrientation()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val newMediaId = mediaItem?.mediaId
            // setMediaItems 首次回调时 newMediaId 已等于当前项，不能当作「切集」，
            // 否则会清掉 startPlaylist 刚设置的续播位置
            val isNewItem = newMediaId != currentMediaId
            if (isNewItem) progressTracker.finalizeCurrentForTransition()

            // 切集：清掉上一集的容器尺寸，
            // 避免横屏视频的旧尺寸误判新集（新集轨道信息到达前 READY 先触发）
            sessionCoordinator.resetVideoFormatDimensions()
            if (isNewItem) {
                progressTracker.resetSampling()
                sessionCoordinator.expectedStartPositionMs = 0L
            }

            currentMediaId = newMediaId
            val localIndex = localCoordinator.indexOfLocalMedia(currentMediaId)
            val localFile = localCoordinator.localQueue.getOrNull(localIndex)
            if (localFile != null) {
                sessionCoordinator.updateMetadataAnimeId(localFile.matchedAnimeId)
            }
            _uiState.update {
                it.copy(
                    mediaTitle = localFile?.let(localCoordinator::localDisplayTitle) ?: it.mediaTitle,
                    seriesTitle = if (localFile?.archiveKey != null) it.seriesTitle ?: localFile.parsedTitle
                    else localFile?.parsedTitle ?: it.seriesTitle,
                    currentEpisodeIndex = webCoordinator.webQueue?.episodeIndex ?: localIndex
                )
            }
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (command.customAction) {
                PlaybackSessionCommands.ACTION_WEB_PREVIOUS -> seekToPrevious()
                PlaybackSessionCommands.ACTION_WEB_NEXT -> seekToNext()
                else -> return super.onCustomCommand(controller, command, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    init {
        sessionCoordinator.connect(playerListener, controllerListener)
        sessionCoordinator.observeMetadataAnime()
        progressTracker.observeSavedProgress()
        // 常驻订阅各设置项，保证触发时可同步应用
        viewModelScope.launch {
            settingsRepository.playerLongPressSpeed.collect { longPressSpeedCache = it }
        }
        viewModelScope.launch {
            settingsRepository.playerDefaultSpeed.collect { defaultSpeedCache = it }
        }
        viewModelScope.launch {
            settingsRepository.playerRememberPosition.collect { enabled ->
                rememberPositionCache = enabled
                _uiState.update { it.copy(rememberPosition = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.playerAutoPlayNext.collect { enabled ->
                _uiState.update { it.copy(autoPlayNext = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.playerAutoLandscape.collect { enabled ->
                autoLandscapeCache = enabled
                _uiState.update { it.copy(autoLandscape = enabled) }
                sessionCoordinator.updateFullscreenOrientation()
            }
        }
        // 硬件加速在 PlaybackService 启动时读取。
    }

    fun playWebDavUrl(url: String, title: String? = null) =
        localCoordinator.playWebDavUrl(url, title)

    fun playWebPage(
        request: PendingWebPlaybackRequest,
        startPositionMs: Long? = null,
        playWhenReady: Boolean = true,
        preserveSpeed: Boolean = false
    ) = webCoordinator.playWebPage(request, startPositionMs, playWhenReady, preserveSpeed)

    fun completeWebPage(
        request: PendingWebPlaybackRequest,
        url: String,
        mimeType: String?,
        headers: Map<String, String> = emptyMap(),
        userAgent: String? = request.userAgent,
        sources: List<WebMediaSource> = emptyList()
    ) = webCoordinator.completeWebPage(request, url, mimeType, headers, userAgent, sources)

    fun updateWebSources(request: PendingWebPlaybackRequest, sources: List<WebMediaSource>) =
        webCoordinator.updateWebSources(request, sources)

    fun cancelWebResolution() = webCoordinator.cancelWebResolution()

    fun playWeb(
        url: String,
        mimeType: String?,
        title: String? = null,
        pageUrl: String? = null,
        headers: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        referer: String? = null,
        queue: WebPlaybackQueue? = null,
        animeId: Int? = null,
        animeTitle: String? = null
    ) = webCoordinator.playWeb(
        url, mimeType, title, pageUrl, headers, userAgent, referer, queue, animeId, animeTitle
    )

    fun selectWebChannel(index: Int) = webCoordinator.selectWebChannel(index)

    fun selectWebSource(index: Int) = webCoordinator.selectWebSource(index)

    fun clearNotice() { _uiState.update { it.copy(notice = null) } }

    fun playLocalUri(uri: Uri, title: String? = null) = localCoordinator.playLocalUri(uri, title)

    fun playLocalMedia(mediaId: Long) = localCoordinator.playLocalMedia(mediaId)

    fun selectEpisode(index: Int) {
        if (webCoordinator.hasQueue) {
            if (index != webCoordinator.webQueue?.episodeIndex) webCoordinator.selectEpisode(index)
            return
        }
        localCoordinator.selectEpisode(index)
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int = 0) =
        localCoordinator.playMediaItems(items, startIndex)

    /** 在任何异步工作之前标记已选源，避免准备过程中回落到选源界面。 */
    private fun beginPlaybackRequest() {
        webCoordinator.cancelActiveResolution()
        sessionCoordinator.cancelStartJob()
        _uiState.update {
            it.copy(
                hasPlaybackRequest = true,
                isPreparingMedia = true,
                isResolvingMedia = false,
                isBuffering = false,
                error = null
            )
        }
    }

    private fun resetEpisodeState() {
        progressTracker.resetSampling()
        localCoordinator.clearQueue()
        webCoordinator.resetState()
        _uiState.update {
            it.copy(
                mediaTitle = null,
                seriesTitle = null,
                sourceLabel = null,
                episodes = emptyList(),
                currentEpisodeIndex = -1,
                isWebPlayback = false,
                channels = emptyList(),
                currentChannelIndex = -1,
                webSources = emptyList(),
                selectedWebSource = -1,
                notice = null,
                episodeProgress = emptyMap()
            )
        }
        sessionCoordinator.syncWebNavigation(null)
    }

    private fun normalPlaybackSpeed(): Float =
        if (_uiState.value.isLongPressSpeedActive) speedBeforeLongPress
        else sessionCoordinator.controller?.playbackParameters?.speed ?: defaultSpeedCache

    fun fullscreenLandscapeTarget(): Boolean = sessionCoordinator.fullscreenLandscapeTarget()

    /** 错误重试：重放最近一次播放请求；无可重试请求时返回 false */
    fun retryLast(): Boolean {
        val request = lastPlayRequest ?: return false
        _uiState.update { it.copy(error = null) }
        when (request) {
            is PlayRequest.WebPage -> webCoordinator.playWebPage(
                request.request,
                if (currentMediaId == null && !webCoordinator.hasStartPositionForRetry()) null
                else webCoordinator.currentWebPosition(),
                webCoordinator.currentWebIntent(),
                preserveSpeed = true
            )
            is PlayRequest.WebDav -> playWebDavUrl(request.url, request.title)
            is PlayRequest.Local -> playLocalUri(request.uri, request.title)
            is PlayRequest.LocalMedia -> playLocalMedia(request.mediaId)
            is PlayRequest.Web -> playWeb(
                request.url, request.mimeType, request.title, request.pageUrl, request.headers,
                request.userAgent, request.referer, animeId = request.animeId,
                animeTitle = request.animeTitle
            )
            is PlayRequest.Playlist -> playMediaItems(request.items, request.startIndex)
        }
        return true
    }

    fun togglePlayPause() {
        val c = sessionCoordinator.controller ?: return
        if (c.playWhenReady) {
            c.pause()
            progressTracker.saveCurrentPosition()
        } else {
            c.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val c = sessionCoordinator.controller ?: return
        val target = if (c.duration > 0) positionMs.coerceIn(0L, c.duration)
        else positionMs.coerceAtLeast(0L)
        c.seekTo(target)
        // 保留 playWhenReady：播放中快进应等待缓冲后续播，暂停中快进仍保持暂停。
        progressTracker.updatePositionState()
    }

    fun seekToPositionRatio(ratio: Float) {
        val duration = sessionCoordinator.controller?.duration ?: 0
        if (duration > 0) {
            seekTo((duration * ratio.coerceIn(0f, 1f)).toLong())
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        sessionCoordinator.controller?.setPlaybackSpeed(speed)
    }

    fun setVideoScale(scale: Int) {
        _uiState.update { it.copy(videoScale = scale.coerceIn(0, 2)) }
    }

    /** 幂等设置全屏态：方向在进入/退出时先由界面请求，布局等系统完成切换后再跟随。 */
    fun setFullscreen(fullscreen: Boolean) {
        if (_uiState.value.isFullscreen == fullscreen) return
        _uiState.update {
            it.copy(
                isFullscreen = fullscreen,
                fullscreenLandscape = if (fullscreen) fullscreenLandscapeTarget()
                else it.fullscreenLandscape
            )
        }
        sessionCoordinator.updateFullscreenOrientation()
    }

    /** 设置「自动横屏」开关：仅在全屏内按视频比例适配方向（持久化） */
    fun setAutoLandscape(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPlayerAutoLandscape(enabled) }
    }

    fun setAutoPlayNext(enabled: Boolean) {
        _uiState.update { it.copy(autoPlayNext = enabled) }
        viewModelScope.launch { settingsRepository.setPlayerAutoPlayNext(enabled) }
    }

    fun setRememberPosition(enabled: Boolean) {
        rememberPositionCache = enabled
        _uiState.update { it.copy(rememberPosition = enabled) }
        viewModelScope.launch { settingsRepository.setPlayerRememberPosition(enabled) }
    }

    /** 跳到下一个媒体项 */
    fun seekToNext() {
        if (webCoordinator.hasQueue) {
            if (hasNextEpisode()) webCoordinator.selectEpisode(_uiState.value.currentEpisodeIndex + 1)
            return
        }
        val c = sessionCoordinator.controller ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNext()
        }
    }

    /** 是否有下一集 */
    fun hasNextEpisode(): Boolean =
        if (webCoordinator.hasQueue) webCoordinator.hasNextEpisode()
        else sessionCoordinator.controller?.hasNextMediaItem() == true

    fun hasPreviousEpisode(): Boolean =
        if (webCoordinator.hasQueue) webCoordinator.hasPreviousEpisode()
        else sessionCoordinator.controller?.hasPreviousMediaItem() == true

    fun seekToPrevious() {
        if (webCoordinator.hasQueue) {
            if (hasPreviousEpisode()) webCoordinator.selectEpisode(_uiState.value.currentEpisodeIndex - 1)
            return
        }
        sessionCoordinator.controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }

    /** 长按加速：切换到长按速度（同步执行，避免竞态与延迟） */
    fun startLongPressSpeed() {
        val c = sessionCoordinator.controller ?: return
        if (_uiState.value.isLongPressSpeedActive) return
        speedBeforeLongPress = c.playbackParameters.speed
        c.setPlaybackSpeed(longPressSpeedCache)
        _uiState.update { it.copy(isLongPressSpeedActive = true) }
    }

    /** 松手恢复：回到长按前的速度 */
    fun stopLongPressSpeed() {
        val c = sessionCoordinator.controller ?: return
        if (!_uiState.value.isLongPressSpeedActive) return
        c.setPlaybackSpeed(speedBeforeLongPress)
        _uiState.update { it.copy(isLongPressSpeedActive = false) }
    }

    override fun onCleared() {
        super.onCleared()
        progressTracker.stopPositionUpdates()
        progressTracker.saveCurrentPosition()
        progressTracker.persistSampledPosition()
        sessionCoordinator.release(playerListener)
        WebPlaybackSession.clear()
        // 注意：PlaybackProgressTracker 内部的 persistScope 有意不取消，保证最后一次位置写入完成
    }

    class Factory(
        private val animeId: Int = 0
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val application = AppContainer.getApplication()
            val playerRepository = AppContainer.getPlayerRepository()
            val settingsRepository = AppContainer.getSettingsRepository()
            return PlayerViewModel(
                application = application,
                animeId = animeId,
                animeRepository = AppContainer.getAnimeRepository(),
                playerRepository = playerRepository,
                settingsRepository = settingsRepository,
                localMediaRepository = AppContainer.getLocalMediaRepository(),
                localWatchProgressUseCase = AppContainer.getLocalWatchProgressUseCase(),
                webSourceEngine = AppContainer.getWebSourceEngine(),
                webPlaybackHistoryRepository = AppContainer.getWebPlaybackHistoryRepository(),
                webSourceRuleRepository = AppContainer.getWebSourceRuleRepository()
            ) as T
        }
    }
}