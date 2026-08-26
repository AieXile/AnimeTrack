package com.aiexile.animetrack.ui.player

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.media3.session.SessionToken
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.PlaybackService
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.SubtitleLocator
import com.aiexile.animetrack.di.AppContainer
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.withTimeoutOrNull

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val playbackSpeed: Float = 1f,
    val isFullscreen: Boolean = false,
    val error: String? = null,
    val mediaTitle: String? = null,
    val isLongPressSpeedActive: Boolean = false,
    /** 遥控器(MediaController)是否已连上后台播放服务 */
    val isControllerReady: Boolean = false,
    /** 自动横屏：播放横屏视频时自动进入全屏并旋转，默认关闭（手动全屏按钮不受影响） */
    val autoLandscape: Boolean = false
)

/** 最近一次播放请求，用于错误后重试 */
private sealed interface PlayRequest {
    data class WebDav(val url: String, val title: String?) : PlayRequest
    data class Local(val uri: Uri, val title: String?) : PlayRequest
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L

        /** 起播前外挂字幕扫描的等待上限：超时降级为无字幕起播，防慢服务器拖死点播 */
        private const val SUBTITLE_SCAN_TIMEOUT_MS = 3_000L
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

    private var currentMediaId: String? = null

    private var lastPlayRequest: PlayRequest? = null

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
    private var autoLandscapeEvaluated = false

