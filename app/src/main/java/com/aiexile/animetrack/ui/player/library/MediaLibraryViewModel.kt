package com.aiexile.animetrack.ui.player.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaMatchState
import com.aiexile.animetrack.data.media.LocalMediaScrapeState
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.media.LocalMediaAnimeGroup
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.media.LocalMediaScanScheduler
import com.aiexile.animetrack.data.media.LocalMediaScanState
import com.aiexile.animetrack.data.media.ScannedLibraryGroup
import com.aiexile.animetrack.data.media.WebDavLocalMediaSource
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.web.PendingWebPlayback
import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.playback.LocalMediaMatcher
import com.aiexile.animetrack.domain.playback.LocalPlaybackResume
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.domain.playback.WebPlaybackResume
import com.aiexile.animetrack.domain.playback.WebSourceClassifier
import com.aiexile.animetrack.domain.playback.tmdbKey
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.util.normalizeTitleForMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class MediaLibrarySeries(
    val key: String,
    val title: String,
    val coverUrl: String?,
    val scanned: List<ScannedLibraryGroup>,
    val web: List<WebPlaybackHistoryRepository.Entry>
) {
    val sources: List<MediaLibrarySource>
        get() = scanned.map(MediaLibrarySource::Scanned) + web.map(MediaLibrarySource::Web)
}

internal sealed interface MediaLibrarySource {
    val key: String

    data class Scanned(val item: ScannedLibraryGroup) : MediaLibrarySource {
        override val key = "scan:${item.group.sourceId}:${item.group.displayKey}"
    }

    data class Web(val entry: WebPlaybackHistoryRepository.Entry) : MediaLibrarySource {
        override val key = "web:${entry.libraryKey}"
        val pageUrl get() = entry.seriesUrl ?: entry.url
    }
}

internal data class MediaLibraryPlayback(
    val series: MediaLibrarySeries,
    val source: MediaLibrarySource,
    val file: LocalMediaEntity?,
    val progress: PlaybackProgress?,
    val playedAt: Long
) {
    val canResume get() = progress?.let { !it.completed && it.positionMs > 0 } == true
}

