package com.aiexile.animetrack.ui.player

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.player.PlaybackService
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.SubtitleLocator
import com.aiexile.animetrack.data.player.WebPlaybackSession
import com.aiexile.animetrack.data.player.PendingWebPlaybackRequest
import com.aiexile.animetrack.data.player.WebSourceClassifier
import com.aiexile.animetrack.data.websource.WebSourceEngine
import com.aiexile.animetrack.data.websource.WebSourceRuleRepository
import com.aiexile.animetrack.model.PlaybackProgress
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.aiexile.animetrack.model.WebSourceRule
import com.aiexile.animetrack.R
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.media.LocalPlaybackQueue
import com.aiexile.animetrack.domain.media.LocalWatchProgressUseCase
import com.aiexile.animetrack.domain.media.WatchProgressRules
import com.aiexile.animetrack.domain.media.WebPlaybackQueue
import com.aiexile.animetrack.model.WebMediaSource
import com.aiexile.animetrack.data.player.WebPlaybackHistoryRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PlayerUiState(
    val isPlaying: Boolean = false,
    /** 用户的播放意图，缓冲时仍为 true，不能用 isPlaying 代替。 */
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val isResolvingMedia: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val isFullscreen: Boolean = false,
    val fullscreenLandscape: Boolean = true,
    val error: String? = null,
    val mediaTitle: String? = null,
    val seriesTitle: String? = null,
    val sourceLabel: String? = null,
    val isLongPressSpeedActive: Boolean = false,
    /** 遥控器(MediaController)是否已连上后台播放服务 */
    val isControllerReady: Boolean = false,
    /** 已收到播放请求；准备、缓冲、暂停或直播时长未知均不表示未选源。 */
    val hasPlaybackRequest: Boolean = false,
    /** 查询媒体、扫描字幕和向服务提交媒体源期间显示加载状态。 */
    val isPreparingMedia: Boolean = false,
    /** 自动横屏：仅在用户进入全屏后按视频比例适配方向，不自动放大详情页 */
    val autoLandscape: Boolean = false,
    val autoPlayNext: Boolean = false,
    val rememberPosition: Boolean = true,
    val isWebPlayback: Boolean = false,
    val channels: List<String?> = emptyList(),
    val currentChannelIndex: Int = -1,
    val webSources: List<WebMediaSource> = emptyList(),
    val selectedWebSource: Int = -1,
    val notice: String? = null,
    /** 0 适应，1 填满，2 裁剪。 */
    val videoScale: Int = 0,
    /** 本地或网页队列的选集标签。 */
    val episodes: List<PlayerEpisode> = emptyList(),
    /** 当前选集在队列中的下标；-1 表示不可选集。 */
    val currentEpisodeIndex: Int = -1,
    val episodeProgress: Map<Int, PlaybackProgress> = emptyMap()
)

/** 播放器内的选集条目；index 为播放列表下标 */
data class PlayerEpisode(val label: String, val index: Int)

/** 最近一次播放请求，用于错误后重试 */
private sealed interface PlayRequest {
    data class WebPage(val request: PendingWebPlaybackRequest) : PlayRequest
    data class WebDav(val url: String, val title: String?) : PlayRequest
    data class Local(val uri: Uri, val title: String?) : PlayRequest
    data class LocalMedia(val mediaId: Long) : PlayRequest
    data class Web(
        val url: String,
        val mimeType: String?,
        val title: String?,
        val pageUrl: String?,
        val headers: Map<String, String>,
        val userAgent: String?,
        val referer: String?
    ) : PlayRequest
    data class Playlist(val items: List<MediaItem>, val startIndex: Int) : PlayRequest
}

/**
 * 播放器页面控制器：手里握的是 MediaController（遥控器），
 * 真正的 ExoPlayer 常驻 [PlaybackService] 后台服务中。
 *
 * 页面只负责发指令；锁屏/切后台由服务保活，通知栏媒体卡片由 MediaSession 自动提供。
 * 退出播放页（onCleared）= 停止播放并关闭服务。
 */