    /** 独立于 viewModelScope 的持久化作用域：
     *  viewModelScope 在 onCleared 前即被关闭，其内启动的保存协程不会执行；
     *  此作用域 fire-and-forget，不手动取消，让退出时的最后一次写入自然完成。 */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val playerListener = object : Player.Listener {
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
                    tryEnterFullscreenByAspectRatio()
                }
                Player.STATE_ENDED -> {
                    stopPositionUpdates()
                    _uiState.update { it.copy(isPlaying = false) }
                    // 播完保存：进度 ≥95% 时仓库层会自动清除记忆（下次从头播）
                    saveCurrentPosition()
                }
            }
            updatePositionState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback error", error)
            _uiState.update { it.copy(error = error.message ?: "播放出错") }
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
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 切集：清掉上一集的容器尺寸并重置自动横屏评估，
            // 避免横屏视频的旧尺寸误判新集（新集轨道信息到达前 READY 先触发）
            videoFormatWidth = 0
            videoFormatHeight = 0
            autoLandscapeEvaluated = false
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
            settingsRepository.playerRememberPosition.collect { rememberPositionCache = it }
        }
        viewModelScope.launch {
            settingsRepository.playerAutoLandscape.collect { enabled ->
                autoLandscapeCache = enabled
                _uiState.update { it.copy(autoLandscape = enabled) }
            }
        }
        // 注：硬件加速与自动连播在 PlaybackService 启动时读取，更改后重新进入播放器生效
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
                _uiState.update { it.copy(isControllerReady = true) }
            } catch (e: java.util.concurrent.CancellationException) {
                // onCleared 已取消连接（快速退出页面），controller 由 future 内部释放
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to PlaybackService", e)
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
        val c = controller ?: return
        _uiState.update {
            it.copy(
                currentPositionMs = if (c.duration > 0) c.currentPosition else 0,
                durationMs = if (c.duration > 0) c.duration else 0,
                bufferedPositionMs = if (c.duration > 0) c.bufferedPosition else 0
            )
        }
    }

    fun playWebDavUrl(url: String, title: String? = null) {
        if (url.isBlank()) {
            _uiState.update { it.copy(error = "无效的播放地址") }
            return
        }
        lastPlayRequest = PlayRequest.WebDav(url, title)
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
                Log.e(TAG, "Failed to play WebDAV URL", e)
                _uiState.update { it.copy(error = "无法播放: ${e.message}") }
            }
        }
    }

    fun playLocalUri(uri: Uri, title: String? = null) {
        lastPlayRequest = PlayRequest.Local(uri, title)
        currentMediaId = uri.toString()
        _uiState.update { it.copy(mediaTitle = title, error = null) }
        startPlayback(MediaItem.fromUri(uri), currentMediaId!!)
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        lastPlayRequest = PlayRequest.Playlist(items, startIndex)

        currentMediaId = items[startIndex].mediaId
        _uiState.update {
            it.copy(mediaTitle = items[startIndex].mediaMetadata.title?.toString(), error = null)
        }
        startPlaylist(items, startIndex, currentMediaId!!)
    }

    /** 单媒体项起播：等连接就绪 → 读记忆进度 → 带初始位置设源 → prepare → 应用默认值 → 播放 */
    private fun startPlayback(mediaItem: MediaItem, mediaId: String) {
        viewModelScope.launch {
            val c = awaitController() ?: run {
                _uiState.update { it.copy(error = "无法连接播放服务") }
                return@launch
            }
            // 官方姿势：IDLE 状态下 setMediaItem(item, startPositionMs)，
            // prepare 后直接从该位置起播，避免 prepare 后再异步 seek 的竞态
            val startPosition = readSavedPosition(mediaId)
            expectedStartPositionMs = startPosition
            c.setMediaItem(mediaItem, startPosition)
            c.prepare()
            applyPlaybackDefaults(c)
            c.playWhenReady = true
        }
    }

    /** 多媒体列表起播（仅首项恢复记忆进度，后续集从头播） */
    private fun startPlaylist(items: List<MediaItem>, startIndex: Int, mediaId: String) {
        viewModelScope.launch {
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
            cont.invokeOnCancellation { future.cancel(true) }
        }
    }

    /** 按「记住播放位置」开关读取已存进度（无记录/开关关闭返回 0） */
    private suspend fun readSavedPosition(mediaId: String): Long {
        if (!rememberPositionCache) return 0L
        return try {
            playerRepository.getPlaybackPosition(mediaId)?.coerceAtLeast(0L) ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read saved position", e)
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

    /** 横向视频且「自动横屏」开启且未全屏 → 进入全屏（UI 层随 isFullscreen 旋转到横向）。
     *  唯一触发点：STATE_READY；每集只评估一次（见 autoLandscapeEvaluated）；
     *  分辨率取轨道 Format，缺失时回退 controller.videoSize。 */
    private fun tryEnterFullscreenByAspectRatio() {
        if (!autoLandscapeCache) return
        // 每集一次：评估完成后，缓冲恢复等后续 READY 不再强制横屏，
        // 用户手动退出全屏的意愿必须被尊重；切集时重置重新评估
        if (autoLandscapeEvaluated) return
        autoLandscapeEvaluated = true

        val vs = controller?.videoSize
        val w = videoFormatWidth.takeIf { it > 0 } ?: (vs?.width ?: 0)
        val h = videoFormatHeight.takeIf { it > 0 } ?: (vs?.height ?: 0)
        if (w > 0 && h > 0 && w > h && !_uiState.value.isFullscreen) {
            _uiState.update { it.copy(isFullscreen = true) }
        }
    }

    /** 错误重试：重放最近一次播放请求；无可重试请求时返回 false */
    fun retryLast(): Boolean {
        val request = lastPlayRequest ?: return false
        _uiState.update { it.copy(error = null) }
        when (request) {
            is PlayRequest.WebDav -> playWebDavUrl(request.url, request.title)
            is PlayRequest.Local -> playLocalUri(request.uri, request.title)
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
        if (c.playbackState != Player.STATE_READY) return
        val position = c.currentPosition
        val duration = c.duration
        if (duration <= 0 || position <= 0) return
        persistScope.launch {
            try {
                playerRepository.savePlaybackPosition(mediaId, position, duration)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save playback position", e)
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
            saveCurrentPosition()
        } else {
            c.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        updatePositionState()
    }

    fun seekToPositionRatio(ratio: Float) {
        val duration = controller?.duration ?: 0
        if (duration > 0) {
            controller?.seekTo((duration * ratio.coerceIn(0f, 1f)).toLong())
            updatePositionState()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    /** 设置「自动横屏」开关：开启后播放横屏视频时自动进入全屏并旋转（持久化） */
    fun setAutoLandscape(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPlayerAutoLandscape(enabled) }
    }

    /** 跳到下一个媒体项 */
    fun seekToNext() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) {
            c.seekToNext()
        }
    }

    /** 是否有下一集 */
    fun hasNextEpisode(): Boolean = controller?.hasNextMediaItem() == true

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
        // 注意：persistScope 有意不取消，保证最后一次位置写入完成
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val application = AppContainer.getApplication()
            val playerRepository = AppContainer.getPlayerRepository()
            val settingsRepository = AppContainer.getSettingsRepository()
            return PlayerViewModel(application, playerRepository, settingsRepository) as T
        }
    }
}
