package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import kotlinx.coroutines.launch

/** 播放窗口下方的媒体信息、常用选项及选集；仅消费已有播放状态。 */
@Composable
internal fun PlayerDetailsContent(
    state: PlayerUiState,
    gridState: LazyGridState,
    onSelectEpisode: (Int) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val title = state.seriesTitle?.takeIf { it.isNotBlank() }
        ?: state.mediaTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_playback)
    val episode = state.episodes.firstOrNull { it.index == state.currentEpisodeIndex }
    val subtitle = episode?.label ?: state.mediaTitle?.takeIf { it != title }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "info", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.sourceLabel?.let {
                    Text(stringResource(R.string.player_source_label, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "options", span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onOpenQuality, enabled = state.isControllerReady,
                    label = { Text(stringResource(R.string.player_quality)) })
                AssistChip(onClick = onOpenSpeed,
                    label = { Text(stringResource(R.string.player_speed_value, state.playbackSpeed)) })
                AssistChip(onClick = onOpenSubtitles, enabled = state.isControllerReady,
                    label = { Text(stringResource(R.string.player_subtitles)) })
                AssistChip(onClick = onOpenSettings,
                    label = { Text(stringResource(R.string.player_menu_more)) })
            }
        }
        if (state.channels.isNotEmpty()) {
            item(key = "channels", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.player_channels), style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.channels.forEachIndexed { index, name ->
                            FilterChip(
                                selected = index == state.currentChannelIndex,
                                onClick = { onSelectChannel(index) },
                                label = { Text(name ?: stringResource(R.string.web_source_channel, index + 1)) }
                            )
                        }
                    }
                }
            }
        }
        item(key = "episodes", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_episodes),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (episode != null) {
                    TextButton(onClick = {
                        scope.launch {
                            val index = state.episodes.indexOfFirst { it.index == state.currentEpisodeIndex }
                            val headerCount = if (state.channels.isEmpty()) 3 else 4
                            if (index >= 0) gridState.animateScrollToItem(headerCount + index)
                        }
                    }) { Text(stringResource(R.string.player_current_episode)) }
                }
            }
        }
        if (state.episodes.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(if (state.isPreparingMedia) R.string.player_preparing
                    else R.string.player_no_episode_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(state.episodes, key = { "episode:${it.index}" }) { item ->
                val progress = state.episodeProgress[item.index]
                val status = when {
                    item.index == state.currentEpisodeIndex -> stringResource(R.string.player_current_episode)
                    progress?.completed == true -> stringResource(R.string.web_watch_completed)
                    (progress?.positionMs ?: 0L) > 0 -> stringResource(
                        R.string.web_watch_position, formatDuration(progress!!.positionMs))
                    progress != null -> stringResource(R.string.web_watch_not_started)
                    else -> null
                }
                FilterChip(
                    selected = item.index == state.currentEpisodeIndex,
                    onClick = { onSelectEpisode(item.index) },
                    label = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (status != null) {
                                Text(status, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else {
                                // 为未开始播放的条目保留状态行高度，保证所有卡片一致。
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
            }
        }
    }
}
