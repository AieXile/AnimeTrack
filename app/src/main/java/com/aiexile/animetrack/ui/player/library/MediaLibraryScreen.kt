package com.aiexile.animetrack.ui.player.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.EmptyCoverPlaceholder
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.rememberAdaptiveGridColumns
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.player.local.localMediaEpisodeLabel
import com.aiexile.animetrack.util.coverImageRequestForList

private enum class MediaLibraryFilter(@StringRes val label: Int) {
    ALL(R.string.common_all),
    LOCAL(R.string.media_library_source_local),
    WEBDAV(R.string.media_library_source_webdav),
    WEB(R.string.media_library_source_web);

    fun includes(series: MediaLibrarySeries): Boolean = when (this) {
        ALL -> true
        LOCAL -> series.scanned.any { !it.webDav }
        WEBDAV -> series.scanned.any { it.webDav }
        WEB -> series.web.isNotEmpty()
    }
}

@Composable
internal fun MediaLibraryScreen(
    onBack: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onPlayMedia: (Long) -> Unit,
    onPlayWeb: (Int) -> Unit,
    viewModel: MediaLibraryViewModel = viewModel(factory = MediaLibraryViewModel.Factory())
) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val snackbar = rememberMediaLibrarySnackbar(viewModel)
    var filter by rememberSaveable { mutableStateOf(MediaLibraryFilter.ALL) }
    val loaded = series
    val filtered = remember(loaded, filter) { loaded?.filter(filter::includes).orEmpty() }
    val resume = remember(playback, loaded) {
        val keys = loaded?.mapTo(HashSet(), MediaLibrarySeries::key)
        playback?.firstOrNull { it.canResume && keys?.contains(it.series.key) == true }
    }

    AppScaffold(
        title = stringResource(R.string.media_library_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { chrome ->
        val direction = LocalLayoutDirection.current
        val startPadding = chrome.content.calculateStartPadding(direction) + 16.dp
        val endPadding = chrome.content.calculateEndPadding(direction) + 16.dp
        val spacing = 12.dp
        BoxWithConstraints(Modifier.fillMaxSize().then(chrome.layout)) {
            val columns = rememberAdaptiveGridColumns(
                availableWidth = maxWidth,
                cardMinWidth = 104.dp,
                spacing = spacing,
                horizontalPadding = startPadding + endPadding,
                minColumns = 3,
                maxColumnsPortrait = 6,
                maxColumnsLandscape = 8
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = startPadding,
                    top = chrome.content.calculateTopPadding() + 12.dp,
                    end = endPadding,
                    bottom = chrome.content.calculateBottomPadding() + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(spacing),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                // 续播稍后加载时保持页首的滚动锚点，避免筛选栏被留在视口顶部。
                item(key = "library-header", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                        if (resume != null) {
                            MediaLibraryResumeCard(resume) {
                                when (val source = resume.source) {
                                    is MediaLibrarySource.Scanned -> resume.file?.let { onPlayMedia(it.id) }
                                    is MediaLibrarySource.Web -> viewModel.playWeb(source.entry, onPlayWeb)
                                }
                            }
                        }
                        if (!loaded.isNullOrEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(MediaLibraryFilter.entries) { option ->
                                    FilterChip(
                                        selected = filter == option,
                                        onClick = { filter = option },
                                        label = { Text(stringResource(option.label)) }
                                    )
                                }
                            }
                        }
                        when {
                            loaded == null -> MediaLibraryLoading()
                            loaded.isEmpty() -> MediaLibraryMessage(stringResource(R.string.media_library_empty))
                            filtered.isEmpty() -> MediaLibraryMessage(stringResource(R.string.media_library_filter_empty))
                        }
                    }
                }
                items(filtered, key = { "series:${it.key}" }) { item ->
                    MediaLibraryPoster(item) { onOpenSeries(item.key) }
                }
            }
        }
    }
}

@Composable
private fun MediaLibraryPoster(series: MediaLibrarySeries, onClick: () -> Unit) {
    val shape = SquircleShape(12.dp)
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surface) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MediaLibraryArtwork(series.coverUrl, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape))
            Text(
                series.title,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MediaLibraryResumeCard(item: MediaLibraryPlayback, onPlay: () -> Unit) {
    val shape = SquircleShape(24.dp)
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    val subtitle = when (val source = item.source) {
        is MediaLibrarySource.Scanned -> listOfNotNull(
            item.file?.let { localMediaEpisodeLabel(it) },
            stringResource(if (source.item.webDav) R.string.media_library_source_webdav else R.string.media_library_source_local)
        )
        is MediaLibrarySource.Web -> listOfNotNull(source.entry.episodeName, stringResource(R.string.media_library_source_web))
    }.joinToString(" · ")

    Box(Modifier.fillMaxWidth().clip(shape).background(background)) {
        if (!item.series.coverUrl.isNullOrBlank()) {
            MediaLibraryArtwork(item.series.coverUrl, Modifier.matchParentSize(), showPlaceholder = false)
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to background, 0.48f to background, 1f to background.copy(alpha = 0.14f)
                    )
                )
            )
        }
        Column(
            Modifier.widthIn(max = 340.dp).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                item.series.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val progress = item.progress
            if (progress != null && progress.durationMs > 0) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp))
            }
            Button(onClick = onPlay) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(AppIcon.PLAY_ARROW, null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.web_watch_continue))
                }
            }
        }
    }
}

@Composable
internal fun MediaLibraryArtwork(url: String?, modifier: Modifier, showPlaceholder: Boolean = true) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (showPlaceholder) {
            EmptyCoverPlaceholder(shape = SquircleShape(12.dp))
            AppIcon(
                AppIcon.MOVIE,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!url.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(context, url) { coverImageRequestForList(context, url) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun MediaLibraryLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun MediaLibraryMessage(message: String) {
    Text(
        message,
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun rememberMediaLibrarySnackbar(viewModel: MediaLibraryViewModel): SnackbarHostState {
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = stringResource(R.string.media_library_error)
    LaunchedEffect(error, message) {
        if (error) {
            snackbar.showSnackbar(message)
            viewModel.clearError()
        }
    }
    return snackbar
}
