package com.aiexile.animetrack.ui.player.coordinator

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.media.LocalMediaArchiveEntity
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.media.LocalSubtitle
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.webdav.WebDavDirectoryQueue
import com.aiexile.animetrack.data.player.webdav.WebDavPlaybackIdentity
import com.aiexile.animetrack.data.player.webdav.WebDavSubtitleLocator
import com.aiexile.animetrack.domain.playback.LocalPlaybackQueue
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.player.PlayRequest
import com.aiexile.animetrack.ui.player.PlaybackFailure
import com.aiexile.animetrack.ui.player.PlaybackFailureKind
import com.aiexile.animetrack.ui.player.PlayerEpisode
import com.aiexile.animetrack.ui.player.PlayerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.thegrizzlylabs.sardineandroid.DavResource

/**
 * 管理本地 URI、本地媒体库队列、WebDAV 媒体与自定义播放列表的准备和选集跳转。
 */
@UnstableApi
internal class LocalPlaybackCoordinator(
    private val application: Application,
    private val entryAnimeId: Int,
    private val animeRepository: AnimeRepository,
    private val settingsRepository: SettingsRepository,
    private val playerRepository: PlayerRepository,
    private val localMediaRepository: LocalMediaRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val sessionCoordinator: PlaybackSessionCoordinator,
    private val progressTracker: PlaybackProgressTracker,
    private val setCurrentMediaId: (String?) -> Unit,
    private val onBeginPlaybackRequest: () -> Unit,
    private val onResetForNewSource: () -> Unit,
    private val onRecordPlayRequest: (PlayRequest) -> Unit
) {
    companion object {
        private const val TAG = "LocalPlaybackCoord"

        /** 起播前外挂字幕扫描的等待上限：超时降级为无字幕起播，防慢服务器拖死点播 */
        private const val SUBTITLE_SCAN_TIMEOUT_MS = 3_000L
    }

    /** 本地媒体队列；非本地播放时为空 */
    var localQueue: List<LocalMediaEntity> = emptyList()
        private set

    private var webDavQueue = emptyList<WebDavDirectoryQueue.Episode>()
    private var webDavResources = emptyList<DavResource>()
    private var webDavPreparation: Job? = null
    val hasWebDavQueue: Boolean get() = webDavQueue.isNotEmpty()

    fun canSelectWebDav(index: Int): Boolean = index in webDavQueue.indices

    fun syncWebDavNavigation() {
        val index = uiState.value.currentEpisodeIndex
        sessionCoordinator.syncSingleItemQueueNavigation(hasWebDavQueue, index > 0, canSelectWebDav(index + 1))
    }

    fun cancelWebDavPreparation() { webDavPreparation?.cancel() }

    fun clearQueue() {
        localQueue = emptyList()
        webDavQueue = emptyList()
        webDavResources = emptyList()
    }

    fun indexOfWebDavMedia(mediaId: String?): Int = webDavQueue.indexOfFirst { it.target.mediaKey == mediaId }
    fun webDavTitle(index: Int): String? = webDavQueue.getOrNull(index)?.title
    fun webDavUrl(index: Int): String? = webDavQueue.getOrNull(index)?.target?.requestUrl?.toString()

    fun findLocalFile(mediaId: String?): LocalMediaEntity? =
        if (mediaId == null) null
        else localQueue.firstOrNull { LocalPlaybackQueue.mediaKey(it.id) == mediaId }

    fun indexOfLocalMedia(mediaId: String?): Int =
        if (mediaId == null) -1
        else localQueue.indexOfFirst { LocalPlaybackQueue.mediaKey(it.id) == mediaId }

    fun playWebDavUrl(url: String, title: String? = null) {
        if (url.isBlank()) {
            uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.INVALID_ADDRESS)) }
            return
        }
        onRecordPlayRequest(PlayRequest.WebDav(url, title))
        onBeginPlaybackRequest()
        onResetForNewSource()
        uiState.update { it.copy(mediaTitle = title, sourceLabel = "WebDAV") }
        webDavPreparation = scope.launch {
            try {
                val server = WebDavPlaybackIdentity.directory(settingsRepository.playerWebdavUrl.first())
                val root = server?.let { WebDavPlaybackIdentity.mediaRoot(it, settingsRepository.playerWebdavMediaRoot.first()) }
                val username = settingsRepository.playerWebdavUsername.first()
                val target = if (server != null && root != null) WebDavPlaybackIdentity.target(server, root, username, url) else null
                if (target == null || server == null || root == null) {
                    uiState.update {
                        it.copy(error = PlaybackFailure(PlaybackFailureKind.INVALID_ADDRESS), isPreparingMedia = false)
                    }
                    return@launch
                }

                // 复用浏览页已查好的目录，只生成视频和字幕引用，不下载其他集的内容。
                val resources = withTimeoutOrNull(SUBTITLE_SCAN_TIMEOUT_MS) {
                    WebDavSubtitleLocator.loadResources(target.requestUrl.toString(), settingsRepository)
                }.orEmpty()
                val queue = withContext(Dispatchers.IO) {
                    WebDavDirectoryQueue.build(server, root, username, target, title, resources)
                }
                webDavResources = resources
                webDavQueue = queue
                uiState.update {
                    it.copy(episodes = queue.mapIndexed { index, episode ->
                        PlayerEpisode(episode.number?.let { "第${it}集" } ?: episode.title, index)
                    })
                }
                startWebDavEpisode(queue.indexOfFirst { it.target.mediaKey == target.mediaKey })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AppLogManager.w(TAG, "WebDAV 播放准备失败")
                uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.WEBDAV_PREPARE), isPreparingMedia = false) }
            }
        }
    }

    private suspend fun startWebDavEpisode(index: Int) {
        val episode = checkNotNull(webDavQueue.getOrNull(index))
        val target = episode.target
        val requestUrl = target.requestUrl.toString()
        val sources = webDavResources
        val subtitles = withContext(Dispatchers.IO) { WebDavSubtitleLocator.selectTracks(requestUrl, sources) }
        try {
            playerRepository.importLegacyWebDavProgress(target)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AppLogManager.w(TAG, "WebDAV 旧进度读取失败")
        }
        sessionCoordinator.updateMetadataAnimeId(null)
        val anime = sessionCoordinator.resolveMetadataAnime(null)
        val item = MediaItem.Builder().setMediaId(target.mediaKey).setUri(requestUrl)
            .setMediaMetadata(sessionCoordinator.buildMediaMetadata(
                title = episode.title,
                artist = anime?.title?.takeIf(String::isNotBlank) ?: "WebDAV",
                albumTitle = anime?.title,
                anime = anime
            ))
            .setSubtitleConfigurations(subtitles.tracks)
            .build()
        setCurrentMediaId(target.mediaKey)
        onRecordPlayRequest(PlayRequest.WebDav(requestUrl, episode.title))
        uiState.update {
            it.copy(
                mediaTitle = episode.title,
                currentEpisodeIndex = index,
                notice = if (subtitles.skippedOversized) application.getString(R.string.player_webdav_subtitle_too_large) else null
            )
        }
        syncWebDavNavigation()
        sessionCoordinator.startPlayback(item, target.mediaKey)
    }

    fun playLocalUri(uri: Uri, title: String? = null) {
        onRecordPlayRequest(PlayRequest.Local(uri, title))
        onBeginPlaybackRequest()
        onResetForNewSource()
        sessionCoordinator.updateMetadataAnimeId(null)
        val mediaId = uri.toString()
        setCurrentMediaId(mediaId)
        val displayTitle = title?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
            ?: application.getString(R.string.player_local_source)
        uiState.update {
            it.copy(
                mediaTitle = displayTitle,
                error = null,
                sourceLabel = application.getString(R.string.player_local_source)
            )
        }
        val currentAnime = sessionCoordinator.currentMetadataAnime
        val mediaItem = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(
                sessionCoordinator.buildMediaMetadata(
                    title = displayTitle,
                    artist = currentAnime?.title?.takeIf { it.isNotBlank() }
                        ?: application.getString(R.string.player_local_source),
                    albumTitle = currentAnime?.title,
                    anime = currentAnime
                )
            )
            .build()
        sessionCoordinator.startPlayback(mediaItem, mediaId)
    }

    /**
     * 从本地媒体库播放：按当前文件构建选集队列，稳定媒体 ID 为 local:<id>。
     * 字幕查找与队列构建都在 IO 完成，失败不阻塞起播。
     */
    fun playLocalMedia(mediaId: Long) {
        onRecordPlayRequest(PlayRequest.LocalMedia(mediaId))
        onBeginPlaybackRequest()
        onResetForNewSource()
        uiState.update { it.copy(sourceLabel = application.getString(R.string.player_local_source)) }
        scope.launch {
            val selected = localMediaRepository.getMediaById(mediaId)
            if (selected == null || selected.isMissing || selected.isHidden) {
                uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.FILE_NOT_FOUND), isPreparingMedia = false) }
                return@launch
            }
            val archive = selected.archiveKey?.let { localMediaRepository.getArchive(selected.sourceId, it) }
            val files = localMediaRepository.getPlayableGroup(selected)
            val queue = LocalPlaybackQueue.build(selected, files)
            if (queue.isEmpty()) {
                uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.NO_LOCAL_MEDIA), isPreparingMedia = false) }
                return@launch
            }

            val startTargetAnimeId = entryAnimeId.takeIf { it > 0 } ?: selected.matchedAnimeId
            sessionCoordinator.updateMetadataAnimeId(startTargetAnimeId)
            val resolvedAnimeMap = withContext(Dispatchers.IO) {
                val ids = (listOfNotNull(startTargetAnimeId) + queue.mapNotNull { it.matchedAnimeId })
                    .filter { it > 0 }
                    .distinct()
                ids.mapNotNull { id ->
                    animeRepository.getAnimeById(id)?.let { id to it }
                }.toMap()
            }
            val startAnime = resolvedAnimeMap[startTargetAnimeId]
            if (startAnime != null) {
                sessionCoordinator.currentMetadataAnime = startAnime
            }
            val items = withContext(Dispatchers.IO) {
                val subtitlesById = localMediaRepository.findLocalSubtitles(queue, SUBTITLE_SCAN_TIMEOUT_MS)
                queue.map { file ->
                    val itemAnime = resolvedAnimeMap[entryAnimeId.takeIf { it > 0 } ?: file.matchedAnimeId]
                    buildLocalMediaItem(file, subtitlesById[file.id].orEmpty(), itemAnime, archive)
                }
            }
            localQueue = queue
            progressTracker.resetSampling()
            val startIndex = queue.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
            val startMediaId = LocalPlaybackQueue.mediaKey(queue[startIndex].id)
            setCurrentMediaId(startMediaId)
            uiState.update {
                it.copy(
                    mediaTitle = localDisplayTitle(queue[startIndex]),
                    seriesTitle = archive?.title ?: startAnime?.title ?: queue[startIndex].parsedTitle,
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
            sessionCoordinator.startPlaylist(items, startIndex, startMediaId)
        }
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        onRecordPlayRequest(PlayRequest.Playlist(items, startIndex))
        onBeginPlaybackRequest()
        onResetForNewSource()
        sessionCoordinator.updateMetadataAnimeId(null)

        val startMediaId = items[startIndex].mediaId
        setCurrentMediaId(startMediaId)
        uiState.update {
            it.copy(mediaTitle = items[startIndex].mediaMetadata.title?.toString(), error = null)
        }
        val enriched = items.map {
            sessionCoordinator.enrichMediaItemMetadata(it, sessionCoordinator.currentMetadataAnime)
        }
        sessionCoordinator.startPlaylist(enriched, startIndex, startMediaId)
    }

    fun selectEpisode(index: Int) {
        val c = sessionCoordinator.controller ?: return
        if (hasWebDavQueue) {
            if (!canSelectWebDav(index) || uiState.value.currentEpisodeIndex == index) return
            progressTracker.saveCurrentPosition()
            c.pause()
            onBeginPlaybackRequest()
            progressTracker.resetSampling()
            webDavPreparation = scope.launch {
                try {
                    startWebDavEpisode(index)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.WEBDAV_PREPARE), isPreparingMedia = false) }
                }
            }
            return
        }
        if (index !in localQueue.indices) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun localDisplayTitle(file: LocalMediaEntity): String {
        val episode = file.decimalEpisode ?: file.episodeNumber?.toString()
        val title = file.parsedTitle?.takeIf { it.isNotBlank() } ?: file.fileName
        return if (episode != null) "$title · 第${episode}集" else title
    }

    private fun buildLocalMediaItem(
        file: LocalMediaEntity,
        subtitles: List<LocalSubtitle>,
        anime: Anime?,
        archive: LocalMediaArchiveEntity?
    ): MediaItem = MediaItem.Builder()
        .setMediaId(LocalPlaybackQueue.mediaKey(file.id))
        .setUri(Uri.parse(file.documentUri))
        .setMediaMetadata(
            sessionCoordinator.buildMediaMetadata(
                title = localDisplayTitle(file),
                artist = archive?.title ?: anime?.title?.takeIf { it.isNotBlank() }
                    ?: file.parsedTitle?.takeIf { it.isNotBlank() }
                    ?: application.getString(R.string.player_local_source),
                albumTitle = archive?.title ?: anime?.title ?: file.parsedTitle,
                anime = anime,
                artworkUrl = archive?.coverUrl
            )
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
}