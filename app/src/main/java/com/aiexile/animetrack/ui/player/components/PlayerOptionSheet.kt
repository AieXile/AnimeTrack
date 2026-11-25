package com.aiexile.animetrack.ui.player.components

import com.aiexile.animetrack.ui.player.PlayerUiState

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon

/**
 * 竖屏（非全屏）下的选项弹层类型。
 *
 * 全屏播放时这些选项走右侧抽屉 [PlayerDrawerLevel]；竖屏没有视频铺满屏幕的语境，
 * 用底部弹层更顺手，也与页面其余部分（浅色卡片）保持同一材质。
 * 两套容器共用同一份状态逻辑（[rememberPlayerTrackChoices]、
 * [PlayerSelectionContent] 的倍速表），只在绘制层分叉。
 */
internal enum class DetailsSheetType { QUALITY, SPEED, SUBTITLES, MORE }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerOptionSheet(
    type: DetailsSheetType,
    state: PlayerUiState,
    /** 控制器未连上时为空：清晰度/字幕入口此时本身不可点 */
    player: Player?,
    onDismiss: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetScale: () -> Unit,
    onSetAutoNext: (Boolean) -> Unit,
    onSetRememberPosition: (Boolean) -> Unit,
    onSetAutoLandscape: (Boolean) -> Unit,
    onRetry: () -> Unit,
    addSubtitle: (() -> Unit)? = null,
    removeSubtitle: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = when (type) {
                    DetailsSheetType.QUALITY -> stringResource(R.string.player_quality)
                    DetailsSheetType.SPEED -> stringResource(R.string.player_playback_speed)
                    DetailsSheetType.SUBTITLES -> stringResource(R.string.player_subtitles)
                    DetailsSheetType.MORE -> stringResource(R.string.player_menu_more)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            when (type) {
                DetailsSheetType.QUALITY -> player?.let {
                    QualitySheetRows(
                        state = state,
                        player = it,
                        onSelectSource = onSelectSource,
                        onDismiss = onDismiss
                    )
                }

                DetailsSheetType.SPEED -> {
                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                    speeds.forEach { speed ->
                        PlayerSheetRow(
                            label = "${speed}×",
                            selected = speed == state.playbackSpeed,
                            onClick = {
                                onSetSpeed(speed)
                                onDismiss()
                            }
                        )
                    }
                }

                DetailsSheetType.SUBTITLES -> {
                    addSubtitle?.let { add ->
                        PlayerSheetRow(label = stringResource(R.string.player_manual_subtitle_add), onClick = { onDismiss(); add() })
                    }
                    removeSubtitle?.let { remove ->
                        PlayerSheetRow(label = stringResource(R.string.player_manual_subtitle_remove), onClick = { onDismiss(); remove() })
                    }
                    val choices = player?.let { rememberPlayerTrackChoices(it, C.TRACK_TYPE_TEXT) }.orEmpty()
                    if (choices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.player_track_none_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        choices.forEach { choice ->
                            PlayerSheetRow(
                                label = choice.label,
                                selected = choice.selected,
                                subtitleLanguage = choice.subtitleLanguage,
                                preserveLabelEnd = true,
                                onClick = {
                                    choice.select()
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                DetailsSheetType.MORE -> {
                    PlayerSheetToggle(
                        label = stringResource(R.string.player_settings_auto_play_next),
                        checked = state.autoPlayNext,
                        onCheckedChange = onSetAutoNext
                    )
                    PlayerSheetToggle(
                        label = stringResource(R.string.player_settings_remember_position),
                        checked = state.rememberPosition,
                        onCheckedChange = onSetRememberPosition
                    )
                    PlayerSheetToggle(
                        label = stringResource(R.string.player_auto_landscape),
                        checked = state.autoLandscape,
                        onCheckedChange = onSetAutoLandscape
                    )
                    PlayerSheetRow(
                        label = stringResource(R.string.player_video_scale),
                        trailingValue = stringResource(
                            when (state.videoScale) {
                                1 -> R.string.player_scale_fill
                                2 -> R.string.player_scale_crop
                                else -> R.string.player_scale_fit
                            }
                        ),
                        onClick = onSetScale
                    )
                    PlayerSheetRow(
                        label = stringResource(
                            if (state.isWebPlayback) R.string.web_playback_reparse
                            else R.string.common_retry
                        ),
                        onClick = {
                            onDismiss()
                            onRetry()
                        }
                    )
                }
            }
        }
    }
}

/** 清晰度：网站来源与视频轨道两组，逻辑取自共用的轨道选择器。 */
@Composable
private fun QualitySheetRows(
    state: PlayerUiState,
    player: Player,
    onSelectSource: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (state.webSources.size > 1) {
        Text(
            text = stringResource(R.string.player_quality_website),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )
        state.webSources.forEachIndexed { index, source ->
            PlayerSheetRow(
                label = source.label ?: stringResource(R.string.player_video_source, index + 1),
                selected = index == state.selectedWebSource,
                onClick = {
                    onSelectSource(index)
                    onDismiss()
                }
            )
        }
        Text(
            text = stringResource(R.string.player_quality_stream),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
        )
    }
    rememberPlayerTrackChoices(player, C.TRACK_TYPE_VIDEO).forEach { choice ->
        PlayerSheetRow(
            label = choice.label,
            selected = choice.selected,
            onClick = {
                choice.select()
                onDismiss()
            }
        )
    }
}

/** 弹层内的单行选项：左侧文字、右侧勾选，圆角响应区与页面控件一致。 */
@Composable
private fun PlayerSheetRow(
    label: String,
    onClick: () -> Unit,
    selected: Boolean? = null,
    trailingValue: String? = null,
    subtitleLanguage: String? = null,
    preserveLabelEnd: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val titleStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = if (selected == true) FontWeight.SemiBold else FontWeight.Normal
        )
        if (preserveLabelEnd) {
            PlayerSubtitleTrackLabel(
                label = label,
                language = subtitleLanguage,
                titleStyle = titleStyle,
                detailStyle = MaterialTheme.typography.bodySmall,
                titleColor = Color.Unspecified,
                detailColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = label,
                style = titleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (trailingValue != null) {
            Text(
                text = trailingValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected == true) {
            AppIcon(
                icon = AppIcon.CHECK,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 弹层内的开关行：整行可点，开关自身不处理点击，避免双触发。 */
@Composable
private fun PlayerSheetToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}
