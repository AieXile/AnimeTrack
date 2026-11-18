package com.aiexile.animetrack.ui.player.local

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaPermissionState
import com.aiexile.animetrack.data.media.LocalMediaScanState
import com.aiexile.animetrack.ui.components.AppOutlinedTextField
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
    val defaultFolderName = stringResource(R.string.local_media_default_folder)
    var showAddHint by remember { mutableStateOf(false) }
    var matchTarget by remember { mutableStateOf<LocalMediaEntity?>(null) }
    var matchQuery by remember { mutableStateOf("") }

    matchTarget?.let { target ->
        val options = state.animeOptions.filter {
            matchQuery.isBlank() || it.second.contains(matchQuery.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = {
                matchTarget = null
                matchQuery = ""
            },
            title = { Text(stringResource(R.string.local_media_match_title)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 460.dp)) {
                    Text(
                        text = target.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AppOutlinedTextField(
                        value = matchQuery,
                        onValueChange = { matchQuery = it },
                        label = { Text(stringResource(R.string.local_media_match_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(options, key = { it.first }) { (id, title) ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.confirmMatch(target.id, id)
                                        matchTarget = null
                                        matchQuery = ""
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp)
                            )
                        }
                        if (options.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.local_media_match_none),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMatch(target.id)
                        matchTarget = null
                        matchQuery = ""
                    }
                ) {
                    Text(stringResource(R.string.local_media_match_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { matchTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
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

    LocalMediaLibraryContent(
        state = state,
        onBack = onBack,
        onPlayMedia = onPlayMedia,
        onAddFolder = { showAddHint = true },
        onChooseFolder = { folderLauncher.launch(null) },
        onSelectSource = viewModel::selectSource,
        onDeleteSource = viewModel::deleteSource,
        onScanSelectedSource = viewModel::scanSelectedSource,
        onMatchMedia = { media -> matchTarget = media }
    )
}

/** 与 ViewModel 解耦的页面内容，用于验证扫描结果更新时的真实列表布局。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalMediaLibraryContent(
    state: LocalMediaUiState,
    onBack: () -> Unit,
    onPlayMedia: (Long) -> Unit,
    onAddFolder: () -> Unit,
    onChooseFolder: () -> Unit,
    onSelectSource: (Long) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onScanSelectedSource: () -> Unit,
    onMatchMedia: (LocalMediaEntity) -> Unit = {}
) {
    AppScaffold(
        title = stringResource(R.string.local_media_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onChooseFolder) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ADD),
                    contentDescription = stringResource(R.string.local_media_add_folder)
                )
            }
        }
    ) { chrome ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout),
            contentPadding = chrome.content
        ) {
            item {
                Button(
                    onClick = onAddFolder,
                    shape = SquircleShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
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

            if (state.error != null) {
                item {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            if (state.sources.isEmpty()) {
                item {
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

            // 来源和媒体来自不同表，自增 ID 可相同；同一个 LazyColumn 必须区分类型。
            items(state.sources, key = { "source-${it.id}" }) { source ->
                val selected = source.id == state.selectedSourceId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSource(source.id) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
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
                                    source.scanState == LocalMediaScanState.SCANNING ->
                                        stringResource(R.string.local_media_scanning)
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
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
            }

            state.selectedSourceId?.let { selectedId ->
                item(key = "scan-$selectedId") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.local_media_scan_summary),
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (state.isScanning) {
                                Text(
                                    text = "${state.scannedFiles} · ${state.currentPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = stringResource(
                                        R.string.local_media_scan_count,
                                        state.scannedFiles,
                                        state.videoFiles
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (state.isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = onScanSelectedSource) {
                                Text(stringResource(R.string.local_media_scan_again))
                            }
                        }
                    }
                }
            }

            if (state.selectedSourceId != null) {
                item(key = "media-header") {
                    Text(
                        text = stringResource(R.string.local_media_files),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                items(
                    items = state.media.filterNot { it.isMissing },
                    key = { "media-${it.id}" }
                ) { media ->
                    LocalMediaRow(
                        media = media,
                        animeTitle = media.matchedAnimeId?.let(state.animeTitles::get),
                        onPlay = { onPlayMedia(media.id) },
                        onMatch = { onMatchMedia(media) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun LocalMediaRow(
    media: LocalMediaEntity,
    animeTitle: String?,
    onPlay: () -> Unit,
    onMatch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.MOVIE),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = media.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(media.parsedTitle ?: "未解析")
                    media.episodeNumber?.let { append(" · 第${it}集") }
                    if (media.episodeEndNumber != null) append("-${media.episodeEndNumber}")
                    append(" · ")
                    append(animeTitle ?: "未匹配")
                    if (animeTitle != null && !media.isUserOverride) {
                        append(" · ")
                        append(stringResource(R.string.local_media_match_pending))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (animeTitle == null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onMatch) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.EDIT),
                contentDescription = stringResource(R.string.local_media_match_action)
            )
        }
        IconButton(onClick = onPlay) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
                contentDescription = stringResource(R.string.player_play)
            )
        }
    }
}
