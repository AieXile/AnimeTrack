package com.aiexile.animetrack.ui.player.coordinator

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.player.web.PendingWebPlaybackRequest
import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository
import com.aiexile.animetrack.data.player.web.WebPlaybackSession
import com.aiexile.animetrack.data.websource.WebSourceEngine
import com.aiexile.animetrack.data.websource.WebSourceRuleRepository
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.domain.playback.WebSourceClassifier
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.model.WebMediaSource
import com.aiexile.animetrack.model.WebSourceRule
import com.aiexile.animetrack.ui.player.PlayRequest
import com.aiexile.animetrack.ui.player.PlaybackFailure
import com.aiexile.animetrack.ui.player.PlaybackFailureKind
import com.aiexile.animetrack.ui.player.PlayerEpisode
import com.aiexile.animetrack.ui.player.PlayerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 管理网页播放链路：剧集页/规则解析、WebView 辅助嗅探握手、线路与片源切换、网页选集与历史写入。
 */
@UnstableApi
internal class WebPlaybackCoordinator(
    private val application: Application,
    private val entryAnimeId: Int,
    private val webSourceEngine: WebSourceEngine,
    private val webPlaybackHistoryRepository: WebPlaybackHistoryRepository,
    private val webSourceRuleRepository: WebSourceRuleRepository,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val sessionCoordinator: PlaybackSessionCoordinator,
    private val progressTracker: PlaybackProgressTracker,
    private val getCurrentMediaId: () -> String?,
    private val setCurrentMediaId: (String?) -> Unit,
    private val getNormalPlaybackSpeed: () -> Float,
    private val onBeginPlaybackRequest: () -> Unit,
    private val onResetForNewSource: () -> Unit,
    private val onRecordPlayRequest: (PlayRequest) -> Unit
) {
    private val _webParseRequest = MutableStateFlow<PendingWebPlaybackRequest?>(null)
    val webParseRequest = _webParseRequest.asStateFlow()

    private var resolvingWebRequest: PendingWebPlaybackRequest? = null
    private var webResolveJob: Job? = null

    var webQueue: WebPlaybackQueue? = null
        private set

    /** 网页播放绑定的番剧；为空表示全局网页入口，不推进追番进度 */
    private var webAnimeId: Int? = null
    private var activeWebRequest: PendingWebPlaybackRequest? = null
    private var webStartPositionMs: Long? = null
    private var webPlaybackIntent = true
    private var webPlaybackSpeed: Float? = null
    private var capturedWebMediaUrl: String? = null

    val hasQueue: Boolean
        get() = webQueue != null

    fun cancelActiveResolution() {
        webResolveJob?.cancel()
        resolvingWebRequest = null
        _webParseRequest.value = null
    }

    fun resetState() {
        webQueue = null
        webAnimeId = null
        activeWebRequest = null
        capturedWebMediaUrl = null
    }

    /** 选集后立即进入播放页；切线路/清晰度时显式传入位置和播放意图。 */
    fun playWebPage(
        request: PendingWebPlaybackRequest,
        startPositionMs: Long? = null,
        playWhenReady: Boolean = true,
        preserveSpeed: Boolean = false
    ) {
        progressTracker.saveCurrentPosition()
        val preservedWatchedMs = if (startPositionMs != null && startPositionMs > 0L) progressTracker.watchedMs else 0L
        val speed = if (preserveSpeed) getNormalPlaybackSpeed() else null
        sessionCoordinator.controller?.pause()
        sessionCoordinator.controller?.stop()
        onBeginPlaybackRequest()
        onResetForNewSource()
        progressTracker.resetSampling(preservedWatchedMs)
        // 解析期间仍可能收到旧播放器回调，旧媒体不能再写入新集进度。
        setCurrentMediaId(null)
        val configured = request.copy(userAgent = request.userAgent ?: WebSourceRule.DEFAULT_USER_AGENT)
        activeWebRequest = configured
        webQueue = configured.queue
        webAnimeId = configured.animeId
        webStartPositionMs = startPositionMs
        webPlaybackIntent = playWhenReady
        webPlaybackSpeed = speed
        onRecordPlayRequest(PlayRequest.WebPage(configured))
        resolvingWebRequest = configured
        publishWebQueue()
        uiState.update {
            it.copy(
                mediaTitle = configured.title,
                isResolvingMedia = true,
                currentPositionMs = startPositionMs ?: 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
                isWebPlayback = true,
                webSources = emptyList(),
                selectedWebSource = -1
            )
        }
        webResolveJob = scope.launch {
            var resolved = configured
            if (configured.restoreQueue && configured.queue == null &&
                !WebSourceClassifier.isCandidate(configured.url)
            ) {
                val queue = try {
                    val rule = webSourceRuleRepository.rules.first().firstOrNull {
                        it.baseUrl.toHttpUrlOrNull()?.host == configured.url.toHttpUrlOrNull()?.host
                    }
                    val page = webSourceEngine.page(
                        configured.url, configured.userAgent!!,
                        configured.referer, rule
                    )
                    WebPlaybackQueue.from(page, configured.url) ?: configured.seriesUrl
                        ?.takeIf { it != configured.url }?.let { series ->
                            val detail = webSourceEngine.page(
                                series, configured.userAgent,
                                configured.referer, rule
                            )
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
                    onRecordPlayRequest(PlayRequest.WebPage(resolved))
                    webQueue = queue
                    publishWebQueue()
                }
            }
            val sources = try {
                if (WebSourceClassifier.isCandidate(resolved.url)) {
                    listOf(WebMediaSource(resolved.url, mimeType = resolved.mimeType))
                } else {
                    webSourceEngine.resolveMedia(resolved.url, resolved.userAgent!!, resolved.referer)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (resolvingWebRequest !== resolved) return@launch
            uiState.update { it.copy(webSources = sources) }
            val source = sources.firstOrNull()
            if (source != null) {
                completeWebPage(
                    resolved,
                    source.url,
                    source.mimeType ?: WebSourceClassifier.mimeType(source.url)
                )
            } else {
                _webParseRequest.value = resolved
            }
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
        if (sources.isNotEmpty()) uiState.update { it.copy(webSources = sources) }
        val updated = request.copy(headers = headers, userAgent = userAgent)
        activeWebRequest = updated
        capturedWebMediaUrl = url
        playResolvedWeb(updated, url, mimeType, headers, userAgent)
        // 媒体直链可能过期，重试必须重新解析该集网页。
        onRecordPlayRequest(PlayRequest.WebPage(updated))
    }

    fun updateWebSources(request: PendingWebPlaybackRequest, sources: List<WebMediaSource>) {
        if (resolvingWebRequest !== request) return
        uiState.update { it.copy(webSources = sources.distinctBy { source -> source.url }.take(32)) }
    }

    private fun playResolvedWeb(
        request: PendingWebPlaybackRequest,
        url: String,
        mimeType: String?,
        headers: Map<String, String>,
        userAgent: String?
    ) {
        onBeginPlaybackRequest()
        val mediaId = request.progressMediaId ?: webQueue?.mediaKey() ?: "web:${request.pageUrl ?: request.url}"
        setCurrentMediaId(mediaId)
        WebPlaybackSession.prepare(
            application, request.pageUrl ?: request.url, url,
            headers, userAgent, request.referer ?: request.url
        )
        val availableSources = uiState.value.webSources
        val sources = if (availableSources.any { it.url == url }) {
            availableSources
        } else {
            availableSources + WebMediaSource(url, mimeType = mimeType)
        }
        sessionCoordinator.updateMetadataAnimeId(request.animeId)
        val anime = sessionCoordinator.currentMetadataAnime?.takeIf {
            it.id == (entryAnimeId.takeIf { id -> id > 0 } ?: request.animeId)
        }
        val displayTitle = request.title?.takeIf { it.isNotBlank() } ?: url
        val seriesTitle = anime?.title?.takeIf { it.isNotBlank() }
            ?: request.animeTitle?.takeIf { it.isNotBlank() }
            ?: webQueue?.page?.title?.takeIf { it.isNotBlank() }
            ?: uiState.value.sourceLabel
            ?: application.getString(R.string.web_playback_title)
        uiState.update {
            it.copy(
                mediaTitle = displayTitle,
                webSources = sources,
                selectedWebSource = sources.indexOfFirst { source -> source.url == url }
            )
        }
        val builder = MediaItem.Builder().setMediaId(mediaId).setUri(url)
            .setMediaMetadata(
                sessionCoordinator.buildMediaMetadata(
                    title = displayTitle,
                    artist = seriesTitle,
                    albumTitle = anime?.title ?: request.animeTitle ?: webQueue?.page?.title,
                    anime = anime
                )
            )
        mimeType?.let(builder::setMimeType)
        sessionCoordinator.startPlayback(
            builder.build(),
            mediaId,
            webStartPositionMs,
            webPlaybackIntent,
            webPlaybackSpeed
        )
        val queue = webQueue
        scope.launch {
            webPlaybackHistoryRepository.add(
                request.pageUrl ?: request.url, request.title,
                userAgent, request.referer, queue?.seriesUrl ?: request.seriesUrl, queue, mediaId,
                request.animeId, request.animeTitle
            )
        }
    }

    fun cancelWebResolution() {
        if (resolvingWebRequest == null) return
        cancelActiveResolution()
        uiState.update {
            it.copy(
                isPreparingMedia = false,
                isResolvingMedia = false,
                isBuffering = false,
                error = PlaybackFailure(PlaybackFailureKind.RESOLUTION_CANCELLED)
            )
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
        queue: WebPlaybackQueue? = null,
        animeId: Int? = null,
        animeTitle: String? = null
    ) {
        if (url.isBlank()) {
            uiState.update { it.copy(error = PlaybackFailure(PlaybackFailureKind.INVALID_ADDRESS)) }
            return
        }
        progressTracker.saveCurrentPosition()
        sessionCoordinator.controller?.pause()
        onBeginPlaybackRequest()
        onResetForNewSource()
        webQueue = queue
        webStartPositionMs = null
        webPlaybackIntent = true
        webPlaybackSpeed = null
        val request = PendingWebPlaybackRequest(
            pageUrl ?: url, mimeType, title, pageUrl,
            headers, userAgent, referer, resolvePage = pageUrl != null, queue = queue,
            animeId = animeId, animeTitle = animeTitle
        )
        activeWebRequest = request
        webAnimeId = animeId
        capturedWebMediaUrl = url
        publishWebQueue()
        uiState.update { it.copy(isWebPlayback = true, webSources = emptyList(), selectedWebSource = -1) }
        playResolvedWeb(request, url, mimeType, headers, userAgent)
        onRecordPlayRequest(
            if (pageUrl != null) PlayRequest.WebPage(request)
            else PlayRequest.Web(
                url, mimeType, title, pageUrl, headers, userAgent, referer,
                animeId, animeTitle
            )
        )
    }

    private fun publishWebQueue() {
        val queue = webQueue
        uiState.update {
            it.copy(
                seriesTitle = queue?.page?.title,
                sourceLabel = Uri.parse(
                    queue?.page?.url ?: activeWebRequest?.pageUrl
                    ?: activeWebRequest?.url.orEmpty()
                ).host
                    ?: application.getString(R.string.web_playback_title),
                episodes = queue?.episodes?.mapIndexed { index, episode ->
                    PlayerEpisode(episode.name, index)
                }.orEmpty(),
                currentEpisodeIndex = queue?.episodeIndex ?: -1,
                channels = queue?.page?.channels?.map { channel -> channel.name }.orEmpty(),
                currentChannelIndex = queue?.channelIndex ?: -1
            )
        }
        publishEpisodeProgress(progressTracker.savedProgress)
        sessionCoordinator.syncWebNavigation(queue)
    }

    fun publishEpisodeProgress(savedProgress: Map<String, PlaybackProgress>) {
        val queue = webQueue
        val progress = if (queue == null) emptyMap() else queue.mediaKeys.mapIndexedNotNull { index, key ->
            savedProgress[key]?.let { index to it }
        }.toMap()
        uiState.update { it.copy(episodeProgress = progress) }
    }

    private fun playWebQueue(
        queue: WebPlaybackQueue,
        position: Long? = null,
        playWhenReady: Boolean = true
    ) {
        val previous = activeWebRequest ?: return
        val episode = queue.episode ?: return
        val referer = if (previous.referer == previous.url || previous.referer == previous.pageUrl) {
            episode.url
        } else {
            previous.referer
        }
        val request = previous.copy(
            url = episode.url,
            pageUrl = episode.url,
            title = "${queue.page.title} · ${episode.name}",
            queue = queue,
            headers = emptyMap(),
            referer = referer,
            resolvePage = true,
            restoreQueue = false,
            progressMediaId = null,
            seriesUrl = queue.seriesUrl
        )
        playWebPage(request, position, playWhenReady, preserveSpeed = true)
    }

    fun selectEpisode(index: Int, fromStart: Boolean = false) {
        val queue = webQueue?.select(index) ?: return
        playWebQueue(queue, if (fromStart) 0L else null)
    }

    fun selectWebChannel(index: Int) {
        val current = webQueue ?: return
        if (current.channelIndex == index) return
        val next = current.switchChannel(index)
        if (next == null) {
            uiState.update { it.copy(notice = application.getString(R.string.player_channel_missing_episode)) }
            return
        }
        playWebQueue(next, currentWebPosition(), currentWebIntent())
    }

    fun selectWebSource(index: Int) {
        val source = uiState.value.webSources.getOrNull(index) ?: return
        if (index == uiState.value.selectedWebSource) return
        val request = activeWebRequest ?: return
        webStartPositionMs = currentWebPosition()
        webPlaybackIntent = currentWebIntent()
        webPlaybackSpeed = getNormalPlaybackSpeed()
        progressTracker.saveCurrentPosition()
        sessionCoordinator.controller?.pause()
        sessionCoordinator.controller?.stop()
        // 捕获的 Cookie/鉴权头只属于原媒体主机，其他主机重新读取会话 Cookie。
        val sameHost = Uri.parse(capturedWebMediaUrl.orEmpty()).host == Uri.parse(source.url).host
        playResolvedWeb(
            request,
            source.url,
            source.mimeType ?: WebSourceClassifier.mimeType(source.url),
            if (sameHost) request.headers else emptyMap(),
            request.userAgent
        )
    }

    fun currentWebPosition(): Long = if (uiState.value.isPreparingMedia || getCurrentMediaId() == null) {
        webStartPositionMs ?: 0L
    } else {
        sessionCoordinator.controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    fun currentWebIntent(): Boolean = if (uiState.value.isPreparingMedia || getCurrentMediaId() == null) {
        webPlaybackIntent
    } else {
        sessionCoordinator.controller?.playWhenReady == true
    }

    fun hasStartPositionForRetry(): Boolean = webStartPositionMs != null

    fun hasNextEpisode(): Boolean = webQueue?.let { it.episodeIndex + 1 < it.episodes.size } ?: false

    fun hasPreviousEpisode(): Boolean = webQueue?.let { it.episodeIndex > 0 } ?: false

    /**
     * 网页队列完成一集：只在该集属于绑定番剧、且集号可解析时推进。
     * 优先按 mediaId 在队列中定位，队列缺失或未命中时兜底当前集或请求标题，并兜底页面传入的 animeId。
     */
    companion object {
        private const val TAG = "WebPlaybackCoord"
    }

    fun resolveCompletionTarget(mediaId: String): EpisodeCompletionTarget.Web? {
        val targetAnimeId = webAnimeId ?: entryAnimeId.takeIf { it > 0 }
        if (targetAnimeId == null) {
            AppLogManager.i(TAG, "Skip web progress update: missing animeId, mediaId=$mediaId")
            return null
        }
        val queue = webQueue
        val episodeName = if (queue != null) {
            val index = queue.mediaKeys.indexOf(mediaId).takeIf { it >= 0 }
                ?: queue.episodes.indexOfFirst { it.url == activeWebRequest?.pageUrl }.takeIf { it >= 0 }
                ?: queue.episodeIndex
            queue.episodes.getOrNull(index)?.name
        } else {
            null
        } ?: activeWebRequest?.title?.substringAfterLast(" · ")
            ?: activeWebRequest?.title
        val number = episodeName?.let(WebPlaybackQueue::episodeNumberOf)
        if (number == null || number <= 0) {
            AppLogManager.i(TAG, "Skip web progress update: cannot parse episode number from '$episodeName' (mediaId=$mediaId)")
            return null
        }
        return EpisodeCompletionTarget.Web(targetAnimeId, number)
    }
}