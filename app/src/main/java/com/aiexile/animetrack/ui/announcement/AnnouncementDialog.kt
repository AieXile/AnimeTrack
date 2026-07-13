package com.aiexile.animetrack.ui.announcement

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.MarkdownText
import java.text.SimpleDateFormat
import java.util.Locale

/** 格式化 ISO 时间为 "yyyy-MM-dd HH:mm"（24小时制，UTC 转本地时区） */
private fun formatAnnouncementTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val utc = java.util.TimeZone.getTimeZone("UTC")
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        input.timeZone = utc
        val date = input.parse(iso)
        val output = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        output.format(date)
    } catch (_: Exception) {
        try {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            input.timeZone = utc
            val date = input.parse(iso)
            val output = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            output.format(date)
        } catch (_: Exception) {
            iso.take(16).replace("T", " ")
        }
    }
}

/** 将 Markdown 中的相对路径图片 URL 拼接为完整 URL */
private fun resolveImageUrls(markdown: String): String {
    return markdown.replace(Regex("!\\[(.*?)]\\((/uploads/[^)]+)\\)")) { match ->
        val alt = match.groupValues[1]
        val url = match.groupValues[2]
        "![$alt](https://www.aiexile.top$url)"
    }
}

@Composable
fun AnnouncementDialog(viewModel: AnnouncementViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.showDialog) return

    AlertDialog(
        onDismissRequest = { viewModel.dismiss() },
        title = null,
        text = {
            if (uiState.showHistoryList) {
                HistoryListContent(
                    uiState = uiState,
                    onBack = { viewModel.backFromHistory() },
                    onSelect = { viewModel.selectAnnouncement(it) }
                )
            } else {
                val announcement = uiState.currentAnnouncement ?: return@AlertDialog
                AnnouncementContent(
                    announcement = announcement,
                    uiState = uiState
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 历史公告按钮（最左边，多条公告时显示）
                if (uiState.announcements.size > 1 && !uiState.showHistoryList) {
                    TextButton(onClick = { viewModel.showHistoryList() }) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.announcement_history),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier)
                }
                // 我知道了
                TextButton(onClick = { viewModel.dismiss() }) {
                    Text(stringResource(R.string.announcement_close))
                }
            }
        }
    )
}

/** 公告内容视图 */
@Composable
private fun AnnouncementContent(
    announcement: com.aiexile.animetrack.data.network.Announcement,
    uiState: AnnouncementUiState
) {
    Column {
        // 标题
        Text(
            text = announcement.title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 创建时间（24小时制）
        formatAnnouncementTime(announcement.createdAt)?.let { time ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = time,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 头图（后端返回相对路径，需拼接完整 URL）
        announcement.imageUrl?.takeIf { it.isNotBlank() }?.let { raw ->
            val fullUrl = if (raw.startsWith("http")) raw else "https://www.aiexile.top$raw"
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                AsyncImage(
                    model = fullUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // 内容（Markdown 渲染，支持图片）
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AnimatedContent(
                    targetState = uiState.currentIndex,
                    transitionSpec = {
                        (fadeIn(tween(300)) togetherWith fadeOut(tween(200)))
                    },
                    label = "announcementContent"
                ) {
                    val content = uiState.announcements.getOrNull(it)?.content
                    if (content.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.announcement_no_content),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        MarkdownText(markdown = resolveImageUrls(content))
                    }
                }
            }
        }
    }
}

/** 历史公告列表视图 */
@Composable
private fun HistoryListContent(
    uiState: AnnouncementUiState,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Column {
        // 标题行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.announcement_history),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.announcement_back),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 公告列表（在内容框中）
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                uiState.announcements.forEachIndexed { index, ann ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ann.title,
                                fontSize = 14.sp,
                                fontWeight = if (index == uiState.currentIndex)
                                    FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            formatAnnouncementTime(ann.createdAt)?.let { time ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = time,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index == uiState.currentIndex) {
                            Text(
                                text = stringResource(R.string.announcement_current),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