internal class MediaLibraryViewModel(
    private val media: LocalMediaRepository,
    private val web: WebPlaybackHistoryRepository,
    private val player: PlayerRepository,
    anime: AnimeRepository,
    settings: SettingsRepository,
    scheduler: LocalMediaScanScheduler
) : ViewModel() {
    private val webDavConfig = settings.playerWebdavDirectoryConfig.distinctUntilChanged()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val series = combine(
        webDavConfig.map { it?.let(WebDavLocalMediaSource::uri) }.distinctUntilChanged()
            .flatMapLatest(media::observeLibraryGroups),
        web.libraryEntries,
        anime.getAllAnimes(),
        ::groupMediaLibrary
    ).flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val progress = player.progress
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val playedFiles = progress.map { records ->
        records.keys.mapNotNull { key ->
            key.takeIf { it.startsWith("local:") }?.substringAfter(':')?.toLongOrNull()
        }.toSet()
    }.distinctUntilChanged().flatMapLatest(media::observeVisibleMedia)

    val playback = combine(series, playedFiles, progress) { entries, files, records ->
        entries?.let { libraryPlayback(it, files, records) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            webDavConfig.collect {
                updateLibrary {
                    val source = media.syncPlayerWebDavSource()
                    if (source?.scanState == LocalMediaScanState.IDLE && source.lastScanAt == null) {
                        scheduler.enqueue(source.id)
                    }
                }
            }
        }
    }

    fun episodes(group: LocalMediaAnimeGroup) = media.observeCardMedia(group)

    fun playWeb(
        entry: WebPlaybackHistoryRepository.Entry,
        openPlayer: (Int) -> Unit,
        queue: WebPlaybackQueue? = null,
        userAgent: String? = entry.userAgent,
        referer: String? = entry.referer
    ) {
        viewModelScope.launch {
            updateLibrary {
                val progress = if (queue == null) player.progress.first()[WebPlaybackResume.mediaKey(entry)] else null
                val original = WebPlaybackResume.target(entry, progress).request
                val episode = queue?.episode
                val request = if (queue != null && episode != null) {
                    original.copy(
                        url = episode.url,
                        mimeType = WebSourceClassifier.mimeType(episode.url),
                        title = "${queue.page.title} · ${episode.name}",
                        pageUrl = episode.url.takeUnless(WebSourceClassifier::isCandidate),
                        userAgent = userAgent,
                        referer = if (referer == entry.url) episode.url else referer,
                        queue = queue,
                        restoreQueue = false,
                        progressMediaId = if (episode.url == entry.url) entry.mediaId else null,
                        seriesUrl = queue.seriesUrl
                    )
                } else original
                PendingWebPlayback.set(request)
                openPlayer(entry.animeId ?: 0)
            }
        }
    }

    fun removeWeb(entry: WebPlaybackHistoryRepository.Entry) {
        viewModelScope.launch { updateLibrary { web.removeFromLibrary(entry.libraryKey) } }
    }

    fun clearError() { _error.value = false }

    private suspend fun updateLibrary(action: suspend () -> Unit) {
        try {
            action()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _error.value = true
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MediaLibraryViewModel(
            AppContainer.getLocalMediaRepository(),
            AppContainer.getWebPlaybackHistoryRepository(),
            AppContainer.getPlayerRepository(),
            AppContainer.getAnimeRepository(),
            AppContainer.getSettingsRepository(),
            LocalMediaScanScheduler(WorkManager.getInstance(AppContainer.getApplication()))
        ) as T
    }
}

internal fun libraryPlayback(
    series: List<MediaLibrarySeries>,
    files: List<LocalMediaEntity>,
    progress: Map<String, PlaybackProgress>
): List<MediaLibraryPlayback> {
    val scanned = buildMap {
        series.forEach { item ->
            item.scanned.forEach { source ->
                put(source.group.sourceId to source.group.displayKey, item to MediaLibrarySource.Scanned(source))
            }
        }
    }
    return buildList {
        files.forEach { file ->
            if (file.isMissing || file.isHidden) return@forEach
            val record = progress[LocalPlaybackResume.mediaKey(file.id)] ?: return@forEach
            val key = when {
                file.archiveKey != null -> "archive:${file.archiveKey}"
                file.matchedAnimeId != null && (file.matchState == LocalMediaMatchState.AUTO_MATCHED ||
                    file.matchState == LocalMediaMatchState.CONFIRMED) &&
                    (file.sourceId to "anime:${file.matchedAnimeId}") in scanned -> "anime:${file.matchedAnimeId}"
                file.groupKey != null && (file.scrapeState == LocalMediaScrapeState.WAITING ||
                    file.scrapeState == LocalMediaScrapeState.REVIEW) -> "review:${file.groupKey}"
                else -> return@forEach
            }
            val (item, source) = scanned[file.sourceId to key] ?: return@forEach
            add(MediaLibraryPlayback(item, source, file, record, record.updatedAt))
        }
        series.forEach { item ->
            item.web.forEach { entry ->
                val record = progress[WebPlaybackResume.mediaKey(entry)]
                val playedAt = maxOf(record?.updatedAt ?: 0L, entry.lastPlayedAt)
                if (playedAt > 0) add(MediaLibraryPlayback(item, MediaLibrarySource.Web(entry), null, record, playedAt))
            }
        }
    }.sortedByDescending(MediaLibraryPlayback::playedAt)
}

internal fun groupMediaLibrary(
    scanned: List<ScannedLibraryGroup>,
    web: List<WebPlaybackHistoryRepository.Entry>,
    animes: List<Anime>
): List<MediaLibrarySeries> {
    val tracked = animes.associateBy(Anime::id)
    val trackedByBangumi = animes.mapNotNull { item -> item.bangumiId?.let { it to item } }.toMap()
    val trackedByTmdb = animes.mapNotNull { item ->
        item.tmdbId?.let { tmdbKey(it, LocalMediaMatcher.movieTitle(item.title)) to item }
    }.toMap()
    val bangumiByTmdb = scanned.filter { it.tmdbId != null && it.bangumiId != null }
        .groupBy { tmdbKey(checkNotNull(it.tmdbId), it.group.movie) }
        .mapValues { (_, groups) -> groups.mapNotNull { it.bangumiId }.distinct().singleOrNull() }

    fun scannedKey(item: ScannedLibraryGroup): String {
        val group = item.group
        val explicitId = group.animeId.takeIf { it > 0 } ?: group.archiveKey
            ?.takeIf { it.startsWith("anime:") }?.substringAfter(':')?.toIntOrNull()
        val tmdb = item.tmdbId?.let { tmdbKey(it, group.movie) }
        val bangumi = item.bangumiId ?: tmdb?.let(bangumiByTmdb::get)
        val anime = explicitId?.let(tracked::get) ?: bangumi?.let(trackedByBangumi::get)
            ?: tmdb?.let(trackedByTmdb::get)?.takeIf { bangumi == null || it.bangumiId == null || it.bangumiId == bangumi }
        return anime?.let { "anime:${it.id}" } ?: bangumi?.let { "bgm:$it" }
            ?: group.archiveKey ?: "${group.sourceId}:${group.displayKey}"
    }

    val scannedByKey = scanned.groupBy(::scannedKey)
    val keysByTitle = buildList {
        animes.forEach { add(it.title to "anime:${it.id}") }
        scannedByKey.forEach { (key, groups) -> groups.forEach { add(it.group.title to key) } }
    }.groupBy({ normalizeTitleForMatch(it.first) }, { it.second })

    val webByKey = web.groupBy { entry ->
        val title = entry.animeTitle ?: entry.seriesTitle ?: entry.title
        entry.animeId?.let { "anime:$it" }
            ?: title?.let(::normalizeTitleForMatch)?.takeIf { it.isNotEmpty() }
                ?.let { keysByTitle[it]?.distinct()?.singleOrNull() }
            ?: "web:${entry.libraryKey}"
    }

    return (scannedByKey.keys + webByKey.keys).map { key ->
        val files = scannedByKey[key].orEmpty()
        val pages = webByKey[key].orEmpty()
        val anime = key.takeIf { it.startsWith("anime:") }?.substringAfter(':')?.toIntOrNull()?.let(tracked::get)
        val page = pages.firstOrNull()
        val title = anime?.title ?: files.firstOrNull()?.group?.title
            ?: page?.animeTitle ?: page?.seriesTitle ?: page?.title ?: checkNotNull(page).url
        MediaLibrarySeries(key, title, anime?.coverUrl ?: files.firstNotNullOfOrNull { it.group.coverUrl }, files, pages)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item: MediaLibrarySeries -> item.title }.thenBy { it.key })
}
