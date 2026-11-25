package com.aiexile.animetrack.ui.player.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.media.LocalEpisodeType
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository
import com.aiexile.animetrack.domain.playback.LocalPlaybackResume
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.domain.playback.WebPlaybackResume
import com.aiexile.animetrack.domain.playback.WebSourceClassifier
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.player.local.localMediaEpisodeLabel
import com.aiexile.animetrack.ui.player.web.WebPageState
import com.aiexile.animetrack.ui.player.web.WebPageViewModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private data class EpisodeNumber(val season: Int, val episode: Int)

private data class LibraryEpisode(
    val key: String,
    val label: String,
    val number: EpisodeNumber?,
    val description: String? = null,
    val progress: PlaybackProgress? = null
)

@Composable
internal fun MediaLibrarySeriesScreen(
    seriesKey: String,
    onBack: () -> Unit,
    onPlayMedia: (Long) -> Unit,
    onPlayWeb: (Int) -> Unit,
    viewModel: MediaLibraryViewModel,
    pageViewModel: WebPageViewModel = viewModel(factory = WebPageViewModel.Factory())
) {
    val library by viewModel.series.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle(initialValue = emptyMap())
    val snackbar = rememberMediaLibrarySnackbar(viewModel)
    val series = library?.firstOrNull { it.key == seriesKey }
    val sources = remember(series) { series?.sources.orEmpty() }
    val lastPlayed = remember(playback, seriesKey) { playback?.firstOrNull { it.series.key == seriesKey } }
    var sourceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedSeason by rememberSaveable { mutableStateOf<Int?>(null) }
    var requestedEpisode by rememberSaveable { mutableStateOf<Int?>(null) }
    var removeWeb by remember { mutableStateOf<WebPlaybackHistoryRepository.Entry?>(null) }

    val source = sources.firstOrNull { it.key == sourceKey }
        ?: sources.firstOrNull { it.key == lastPlayed?.source?.key }
        ?: sources.firstOrNull()
    val requestedNumber = requestedSeason?.let { season ->
        requestedEpisode?.let { EpisodeNumber(season, it) }
    }
    val changeSource: (String, EpisodeNumber?) -> Unit = { next, number ->
        requestedSeason = number?.season
        requestedEpisode = number?.episode
        sourceKey = next
    }

    if (series == null || source == null || (sourceKey == null && playback == null)) {
        AppScaffold(title = stringResource(R.string.media_library_title), onBack = onBack) { chrome ->
            Column(Modifier.fillMaxSize().then(chrome.layout).padding(chrome.content).padding(horizontal = 16.dp)) {
                if (library == null || (source != null && playback == null)) MediaLibraryLoading()
                else MediaLibraryMessage(stringResource(R.string.media_library_series_unavailable))
            }
        }
    } else {
        LaunchedEffect(source.key) {
            sourceKey = source.key
            if (source is MediaLibrarySource.Scanned) pageViewModel.close()
        }
        key(source.key) {
            when (source) {
                is MediaLibrarySource.Scanned -> {
                    val filesFlow = remember(source.item.group) { viewModel.episodes(source.item.group) }
                    val files by filesFlow.collectAsStateWithLifecycle(initialValue = null)
                    val recent = remember(playback, seriesKey, source.key) {
                        playback?.firstOrNull { it.series.key == seriesKey && it.source.key == source.key }?.file
                    }
                    ScannedSeries(
                        series, sources, source, files, recent, progress, requestedNumber, snackbar, onBack,
                        changeSource, onPlayMedia
                    )
                }
                is MediaLibrarySource.Web -> WebSeries(
                    series, sources, source, progress, requestedNumber, snackbar, onBack, changeSource,
                    onRemove = { removeWeb = source.entry },
                    onPlay = { queue ->
                        viewModel.playWeb(
                            source.entry, onPlayWeb, queue, pageViewModel.request?.userAgent, pageViewModel.request?.referer
                        )
                    },
                    pageViewModel = pageViewModel
                )
            }
        }
    }

    removeWeb?.let { entry ->
        AlertDialog(
            onDismissRequest = { removeWeb = null },
            title = { Text(stringResource(R.string.media_library_remove_web)) },
            text = { Text(entry.seriesTitle ?: entry.title ?: entry.url) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeWeb(entry)
                    removeWeb = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { removeWeb = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun ScannedSeries(
    series: MediaLibrarySeries,
    sources: List<MediaLibrarySource>,
    source: MediaLibrarySource.Scanned,
    files: List<LocalMediaEntity>?,
    recent: LocalMediaEntity?,
    progress: Map<String, PlaybackProgress>,
    requestedNumber: EpisodeNumber?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    changeSource: (String, EpisodeNumber?) -> Unit,
    onPlay: (Long) -> Unit
) {
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    val episodes = files.orEmpty().map { file ->
        val mediaKey = LocalPlaybackResume.mediaKey(file.id)
        val number = file.episodeNumber?.takeIf {
            file.episodeType != LocalEpisodeType.EXTRA && file.seasonNumber != 99 &&
                file.decimalEpisode == null && (file.episodeEndNumber == null || file.episodeEndNumber == it)
        }?.let { EpisodeNumber(file.seasonNumber ?: 1, it) }
        LibraryEpisode(mediaKey, localMediaEpisodeLabel(file), number, file.fileName, progress[mediaKey])
    }
    val selected = episodes.selectEpisode(selection, requestedNumber, recent?.let { LocalPlaybackResume.mediaKey(it.id) })
    SeriesContent(
        series, sources, source, episodes, selected, files == null, snackbar, onBack,
        onSource = { changeSource(it, if (selection != null) selected?.number else requestedNumber ?: selected?.number) },
        onEpisode = { selection = it.key },
        onPlay = selected?.let { episode ->
            { onPlay(episode.key.substringAfter(':').toLong()) }
        },
        emptyMessage = if (files?.isEmpty() == true) stringResource(R.string.media_library_series_unavailable) else null
    )
}

@Composable
private fun WebSeries(
    series: MediaLibrarySeries,
    sources: List<MediaLibrarySource>,
    source: MediaLibrarySource.Web,
    progress: Map<String, PlaybackProgress>,
    requestedNumber: EpisodeNumber?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    changeSource: (String, EpisodeNumber?) -> Unit,
    onRemove: () -> Unit,
    onPlay: (WebPlaybackQueue?) -> Unit,
    pageViewModel: WebPageViewModel
) {
    val entry = source.entry
    val pageUrl = entry.url.takeUnless(WebSourceClassifier::isCandidate) ?: source.pageUrl
    val direct = WebSourceClassifier.isCandidate(pageUrl)
    val fallbackUrl = source.pageUrl.takeIf { it != pageUrl && !WebSourceClassifier.isCandidate(it) }
    LaunchedEffect(pageUrl, fallbackUrl, entry.userAgent, entry.referer, direct) {
        if (direct) pageViewModel.close()
        else pageViewModel.load(pageUrl, entry.userAgent, entry.referer, force = true)
    }
    val state by pageViewModel.state.collectAsStateWithLifecycle()
    val loadedUrl = pageViewModel.request?.url
    val currentState = state.takeIf { !direct && loadedUrl != null && (loadedUrl == pageUrl || loadedUrl == fallbackUrl) }
    val page = (currentState as? WebPageState.Success)?.page
    LaunchedEffect(currentState, loadedUrl, fallbackUrl) {
        if (fallbackUrl != null && loadedUrl == pageUrl &&
            (currentState is WebPageState.Failure || page?.channels?.all { it.episodes.isEmpty() } == true)
        ) {
            pageViewModel.load(fallbackUrl, entry.userAgent, entry.referer)
        }
    }
    val resume = WebPlaybackResume.target(entry, progress[WebPlaybackResume.mediaKey(entry)])
    val recent = page?.let { WebPlaybackQueue.from(it, resume.request.url) ?: WebPlaybackQueue.from(it, entry.url) }
    val channelIndices = page?.channels?.indices?.filter { page.channels[it].episodes.isNotEmpty() }.orEmpty()
    var chosenChannel by rememberSaveable { mutableIntStateOf(-1) }
    var selection by rememberSaveable { mutableStateOf<String?>(null) }
    var channelEpisode by rememberSaveable { mutableStateOf<Int?>(null) }
    val matchingChannels = if (requestedNumber?.season == 1) channelIndices.filter { index ->
        page?.channels?.get(index)?.episodes?.count { WebPlaybackQueue.episodeNumberOf(it.name) == requestedNumber.episode } == 1
    } else emptyList()
    val channel = chosenChannel.takeIf { it in channelIndices }
        ?: recent?.channelIndex?.takeIf { it in matchingChannels }
        ?: matchingChannels.firstOrNull()
        ?: recent?.channelIndex
        ?: channelIndices.firstOrNull()
    val queue = if (page != null && channel != null) WebPlaybackQueue(page, channel, 0) else null
    val episodes = queue?.episodes?.mapIndexed { index, episode ->
        val mediaKey = entry.mediaId?.takeIf { episode.url == entry.url } ?: queue.mediaKeys[index]
        val number = WebPlaybackQueue.episodeNumberOf(episode.name)?.let { EpisodeNumber(1, it) }
        LibraryEpisode(index.toString(), episode.name, number, progress = progress[mediaKey])
    }.orEmpty()
    val requested = channelEpisode?.let { EpisodeNumber(1, it) } ?: requestedNumber
    val defaultKey = if (recent?.channelIndex == channel) recent?.episodeIndex?.toString() else null
    val selected = episodes.selectEpisode(selection, requested, defaultKey)
    val selectedQueue = selected?.key?.toIntOrNull()?.let { queue?.select(it) }
    val fallback = resume.episodeLabel?.let {
        LibraryEpisode(
            key = "",
            label = it,
            number = WebPlaybackQueue.episodeNumberOf(it)?.let { number -> EpisodeNumber(1, number) },
            progress = progress[WebPlaybackResume.mediaKey(entry)]?.takeUnless { resume.isNextEpisode }
        )
    }
    SeriesContent(
        series, sources, source, episodes, selected ?: fallback,
        !direct && (currentState == null || currentState is WebPageState.Loading), snackbar, onBack,
        onSource = {
            val currentNumber = (selected ?: fallback)?.number
            changeSource(it, if (selection != null) currentNumber else requestedNumber ?: currentNumber)
        },
        onEpisode = { selection = it.key },
        onPlay = { onPlay(selectedQueue) },
        onRemove = onRemove,
        channels = {
            if (channelIndices.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channelIndices) { index ->
                        FilterChip(
                            selected = index == channel,
                            onClick = {
                                channelEpisode = selected?.number?.episode
                                chosenChannel = index
                                selection = null
                            },
                            label = {
                                Text(
                                    page?.channels?.get(index)?.name?.takeIf(String::isNotBlank)
                                        ?: stringResource(R.string.web_source_channel, index + 1)
                                )
                            }
                        )
                    }
                }
            }
        }
    )
}

private fun List<LibraryEpisode>.selectEpisode(key: String?, number: EpisodeNumber?, defaultKey: String?): LibraryEpisode? =
    firstOrNull { it.key == key }
        ?: number?.let { wanted -> singleOrNull { it.number == wanted } }
        ?: firstOrNull { it.key == defaultKey }
        ?: firstOrNull()

@Composable
private fun SeriesContent(
    series: MediaLibrarySeries,
    sources: List<MediaLibrarySource>,
    source: MediaLibrarySource,
    episodes: List<LibraryEpisode>,
    selected: LibraryEpisode?,
    loading: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSource: (String) -> Unit,
    onEpisode: (LibraryEpisode) -> Unit,
    onPlay: (() -> Unit)?,
    onRemove: (() -> Unit)? = null,
    emptyMessage: String? = null,
    channels: @Composable () -> Unit = {}
) {
    AppScaffold(
        title = stringResource(R.string.media_library_title),
        onBack = onBack,
        actions = {
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    AppIcon(AppIcon.DELETE, stringResource(R.string.media_library_remove_web))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { chrome ->
        val direction = LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            modifier = Modifier.fillMaxSize().then(chrome.layout),
            contentPadding = PaddingValues(
                start = chrome.content.calculateStartPadding(direction) + 16.dp,
                top = chrome.content.calculateTopPadding() + 12.dp,
                end = chrome.content.calculateEndPadding(direction) + 16.dp,
                bottom = chrome.content.calculateBottomPadding() + 20.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                SeriesHeader(series, selected, onPlay)
            }
            item(key = "sources", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.media_library_sources), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sources, key = MediaLibrarySource::key) { option ->
                            FilterChip(
                                selected = option.key == source.key,
                                onClick = { if (option.key != source.key) onSource(option.key) },
                                label = {
                                    Text(
                                        sourceLabel(option, sources),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                    channels()
                }
            }
            if (episodes.isNotEmpty()) {
                item(key = "episodes", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.web_source_episodes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(episodes, key = { "episode:${it.key}" }) { episode ->
                    EpisodeTile(episode, selected?.key == episode.key) { onEpisode(episode) }
                }
            } else if (loading || emptyMessage != null) {
                item(key = "status", span = { GridItemSpan(maxLineSpan) }) {
                    if (loading) MediaLibraryLoading()
                    else if (emptyMessage != null) MediaLibraryMessage(emptyMessage)
                }
            }
        }
    }
}

@Composable
private fun SeriesHeader(series: MediaLibrarySeries, selected: LibraryEpisode?, onPlay: (() -> Unit)?) {
    val shape = SquircleShape(24.dp)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MediaLibraryArtwork(
                series.coverUrl,
                Modifier.width(76.dp).aspectRatio(2f / 3f).clip(SquircleShape(10.dp))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(series.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                selected?.let {
                    Text(
                        it.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(onClick = { onPlay?.invoke() }, enabled = onPlay != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(AppIcon.PLAY_ARROW, null, modifier = Modifier.size(18.dp))
                        val resume = selected?.progress?.let { !it.completed && it.positionMs > 0 } == true
                        Text(stringResource(if (resume) R.string.web_watch_continue else R.string.player_play))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeTile(episode: LibraryEpisode, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape(12.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface
    ) {
        Column(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            Text(episode.label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.description?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun sourceLabel(source: MediaLibrarySource, sources: List<MediaLibrarySource>): String =
    when (source) {
        is MediaLibrarySource.Scanned -> {
            val type = stringResource(if (source.item.webDav) R.string.media_library_source_webdav else R.string.media_library_source_local)
            if (sources.count { it is MediaLibrarySource.Scanned && it.item.webDav == source.item.webDav } > 1) {
                "$type · ${source.item.sourceName}"
            } else type
        }
        is MediaLibrarySource.Web -> {
            val url = source.pageUrl.toHttpUrlOrNull()
            val host = url?.host
            if (host == null) {
                stringResource(R.string.media_library_source_web)
            } else if (sources.count { it is MediaLibrarySource.Web && it.pageUrl.toHttpUrlOrNull()?.host == host } > 1) {
                "$host${url.encodedPath}"
            } else host
        }
    }
