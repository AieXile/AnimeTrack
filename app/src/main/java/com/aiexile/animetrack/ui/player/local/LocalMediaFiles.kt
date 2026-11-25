package com.aiexile.animetrack.ui.player.local

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.media.LocalEpisodeType
import com.aiexile.animetrack.data.media.LocalMediaAnimeGroup
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaMatchState
import com.aiexile.animetrack.ui.components.EmptyCoverPlaceholder
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.util.coverImageRequestForList

internal fun LazyGridScope.localMediaFiles(
    groups: LazyPagingItems<LocalMediaAnimeGroup>,
    unclassified: LazyPagingItems<LocalMediaEntity>,
    showEmptyHint: Boolean,
    hiddenCount: Int,
    onOpenHidden: () -> Unit,
    onOpenGroup: (LocalMediaAnimeGroup) -> Unit,
    onMatchGroup: (LocalMediaAnimeGroup) -> Unit,
    onConfirmGroup: (LocalMediaAnimeGroup) -> Unit,
    onClearGroup: (LocalMediaAnimeGroup) -> Unit,
    onPlayMedia: (Long) -> Unit,
    onMatchMedia: (LocalMediaEntity) -> Unit,
    onSetHidden: (LocalMediaEntity, Boolean) -> Unit
) {
    item(key = "media-header", span = { GridItemSpan(maxLineSpan) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.local_media_files),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (hiddenCount > 0) {
                TextButton(onClick = onOpenHidden) {
                    Text(stringResource(R.string.local_media_hidden_count, hiddenCount))
                }
            }
        }
    }
    items(
        count = groups.itemCount,
        key = { index -> groups.peek(index)?.let { "group-${it.displayKey}" } ?: "group-placeholder-$index" },
        contentType = { "media-group" }
    ) { index ->
        val group = groups[index]
        if (group == null) Spacer(Modifier.height(78.dp))
        else LocalMediaGroupCard(group, { onOpenGroup(group) }, { onMatchGroup(group) },
            { onConfirmGroup(group) }, { onClearGroup(group) })
    }
    if (groups.hasError) {
        item(key = "group-retry", span = { GridItemSpan(maxLineSpan) }) { MediaRetry(groups) }
    }

    if (unclassified.itemCount > 0 || unclassified.hasError) {
        item(key = "unclassified-header", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.local_media_unclassified),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
            )
        }
    }
    items(
        count = unclassified.itemCount,
        key = { index -> unclassified.mediaKey(index) },
        span = { GridItemSpan(maxLineSpan) },
        contentType = { "media-file" }
    ) { index ->
        val media = unclassified[index]
        if (media == null) {
            Spacer(Modifier.height(72.dp))
        } else {
            // LazyVerticalGrid 会把同一格的多个根节点放在同一个起点。
            Column {
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(start = 46.dp))
                LocalMediaRow(media, inGroup = false, onPlayMedia, onMatchMedia, onSetHidden)
            }
        }
    }
    if (unclassified.hasError) {
        item(key = "unclassified-retry", span = { GridItemSpan(maxLineSpan) }) { MediaRetry(unclassified) }
    }
    if (groups.isLoadingEmpty || unclassified.isLoadingEmpty) {
        item(key = "media-loading", span = { GridItemSpan(maxLineSpan) }) { MediaLoading() }
    } else if (showEmptyHint && groups.isLoadedEmpty && unclassified.isLoadedEmpty) {
        item(key = "media-empty", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(if (hiddenCount > 0) R.string.local_media_no_visible_files else R.string.local_media_files_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LocalMediaGroupCard(
    group: LocalMediaAnimeGroup,
    onClick: () -> Unit,
    onMatch: () -> Unit,
    onConfirm: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(Modifier.testTag("local-group-card-${group.displayKey}")) {
            Row(
                modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shape = SquircleShape(8.dp)
                Box(Modifier.size(36.dp, 50.dp).clip(shape)) {
                    EmptyCoverPlaceholder(shape = shape)
                    AppIcon(
                        icon = AppIcon.MOVIE,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).size(16.dp)
                    )
                    if (!group.coverUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(context, group.coverUrl) { coverImageRequestForList(context, group.coverUrl) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = group.title,
                        // 只为右上角的按钮留出标题空间，不压缩下方集数或识别状态。
                        modifier = Modifier.heightIn(min = 38.dp).padding(end = 38.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pluralStringResource(R.plurals.local_media_group_video_count, group.videoCount, group.videoCount),
                        modifier = Modifier.fillMaxWidth().testTag("local-group-count-${group.displayKey}"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val status = when {
                        group.waiting -> stringResource(R.string.local_media_waiting_metadata)
                        group.reviewKey != null -> stringResource(R.string.local_media_group_match_manually)
                        group.pendingCount > 0 -> pluralStringResource(
                            R.plurals.local_media_group_pending_count, group.pendingCount, group.pendingCount
                        )
                        else -> ""
                    }
                    // 空状态也占一行，跟随字体缩放，但不让读屏读出空白占位。
                    Text(
                        text = status,
                        modifier = if (status.isEmpty()) Modifier.clearAndSetSemantics {} else Modifier,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            MediaActionsMenu(
                tag = "local-group-menu-${group.displayKey}",
                compact = true,
                modifier = Modifier.align(Alignment.TopEnd)
            ) { close ->
                if (group.pendingCount > 0) {
                    MediaActionItem(R.string.local_media_confirm_group, AppIcon.CHECK) { close(); onConfirm() }
                }
                MediaActionItem(if (group.hasMatch) R.string.local_media_change_group else R.string.local_media_match_group,
                    if (group.hasMatch) AppIcon.EDIT else AppIcon.LINK) { close(); onMatch() }
                MediaActionItem(R.string.local_media_clear_group, AppIcon.REMOVE) { close(); onClear() }
            }
        }
    }
}

@Composable
internal fun LocalMediaGroupSheet(
    group: LocalMediaAnimeGroup,
    files: LazyPagingItems<LocalMediaEntity>,
    onDismiss: () -> Unit,
    onPlayMedia: (Long) -> Unit,
    onMatchMedia: ((LocalMediaEntity) -> Unit)? = null,
    onConfirmMatch: (LocalMediaEntity) -> Unit = {},
    onMatchGroup: () -> Unit = {},
    onSetHidden: (LocalMediaEntity, Boolean) -> Unit = { _, _ -> },
    actionError: LocalMediaActionError? = null
) {
    val empty = files.isLoadedEmpty
    LaunchedEffect(empty) { if (empty) onDismiss() }
    if (empty) return

    LocalMediaFilesSheet(group.title, files, actionError, onDismiss, actions = {
        if (onMatchMedia != null && (group.reviewKey != null || group.hasMatch)) {
            TextButton(onClick = onMatchGroup, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(if (group.reviewKey != null) R.string.local_media_match_group else R.string.local_media_change_group))
            }
        }
    }) { media ->
        LocalMediaRow(media, inGroup = true, onPlayMedia, onMatchMedia, onSetHidden, onConfirmMatch,
            allowMatch = !group.waiting)
        HorizontalDivider(modifier = Modifier.padding(start = 46.dp))
    }
}

@Composable
internal fun LocalMediaHiddenSheet(
    files: LazyPagingItems<LocalMediaEntity>,
    onDismiss: () -> Unit,
    onRestore: (LocalMediaEntity) -> Unit,
    actionError: LocalMediaActionError?
) {
    val empty = files.isLoadedEmpty
    LaunchedEffect(empty) { if (empty) onDismiss() }
    if (empty) return

    LocalMediaFilesSheet(stringResource(R.string.local_media_hidden_title), files, actionError, onDismiss) { media ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(media.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (media.relativePath != media.fileName) {
                    Text(
                        media.relativePath.substringBeforeLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = { onRestore(media) }) { Text(stringResource(R.string.local_media_restore)) }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalMediaFilesSheet(
    title: String,
    files: LazyPagingItems<LocalMediaEntity>,
    actionError: LocalMediaActionError?,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit = {},
    row: @Composable (LocalMediaEntity) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        actions()
        val error = when (actionError) {
            LocalMediaActionError.VISIBILITY -> R.string.local_media_visibility_failed
            LocalMediaActionError.MATCH -> R.string.local_media_match_failed
            else -> null
        }
        if (error != null) {
            Text(
                text = stringResource(error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)
        ) {
            mediaRows(files, row)
            if (files.hasError) item(key = "files-retry") { MediaRetry(files) }
            if (files.isLoadingEmpty) item(key = "files-loading") { MediaLoading() }
        }
    }
}

private fun LazyListScope.mediaRows(files: LazyPagingItems<LocalMediaEntity>, row: @Composable (LocalMediaEntity) -> Unit) {
    items(count = files.itemCount, key = files::mediaKey, contentType = { "media-file" }) { index ->
        val media = files[index]
        if (media == null) Spacer(Modifier.height(72.dp)) else row(media)
    }
}

private fun LazyPagingItems<LocalMediaEntity>.mediaKey(index: Int): String =
    peek(index)?.let { "media-${it.id}" } ?: "media-placeholder-$index"

@Composable
private fun LocalMediaRow(
    media: LocalMediaEntity,
    inGroup: Boolean,
    onPlayMedia: (Long) -> Unit,
    onMatchMedia: ((LocalMediaEntity) -> Unit)?,
    onSetHidden: (LocalMediaEntity, Boolean) -> Unit,
    onConfirmMatch: (LocalMediaEntity) -> Unit = {},
    allowMatch: Boolean = true
) {
    val pending = inGroup && media.archiveKey == null && media.matchState == LocalMediaMatchState.AUTO_MATCHED && media.matchedAnimeId != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).clip(SquircleShape(12.dp)).clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.player_play),
                onClick = { onPlayMedia(media.id) }
            ).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = SquircleShape(11.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    AppIcon(
                        AppIcon.PLAY_ARROW, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val episode = localMediaEpisodeLabel(media)
                Text(
                    text = if (inGroup) episode else media.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = if (inGroup) media.relativePath else listOfNotNull(media.parsedTitle, episode)
                    .distinct().joinToString(" · ")
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (pending) {
                    Surface(shape = SquircleShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text = stringResource(R.string.local_media_match_pending),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        if (onMatchMedia != null) MediaActionsMenu(tag = "local-file-menu-${media.id}") { close ->
            if (pending) {
                MediaActionItem(R.string.local_media_match_confirm, AppIcon.CHECK) { close(); onConfirmMatch(media) }
            }
            if (allowMatch) {
                MediaActionItem(
                    if (inGroup) R.string.local_media_match_change else R.string.local_media_match_action,
                    if (inGroup) AppIcon.EDIT else AppIcon.LINK
                ) { close(); onMatchMedia(media) }
            }
            MediaActionItem(R.string.local_media_hide, AppIcon.REMOVE) { close(); onSetHidden(media, true) }
        }
    }
}

@Composable
private fun MediaActionsMenu(
    tag: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable (close: () -> Unit) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val button = Modifier.testTag(tag).then(if (compact) Modifier.size(40.dp) else Modifier)
        IconButton(onClick = { open = true }, modifier = button) {
            AppIcon(AppIcon.MORE_VERT, contentDescription = stringResource(R.string.common_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = SquircleShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) { content { open = false } }
    }
}

@Composable
private fun MediaActionItem(@StringRes label: Int, icon: AppIcon, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        leadingIcon = {
            AppIcon(
                icon, contentDescription = null,
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.padding(horizontal = 4.dp).clip(SquircleShape(10.dp)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        onClick = onClick
    )
}

@Composable
internal fun localMediaEpisodeLabel(media: LocalMediaEntity): String {
    media.decimalEpisode?.let { return stringResource(R.string.local_media_episode_decimal, it) }
    if (media.episodeType == LocalEpisodeType.EXTRA || media.seasonNumber == 99) {
        return stringResource(R.string.local_media_episode_extra)
    }
    val first = media.episodeNumber
    val last = media.episodeEndNumber
    val episode = when {
        first == null -> stringResource(R.string.local_media_episode_unknown)
        last != null && last != first -> stringResource(R.string.local_media_episode_range, first, last)
        else -> stringResource(R.string.local_media_episode, first)
    }
    val season = media.seasonNumber ?: return episode
    return stringResource(R.string.home_season_format, season) + " · " + episode
}

private val LazyPagingItems<*>.hasError: Boolean
    get() = loadState.refresh is LoadState.Error || loadState.append is LoadState.Error || loadState.prepend is LoadState.Error

private val LazyPagingItems<*>.isLoadingEmpty: Boolean
    get() = itemCount == 0 && loadState.refresh is LoadState.Loading

private val LazyPagingItems<*>.isLoadedEmpty: Boolean
    get() = itemCount == 0 && loadState.refresh is LoadState.NotLoading && !hasError

@Composable
private fun MediaRetry(pages: LazyPagingItems<*>) {
    TextButton(onClick = pages::retry) { Text(stringResource(R.string.local_media_list_retry)) }
}

@Composable
private fun MediaLoading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}
