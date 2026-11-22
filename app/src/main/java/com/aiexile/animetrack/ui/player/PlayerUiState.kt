package com.aiexile.animetrack.ui.player

import android.net.Uri
import androidx.media3.common.MediaItem
import com.aiexile.animetrack.data.player.web.PendingWebPlaybackRequest
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.model.WebMediaSource

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
    val error: PlaybackFailure? = null,
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
    /** 自动横屏开关（跟随设置，在全屏内按视频比例切换横竖屏） */
    val autoLandscape: Boolean = false,
    /** 自动连播下一集开关（跟随设置，支持在播放器内快捷切换） */
    val autoPlayNext: Boolean = true,
    /** 记住播放位置开关（跟随设置，支持在播放器内快捷切换） */
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
internal sealed interface PlayRequest {
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
        val referer: String?,
        val animeId: Int? = null,
        val animeTitle: String? = null
    ) : PlayRequest
    data class Playlist(val items: List<MediaItem>, val startIndex: Int) : PlayRequest
}