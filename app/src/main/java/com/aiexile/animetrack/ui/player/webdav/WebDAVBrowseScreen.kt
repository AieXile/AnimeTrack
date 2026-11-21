package com.aiexile.animetrack.ui.player.webdav

import androidx.compose.foundation.clickable
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.aiexile.animetrack.ui.components.ShimmerText
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDAVBrowseScreen(
    onFileClick: (path: String, fileName: String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: WebDAVBrowseViewModel = viewModel(factory = WebDAVBrowseViewModel.Factory())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 返回同一浏览实例时保留目录列表；仅首次进入或进程重建后重新查询。
    LaunchedEffect(viewModel) {
        if (uiState.currentPath.isBlank() && !uiState.isLoading) viewModel.browseDirectory()
    }

    AppScaffold(
        title = stringResource(R.string.webdav_browse_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.browseDirectory(uiState.currentPath) }) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.SYNC),
                    contentDescription = stringResource(R.string.webdav_browse_refresh)
                )
            }
            IconButton(
                onClick = viewModel::navigateToMediaRoot,
                enabled = uiState.mediaRootPath.isNotBlank()
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.HOME),
                    contentDescription = stringResource(R.string.webdav_browse_media_root)
                )
            }
            IconButton(
                onClick = viewModel::setCurrentAsMediaRoot,
                enabled = uiState.currentPath.isNotBlank()
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.FOLDER),
                    contentDescription = stringResource(R.string.webdav_browse_set_media_root)
                )
            }
            IconButton(onClick = viewModel::clearBrowseMemory) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.DELETE),
                    contentDescription = stringResource(R.string.webdav_browse_clear_memory)
                )
            }
        }
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                .padding(horizontal = 16.dp)
                .padding(chrome.content)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = uiState.currentPath.ifBlank { "/" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (uiState.mediaRootPath.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.webdav_browse_root_format,
                                uiState.mediaRootPath
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (uiState.lastBrowsePath.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.player_webdav_recent_location, webDavDisplayPath(uiState.lastBrowsePath)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        ShimmerText(
                            text = stringResource(R.string.webdav_browse_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.webdav_browse_load_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.browseDirectory(uiState.currentPath) }) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }

                !uiState.isConfigured -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.webdav_browse_not_configured),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.webdav_browse_not_configured_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                        items(uiState.files, key = { it.path }) { item ->
                            FileItemRow(
                                item = item,
                                onClick = {
                                    if (item.isDirectory) {
                                        viewModel.browseDirectory(item.path)
                                    } else {
                                        viewModel.markFileSelected(item.path)
                                        onFileClick(item.path, item.name)
                                    }
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItemRow(
    item: WebDAVFileItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(0.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            item.name == ".." -> {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ARROW_UPWARD),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.webdav_browse_parent_dir),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            item.isDirectory -> {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.FOLDER),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            else -> {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.MOVIE),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.size > 0) {
                    Text(
                        text = formatFileSize(item.size),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 展示目录层级，不把服务器地址、认证信息和 URL 查询参数塞进最近浏览摘要。 */
internal fun webDavDisplayPath(url: String): String = url.toHttpUrlOrNull()
    ?.pathSegments?.dropLastWhile(String::isEmpty)?.joinToString(separator = "/", prefix = "/") ?: "/"

private fun formatFileSize(size: Long): String {
    return when {
        size >= 1_000_000_000 -> "%.1f GB".format(size / 1_000_000_000.0)
        size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
        size >= 1_000 -> "%.1f KB".format(size / 1_000.0)
        else -> "$size B"
    }
}
