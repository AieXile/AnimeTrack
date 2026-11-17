package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.WebSourceEntry
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 规则数据源的全屏搜索弹层：搜索结果 → 剧集列表。
 *
 * 选中剧集后把播放页地址交回网页播放，由现有 WebView 嗅探解析最终视频地址。
 */
@Composable
fun WebSourceSearchDialog(
    ruleName: String,
    searchState: WebSourceSearchState,
    episodeState: WebSourceEpisodeState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onBackToResults: () -> Unit,
    onSelectEntry: (WebSourceEntry) -> Unit,
    onSelectEpisode: (WebSourceEpisode) -> Unit
) {
    val showingEpisodes = episodeState !is WebSourceEpisodeState.Idle
    val activeEntry = when (episodeState) {
        is WebSourceEpisodeState.Success -> episodeState.entry
        is WebSourceEpisodeState.Failure -> episodeState.entry
        else -> null
    }
    val canRetry = searchState is WebSourceSearchState.Failure ||
        episodeState is WebSourceEpisodeState.Failure

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (showingEpisodes) onBackToResults() else onDismiss() }
                    ) {
                        Icon(
                            painter = rememberAppIconPainter(
                                if (showingEpisodes) AppIcon.ARROW_BACK else AppIcon.CLOSE
                            ),
                            contentDescription = stringResource(
                                if (showingEpisodes) R.string.common_back else R.string.common_close
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeEntry?.name ?: ruleName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showingEpisodes) {
                            Text(
                                text = stringResource(R.string.web_source_episodes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onRetry, enabled = canRetry) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.SYNC),
                            contentDescription = stringResource(R.string.common_retry)
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        episodeState is WebSourceEpisodeState.Loading -> LoadingPane()
                        episodeState is WebSourceEpisodeState.Failure -> ErrorPane(
                            error = episodeState.error,
                            onRetry = onRetry
                        )
                        episodeState is WebSourceEpisodeState.Success -> EpisodeList(
                            episodes = episodeState.episodes,
                            onSelect = onSelectEpisode
                        )
                        searchState is WebSourceSearchState.Loading -> LoadingPane()
                        searchState is WebSourceSearchState.Failure -> ErrorPane(
                            error = searchState.error,
                            onRetry = onRetry
                        )
                        searchState is WebSourceSearchState.Success -> EntryList(
                            entries = searchState.entries,
                            onSelect = onSelectEntry
                        )
                        else -> Text(
                            text = stringResource(R.string.web_source_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingPane() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.web_source_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorPane(error: WebSourceError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = webSourceErrorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.common_retry))
        }
    }
}

@Composable
fun webSourceErrorText(error: WebSourceError): String = when (error.kind) {
    WebSourceErrorKind.NETWORK -> stringResource(R.string.web_source_error_network)
    WebSourceErrorKind.HTTP -> error.statusCode?.let {
        stringResource(R.string.web_source_error_http, it)
    } ?: stringResource(R.string.web_source_error_network)
    WebSourceErrorKind.PARSE -> stringResource(R.string.web_source_error_parse)
    WebSourceErrorKind.EMPTY -> stringResource(R.string.web_source_no_results)
    WebSourceErrorKind.INVALID_RULE -> stringResource(R.string.web_source_error_invalid_rule)
    WebSourceErrorKind.IMPORT -> stringResource(R.string.web_source_import_failed)
}

@Composable
private fun EntryList(entries: List<WebSourceEntry>, onSelect: (WebSourceEntry) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(entries, key = { it.url }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun EpisodeList(episodes: List<WebSourceEpisode>, onSelect: (WebSourceEpisode) -> Unit) {
    val grouped = episodes.groupBy { it.channelIndex }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        grouped.forEach { (channelIndex, list) ->
            if (channelIndex != null) {
                item(key = "channel-$channelIndex") {
                    Text(
                        text = stringResource(R.string.web_source_channel, channelIndex + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
            items(list, key = { it.url }) { episode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(episode) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = episode.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
