package com.aiexile.animetrack.ui.player

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.WebDAVDataSourceFactory
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
import kotlinx.coroutines.runBlocking
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
    val isLongPressSpeedActive: Boolean = false
)

/** 最近一次播放请求，用于错误后重试 */
private sealed interface PlayRequest {
    data class WebDav(val url: String, val title: String?) : PlayRequest
    data class Local(val uri: Uri, val title: String?) : PlayRequest
    data class Playlist(val items: List<MediaItem>, val startIndex: Int) : PlayRequest
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel(
    private val application: Application,
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
    }

    /** 硬件加速开关仅在构建播放器时读取一次（RenderersFactory 只能构建期传入），
     *  更改设置后需重新进入播放器页面生效。 */
    private val hardwareAccelerationEnabled: Boolean = runBlocking(Dispatchers.IO) {
        settingsRepository.playerHardwareAcceleration.first()
    }

    val player: ExoPlayer = ExoPlayer.Builder(application, createRenderersFactory(application, hardwareAccelerationEnabled))
        .setSeekBackIncrementMs(10000)
        .setSeekForwardIncrementMs(10000)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

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
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _uiState.update { it.copy(error = null) }
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

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
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
        player.addListener(playerListener)
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
        // 自动连播：关闭时在每集结尾暂停（官方 API，仅影响自动过渡，手动点下一集不受限）
        viewModelScope.launch {
            settingsRepository.playerAutoPlayNext.collect { enabled ->
                player.setPauseAtEndOfMediaItems(!enabled)
            }
        }
    }

    /** 构建渲染器工厂：硬解关闭时强制软件解码（保留回退，避免无解码器黑屏） */
    private fun createRenderersFactory(context: Context, allowHardware: Boolean): RenderersFactory =
        object : DefaultRenderersFactory(context) {
            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                if (allowHardware) {
                    super.buildVideoRenderers(
                        context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                        eventHandler, eventListener, allowedVideoJoiningTimeMs, out
                    )
                    return
                }
                val softwareOnlySelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    all.filterNot { it.hardwareAccelerated }.ifEmpty { all }
                }
                super.buildVideoRenderers(
                    context, extensionRendererMode, softwareOnlySelector, true,
                    eventHandler, eventListener, allowedVideoJoiningTimeMs, out
                )
            }
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
        _uiState.update {
            it.copy(
                currentPositionMs = if (player.duration > 0) player.currentPosition else 0,
                durationMs = if (player.duration > 0) player.duration else 0,
                bufferedPositionMs = if (player.duration > 0) player.bufferedPosition else 0
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
                val username = settingsRepository.webdavUsername.first()
                val password = settingsRepository.webdavPassword.first()

                val fullUrl = buildFullUrl(baseUrl, url)

                val webdavFactory = WebDAVDataSourceFactory(okHttpClient, username, password)
                val mediaSource = ProgressiveMediaSource.Factory(webdavFactory)
                    .createMediaSource(MediaItem.fromUri(fullUrl))

                val mediaId = fullUrl
                currentMediaId = mediaId

                _uiState.update { it.copy(mediaTitle = title, error = null) }

                player.setMediaSource(mediaSource)
                player.prepare()
                applyPlaybackDefaults()
                restorePositionIfRemembered(mediaId)

                player.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play WebDAV URL", e)
                _uiState.update { it.copy(error = "无法播放: ${e.message}") }
            }
        }
    }

    fun playLocalUri(uri: Uri, title: String? = null) {
        lastPlayRequest = PlayRequest.Local(uri, title)
        val dataSourceFactory = DefaultDataSource.Factory(application)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))

        val mediaId = uri.toString()
        currentMediaId = mediaId

        _uiState.update { it.copy(mediaTitle = title, error = null) }

        player.setMediaSource(mediaSource)
        player.prepare()
        applyPlaybackDefaults()
        restorePositionIfRemembered(mediaId)

        player.playWhenReady = true
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        lastPlayRequest = PlayRequest.Playlist(items, startIndex)

        currentMediaId = items[startIndex].mediaId
        _uiState.update { it.copy(mediaTitle = items[startIndex].mediaMetadata.title?.toString(), error = null) }

        player.setMediaItems(items, startIndex, 0L)
        player.prepare()
        applyPlaybackDefaults()
        restorePositionIfRemembered(currentMediaId!!)

        player.playWhenReady = true
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
    private fun applyPlaybackDefaults() {
        player.setPlaybackSpeed(defaultSpeedCache)
        _uiState.update { it.copy(isLongPressSpeedActive = false) }
    }

    /** 按「记忆播放位置」开关恢复进度 */
    private fun restorePositionIfRemembered(mediaId: String) {
        if (!rememberPositionCache) return
        viewModelScope.launch {
            val savedPosition = playerRepository.getPlaybackPosition(mediaId)
            if (savedPosition != null && savedPosition > 0) {
                player.seekTo(savedPosition)
            }
        }
    }

    /** 按「记忆播放位置」开关保存当前进度（幂等，可在暂停/播完/退出时多次调用） */
    private fun saveCurrentPosition() {
        val mediaId = currentMediaId ?: return
        if (!rememberPositionCache) return
        val position = player.currentPosition
        val duration = player.duration
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
        if (player.isPlaying) {
            player.pause()
            saveCurrentPosition()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        updatePositionState()
    }

    fun seekToPositionRatio(ratio: Float) {
        val duration = player.duration
        if (duration > 0) {
            player.seekTo((duration * ratio.coerceIn(0f, 1f)).toLong())
            updatePositionState()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    /** 跳到下一个媒体项 */
    fun seekToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    /** 是否有下一集 */
    fun hasNextEpisode(): Boolean = player.hasNextMediaItem()

    /** 长按加速：切换到长按速度（同步执行，避免竞态与延迟） */
    fun startLongPressSpeed() {
        if (_uiState.value.isLongPressSpeedActive) return
        speedBeforeLongPress = player.playbackParameters.speed
        player.setPlaybackSpeed(longPressSpeedCache)
        _uiState.update { it.copy(isLongPressSpeedActive = true) }
    }

    /** 松手恢复：回到长按前的速度 */
    fun stopLongPressSpeed() {
        if (!_uiState.value.isLongPressSpeedActive) return
        player.setPlaybackSpeed(speedBeforeLongPress)
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

        player.removeListener(playerListener)
        player.release()
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
