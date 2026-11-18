package com.aiexile.animetrack.ui.player.components

import com.aiexile.animetrack.ui.player.PlayerUiState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.ui.components.CapsuleSegmentedButton
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.theme.LocalAnimeColors

/** 播放窗口下方的番剧身份、常用选项、线路与选集；仅消费已有播放状态。 */
@Composable
internal fun PlayerDetailsContent(
    state: PlayerUiState,
    anime: Anime?,
    gridState: LazyGridState,
    onSelectEpisode: (Int) -> Unit,
    onSelectChannel: (Int) -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 非空时身份卡可点击回到番剧详情页 */
    onOpenAnimeDetail: (() -> Unit)? = null
) {
    val title = anime?.title?.takeIf { it.isNotBlank() }
        ?: state.seriesTitle?.takeIf { it.isNotBlank() }
        ?: state.mediaTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_playback)
    val currentIndex = state.episodes.indexOfFirst { it.index == state.currentEpisodeIndex }
    val currentEpisode = state.episodes.getOrNull(currentIndex)
    val hasChannels = state.channels.isNotEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 单元格按可用宽度自适应：竖屏约 5 列，平板/分屏随宽度增加，最大 10 列防止过密
        val columns = (maxWidth / 72.dp).toInt().coerceIn(4, 10)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "identity", span = { GridItemSpan(maxLineSpan) }) {
                PlayerNowPlayingCard(
                    anime = anime,
                    title = title,
                    // 网页播放时不要用 mediaTitle 兜底：它形如「番剧名 · 第x集」，
                    // 会在选集队列到达前先闪一下，随后才被真正的集名替换
                    episodeLabel = currentEpisode?.label
                        ?: state.mediaTitle?.takeIf { it != title && !state.isWebPlayback },
                    sourceLabel = state.sourceLabel,
                    onOpenDetail = onOpenAnimeDetail
                )
            }
            item(key = "actions", span = { GridItemSpan(maxLineSpan) }) {
                PlayerActionsCard(
                    qualityValue = qualityValue(state),
                    playbackSpeed = state.playbackSpeed,
                    qualityEnabled = state.isControllerReady,
                    subtitleEnabled = state.isControllerReady,
                    onOpenQuality = onOpenQuality,
                    onOpenSpeed = onOpenSpeed,
                    onOpenSubtitles = onOpenSubtitles,
                    onOpenMore = onOpenSettings
                )
            }
            if (hasChannels) {
                item(key = "channels", span = { GridItemSpan(maxLineSpan) }) {
                    PlayerChannelCard(
                        channels = state.channels,
                        selectedIndex = state.currentChannelIndex,
                        onSelect = onSelectChannel
                    )
                }
            }
            item(key = "episodes-header", span = { GridItemSpan(maxLineSpan) }) {
                PlayerEpisodesHeader()
            }
            if (state.episodes.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(
                            if (state.isPreparingMedia) R.string.player_preparing
                            else R.string.player_no_episode_list
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.episodes, key = { "episode:${it.index}" }) { item ->
                    PlayerEpisodeCell(
                        label = item.label,
                        selected = item.index == state.currentEpisodeIndex,
                        progress = state.episodeProgress[item.index],
                        onClick = { onSelectEpisode(item.index) }
                    )
                }
            }
        }
    }
}

/** 清晰度入口的副标题：多来源时显示当前网站，否则是自适应轨道的「自动」。 */
@Composable
private fun qualityValue(state: PlayerUiState): String {
    if (state.webSources.size <= 1) return stringResource(R.string.player_quality_auto)
    val index = state.selectedWebSource
    state.webSources.getOrNull(index)?.label?.let { return it }
    return if (index >= 0) stringResource(R.string.player_video_source, index + 1)
    else stringResource(R.string.player_quality_auto)
}

