package com.aiexile.animetrack.ui.settings.duplicate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.components.EmptyCoverPlaceholder
import com.aiexile.animetrack.ui.components.ShimmerText
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.util.coverImageRequestForList
import com.aiexile.animetrack.util.formatDate
import com.aiexile.animetrack.util.resolveCoverModel

/**
 * 同标题重复卡清理确认界面：逐组列出候选卡，默认只预选「没有任何用户数据」的卡片，
 * 用户可改保留卡、可取消；确认后才删。
 */
@Composable
fun DuplicateCleanupDialog(
    onDismiss: () -> Unit,
    viewModel: DuplicateCleanupViewModel = viewModel(factory = DuplicateCleanupViewModel.factory())
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.scan()
    }

    Dialog(
        onDismissRequest = { if (!state.isCleaning) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.85f),
            shape = SquircleShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                    Text(
                        text = stringResource(R.string.duplicate_cleanup_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.duplicate_cleanup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    when {
                        state.isLoading || state.isCleaning -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                ShimmerText(
                                    text = stringResource(
                                        if (state.isCleaning) R.string.duplicate_cleanup_cleaning
                                        else R.string.duplicate_cleanup_loading
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                state.result?.let { result ->
                                    item {
                                        Text(
                                            text = stringResource(
                                                R.string.duplicate_cleanup_result,
                                                result.cleanedGroups,
                                                result.deletedCards,
                                                result.remainingGroups
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                start = 20.dp,
                                                end = 20.dp,
                                                bottom = 8.dp
                                            )
                                        )
                                        if (result.failed) {
                                            Text(
                                                text = stringResource(R.string.duplicate_cleanup_failed),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(
                                                    start = 20.dp,
                                                    end = 20.dp,
                                                    bottom = 8.dp
                                                )
                                            )
                                        }
                                    }
                                }

                                if (state.groups.isEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.duplicate_cleanup_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                                        )
                                    }
                                }

                                state.groups.forEachIndexed { index, group ->
                                    item(key = "group_${group.key}") {
                                        Column {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${index + 1}. ${group.title}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = stringResource(
                                                        R.string.duplicate_cleanup_episode_count,
                                                        group.totalEpisodes
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    items(group.items, key = { anime -> "card_${anime.id}" }) { anime ->
                                        DuplicateCardRow(
                                            anime = anime,
                                            isKeeper = anime.id == group.keeperId,
                                            markedForDelete = anime.id in group.deleteIds,
                                            // 清理结果出来后只读展示，避免「点了没反应」
                                            enabled = state.result == null,
                                            onSelectKeeper = { viewModel.setKeeper(group.key, anime.id) },
                                            onToggleDelete = { viewModel.toggleDelete(group.key, anime.id) }
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(8.dp)) }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectedCount = state.groups.sumOf { it.deleteIds.size }
                    if (state.result == null && selectedCount > 0) {
                        Text(
                            text = stringResource(R.string.duplicate_cleanup_selected, selectedCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (state.result != null) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.duplicate_cleanup_close))
                        }
                    } else {
                        TextButton(onClick = onDismiss, enabled = !state.isCleaning) {
                            Text(stringResource(R.string.duplicate_cleanup_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.confirm() },
                            enabled = selectedCount > 0 && !state.isCleaning
                        ) {
                            Text(stringResource(R.string.duplicate_cleanup_confirm))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateCardRow(
    anime: Anime,
    isKeeper: Boolean,
    markedForDelete: Boolean,
    enabled: Boolean,
    onSelectKeeper: () -> Unit,
    onToggleDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelectKeeper)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimeThumb(anime)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.duplicate_cleanup_progress,
                    anime.watchedEpisodes,
                    anime.totalEpisodes
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val updated = anime.lastProgressAt ?: anime.startDate
            if (updated != null) {
                Text(
                    text = stringResource(R.string.duplicate_cleanup_updated, formatDate(updated)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RadioButton(selected = isKeeper, onClick = onSelectKeeper, enabled = enabled)
            Text(
                text = stringResource(R.string.duplicate_cleanup_keep),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(
            checked = markedForDelete,
            onCheckedChange = { onToggleDelete() },
            enabled = enabled && !isKeeper
        )
    }
}

@Composable
private fun AnimeThumb(anime: Anime) {
    val context = LocalContext.current
    val coverModel = remember(anime.coverUrl) { resolveCoverModel(anime.coverUrl) }
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 58.dp)
            .clip(SquircleShape(8.dp))
    ) {
        if (coverModel != null) {
            val request = remember(anime.coverUrl) {
                coverImageRequestForList(context, anime.coverUrl)
            }
            AsyncImage(
                model = request,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            EmptyCoverPlaceholder(shape = SquircleShape(8.dp))
        }
    }
}
