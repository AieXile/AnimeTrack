package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.domain.media.WebPlaybackQueue
import com.aiexile.animetrack.ui.components.chrome.AppScaffold

/** 直接网页入口的原生线路、选集页；不在页面材质层中嵌入 WebView。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WebEpisodeDialog(
    state: WebPageState,
    selectedUrl: String?,
    progress: Map<String, PlaybackProgress>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAssist: () -> Unit,
    onSelect: (WebSourceEpisode) -> Unit
) {
    val page = (state as? WebPageState.Success)?.page
    var channelIndex by rememberSaveable(page?.url) {
        mutableIntStateOf(page?.channels?.indexOfFirst { channel ->
            channel.episodes.any { it.url == (selectedUrl ?: page.selectedEpisodeUrl) }
        }?.coerceAtLeast(0) ?: 0)
    }
    val channel = page?.channels?.getOrNull(channelIndex)
    val selectedQueue = remember(page, selectedUrl) {
        page?.let { WebPlaybackQueue.from(it, selectedUrl ?: it.selectedEpisodeUrl) }
    }
    val displayedQueue = remember(page, channelIndex) {
        page?.let { WebPlaybackQueue(it, channelIndex, 0) }
    }
    val selectedIndex = if (selectedQueue?.channelIndex == channelIndex) selectedQueue.episodeIndex
        else selectedQueue?.switchChannel(channelIndex)?.episodeIndex ?: -1
    val gridState = rememberLazyGridState()
    LaunchedEffect(page?.url, selectedUrl) {
        selectedQueue?.let { channelIndex = it.channelIndex }
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        AppScaffold(
            title = page?.title ?: stringResource(R.string.web_source_episodes),
            onBack = onDismiss
        ) { chrome ->
            LaunchedEffect(channelIndex, page?.url, selectedIndex) {
                if (!channel?.episodes.isNullOrEmpty()) {
                    gridState.scrollToItem(if (selectedIndex >= 0) selectedIndex + 2 else 0,
                        chrome.scrollCompensation)
                }
            }
            if (state is WebPageState.Loading) {
                Column(Modifier.fillMaxSize().then(chrome.layout).padding(chrome.content),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.web_episode_loading), Modifier.padding(16.dp))
                }
            } else if (state is WebPageState.Failure || page?.channels.isNullOrEmpty()) {
                Column(Modifier.fillMaxSize().then(chrome.layout).padding(chrome.content).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (state is WebPageState.Failure) webSourceErrorText(state.error)
                        else stringResource(R.string.web_episode_empty))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.web_playback_reparse)) }
                    OutlinedButton(onClick = onAssist) { Text(stringResource(R.string.web_episode_assist)) }
                }
            } else if (page != null && channel != null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp), state = gridState,
                    modifier = Modifier.fillMaxSize().then(chrome.layout).padding(horizontal = 16.dp),
                    contentPadding = chrome.content,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.web_episode_summary, page.channels.size,
                            channel.episodes.size), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            page.channels.forEachIndexed { index, source ->
                                FilterChip(selected = channelIndex == index,
                                    onClick = { channelIndex = index },
                                    label = { Text(source.name ?: stringResource(R.string.web_source_channel, index + 1)) })
                            }
                        }
                    }
                    itemsIndexed(channel.episodes, key = { _, episode -> episode.url }) { index, episode ->
                        val watched = progress[displayedQueue?.mediaKeys?.getOrNull(index)]
                        FilterChip(
                            selected = index == selectedIndex,
                            onClick = { onSelect(episode) },
                            label = {
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text(episode.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(when {
                                        watched?.completed == true -> stringResource(R.string.web_watch_completed)
                                        watched != null && watched.positionMs > 0 -> stringResource(
                                            R.string.web_watch_position, formatDuration(watched.positionMs))
                                        else -> stringResource(R.string.web_watch_not_started)
                                    }, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            OutlinedButton(onClick = onAssist) { Text(stringResource(R.string.web_episode_assist)) }
                        }
                    }
                }
            }
        }
    }
}
