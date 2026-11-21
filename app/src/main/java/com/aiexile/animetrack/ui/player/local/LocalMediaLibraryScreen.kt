package com.aiexile.animetrack.ui.player.local

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.media.LocalMediaAnimeGroup
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaPermissionState
import com.aiexile.animetrack.data.media.LocalMediaSourceEntity
import com.aiexile.animetrack.data.media.LocalScanFailure
import com.aiexile.animetrack.data.media.LocalScanPhase
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMediaLibraryScreen(
    onBack: () -> Unit,
    onPlayMedia: (mediaId: Long) -> Unit,
    viewModel: LocalMediaViewModel = viewModel(factory = LocalMediaViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceId = state.selectedSourceId
    val defaultFolderName = stringResource(R.string.local_media_default_folder)
    var showAddHint by remember { mutableStateOf(false) }
    var matchTarget by remember(sourceId) { mutableStateOf<LocalMediaMatchTarget?>(null) }
    var clearTarget by remember(sourceId) { mutableStateOf<LocalMediaAnimeGroup?>(null) }

    clearTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text(stringResource(R.string.local_media_clear_group)) },
            text = { Text(stringResource(R.string.local_media_clear_group_hint, group.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCardMatch(group)
                    clearTarget = null
                }) { Text(stringResource(R.string.local_media_clear_group)) }
            },
            dismissButton = {
                TextButton(onClick = { clearTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    val onMatchGroup: (LocalMediaAnimeGroup) -> Unit = { group ->
        matchTarget = LocalMediaMatchTarget(group.sourceId, group.title, group.title, movie = group.movie, card = group)
    }

    matchTarget?.let { target ->
        val close = {
            viewModel.searchMatches("", false)
            matchTarget = null
        }
        LocalMediaMatchDialog(
            target = target,
            options = state.animeOptions,
            search = state.matchSearch,
            onSearch = viewModel::searchMatches,
            onLocal = { viewModel.pickLocalMatch(target, it); close() },
            onRemote = { viewModel.pickRemoteMatch(target, it); close() },
            onClear = { viewModel.clearMatch(target); close() },
            onDismiss = close
        )
    }

    var reauthorizeSourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val originalSource = reauthorizeSourceId
        reauthorizeSourceId = null
        uri ?: return@rememberLauncherForActivityResult
        if (originalSource != null) {
            viewModel.reauthorize(originalSource, uri)
            return@rememberLauncherForActivityResult
        }
        val displayName = uri.lastPathSegment
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: defaultFolderName
        viewModel.addFolder(uri, displayName)
    }

    if (showAddHint) {
        AlertDialog(
            onDismissRequest = { showAddHint = false },
            title = { Text(stringResource(R.string.local_media_add_title)) },
            text = { Text(stringResource(R.string.local_media_add_hint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddHint = false
                        folderLauncher.launch(null)
                    }
                ) {
                    Text(stringResource(R.string.local_media_choose_folder))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHint = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    var openGroup by remember(sourceId) { mutableStateOf<LocalMediaAnimeGroup?>(null) }
    var showHidden by remember(sourceId) { mutableStateOf(false) }
    val onSetHidden: (LocalMediaEntity, Boolean) -> Unit = { media, hidden ->
        viewModel.setHidden(media.sourceId, media.id, hidden)
    }
    val onConfirmMatch: (LocalMediaEntity) -> Unit = { media ->
        media.matchedAnimeId?.let { viewModel.confirmMatch(media.id, it) }
    }
    val onMatchMedia: (LocalMediaEntity) -> Unit = { media ->
        matchTarget = LocalMediaMatchTarget(media.sourceId, media.fileName, media.parsedTitle.orEmpty(),
            mediaId = media.id, movie = media.movie)
    }
    key(sourceId) {
        val groups = sourceId?.let { id -> remember(id) { viewModel.animeGroups(id) }.collectAsLazyPagingItems() }
        val unclassified = sourceId?.let { id -> remember(id) { viewModel.unclassifiedMedia(id) }.collectAsLazyPagingItems() }
        LocalMediaLibraryContent(
            state = state,
            groups = groups,
            unclassified = unclassified,
            onBack = onBack,
            onPlayMedia = onPlayMedia,
            onAddFolder = { showAddHint = true },
            onSelectSource = viewModel::selectSource,
            onDeleteSource = viewModel::deleteSource,
            onScanSelectedSource = viewModel::scanSelectedSource,
            onReidentify = viewModel::reidentifySelectedSource,
            onCancelScan = viewModel::cancelSelectedScan,
            onReauthorize = { source ->
                reauthorizeSourceId = source.id
                folderLauncher.launch(Uri.parse(source.treeUri))
            },
            onOpenGroup = { openGroup = it },
            onMatchGroup = onMatchGroup,
            onConfirmGroup = { viewModel.confirmCardMatch(it) },
            onClearGroup = { clearTarget = it },
            onOpenHidden = { showHidden = true },
            onSetHidden = onSetHidden,
            onMatchMedia = onMatchMedia
        )
        openGroup?.let { group ->
            key(group.displayKey) {
                val files = remember(group.sourceId, group.displayKey) {
                    viewModel.animeMedia(group)
                }.collectAsLazyPagingItems()
                val liveGroup = groups?.itemSnapshotList?.items?.firstOrNull { it.displayKey == group.displayKey } ?: group
                LocalMediaGroupSheet(
                    group = liveGroup,
                    files = files,
                    onDismiss = { openGroup = null },
                    onPlayMedia = { id ->
                        openGroup = null
                        onPlayMedia(id)
                    },
                    onMatchMedia = onMatchMedia,
                    onConfirmMatch = onConfirmMatch,
                    onMatchGroup = { onMatchGroup(liveGroup) },
                    onSetHidden = onSetHidden,
                    actionError = state.error
                )
            }
        }
        if (sourceId != null && showHidden) {
            val files = remember(sourceId) { viewModel.hiddenMedia(sourceId) }.collectAsLazyPagingItems()
            LocalMediaHiddenSheet(
                files = files,
                onDismiss = { showHidden = false },
                onRestore = { onSetHidden(it, false) },
                actionError = state.error
            )
        }
    }
}

/** 与 ViewModel 解耦的页面内容，用于验证扫描结果更新时的真实列表布局。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalMediaLibraryContent(
    state: LocalMediaUiState,
    onBack: () -> Unit,
    onPlayMedia: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onSelectSource: (Long) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onScanSelectedSource: () -> Unit,
    onReidentify: () -> Unit = {},
    onCancelScan: () -> Unit = {},
    onReauthorize: (LocalMediaSourceEntity) -> Unit = {},
    onOpenGroup: (LocalMediaAnimeGroup) -> Unit = {},
    onMatchGroup: (LocalMediaAnimeGroup) -> Unit = {},
    onConfirmGroup: (LocalMediaAnimeGroup) -> Unit = {},
    onClearGroup: (LocalMediaAnimeGroup) -> Unit = {},
    onOpenHidden: () -> Unit = {},
    onSetHidden: (LocalMediaEntity, Boolean) -> Unit = { _, _ -> },
    onMatchMedia: (LocalMediaEntity) -> Unit = {},
    groups: LazyPagingItems<LocalMediaAnimeGroup>?,
    unclassified: LazyPagingItems<LocalMediaEntity>?
) {
    AppScaffold(
        title = stringResource(R.string.local_media_title),
        onBack = onBack
    ) { chrome ->
        val gridState = key(state.selectedSourceId) { rememberLazyGridState() }
        val direction = LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Adaptive(128.dp),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                .testTag("local-media-list"),
            contentPadding = PaddingValues(
                start = chrome.content.calculateStartPadding(direction) + 20.dp,
                top = chrome.content.calculateTopPadding(),
                end = chrome.content.calculateEndPadding(direction) + 20.dp,
                bottom = chrome.content.calculateBottomPadding() + 12.dp
            )
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = onAddFolder,
                    shape = SquircleShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.FOLDER_OPEN),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.local_media_add_folder))
                }
            }

            val revoked = state.sources.firstOrNull {
                it.id == state.selectedSourceId && it.permissionState == LocalMediaPermissionState.REVOKED
            } ?: state.sources.firstOrNull { it.permissionState == LocalMediaPermissionState.REVOKED }
            if (revoked != null) {
                item(key = "reauthorize-${revoked.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.local_media_reauthorize_hint, revoked.displayName),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onReauthorize(revoked) }, modifier = Modifier.testTag("local-reauthorize")) {
                            Text(stringResource(R.string.local_media_reauthorize))
                        }
                    }
                }
            }

            state.error?.let { error ->
                item(key = "local-media-error", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(when (error) {
                            LocalMediaActionError.ADD_FOLDER -> R.string.local_media_add_failed
                            LocalMediaActionError.WRONG_FOLDER -> R.string.local_media_wrong_folder
                            LocalMediaActionError.REAUTHORIZE -> R.string.local_media_permission_revoked
                            LocalMediaActionError.SCAN_REQUEST -> R.string.local_media_scan_request_failed
                            LocalMediaActionError.REMOVE_SOURCE -> R.string.local_media_remove_failed
                            LocalMediaActionError.MATCH -> R.string.local_media_match_failed
                            LocalMediaActionError.VISIBILITY -> R.string.local_media_visibility_failed
                        }),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            if (state.sources.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.INSERT_DRIVE_FILE),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.local_media_empty))
                    }
                }
            }

            // 来源和媒体来自不同表，自增 ID 可相同；同一个网格必须区分类型。
            items(state.sources, key = { "source-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { source ->
                val selected = source.id == state.selectedSourceId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSource(source.id) }
                        .padding(vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.FOLDER),
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = source.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    source.permissionState != LocalMediaPermissionState.GRANTED ->
                                        stringResource(R.string.local_media_permission_revoked)
                                    else -> stringResource(
                                        R.string.local_media_video_count,
                                        source.videoCount
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteSource(source.id) }) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.DELETE),
                                contentDescription = stringResource(R.string.common_delete)
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            state.sources.firstOrNull { it.id == state.selectedSourceId }?.let { source ->
                item(key = "scan-${source.id}", span = { GridItemSpan(maxLineSpan) }) {
                    val scan = state.scan
                    val active = scan?.isActive == true
                    val message = when (scan?.phase) {
                        LocalScanPhase.QUEUED -> stringResource(R.string.local_media_scan_queued)
                        LocalScanPhase.SCANNING -> stringResource(R.string.local_media_scanning)
                        LocalScanPhase.MATCHING -> stringResource(R.string.local_media_scan_matching)
                        LocalScanPhase.CANCELLED -> stringResource(R.string.local_media_scan_cancelled)
                        LocalScanPhase.FAILED -> when (scan.failure) {
                            LocalScanFailure.PERMISSION -> stringResource(R.string.local_media_permission_revoked)
                            LocalScanFailure.READ -> stringResource(R.string.local_media_scan_read_failed)
                            else -> stringResource(R.string.local_media_scan_failed)
                        }
                        else -> stringResource(R.string.local_media_video_count, source.videoCount)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("local-scan-summary")) {
                            Text(stringResource(R.string.local_media_scan_summary), style = MaterialTheme.typography.titleSmall)
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (scan != null && (scan.phase == LocalScanPhase.SCANNING || scan.phase == LocalScanPhase.MATCHING)) {
                                Text(
                                    stringResource(R.string.local_media_scan_count, scan.scannedFiles, scan.videoFiles),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (active) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            TextButton(onClick = onCancelScan, modifier = Modifier.testTag("local-scan-cancel")) {
                                Text(stringResource(R.string.local_media_scan_cancel))
                            }
                        } else if (source.permissionState == LocalMediaPermissionState.GRANTED) {
                            val actionPadding = PaddingValues(horizontal = 6.dp)
                            TextButton(onClick = onScanSelectedSource, contentPadding = actionPadding,
                                modifier = Modifier.testTag("local-scan-start")) {
                                Text(stringResource(R.string.local_media_scan_again), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (source.videoCount > 0) {
                                TextButton(onClick = onReidentify, contentPadding = actionPadding,
                                    modifier = Modifier.testTag("local-scan-reidentify")) {
                                    Text(stringResource(R.string.local_media_reidentify), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (state.selectedSourceId != null && groups != null && unclassified != null) {
                localMediaFiles(
                    groups = groups,
                    unclassified = unclassified,
                    showEmptyHint = state.scan?.isActive != true && state.sources.any {
                        it.id == state.selectedSourceId && it.permissionState == LocalMediaPermissionState.GRANTED
                    },
                    hiddenCount = state.hiddenCount,
                    onOpenHidden = onOpenHidden,
                    onOpenGroup = onOpenGroup,
                    onMatchGroup = onMatchGroup,
                    onConfirmGroup = onConfirmGroup,
                    onClearGroup = onClearGroup,
                    onPlayMedia = onPlayMedia,
                    onMatchMedia = onMatchMedia,
                    onSetHidden = onSetHidden
                )
            }
        }
    }
}
