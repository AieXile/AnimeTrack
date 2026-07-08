package com.aiexile.animetrack.ui.player

import android.app.Application
import android.net.Uri
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.WebDAVDataSourceFactory
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Job
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
    val isLongPressSpeedActive: Boolean = false
)

class PlayerViewModel(
    private val application: Application,
    private val playerRepository: PlayerRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
    }

    val player: ExoPlayer = ExoPlayer.Builder(application)
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

    private var positionUpdateJob: Job? = null

    /** 长按加速前的原始播放速度 */
    private var speedBeforeLongPress: Float = 1f

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

                val savedPosition = playerRepository.getPlaybackPosition(mediaId)
                if (savedPosition != null && savedPosition > 0) {
                    player.seekTo(savedPosition)
                }

                player.playWhenReady = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play WebDAV URL", e)
                _uiState.update { it.copy(error = "无法播放: ${e.message}") }
            }
        }
    }

    fun playLocalUri(uri: Uri, title: String? = null) {
        val dataSourceFactory = DefaultDataSource.Factory(application)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))

        val mediaId = uri.toString()
        currentMediaId = mediaId

        _uiState.update { it.copy(mediaTitle = title, error = null) }

        player.setMediaSource(mediaSource)
        player.prepare()

        viewModelScope.launch {
            val savedPosition = playerRepository.getPlaybackPosition(mediaId)
            if (savedPosition != null && savedPosition > 0) {
                player.seekTo(savedPosition)
            }
        }

        player.playWhenReady = true
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return

        currentMediaId = items[startIndex].mediaId
        _uiState.update { it.copy(mediaTitle = items[startIndex].mediaMetadata.title?.toString(), error = null) }

        player.setMediaItems(items, startIndex, 0L)
        player.prepare()

        viewModelScope.launch {
            val savedPosition = playerRepository.getPlaybackPosition(currentMediaId!!)
            if (savedPosition != null && savedPosition > 0) {
                player.seekTo(savedPosition)
            }
        }

        player.playWhenReady = true
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
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

    /** 长按加速：切换到长按速度 */
    fun startLongPressSpeed() {
        if (_uiState.value.isLongPressSpeedActive) return
        speedBeforeLongPress = player.playbackParameters.speed
        viewModelScope.launch {
            val longPressSpeed = settingsRepository.playerLongPressSpeed.first()
            player.setPlaybackSpeed(longPressSpeed)
            _uiState.update { it.copy(isLongPressSpeedActive = true) }
        }
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

        val mediaId = currentMediaId
        if (mediaId != null) {
            val position = player.currentPosition
            val duration = player.duration
            if (duration > 0) {
                viewModelScope.launch {
                    try {
                        playerRepository.savePlaybackPosition(mediaId, position, duration)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save playback position", e)
                    }
                }
            }
        }

        player.removeListener(playerListener)
        player.release()
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
