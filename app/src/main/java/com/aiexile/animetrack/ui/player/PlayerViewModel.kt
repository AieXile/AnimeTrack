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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.PlaybackService
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.di.AppContainer
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
import okhttp3.OkHttpClient

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
    val isControllerReady: Boolean = false
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
    }

    /** 遥控器：连接 PlaybackService 内的播放器。所有指令经它转发到服务侧。 */
    private var controller: MediaController? = null

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

    /** 独立于 viewModelScope 的持久化作用域：
     *  viewModelScope 在 onCleared 前即被关闭，其内启动的保存协程不会执行；
     *  此作用域 fire-and-forget，不手动取消，让退出时的最后一次写入自然完成。 */
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionUpdates() else stopPositionUpdates()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> _uiState.update { it.copy(error = null) }
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

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // 横屏视频自动进入全屏
            if (videoSize.width > 0 && videoSize.height > 0 && videoSize.width > videoSize.height) {
                if (!_uiState.value.isFullscreen) {
                    _uiState.update { it.copy(isFullscreen = true) }
                }
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
            settingsRepository.playerRememberPosition.collect { rememberPositionCache = it }
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
        future.addListener({
            try {
                controller = future.get().also {
                    it.addListener(playerListener)
                    // 连接期间可能已有排队的请求，补挂自动连播状态由服务侧管理，此处仅标记就绪
                }
                _uiState.update { it.copy(isControllerReady = true) }
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
                val baseUrl = settingsRepository.webdavUrl.first()
                val fullUrl = buildFullUrl(baseUrl, url)

                val mediaId = fullUrl
                currentMediaId = mediaId
                _uiState.update { it.copy(mediaTitle = title, error = null) }

                // URI 解析与认证由服务侧 MediaSourceFactory 完成（WebDAV DataSource 在服务内）
                startPlayback(MediaItem.fromUri(fullUrl), mediaId)
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

    /** 单媒体项起播：设源 → prepare → 应用默认值 → 恢复进度 → 播放 */
    private fun startPlayback(mediaItem: MediaItem, mediaId: String) {
        val c = controller
        if (c == null) {
            _uiState.update { it.copy(error = "播放服务尚未就绪") }
            return
        }
        c.setMediaItem(mediaItem)
        c.prepare()
        applyPlaybackDefaults(c)
        restorePositionIfRemembered(mediaId)
        c.playWhenReady = true
    }

    /** 多媒体列表起播 */
    private fun startPlaylist(items: List<MediaItem>, startIndex: Int, mediaId: String) {
        val c = controller
        if (c == null) {
            _uiState.update { it.copy(error = "播放服务尚未就绪") }
            return
        }
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        applyPlaybackDefaults(c)
        restorePositionIfRemembered(mediaId)
        c.playWhenReady = true
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

    /** 按「记忆播放位置」开关恢复进度 */
    private fun restorePositionIfRemembered(mediaId: String) {
        if (!rememberPositionCache) return
        viewModelScope.launch {
            val savedPosition = playerRepository.getPlaybackPosition(mediaId)
            if (savedPosition != null && savedPosition > 0) {
                controller?.seekTo(savedPosition)
            }
        }
    }

    /** 按「记忆播放位置」开关保存当前进度（幂等，可在暂停/播完/退出时多次调用） */
    private fun saveCurrentPosition() {
        val mediaId = currentMediaId ?: return
        if (!rememberPositionCache) return
        val c = controller ?: return
        val position = c.currentPosition
        val duration = c.duration
        if (duration <= 0) return
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
