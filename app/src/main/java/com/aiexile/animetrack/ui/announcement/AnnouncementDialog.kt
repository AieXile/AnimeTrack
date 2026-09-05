package com.aiexile.animetrack.ui.announcement

import androidx.compose.animation.AnimatedContent
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.AnnouncementDetail
import com.aiexile.animetrack.data.network.AnnouncementOption
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
        val date = input.parse(iso) ?: error("Unparseable date: $iso")
        val output = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        output.format(date)
    } catch (_: Exception) {
        try {
            val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            input.timeZone = utc
            val date = input.parse(iso) ?: error("Unparseable date: $iso")
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
        shape = SquircleShape(24.dp),
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
                    uiState = uiState,
                    onVote = { viewModel.submitVote(it) },
                    onClearVoteError = { viewModel.clearVoteError() }
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
                            painter = rememberAppIconPainter(AppIcon.HISTORY),
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
                // 右侧：未投票的投票公告时显示提示，否则显示"我知道了"
                // detail 加载期间也隐藏按钮，避免切换公告时 detail 暂为 null 导致按钮闪现、
                // 绕过投票约束（任何含投票且未投的公告，无论从当前还是历史进入都必须先投票）。
                val detail = uiState.currentDetail
                val shouldHideClose = !uiState.showHistoryList && (
                    uiState.isDetailLoading ||
                        (detail != null && detail.options.isNotEmpty() && detail.selectedOptionId == null)
                    )
                if (shouldHideClose) {
                    Text(
                        text = stringResource(R.string.announcement_vote_required),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TextButton(onClick = { viewModel.dismiss() }) {
                        Text(stringResource(R.string.announcement_close))
                    }
                }
            }
        }
    )
}

/** 公告内容视图 */
@Composable
private fun AnnouncementContent(
    announcement: com.aiexile.animetrack.data.network.Announcement,
    uiState: AnnouncementUiState,
    onVote: (Int) -> Unit,
    onClearVoteError: () -> Unit
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
                shape = SquircleShape(8.dp),
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
            shape = SquircleShape(8.dp),
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

        // 投票区（需登录，未登录或无选项时不显示）
        VoteSection(
            detail = uiState.currentDetail,
            isDetailLoading = uiState.isDetailLoading,
            isVoting = uiState.isVoting,
            voteError = uiState.voteError,
            onVote = onVote,
            onClearVoteError = onClearVoteError
        )
    }
}

/**
 * 投票区组件。
 * - 未投票：选项为可点击行（RadioButton 指示器，无 ripple）。
 * - 已投票：显示各选项得票数 + 占比 + 进度条，已选项高亮。
 * - 投票状态由后端 selectedOptionId 驱动，而非本地 remember 锁定。
 */
@Composable
private fun VoteSection(
    detail: AnnouncementDetail?,
    isDetailLoading: Boolean,
    isVoting: Boolean,
    voteError: String?,
    onVote: (Int) -> Unit,
    onClearVoteError: () -> Unit
) {
    // 详情加载中
    if (isDetailLoading) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.announcement_vote_loading),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val safeDetail = detail ?: return
    val options = safeDetail.options.sortedBy { it.sortOrder }
    if (options.isEmpty()) return

    val selectedOptionId = safeDetail.selectedOptionId
    val hasVoted = selectedOptionId != null
    val totalVotes = options.sumOf { it.count }

    Spacer(modifier = Modifier.height(12.dp))
    // 投票区标题（投票中显示加载指示）
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.announcement_vote_title),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isVoting && !hasVoted) {
            Spacer(modifier = Modifier.width(8.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            options.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                if (hasVoted) {
                    VotedOptionRow(
                        option = option,
                        isSelected = option.id == selectedOptionId,
                        totalVotes = totalVotes
                    )
                } else {
                    UnvotedOptionRow(
                        option = option,
                        isVoting = isVoting,
                        onVote = onVote
                    )
                }
            }
        }
    }

    // 总票数
    if (hasVoted) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.announcement_vote_total_format, totalVotes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 投票错误提示（点击关闭）
    voteError?.let { error ->
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SquircleShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            onClick = onClearVoteError
        ) {
            Text(
                text = error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** 未投票选项行：RadioButton 指示器，无 ripple（遵循项目偏好） */
@Composable
private fun UnvotedOptionRow(
    option: AnnouncementOption,
    isVoting: Boolean,
    onVote: (Int) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isVoting,
                onClick = { onVote(option.id) }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.text,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = if (isVoting) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        RadioButton(
            selected = false,
            onClick = null,
            enabled = !isVoting,
            interactionSource = interactionSource,
            colors = RadioButtonDefaults.colors(
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

/** 已投票选项行：进度条 + 票数 + 占比，已选项高亮 */
@Composable
private fun VotedOptionRow(
    option: AnnouncementOption,
    isSelected: Boolean,
    totalVotes: Int
) {
    val ratio = if (totalVotes > 0) option.count.toFloat() / totalVotes else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "voteProgress"
    )
    val percent = (ratio * 100).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.text,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            if (isSelected) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CHECK),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(end = 0.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "$percent%",
                fontSize = 12.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.announcement_vote_count_format, option.count),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(SquircleShape(2.dp)),
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
            drawStopIndicator = {}
        )
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
                    painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
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
            shape = SquircleShape(8.dp),
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
                    // 已读/未读仅以字重区分：未读加粗，已读常规（颜色均用 onSurface）
                    val isRead = ann.id in uiState.readAnnouncementIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ann.title,
                                fontSize = 14.sp,
                                fontWeight = if (isRead) FontWeight.Normal
                                else FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            formatAnnouncementTime(ann.createdAt)?.let { time ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRead)
                                        stringResource(R.string.announcement_read_format, time)
                                    else time,
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
