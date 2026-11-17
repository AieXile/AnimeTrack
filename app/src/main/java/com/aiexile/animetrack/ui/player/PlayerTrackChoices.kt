package com.aiexile.animetrack.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.aiexile.animetrack.R

/** 详情弹层与全屏抽屉共用轨道选择逻辑，各自绘制自己的界面。 */
internal data class PlayerTrackChoice(val label: String, val selected: Boolean, val select: () -> Unit)

@Composable
internal fun rememberPlayerTrackChoices(player: Player, type: Int): List<PlayerTrackChoice> {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    var parameters by remember(player) { mutableStateOf(player.trackSelectionParameters) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(value: Tracks) { tracks = value }
            override fun onTrackSelectionParametersChanged(value: TrackSelectionParameters) { parameters = value }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val groups = tracks.groups.filter { it.type == type }
    val supported = groups.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it }
    }.let { choices ->
        if (type == C.TRACK_TYPE_VIDEO) choices.sortedWith(
            compareByDescending<Pair<Tracks.Group, Int>> { (group, index) -> group.getTrackFormat(index).height }
                .thenByDescending { (group, index) -> group.getTrackFormat(index).bitrate })
        else choices
    }
    val autoLabel = stringResource(R.string.player_quality_auto)
    val disabledLabel = stringResource(R.string.player_track_disable)
    val disabled = type in parameters.disabledTrackTypes
    return buildList {
        if (type == C.TRACK_TYPE_VIDEO) {
            add(PlayerTrackChoice(autoLabel, !disabled && groups.none {
                parameters.overrides.containsKey(it.mediaTrackGroup)
            }) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(type).setTrackTypeDisabled(type, false).build()
            })
        }
        if (type == C.TRACK_TYPE_TEXT && supported.isNotEmpty()) {
            add(PlayerTrackChoice(disabledLabel, disabled) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(type, true).build()
            })
        }
        supported.forEachIndexed { fallback, (group, index) ->
            val format = group.getTrackFormat(index)
            val label = if (type == C.TRACK_TYPE_VIDEO) buildList {
                format.label?.takeIf { it.isNotBlank() }?.let(::add)
                if (format.height > 0) add("${format.height}p")
                if (format.width > 0 && format.height > 0) add("${format.width} × ${format.height}")
                if (format.bitrate > 0) add("${format.bitrate / 1000} kbps")
            }.distinct().joinToString(" · ").ifBlank { "#${fallback + 1}" }
            else format.label?.takeIf { it.isNotBlank() }
                ?: format.language?.takeIf { it.isNotBlank() } ?: "#${fallback + 1}"
            val selected = !disabled && if (type == C.TRACK_TYPE_VIDEO)
                parameters.overrides[group.mediaTrackGroup]?.trackIndices?.contains(index) == true
            else group.isTrackSelected(index)
            add(PlayerTrackChoice(label, selected) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
            })
        }
    }
}