/** 常用选项条：四个等分图标入口，无描边，未就绪时整体降透明度而不是显示为禁用边框。 */
@Composable
private fun PlayerActionsCard(
    qualityValue: String,
    playbackSpeed: Float,
    qualityEnabled: Boolean,
    subtitleEnabled: Boolean,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenMore: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            PlayerActionItem(
                icon = AppIcon.MOVIE,
                label = stringResource(R.string.player_quality),
                value = qualityValue,
                enabled = qualityEnabled,
                onClick = onOpenQuality,
                modifier = Modifier.weight(1f)
            )
            PlayerActionItem(
                icon = AppIcon.SPEED,
                label = stringResource(R.string.player_playback_speed),
                value = "${playbackSpeed}×",
                enabled = true,
                onClick = onOpenSpeed,
                modifier = Modifier.weight(1f)
            )
            PlayerActionItem(
                icon = AppIcon.CLOSED_CAPTION,
                label = stringResource(R.string.player_subtitles),
                value = null,
                enabled = subtitleEnabled,
                onClick = onOpenSubtitles,
                modifier = Modifier.weight(1f)
            )
            PlayerActionItem(
                icon = AppIcon.MORE_VERT,
                label = stringResource(R.string.player_menu_more),
                value = null,
                enabled = true,
                onClick = onOpenMore,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlayerActionItem(
    icon: AppIcon,
    label: String,
    value: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(
        modifier = modifier
            .clip(SquircleShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AppIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = 12.sp,
            // 显式行高：只改 fontSize 会继承 bodyLarge 的 24sp 行高，撑出大段空隙
            lineHeight = 16.sp,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // 没有副标题的入口用空格占位：行高由字体自然撑开，不会像固定高度那样裁掉半行
        Text(
            text = value ?: " ",
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 线路选择：常规线路数用胶囊分段控件（与详情页连载状态选择器同一手感）；
 * 超过 4 条时等分宽度会挤扁文字，退化为可横向滚动的胶囊行。
 */
@Composable
private fun PlayerChannelCard(
    channels: List<String?>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val labels = mutableListOf<String>()
    for (index in channels.indices) {
        labels += channels[index] ?: stringResource(R.string.web_source_channel, index + 1)
    }
    val selected = selectedIndex.coerceIn(0, (channels.size - 1).coerceAtLeast(0))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.player_channels),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (labels.size <= 4) {
                CapsuleSegmentedButton(
                    options = channels.indices.toList(),
                    selectedOption = selected,
                    onOptionSelected = onSelect,
                    label = { labels[it] }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    labels.forEachIndexed { index, label ->
                        PlayerChannelPill(
                            label = label,
                            selected = index == selected,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerChannelPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val animeColors = LocalAnimeColors.current
    Surface(
        onClick = onClick,
        shape = SquircleShape(100.dp),
        color = if (selected) animeColors.chipSelectedContainer else animeColors.chipUnselectedContainer,
        contentColor = if (selected) animeColors.chipSelectedContent else animeColors.chipUnselectedContent
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/** 选集标题栏。 */
@Composable
private fun PlayerEpisodesHeader() {
    Text(
        text = stringResource(R.string.player_episodes),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    )
}

/**
 * 选集格子：只放集数数字，当前集用主题色实心，已看/未看完在底部用 2dp 进度线表示。
 * 相比带描边的卡片，网格更紧凑，长剧集不需要滚动太多屏就能看全。
 */
@Composable
private fun PlayerEpisodeCell(
    label: String,
    selected: Boolean,
    progress: PlaybackProgress?,
    onClick: () -> Unit
) {
    // 「看过」只要有过播放进度就算（部分观看也算），仅当过零才算未看；
    // 这样从网页续播回来时，看过一截的集数也会立刻变灰。
    val watched = progress != null && (progress.completed || progress.fraction > 0f)
    val container = when {
        selected -> MaterialTheme.colorScheme.primary
        watched -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        // 已看完的文字再压一档灰度：tone 30 的 onSurfaceVariant 在灰底上仍偏深，区分不够明显
        watched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    // 已看完的进度线也退成灰，避免黑色进度线反而把「看过的」衬得更醒目
    val progressColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
        watched -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary
    }
    val fraction = progress?.fraction ?: 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(SquircleShape(12.dp))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(progressColor)
                )
            }
        }
    }
}
