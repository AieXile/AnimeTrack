package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.WebMediaSource

/** 清晰度有两种来源：网站声明的独立地址，以及媒体流的自适应视频轨道。 */
@Composable
internal fun ColumnScope.PlayerQualityContent(
    player: Player,
    sources: List<WebMediaSource>,
    selectedSource: Int,
    onSelectSource: (Int) -> Unit,
    requestClose: () -> Unit
) {
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
    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val manual = groups.any { parameters.overrides.containsKey(it.mediaTrackGroup) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        if (sources.size > 1) {
            Text(stringResource(R.string.player_quality_website), color = Color.White.copy(alpha = .6f),
                fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            sources.forEachIndexed { index, source ->
                TrackPanelItem(source.label ?: stringResource(R.string.player_video_source, index + 1),
                    index == selectedSource, {
                        onSelectSource(index)
                        requestClose()
                    })
            }
            HorizontalDivider(color = Color.White.copy(alpha = .15f))
        }
        Text(stringResource(R.string.player_quality_stream), color = Color.White.copy(alpha = .6f),
            fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        TrackPanelItem(stringResource(R.string.player_quality_auto), !manual, {
            player.trackSelectionParameters = parameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
            requestClose()
        })
        val supported = groups.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map { group to it }
        }.sortedWith(compareByDescending<Pair<Tracks.Group, Int>> { (group, index) ->
            group.getTrackFormat(index).height
        }.thenByDescending { (group, index) -> group.getTrackFormat(index).bitrate })
        supported.forEachIndexed { fallbackIndex, (group, index) ->
            val format = group.getTrackFormat(index)
            val label = buildList {
                format.label?.takeIf { it.isNotBlank() }?.let(::add)
                if (format.height > 0) add("${format.height}p")
                if (format.width > 0 && format.height > 0) add("${format.width} × ${format.height}")
                if (format.bitrate > 0) add("${format.bitrate / 1000} kbps")
            }.distinct().joinToString(" · ").ifBlank { "#${fallbackIndex + 1}" }
            val override = parameters.overrides[group.mediaTrackGroup]
            TrackPanelItem(label, override?.trackIndices?.contains(index) == true, {
                player.trackSelectionParameters = parameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                    .build()
                requestClose()
            })
        }
        if (supported.size <= 1) {
            Text(stringResource(R.string.player_quality_single), color = Color.White.copy(alpha = .6f),
                fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}
