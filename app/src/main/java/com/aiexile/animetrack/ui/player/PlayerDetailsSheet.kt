package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.aiexile.animetrack.R

internal enum class DetailsSheetType { QUALITY, SPEED, SUBTITLES, MORE }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerDetailsSheet(
    type: DetailsSheetType,
    state: PlayerUiState,
    player: Player,
    onDismiss: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetScale: (Int) -> Unit,
    onSetAutoNext: (Boolean) -> Unit,
    onSetRememberPosition: (Boolean) -> Unit,
    onSetAutoLandscape: (Boolean) -> Unit,
    onRetry: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = when (type) {
                    DetailsSheetType.QUALITY -> stringResource(R.string.player_quality)
                    DetailsSheetType.SPEED -> stringResource(R.string.player_playback_speed)
                    DetailsSheetType.SUBTITLES -> stringResource(R.string.player_subtitles)
                    DetailsSheetType.MORE -> stringResource(R.string.player_menu_more)
                }, style = MaterialTheme.typography.titleLarge
            )
            when (type) {
                DetailsSheetType.QUALITY -> QualityChoices(state, player, onSelectSource)
                DetailsSheetType.SPEED -> {
                    val speeds = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        speeds.forEach { speed ->
                            FilterChip(selected = speed == state.playbackSpeed,
                                onClick = { onSetSpeed(speed); onDismiss() },
                                label = { Text("${speed}×") })
                        }
                    }
                }
                DetailsSheetType.SUBTITLES -> TrackChoices(player, C.TRACK_TYPE_TEXT, onDismiss)
                DetailsSheetType.MORE -> {
                    SettingSwitch(stringResource(R.string.player_settings_auto_play_next), state.autoPlayNext, onSetAutoNext)
                    SettingSwitch(stringResource(R.string.player_settings_remember_position), state.rememberPosition, onSetRememberPosition)
                    SettingSwitch(stringResource(R.string.player_auto_landscape), state.autoLandscape, onSetAutoLandscape)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = { onSetScale((state.videoScale + 1) % 3) }) {
                        Text(stringResource(R.string.player_video_scale))
                    }
                    TextButton(onClick = { onRetry(); onDismiss() }) {
                        Text(stringResource(if (state.isWebPlayback) R.string.web_playback_reparse else R.string.common_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityChoices(state: PlayerUiState, player: Player, onSelectSource: (Int) -> Unit) {
    if (state.webSources.size > 1) {
        Text(stringResource(R.string.player_quality_website), style = MaterialTheme.typography.labelMedium)
        state.webSources.forEachIndexed { index, source ->
            TextButton(onClick = { onSelectSource(index) }, modifier = Modifier.fillMaxWidth()) {
                Text(source.label ?: stringResource(R.string.player_video_source, index + 1), modifier = Modifier.weight(1f))
                if (index == state.selectedWebSource) Text("✓")
            }
        }
        HorizontalDivider()
    }
    TrackChoices(player, C.TRACK_TYPE_VIDEO, onDismiss = {})
}

@Composable
private fun TrackChoices(player: Player, type: Int, onDismiss: () -> Unit) {
    val choices = rememberPlayerTrackChoices(player, type)
    if (choices.isEmpty()) {
        Text(stringResource(R.string.player_track_none_available),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp))
    } else {
        choices.forEach { choice ->
            TextButton(onClick = { choice.select(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(choice.label, modifier = Modifier.weight(1f))
                if (choice.selected) Text("✓")
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
