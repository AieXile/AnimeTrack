package com.aiexile.animetrack.ui.player.coordinator

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.player.PlaybackTrace
import com.aiexile.animetrack.data.player.ManualSubtitleFile
import com.aiexile.animetrack.data.player.service.CoilBitmapLoader
import com.aiexile.animetrack.data.player.service.PlaybackService
import com.aiexile.animetrack.data.player.service.PlaybackSessionCommands
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.player.PlaybackFailure
import com.aiexile.animetrack.ui.player.PlaybackFailureKind
import com.aiexile.animetrack.ui.player.PlayerUiState
import com.aiexile.animetrack.ui.player.components.renderedVideoDisplaySize
import com.aiexile.animetrack.ui.player.components.selectedVideoLandscape
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File

/**
 * 管理与后台 [PlaybackService] 的 [MediaController] 连接、媒体起播、
 * 媒体会话元数据（封面/番剧名）与全屏方向判定。
 */
@UnstableApi
internal class PlaybackSessionCoordinator(
    private val application: Application,
    private val animeId: Int,
    private val animeRepository: AnimeRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val progressTracker: PlaybackProgressTracker,
    private val getDefaultSpeed: () -> Float,
    private val onControllerConnected: () -> Unit,
    private val findLocalFile: (mediaId: String) -> LocalMediaEntity?,
    private val hasLocalQueue: () -> Boolean,
    private val localDisplayTitle: (LocalMediaEntity) -> String
) {
    companion object {
        private const val TAG = "PlaybackSessionCoord"
    }

    var controller: MediaController? = null
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playbackStartJob: Job? = null
    private val fullscreenVideoOrientation = FullscreenVideoOrientation()

    /** 供媒体会话元数据（封面/番剧名）使用的番剧 ID：优先入口参数，其次本地匹配/网页绑定 */
    val metadataAnimeId = MutableStateFlow(animeId.takeIf { it > 0 })
    var currentMetadataAnime: Anime? = null

    /** 本次起播期望的初始位置：用于 READY 时校验位置是否被字幕合并重启丢掉 */
    var expectedStartPositionMs: Long = 0L

    fun connect(playerListener: Player.Listener, controllerListener: MediaController.Listener) {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, PlaybackService::class.java)
        )
        val future = MediaController.Builder(application, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get().also {
                    it.addListener(playerListener)
                }
                val connected = controller
                if (connected != null) {
                    fullscreenVideoOrientation.trackLandscape = connected.currentTracks.selectedVideoLandscape()
                    if (connected.playbackState == Player.STATE_READY &&
                        connected.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
                    ) {
                        fullscreenVideoOrientation.renderedLandscape = connected.renderedVideoDisplaySize()?.isLandscape
                    }
                }
                uiState.update {
                    it.copy(
                        isControllerReady = true,
                        playWhenReady = connected?.playWhenReady == true,
                        isBuffering = connected?.playbackState == Player.STATE_BUFFERING
                    )
                }
                onControllerConnected()
                updateFullscreenOrientation()
            } catch (_: java.util.concurrent.CancellationException) {
                // onCleared 已取消连接（快速退出页面），controller 由 future 内部释放
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to connect to PlaybackService", e)
                uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.SERVICE), isPreparingMedia = false) }
            }
        }, MoreExecutors.directExecutor())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMetadataAnime() {
        scope.launch {
            metadataAnimeId
                .flatMapLatest { id ->
                    if (id != null && id > 0) animeRepository.observeAnimeById(id) else flowOf(null)
                }
                .collectLatest { anime ->
                    currentMetadataAnime = anime
                    refreshActiveMediaMetadata()
                }
        }
    }

    fun cancelStartJob() {
        playbackStartJob?.cancel()
    }

    fun updateManualSubtitle(expected: MediaItem, subtitle: MediaItem.SubtitleConfiguration?): Boolean {
        val player = controller ?: return false
        if (player.currentMediaItem != expected) return false
        val original = expected.localConfiguration?.subtitleConfigurations ?: return false
        val tracks = listOfNotNull(subtitle) + original.filter { it.id != ManualSubtitleFile.TRACK_ID }
        if (tracks == original) return false
        val index = player.currentMediaItemIndex
        val position = player.currentPosition.coerceAtLeast(0)
        // 只替换当前项；保留队列、播放意图和倍速。重新开源时明确恢复当前时间轴位置。
        player.replaceMediaItem(index, expected.buildUpon().setSubtitleConfigurations(tracks).build())
        if (player.currentMediaItemIndex == index && player.currentPosition != position) player.seekTo(index, position)
        val params = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (subtitle != null) params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        player.trackSelectionParameters = params.build()
        return true
    }

    fun updateMetadataAnimeId(targetId: Int?) {
        val effectiveId = animeId.takeIf { it > 0 } ?: targetId?.takeIf { it > 0 }
        metadataAnimeId.value = effectiveId
        if (effectiveId == null || currentMetadataAnime?.id != effectiveId) {
            currentMetadataAnime = null
        }
    }

    suspend fun resolveMetadataAnime(targetId: Int?): Anime? {
        val effectiveId = animeId.takeIf { it > 0 } ?: targetId?.takeIf { it > 0 } ?: return null
        currentMetadataAnime?.takeIf { it.id == effectiveId }?.let { return it }
        return runCatching { animeRepository.getAnimeById(effectiveId) }.getOrNull()?.also {
            currentMetadataAnime = it
        }
    }

    fun buildMediaMetadata(
        title: String,
        artist: String?,
        albumTitle: String?,
        anime: Anime?,
        artworkUrl: String? = null
    ): MediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist?.takeIf { it.isNotBlank() } ?: anime?.title?.takeIf { it.isNotBlank() })
        .setAlbumTitle(albumTitle?.takeIf { it.isNotBlank() } ?: anime?.title?.takeIf { it.isNotBlank() })
        .setArtworkUri(resolveArtworkUri(anime, artworkUrl))
        .build()

    private fun resolveArtworkUri(anime: Anime?, artworkUrl: String?): Uri {
        val candidates = listOfNotNull(
            anime?.coverUrl?.takeIf { it.isNotBlank() },
            anime?.remoteCoverUrl?.takeIf { it.isNotBlank() },
            artworkUrl?.takeIf { it.isNotBlank() }
        )
        for (raw in candidates) {
            if (raw.startsWith("/") || raw.startsWith("file://")) {
                val file = File(raw.removePrefix("file://"))
                if (file.exists() && file.length() > 0) {
                    return Uri.fromFile(file)
                }
            } else {
                return Uri.parse(raw)
            }
        }
        return CoilBitmapLoader.appIconUri(application)
    }

    fun enrichMediaItemMetadata(item: MediaItem, anime: Anime?): MediaItem {
        val localFile = findLocalFile(item.mediaId)
        val existing = item.mediaMetadata
        val title = localFile?.let(localDisplayTitle)
            ?: existing.title?.toString()?.takeIf { it.isNotBlank() }
            ?: uiState.value.mediaTitle?.takeIf { it.isNotBlank() }
            ?: item.localConfiguration?.uri?.lastPathSegment
            ?: application.getString(R.string.app_name)
        val artist = anime?.title?.takeIf { it.isNotBlank() }
            ?: existing.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: localFile?.parsedTitle?.takeIf { it.isNotBlank() }
            ?: uiState.value.seriesTitle?.takeIf { it.isNotBlank() }
            ?: uiState.value.sourceLabel
        val albumTitle = anime?.title?.takeIf { it.isNotBlank() }
            ?: existing.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: localFile?.parsedTitle?.takeIf { it.isNotBlank() }
        val updated = buildMediaMetadata(title, artist, albumTitle, anime,
            existing.artworkUri?.toString()?.takeIf { localFile?.archiveKey != null })
        return if (updated == existing) item else item.buildUpon().setMediaMetadata(updated).build()
    }

    fun refreshActiveMediaMetadata() {
        val c = controller ?: return
        val count = c.mediaItemCount
        if (count <= 0) return
        val index = c.currentMediaItemIndex
        if (index !in 0 until count) return
        val current = c.getMediaItemAt(index)
        val updated = enrichMediaItemMetadata(current, currentMetadataAnime)
        if (updated != current) {
            c.replaceMediaItem(index, updated)
        }
    }

    fun syncWebNavigation(queue: WebPlaybackQueue?) = syncSingleItemQueueNavigation(
        hasQueue = queue != null,
        hasPrevious = queue != null && queue.episodeIndex > 0,
        hasNext = queue != null && queue.episodeIndex + 1 < queue.episodes.size
    )

    /** 网页与 WebDAV 都只向服务提交当前集；通知栏切集由页面队列处理。 */
    fun syncSingleItemQueueNavigation(hasQueue: Boolean, hasPrevious: Boolean, hasNext: Boolean) {
        val c = controller ?: return
        val args = PlaybackSessionCommands.buildWebNavigationArgs(
            isWebQueue = hasQueue, hasPrevious = hasPrevious, hasNext = hasNext
        )
        c.sendCustomCommand(
            SessionCommand(PlaybackSessionCommands.ACTION_SET_WEB_NAVIGATION, Bundle.EMPTY),
            args
        )
    }

    /** 单媒体项起播：等连接就绪 → 读记忆进度 → 带初始位置设源 → prepare → 应用默认值 → 播放 */
    fun startPlayback(
        mediaItem: MediaItem,
        mediaId: String,
        initialPositionMs: Long? = null,
        playWhenReady: Boolean = true,
        speed: Float? = null
    ) {
        playbackStartJob = scope.launch {
            val c = awaitController() ?: run {
                uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.SERVICE), isPreparingMedia = false) }
                return@launch
            }
            val anime = resolveMetadataAnime(metadataAnimeId.value)
            val resolvedItem = enrichMediaItemMetadata(mediaItem, anime)
            // 官方姿势：IDLE 状态下 setMediaItem(item, startPositionMs)，
            // prepare 后直接从该位置起播，避免 prepare 后再异步 seek 的竞态
            val startPosition = initialPositionMs?.coerceAtLeast(0L)
                ?: progressTracker.readSavedPosition(mediaId)
            // 分多次看完一集时累计有效观看，避免续播后的片尾被误判为未看完。
            if (startPosition > 0) {
                progressTracker.seedWatchedMsFromSaved(mediaId)
            }
            expectedStartPositionMs = startPosition
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
            PlaybackTrace.event { "start.single pos=$startPosition play=$playWhenReady subtitles=${resolvedItem.localConfiguration?.subtitleConfigurations?.size ?: 0}" }
            c.setMediaItem(resolvedItem, startPosition)
            c.prepare()
            applyPlaybackDefaults(c)
            speed?.let { c.setPlaybackSpeed(it) }
            c.playWhenReady = playWhenReady
            uiState.update {
                it.copy(
                    isPreparingMedia = false,
                    isResolvingMedia = false,
                    currentPositionMs = startPosition,
                    durationMs = 0,
                    bufferedPositionMs = 0,
                    playWhenReady = c.playWhenReady,
                    isBuffering = c.playbackState == Player.STATE_BUFFERING
                )
            }
            updateFullscreenOrientation()
        }
    }

    /** 多媒体列表起播（仅首项恢复记忆进度，后续集从头播） */
    fun startPlaylist(items: List<MediaItem>, startIndex: Int, mediaId: String) {
        playbackStartJob = scope.launch {
            val c = awaitController() ?: run {
                uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.SERVICE), isPreparingMedia = false) }
                return@launch
            }
            val startPosition = progressTracker.readSavedPosition(mediaId)
            // 分多次看完本地单集时累计已观看时长，避免续播后半段误判为未看完
            if (startPosition > 0) {
                progressTracker.seedWatchedMsFromSaved(mediaId)
            }
            val anime = resolveMetadataAnime(metadataAnimeId.value)
            val resolvedItems = if (anime != null && !hasLocalQueue()) {
                items.map { enrichMediaItemMetadata(it, anime) }
            } else {
                items
            }
            expectedStartPositionMs = startPosition
            PlaybackTrace.event { "start.playlist index=$startIndex pos=$startPosition" }
            c.setMediaItems(resolvedItems, startIndex, startPosition)
            c.prepare()
            applyPlaybackDefaults(c)
            c.playWhenReady = true
            uiState.update {
                it.copy(
                    isPreparingMedia = false,
                    playWhenReady = c.playWhenReady,
                    isBuffering = c.playbackState == Player.STATE_BUFFERING
                )
            }
            updateFullscreenOrientation()
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
            }, MoreExecutors.directExecutor())
            // 连接由 ViewModel 持有；切换播放请求只取消等待，退出页面时再释放 future。
        }
    }

    /** 每次起播应用默认播放速度，并复位长按加速状态 */
    private fun applyPlaybackDefaults(target: Player) {
        target.setPlaybackSpeed(getDefaultSpeed())
        uiState.update { it.copy(isLongPressSpeedActive = false) }
    }

    /**
     * 首次 READY 时校验记忆位置是否真的生效。
     * 实测：外挂字幕（MergingMediaSource）合并完成时会重启视频提取，
     * setMediaItem(item, startPosition) 的初始位置会被丢回 0；
     * 发现回退即补偿 seek，保证「播放记忆」可靠。
     */
    fun restoreStartPositionIfNeeded() {
        val expected = expectedStartPositionMs
        if (expected <= 0L) return
        expectedStartPositionMs = 0L
        val c = controller ?: return
        val position = c.currentPosition
        PlaybackTrace.event { "resume.ready pos=$position expected=$expected compensating=${position < expected - 1000L}" }
        if (position < expected - 1000L) {
            c.seekTo(expected)
        }
    }

    fun onFullscreenVideoEvents(player: Player, events: Player.Events) {
        val mediaChanged = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
        if (mediaChanged) fullscreenVideoOrientation.onMediaItemTransition()
        if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED)) {
            fullscreenVideoOrientation.renderedLandscape = player.renderedVideoDisplaySize()?.isLandscape
        }
        // MediaController 会在切集的本地预测状态中暂时保留旧轨道和尺寸。
        // 读取新轨道事件，或等待切集后的 READY 来确认轨道。渲染尺寸只在收到尺寸事件时更新。
        if (events.contains(Player.EVENT_TRACKS_CHANGED) ||
            (!mediaChanged && events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_READY)
        ) {
            fullscreenVideoOrientation.trackLandscape = player.currentTracks.selectedVideoLandscape()
        }
        updateFullscreenOrientation()
    }

    /** 尺寸未就绪时返回 null，让首次全屏保持竖向、切集期间保持当前方向。 */
    fun fullscreenLandscapeTarget(): Boolean? {
        val state = uiState.value
        if (!state.autoLandscape) return true
        if (state.isPreparingMedia) return null
        return fullscreenVideoOrientation.landscape
    }

    /** 自动方向只作用于已经打开的全屏模式，媒体就绪、换集和缓冲都不能放大详情页。 */
    fun updateFullscreenOrientation() {
        if (!uiState.value.isFullscreen) return
        val landscape = fullscreenLandscapeTarget() ?: return
        uiState.update { it.copy(fullscreenLandscape = landscape) }
    }

    fun release(playerListener: Player.Listener) {
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
    }
}