@UnstableApi
class PlayerViewModel(
    private val application: android.app.Application,
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
        private const val POSITION_UPDATE_INTERVAL_MS = 500L

        /** 起播前外挂字幕扫描的等待上限：超时降级为无字幕起播，防慢服务器拖死点播 */
        private const val SUBTITLE_SCAN_TIMEOUT_MS = 3_000L

        /** 播放中周期性落盘进度，避免进程被杀时丢失整段观看记录 */
        private const val POSITION_PERSIST_INTERVAL_MS = 15_000L
    }

    /** 遥控器：连接 PlaybackService 内的播放器。所有指令经它转发到服务侧。 */
    private var controller: MediaController? = null

    /** 进行中的连接：快速退出页面时在 onCleared 取消，防止 controller 迟到后无人释放 */
    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** 仅在 isControllerReady=true 之后访问；供 PlayerView / 轨道选择弹窗使用 */
    val player: Player
        get() = checkNotNull(controller) { "PlaybackService not connected yet" }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _webParseRequest = MutableStateFlow<PendingWebPlaybackRequest?>(null)
    val webParseRequest = _webParseRequest.asStateFlow()
    private var resolvingWebRequest: PendingWebPlaybackRequest? = null
    private var webResolveJob: Job? = null
    private var playbackStartJob: Job? = null

    private var currentMediaId: String? = null

    private var lastPlayRequest: PlayRequest? = null

    /** 本地媒体队列；非本地播放时为空 */
    private var localQueue: List<LocalMediaEntity> = emptyList()
    private var webQueue: WebPlaybackQueue? = null
    private var activeWebRequest: PendingWebPlaybackRequest? = null
    private var webStartPositionMs: Long? = null
    private var webPlaybackIntent = true
    private var webPlaybackSpeed: Float? = null
    private var capturedWebMediaUrl: String? = null
    private var savedProgress: Map<String, PlaybackProgress> = emptyMap()

    /** 已上报完成的 番剧:集数，避免重复推进进度 */
    private val completedEpisodeKeys = mutableSetOf<String>()

    /** 有效观看累计时长（仅统计连续播放，拖动产生的大跳变不计入） */
    private var watchedMs = 0L

    /** 上一次采样位置；-1 表示本段播放尚无有效采样 */
    private var lastSampledPositionMs = -1L

    private var lastSampledDurationMs = 0L

    private var lastPersistAt = 0L

    private var positionUpdateJob: Job? = null

    /** 长按加速前的原始播放速度 */
    private var speedBeforeLongPress: Float = 1f

    /** 设置项内存缓存：避免触发路径上的异步 IO，保证即时生效 */
    private var longPressSpeedCache: Float = 2f
    private var defaultSpeedCache: Float = 1f
    private var rememberPositionCache: Boolean = true
    private var autoLandscapeCache: Boolean = false

    /** 本次起播期望的初始位置：用于 READY 时校验位置是否被字幕合并重启丢掉 */
    private var expectedStartPositionMs = 0L

    /** 容器声明的视频分辨率（来自轨道 Format 元数据）。
     *  libass 特效渲染管线下 onVideoSizeChanged/videoSize 可能被重写或丢失，
     *  封装层元数据与渲染器无关，始终可靠。切集时在 onMediaItemTransition 重置。 */
    private var videoFormatWidth = 0
    private var videoFormatHeight = 0

    /** 当前媒体项是否已做过「自动横屏」评估。
     *  每集只评估一次：用户手动退出全屏后，缓冲恢复（STATE_READY 反复触发）
     *  不得再次强制横屏；切集时重置。 */

    /** 独立于 viewModelScope 的持久化作用域：
     *  viewModelScope 在 onCleared 前即被关闭，其内启动的保存协程不会执行；
     *  此作用域 fire-and-forget，不手动取消，让退出时的最后一次写入自然完成。 */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                startPositionUpdates()
            } else {
                stopPositionUpdates()
                // 任一来源的暂停（通知栏/音频焦点丢失/片尾自动暂停）都落一次进度，
                // 不再只依赖手动暂停与页面退出两条路径（幂等，多次写入无害）
                saveCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _uiState.update { it.copy(error = null) }
                    restoreStartPositionIfNeeded()
                    updateFullscreenOrientation()
                }
                Player.STATE_ENDED -> {
                    stopPositionUpdates()
                    _uiState.update { it.copy(isPlaying = false) }
                    finishCurrentMedia()
                    if (webQueue != null && _uiState.value.autoPlayNext &&
                        player.playWhenReady && hasNextEpisode() && !_uiState.value.isPreparingMedia) {
                        selectWebEpisode(_uiState.value.currentEpisodeIndex + 1, fromStart = true)
                    }
                }
            }
            updatePositionState()
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
            // 不用 onVideoSizeChanged/videoSize：libass 特效渲染管线下会被重写或丢失，
            // 封装层元数据与渲染器无关，始终可靠
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_VIDEO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSelected(i)) continue
                    val f = group.getTrackFormat(i)
                    if (f.width > 0 && f.height > 0) {
                        videoFormatWidth = f.width
                        videoFormatHeight = f.height
                    }
                }
            }
            updateFullscreenOrientation()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val newMediaId = mediaItem?.mediaId
            // setMediaItems 首次回调时 newMediaId 已等于当前项，不能当作「切集」，
            // 否则会清掉 startPlaylist 刚设置的续播位置
            val isNewItem = newMediaId != currentMediaId
            if (isNewItem) finalizeCurrentForTransition()

            // 切集：清掉上一集的容器尺寸，
            // 避免横屏视频的旧尺寸误判新集（新集轨道信息到达前 READY 先触发）
            videoFormatWidth = 0
            videoFormatHeight = 0
            if (isNewItem) {
                watchedMs = 0
                lastSampledPositionMs = -1
                expectedStartPositionMs = 0
            }

            currentMediaId = newMediaId
            val localIndex = localQueue.indexOfFirst {
                LocalPlaybackQueue.mediaKey(it.id) == currentMediaId
            }
            val localFile = localQueue.getOrNull(localIndex)
            _uiState.update {
                it.copy(
                    mediaTitle = localFile?.let(::localDisplayTitle) ?: it.mediaTitle,
                    seriesTitle = localFile?.parsedTitle ?: it.seriesTitle,
                    currentEpisodeIndex = if (webQueue != null) webQueue!!.episodeIndex else localIndex
                )
            }
        }
    }

    init {
        connectToPlaybackService()
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
                updateFullscreenOrientation()
            }
        }
        viewModelScope.launch {
            playerRepository.progress.collect { records ->
                savedProgress = records
                publishEpisodeProgress()
            }
        }
        // 硬件加速在 PlaybackService 启动时读取。
    }

    /** 异步连接后台播放服务，拿到遥控器后挂监听 */
    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java)
        )
        val future = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get().also {
                    it.addListener(playerListener)
                    // 连接期间可能已有排队的请求，补挂自动连播状态由服务侧管理，此处仅标记就绪
                }
                val connected = controller
                _uiState.update {
                    it.copy(isControllerReady = true,
                        playWhenReady = connected?.playWhenReady == true,
                        isBuffering = connected?.playbackState == Player.STATE_BUFFERING)
                }
            } catch (e: java.util.concurrent.CancellationException) {
                // onCleared 已取消连接（快速退出页面），controller 由 future 内部释放
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to connect to PlaybackService", e)
                _uiState.update { it.copy(error = "无法启动播放服务: ${e.message}") }
            }
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    }

    private fun startPositionUpdates() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                updatePositionState()
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun updatePositionState() {
        if (_uiState.value.isPreparingMedia) return
        val c = controller ?: return
        val hasDuration = c.duration > 0
        val position = if (hasDuration) c.currentPosition else 0L
        val duration = if (hasDuration) c.duration else 0L

        if (c.isPlaying) {
            // 只累计连续播放的时长；一眼拖动几百秒不视为有效观看
            val delta = position - lastSampledPositionMs
            if (lastSampledPositionMs >= 0 && delta in 1..2_000L) {
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

        _uiState.update {
            it.copy(
                currentPositionMs = position,
                durationMs = duration,
                bufferedPositionMs = if (hasDuration) c.bufferedPosition else 0
            )
        }
    }

    /** 切集/退出时用最近一次采样落盘；没有有效采样则跳过 */
    private fun persistSampledPosition() {
        if (!rememberPositionCache) return
        val mediaId = currentMediaId ?: return
        val position = lastSampledPositionMs
        if (position <= 0 || lastSampledDurationMs <= 0) return
        val duration = lastSampledDurationMs
        persistPlaybackProgress(mediaId, position, duration,
            WatchProgressRules.isCompleted(position, duration, watchedMs))
    }

    /**
     * 播放列表自动切集时 ExoPlayer 不会为上一项触发 STATE_ENDED，
     * 必须在这里用采样值结算：完成则清续播点并上报，未完成则保留进度。
     */
    private fun finalizeCurrentForTransition() {
        val mediaId = currentMediaId ?: return
        val duration = lastSampledDurationMs
        if (duration <= 0) return
        val position = lastSampledPositionMs.coerceAtLeast(0)
        val completed = WatchProgressRules.isCompleted(position, duration, watchedMs)
        persistPlaybackProgress(mediaId, position, duration, completed)
    }

    /** 播放结束：完成判定基于有效观看时长，直接拖到尾不算看完 */
    private fun finishCurrentMedia() {
        val mediaId = currentMediaId ?: return
        val c = controller ?: return
        val duration = c.duration
        if (duration <= 0) return
        val position = c.currentPosition
        val completed = WatchProgressRules.isCompleted(position, duration, watchedMs)

        persistPlaybackProgress(mediaId, position, duration, completed)
    }

    private fun persistPlaybackProgress(mediaId: String, position: Long, duration: Long, completed: Boolean) {
        val observedAt = System.currentTimeMillis()
        val watched = watchedMs
        val remember = rememberPositionCache
        persistScope.launch {
            try {
                if (remember && position > 0) {
                    playerRepository.savePlaybackPosition(mediaId, position, duration, completed, observedAt, watched)
                } else if (completed) {
                    playerRepository.clearPlaybackPosition(mediaId)
                }
                if (completed) notifyEpisodeCompleted(mediaId)
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to save playback record", e)
            }
        }
    }

    /**
     * 本地队列单集完成 → 追番进度。
     * 只处理用户确认过关联、集数明确的正片；自动匹配不足以改动追番记录。
     */
    private fun notifyEpisodeCompleted(mediaId: String) {
        val file = localQueue.firstOrNull { LocalPlaybackQueue.mediaKey(it.id) == mediaId } ?: return
        val animeId = file.matchedAnimeId ?: return
        val episode = file.episodeNumber ?: return
        if (!file.isUserOverride || file.episodeEndNumber != null) return
        val dedupeKey = "$animeId:$episode"
        if (!completedEpisodeKeys.add(dedupeKey)) return
        persistScope.launch {
            try {
                localWatchProgressUseCase.onEpisodeCompleted(animeId, file.seasonNumber, episode)
            } catch (e: Exception) {
                completedEpisodeKeys.remove(dedupeKey)
                AppLogManager.e(TAG, "Failed to update watch progress", e)
            }
        }
    }

    fun playWebDavUrl(url: String, title: String? = null) {
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "无效的播放地址") }
            return
        }
        lastPlayRequest = PlayRequest.WebDav(url, title)
        beginPlaybackRequest()
        localQueue = emptyList()
        resetEpisodeState()
        _uiState.update { it.copy(mediaTitle = title, sourceLabel = "WebDAV") }
        viewModelScope.launch {
            try {
                val baseUrl = settingsRepository.playerWebdavUrl.first()
                val fullUrl = buildFullUrl(baseUrl, url)

                val mediaId = fullUrl
                currentMediaId = mediaId
                _uiState.update { it.copy(mediaTitle = title, error = null) }

                // 扫描同目录外挂字幕（优先读浏览页目录缓存；失败/无字幕/超时均静默降级，不阻塞起播）
                val subtitles = withTimeoutOrNull(SUBTITLE_SCAN_TIMEOUT_MS) {
                    SubtitleLocator.findExternalSubtitles(fullUrl, settingsRepository)
                } ?: emptyList()

                val mediaItem = MediaItem.Builder()
                    .setUri(fullUrl)
                    .setSubtitleConfigurations(subtitles)
                    .build()
                startPlayback(mediaItem, mediaId)
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to play WebDAV URL", e)
                _uiState.update { it.copy(error = "无法播放: ${e.message}") }
            }
        }
    }

    /** 选集后立即进入播放页；切线路/清晰度时显式传入位置和播放意图。 */
    fun playWebPage(
        request: PendingWebPlaybackRequest,
        startPositionMs: Long? = null,
        playWhenReady: Boolean = true,
        preserveSpeed: Boolean = false
    ) {
        saveCurrentPosition()
        val speed = if (preserveSpeed) normalPlaybackSpeed() else null
        controller?.pause()
        controller?.stop()
        beginPlaybackRequest()
        localQueue = emptyList()
        resetEpisodeState()
        // 解析期间仍可能收到旧播放器回调，旧媒体不能再写入新集进度。
        currentMediaId = null
        val configured = request.copy(userAgent = request.userAgent ?: WebSourceRule.DEFAULT_USER_AGENT)
        activeWebRequest = configured
        webQueue = configured.queue
        webStartPositionMs = startPositionMs
        webPlaybackIntent = playWhenReady
        webPlaybackSpeed = speed
        lastPlayRequest = PlayRequest.WebPage(configured)
        resolvingWebRequest = configured
        publishWebQueue()
        _uiState.update {
            it.copy(mediaTitle = configured.title, isResolvingMedia = true,
                currentPositionMs = startPositionMs ?: 0L, durationMs = 0L, bufferedPositionMs = 0L,
                isWebPlayback = true, webSources = emptyList(), selectedWebSource = -1)
        }
        webResolveJob = viewModelScope.launch {
            var resolved = configured
            if (configured.restoreQueue && configured.queue == null &&
                !WebSourceClassifier.isCandidate(configured.url)) {
                val queue = try {
                    val rule = webSourceRuleRepository.rules.first().firstOrNull {
                        it.baseUrl.toHttpUrlOrNull()?.host == configured.url.toHttpUrlOrNull()?.host
                    }
                    val page = webSourceEngine.page(configured.url, configured.userAgent!!,
                        configured.referer, rule)
                    WebPlaybackQueue.from(page, configured.url) ?: configured.seriesUrl
                        ?.takeIf { it != configured.url }?.let { series ->
                            val detail = webSourceEngine.page(series, configured.userAgent,
                                configured.referer, rule)
                            WebPlaybackQueue.from(detail, configured.url)
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 剧集页暂时不可用时仍可继续解析该集，使用历史中的稳定进度标识。
                    null
                }
                if (resolvingWebRequest !== configured) return@launch
                if (queue != null) {
                    resolved = configured.copy(queue = queue, restoreQueue = false)
                    resolvingWebRequest = resolved
                    activeWebRequest = resolved
                    lastPlayRequest = PlayRequest.WebPage(resolved)
                    webQueue = queue
                    publishWebQueue()
                }
            }
            val sources = try {
                if (WebSourceClassifier.isCandidate(resolved.url))
                    listOf(WebMediaSource(resolved.url, mimeType = resolved.mimeType))
                else webSourceEngine.resolveMedia(resolved.url, resolved.userAgent!!, resolved.referer)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (resolvingWebRequest !== resolved) return@launch
            _uiState.update { it.copy(webSources = sources) }
            val source = sources.firstOrNull()
            if (source != null) completeWebPage(resolved, source.url,
                source.mimeType ?: WebSourceClassifier.mimeType(source.url))
            else _webParseRequest.value = resolved
        }
    }

    fun completeWebPage(
        request: PendingWebPlaybackRequest,
        url: String,
        mimeType: String?,
        headers: Map<String, String> = emptyMap(),
        userAgent: String? = request.userAgent,
        sources: List<WebMediaSource> = emptyList()
    ) {
        if (resolvingWebRequest !== request) return
        webResolveJob = null
        if (sources.isNotEmpty()) _uiState.update { it.copy(webSources = sources) }
        activeWebRequest = request.copy(headers = headers, userAgent = userAgent)
        capturedWebMediaUrl = url
        playResolvedWeb(activeWebRequest!!, url, mimeType, headers, userAgent)
        // 媒体直链可能过期，重试必须重新解析该集网页。
        lastPlayRequest = PlayRequest.WebPage(activeWebRequest!!)
    }

    fun updateWebSources(request: PendingWebPlaybackRequest, sources: List<WebMediaSource>) {
        if (resolvingWebRequest !== request) return
        _uiState.update { it.copy(webSources = sources.distinctBy { source -> source.url }.take(32)) }
    }

    private fun playResolvedWeb(
        request: PendingWebPlaybackRequest,
        url: String,
        mimeType: String?,
        headers: Map<String, String>,
        userAgent: String?
    ) {
        beginPlaybackRequest()
        val mediaId = request.progressMediaId ?: webQueue?.mediaKey() ?: "web:${request.pageUrl ?: request.url}"
        currentMediaId = mediaId
        WebPlaybackSession.prepare(application, request.pageUrl ?: request.url, url,
            headers, userAgent, request.referer ?: request.url)
        val availableSources = _uiState.value.webSources
        val sources = if (availableSources.any { it.url == url }) availableSources
            else availableSources + WebMediaSource(url, mimeType = mimeType)
        _uiState.update {
            it.copy(mediaTitle = request.title ?: url, webSources = sources,
                selectedWebSource = sources.indexOfFirst { source -> source.url == url })
        }
        val builder = MediaItem.Builder().setMediaId(mediaId).setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(request.title ?: url).build())
        mimeType?.let(builder::setMimeType)
        startPlayback(builder.build(), mediaId, webStartPositionMs, webPlaybackIntent, webPlaybackSpeed)
        val queue = webQueue
        viewModelScope.launch {
            webPlaybackHistoryRepository.add(request.pageUrl ?: request.url, request.title,
                userAgent, request.referer, queue?.seriesUrl ?: request.seriesUrl, queue, mediaId)
        }
    }

    fun cancelWebResolution() {
        if (resolvingWebRequest == null) return
        webResolveJob?.cancel()
        resolvingWebRequest = null
        _webParseRequest.value = null
        _uiState.update {
            it.copy(isPreparingMedia = false, isResolvingMedia = false, isBuffering = false,
                error = application.getString(R.string.player_resolve_cancelled))
        }
    }

    /** 网页直链/辅助解析结果也使用原网页作为稳定进度标识。 */
    fun playWeb(
        url: String,
        mimeType: String?,
        title: String? = null,
        pageUrl: String? = null,
        headers: Map<String, String> = emptyMap(),
        userAgent: String? = null,
        referer: String? = null,
        queue: WebPlaybackQueue? = null
    ) {
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "无效的播放地址") }
            return
        }
        saveCurrentPosition()
        controller?.pause()
        beginPlaybackRequest()
        localQueue = emptyList()
        resetEpisodeState()
        webQueue = queue
        webStartPositionMs = null
        webPlaybackIntent = true
        webPlaybackSpeed = null
        val request = PendingWebPlaybackRequest(pageUrl ?: url, mimeType, title, pageUrl,
            headers, userAgent, referer, resolvePage = pageUrl != null, queue = queue)
        activeWebRequest = request
        capturedWebMediaUrl = url
        publishWebQueue()
        _uiState.update { it.copy(isWebPlayback = true, webSources = emptyList(), selectedWebSource = -1) }
        playResolvedWeb(request, url, mimeType, headers, userAgent)
        lastPlayRequest = if (pageUrl != null) PlayRequest.WebPage(request)
            else PlayRequest.Web(url, mimeType, title, pageUrl, headers, userAgent, referer)
    }

    private fun normalPlaybackSpeed(): Float = if (_uiState.value.isLongPressSpeedActive)
        speedBeforeLongPress else controller?.playbackParameters?.speed ?: defaultSpeedCache

    private fun publishWebQueue() {
        val queue = webQueue
        _uiState.update {
            it.copy(seriesTitle = queue?.page?.title,
                sourceLabel = Uri.parse(queue?.page?.url ?: activeWebRequest?.pageUrl
                    ?: activeWebRequest?.url.orEmpty()).host
                    ?: application.getString(R.string.web_playback_title),
                episodes = queue?.episodes?.mapIndexed { index, episode ->
                PlayerEpisode(episode.name, index)
            }.orEmpty(), currentEpisodeIndex = queue?.episodeIndex ?: -1,
                channels = queue?.page?.channels?.map { channel -> channel.name }.orEmpty(),
                currentChannelIndex = queue?.channelIndex ?: -1)
        }
        publishEpisodeProgress()
    }

    private fun publishEpisodeProgress() {
        val queue = webQueue
        val progress = if (queue == null) emptyMap() else queue.mediaKeys.mapIndexedNotNull { index, key ->
            savedProgress[key]?.let { index to it }
        }.toMap()
        _uiState.update { it.copy(episodeProgress = progress) }
    }

    private fun playWebQueue(queue: WebPlaybackQueue, position: Long? = null,
        playWhenReady: Boolean = true) {
        val previous = activeWebRequest ?: return
        val episode = queue.episode ?: return
        val referer = if (previous.referer == previous.url || previous.referer == previous.pageUrl)
            episode.url else previous.referer
        val request = previous.copy(url = episode.url, pageUrl = episode.url,
            title = "${queue.page.title} · ${episode.name}", queue = queue, headers = emptyMap(),
            referer = referer, resolvePage = true, restoreQueue = false,
            progressMediaId = null, seriesUrl = queue.seriesUrl)
        playWebPage(request, position, playWhenReady, preserveSpeed = true)
    }

    private fun selectWebEpisode(index: Int, fromStart: Boolean = false) {
        val queue = webQueue?.select(index) ?: return
        playWebQueue(queue, if (fromStart) 0L else null)
    }

    fun selectWebChannel(index: Int) {
        val current = webQueue ?: return
        if (current.channelIndex == index) return
        val next = current.switchChannel(index)
        if (next == null) {
            _uiState.update { it.copy(notice = application.getString(R.string.player_channel_missing_episode)) }
            return
        }
        playWebQueue(next, currentWebPosition(), currentWebIntent())
    }

    fun selectWebSource(index: Int) {
        val source = _uiState.value.webSources.getOrNull(index) ?: return
        if (index == _uiState.value.selectedWebSource) return
        val request = activeWebRequest ?: return
        webStartPositionMs = currentWebPosition()
        webPlaybackIntent = currentWebIntent()
        webPlaybackSpeed = normalPlaybackSpeed()
        saveCurrentPosition()
        controller?.pause()
        controller?.stop()
        // 捕获的 Cookie/鉴权头只属于原媒体主机，其他主机重新读取会话 Cookie。
        val sameHost = Uri.parse(capturedWebMediaUrl.orEmpty()).host == Uri.parse(source.url).host
        playResolvedWeb(request, source.url, source.mimeType ?: WebSourceClassifier.mimeType(source.url),
            if (sameHost) request.headers else emptyMap(), request.userAgent)
    }

    private fun currentWebPosition(): Long = if (_uiState.value.isPreparingMedia || currentMediaId == null)
        webStartPositionMs ?: 0L else controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    private fun currentWebIntent(): Boolean = if (_uiState.value.isPreparingMedia || currentMediaId == null)
        webPlaybackIntent else controller?.playWhenReady == true

    fun clearNotice() { _uiState.update { it.copy(notice = null) } }

    fun playLocalUri(uri: Uri, title: String? = null) {
        lastPlayRequest = PlayRequest.Local(uri, title)
        beginPlaybackRequest()
        localQueue = emptyList()
        resetEpisodeState()
        currentMediaId = uri.toString()
        _uiState.update { it.copy(mediaTitle = title, error = null,
            sourceLabel = application.getString(R.string.player_local_source)) }
        startPlayback(MediaItem.fromUri(uri), currentMediaId!!)
    }

    /**
     * 从本地媒体库播放：按当前文件构建选集队列，稳定媒体 ID 为 local:<id>。
     * 字幕查找与队列构建都在 IO 完成，失败不阻塞起播。
     */
    fun playLocalMedia(mediaId: Long) {
        lastPlayRequest = PlayRequest.LocalMedia(mediaId)
        beginPlaybackRequest()
        resetEpisodeState()
        _uiState.update { it.copy(sourceLabel = application.getString(R.string.player_local_source)) }
        viewModelScope.launch {
            val selected = localMediaRepository.getMediaById(mediaId)
            if (selected == null || selected.isMissing) {
                _uiState.update { it.copy(error = "本地文件不存在，请重新扫描") }
                return@launch
            }
            val files = localMediaRepository.getMediaForSource(selected.sourceId)
            val queue = LocalPlaybackQueue.build(selected, files)
            if (queue.isEmpty()) {
                _uiState.update { it.copy(error = "没有可播放的本地文件") }
                return@launch
            }

            val items = withContext(Dispatchers.IO) {
                queue.map { file ->
                    val subtitles = withTimeoutOrNull(SUBTITLE_SCAN_TIMEOUT_MS) {
                        localMediaRepository.findLocalSubtitles(file)
                    } ?: emptyList()
                    buildLocalMediaItem(file, subtitles)
                }
            }
            localQueue = queue
            watchedMs = 0
            lastSampledPositionMs = -1
            val startIndex = queue.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
            currentMediaId = LocalPlaybackQueue.mediaKey(queue[startIndex].id)
            _uiState.update {
                it.copy(
                    mediaTitle = localDisplayTitle(queue[startIndex]),
                    seriesTitle = queue[startIndex].parsedTitle,
                    episodes = queue.mapIndexed { index, file ->
                        PlayerEpisode(
                            label = file.episodeNumber?.let { ep -> "第${ep}集" } ?: file.fileName,
                            index = index
                        )
                    },
                    currentEpisodeIndex = startIndex,
                    error = null
                )
            }
            startPlaylist(items, startIndex, currentMediaId!!)
        }
    }

    private fun buildLocalMediaItem(
        file: LocalMediaEntity,
        subtitles: List<com.aiexile.animetrack.data.media.LocalSubtitle>
    ): MediaItem = MediaItem.Builder()
        .setMediaId(LocalPlaybackQueue.mediaKey(file.id))
        .setUri(Uri.parse(file.documentUri))
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(localDisplayTitle(file)).build()
        )
        .setSubtitleConfigurations(
            subtitles.map { subtitle ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
                    .setMimeType(subtitle.mimeType)
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.label)
                    .build()
            }
        )
        .build()

    private fun localDisplayTitle(file: LocalMediaEntity): String {
        val episode = file.episodeNumber
        val title = file.parsedTitle?.takeIf { it.isNotBlank() } ?: file.fileName
        return if (episode != null) "$title · 第${episode}集" else title
    }

    /** 选集：直接跳到队列中的目标项，从头播放 */
    fun selectEpisode(index: Int) {
        if (webQueue != null) {
            if (index != webQueue?.episodeIndex) selectWebEpisode(index)
            return
        }
        val c = controller ?: return
        if (index !in localQueue.indices) return
        c.seekTo(index, 0L)
        c.play()
    }

    private fun resetEpisodeState() {
        watchedMs = 0
        lastSampledPositionMs = -1
        lastSampledDurationMs = 0
        webQueue = null
        activeWebRequest = null
        capturedWebMediaUrl = null
        _uiState.update {
            it.copy(mediaTitle = null, seriesTitle = null, sourceLabel = null,
                episodes = emptyList(), currentEpisodeIndex = -1, isWebPlayback = false,
                channels = emptyList(), currentChannelIndex = -1,
                webSources = emptyList(), selectedWebSource = -1, notice = null,
                episodeProgress = emptyMap())
        }
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        lastPlayRequest = PlayRequest.Playlist(items, startIndex)
        beginPlaybackRequest()
        localQueue = emptyList()
        resetEpisodeState()

        currentMediaId = items[startIndex].mediaId
        _uiState.update {
            it.copy(mediaTitle = items[startIndex].mediaMetadata.title?.toString(), error = null)
        }
        startPlaylist(items, startIndex, currentMediaId!!)
    }

    /** 在任何异步工作之前标记已选源，避免准备过程中回落到选源界面。 */
    private fun beginPlaybackRequest() {
        webResolveJob?.cancel()
        playbackStartJob?.cancel()
        resolvingWebRequest = null
        _webParseRequest.value = null
        _uiState.update {
            it.copy(hasPlaybackRequest = true, isPreparingMedia = true,
                isResolvingMedia = false, isBuffering = false, error = null)
        }
    }

    /** 单媒体项起播：等连接就绪 → 读记忆进度 → 带初始位置设源 → prepare → 应用默认值 → 播放 */
    private fun startPlayback(mediaItem: MediaItem, mediaId: String,
        initialPositionMs: Long? = null, playWhenReady: Boolean = true, speed: Float? = null) {
        playbackStartJob = viewModelScope.launch {
            val c = awaitController() ?: run {
                _uiState.update { it.copy(error = "无法连接播放服务") }
                return@launch
            }
            // 官方姿势：IDLE 状态下 setMediaItem(item, startPositionMs)，
            // prepare 后直接从该位置起播，避免 prepare 后再异步 seek 的竞态
            val startPosition = initialPositionMs?.coerceAtLeast(0L) ?: readSavedPosition(mediaId)
            // 分多次看完一集时累计有效观看，避免续播后的片尾被误判为未看完。
            if (startPosition > 0 && rememberPositionCache) {
                val saved = playerRepository.progress.first()[mediaId]
                if (saved != null && !saved.completed) watchedMs = maxOf(watchedMs, saved.watchedMs)
            }
            expectedStartPositionMs = startPosition
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            c.setMediaItem(mediaItem, startPosition)
            c.prepare()
            applyPlaybackDefaults(c)
            speed?.let { c.setPlaybackSpeed(it) }
            c.playWhenReady = playWhenReady
            _uiState.update {
                it.copy(isPreparingMedia = false, isResolvingMedia = false,
                    currentPositionMs = startPosition, durationMs = 0, bufferedPositionMs = 0,
                    playWhenReady = c.playWhenReady,
                    isBuffering = c.playbackState == Player.STATE_BUFFERING)
            }
        }
    }

    /** 多媒体列表起播（仅首项恢复记忆进度，后续集从头播） */
    private fun startPlaylist(items: List<MediaItem>, startIndex: Int, mediaId: String) {
        playbackStartJob = viewModelScope.launch {
            val c = awaitController() ?: run {
                _uiState.update { it.copy(error = "无法连接播放服务") }
                return@launch
            }
            val startPosition = readSavedPosition(mediaId)
            expectedStartPositionMs = startPosition
            c.setMediaItems(items, startIndex, startPosition)
            c.prepare()
            applyPlaybackDefaults(c)
            c.playWhenReady = true
            _uiState.update {
                it.copy(isPreparingMedia = false, playWhenReady = c.playWhenReady,
                    isBuffering = c.playbackState == Player.STATE_BUFFERING)
            }
        }
    }

    /**
     * 挂起等待遥控器连接完成（起播请求先到、服务后连上的竞态场景）。
     * 已连接直接返回；页面退出或连接失败返回 null。
     */
    private suspend fun awaitController(): MediaController? {
        controller?.let { return it }
        val future = controllerFuture ?: return null
        return suspendCancellableCoroutine { cont ->
            future.addListener({
                val c = runCatching { future.get() }.getOrNull()
                // runCatching 防御极端竞态（协程已取消后 resume）时抛出的异常
                runCatching { cont.resumeWith(Result.success(c)) }
            }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
            // 连接由 ViewModel 持有；切换播放请求只取消等待，退出页面时再释放 future。
        }
    }

    /** 按「记住播放位置」开关读取已存进度（无记录/开关关闭返回 0） */
    private suspend fun readSavedPosition(mediaId: String): Long {
        if (!rememberPositionCache) return 0L
        return try {
            playerRepository.getPlaybackPosition(mediaId)?.coerceAtLeast(0L) ?: 0L
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogManager.w(TAG, "Failed to read saved position", e)
            0L
        }
    }

    /** 首次 READY 时校验记忆位置是否真的生效。
     *  实测：外挂字幕（MergingMediaSource）合并完成时会重启视频提取，
     *  setMediaItem(item, startPosition) 的初始位置会被丢回 0；
     *  发现回退即补偿 seek，保证「播放记忆」可靠。 */
    private fun restoreStartPositionIfNeeded() {
        val expected = expectedStartPositionMs
        if (expected <= 0L) return
        expectedStartPositionMs = 0L
        val c = controller ?: return
        if (c.currentPosition < expected - 1000L) {
            c.seekTo(expected)
        }
    }

    /**
     * 进入全屏时的目标方向：自动横屏关闭时固定横向；开启时按当前视频宽高。
     * 供界面在请求方向切换前计算，避免先转错方向再转回来。
     */
    fun fullscreenLandscapeTarget(): Boolean {
        if (!autoLandscapeCache) return true
        val size = controller?.videoSize
        val width = videoFormatWidth.takeIf { it > 0 } ?: (size?.width ?: 0)
        val height = videoFormatHeight.takeIf { it > 0 } ?: (size?.height ?: 0)
        return width <= 0 || height <= 0 || width > height
    }

    /** 自动方向只作用于已经打开的全屏模式，媒体就绪、换集和缓冲都不能放大详情页。 */
    private fun updateFullscreenOrientation() {
        if (!_uiState.value.isFullscreen) return
        val size = controller?.videoSize
        val width = videoFormatWidth.takeIf { it > 0 } ?: (size?.width ?: 0)
        val height = videoFormatHeight.takeIf { it > 0 } ?: (size?.height ?: 0)
        if (autoLandscapeCache && (width <= 0 || height <= 0)) return
        _uiState.update {
            it.copy(fullscreenLandscape = !autoLandscapeCache || width > height)
        }
    }

    /** 错误重试：重放最近一次播放请求；无可重试请求时返回 false */
    fun retryLast(): Boolean {
        val request = lastPlayRequest ?: return false
        _uiState.update { it.copy(error = null) }
        when (request) {
            is PlayRequest.WebPage -> playWebPage(request.request,
                if (currentMediaId == null && webStartPositionMs == null) null else currentWebPosition(),
                currentWebIntent(), preserveSpeed = true)
            is PlayRequest.WebDav -> playWebDavUrl(request.url, request.title)
            is PlayRequest.Local -> playLocalUri(request.uri, request.title)
            is PlayRequest.LocalMedia -> playLocalMedia(request.mediaId)
            is PlayRequest.Web -> playWeb(
                request.url, request.mimeType, request.title, request.pageUrl, request.headers,
                request.userAgent, request.referer
            )
            is PlayRequest.Playlist -> playMediaItems(request.items, request.startIndex)
        }
        return true
    }

    /** 每次起播应用默认播放速度，并复位长按加速状态 */
    private fun applyPlaybackDefaults(target: Player) {
        target.setPlaybackSpeed(defaultSpeedCache)
        _uiState.update { it.copy(isLongPressSpeedActive = false) }
    }

    /** 按「记住播放位置」开关保存当前进度（幂等，可在暂停/播完/退出时多次调用）。
     *  仅在 STATE_READY 后才取位置，避免 IDLE/BUFFERING 期间读到无效的 0。 */
    private fun saveCurrentPosition() {
        val mediaId = currentMediaId ?: return
        if (!rememberPositionCache) return
        val c = controller ?: return
        if (_uiState.value.isPreparingMedia || c.currentMediaItem?.mediaId != mediaId) return
        if (c.playbackState != Player.STATE_READY) return
        val position = c.currentPosition
        val duration = c.duration
        if (duration <= 0 || position <= 0) return
        val completed = WatchProgressRules.isCompleted(position, duration, watchedMs)
        persistPlaybackProgress(mediaId, position, duration, completed)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playWhenReady) {
            c.pause()
            saveCurrentPosition()
        } else {
            c.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        val target = if (c.duration > 0) positionMs.coerceIn(0L, c.duration)
            else positionMs.coerceAtLeast(0L)
        c.seekTo(target)
        // 保留 playWhenReady：播放中快进应等待缓冲后续播，暂停中快进仍保持暂停。
        updatePositionState()
    }

    fun seekToPositionRatio(ratio: Float) {
        val duration = controller?.duration ?: 0
        if (duration > 0) {
            seekTo((duration * ratio.coerceIn(0f, 1f)).toLong())
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
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
        updateFullscreenOrientation()
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
        if (webQueue != null) {
            if (hasNextEpisode()) selectWebEpisode(_uiState.value.currentEpisodeIndex + 1)
            return
        }
        val c = controller ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNext()
        }
    }

    /** 是否有下一集 */
    fun hasNextEpisode(): Boolean = webQueue?.let { it.episodeIndex + 1 < it.episodes.size }
        ?: (controller?.hasNextMediaItem() == true)

    fun hasPreviousEpisode(): Boolean = webQueue?.let { it.episodeIndex > 0 }
        ?: (controller?.hasPreviousMediaItem() == true)

    fun seekToPrevious() {
        if (webQueue != null) {
            if (hasPreviousEpisode()) selectWebEpisode(_uiState.value.currentEpisodeIndex - 1)
            return
        }
        controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() }
    }

    /** 长按加速：切换到长按速度（同步执行，避免竞态与延迟） */
    fun startLongPressSpeed() {
        val c = controller ?: return
        if (_uiState.value.isLongPressSpeedActive) return
        speedBeforeLongPress = c.playbackParameters.speed
        c.setPlaybackSpeed(longPressSpeedCache)
        _uiState.update { it.copy(isLongPressSpeedActive = true) }
    }

    /** 松手恢复：回到长按前的速度 */
    fun stopLongPressSpeed() {
        val c = controller ?: return
        if (!_uiState.value.isLongPressSpeedActive) return
        c.setPlaybackSpeed(speedBeforeLongPress)
        _uiState.update { it.copy(isLongPressSpeedActive = false) }
    }

    private fun buildFullUrl(baseUrl: String, relativePath: String): String {
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath
        }
        val base = baseUrl.trimEnd('/')
        val path = relativePath.trimStart('/')
        return "$base/$path"
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        saveCurrentPosition()
        persistSampledPosition()

        // 退出播放页 = 停止播放并关掉后台服务
        // （后台播放指锁屏/切换其他应用场景；页面销毁不应留下无界面的持续播放）
        // 连接尚未完成就退出：取消挂起的连接，否则 controller 迟到后无人释放，
        // binder 连接会阻止 stopService 销毁服务 → 通知栏媒体卡片残留
        controllerFuture?.let { if (!it.isDone) it.cancel(true) }
        controllerFuture = null
        controller?.let {
            it.removeListener(playerListener)
            it.release()
        }
        controller = null
        runCatching {
            application.stopService(Intent(application, PlaybackService::class.java))
        }
        WebPlaybackSession.clear()
        // 注意：persistScope 有意不取消，保证最后一次位置写入完成
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val application = AppContainer.getApplication()
            val playerRepository = AppContainer.getPlayerRepository()
            val settingsRepository = AppContainer.getSettingsRepository()
            return PlayerViewModel(
                application = application,
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
