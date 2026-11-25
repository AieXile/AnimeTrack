package com.aiexile.animetrack.ui.player.web

import com.aiexile.animetrack.ui.player.components.formatDuration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.domain.playback.WebSourceClassifier
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/** 网页历史独立展示观看记录与续播动作，避免入口页继续承担卡片布局。 */
@Composable
internal fun WebPlaybackHistoryCard(
    item: WebPlaybackHistoryItem,
    onContinue: () -> Unit,
    onEpisodes: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val entry = item.entry
    val progress = item.progress
    Card(shape = SquircleShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.seriesTitle ?: entry.title ?: entry.url,
                        style = MaterialTheme.typography.titleSmall, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    entry.episodeName?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onSave) {
                    Icon(rememberAppIconPainter(AppIcon.BOOKMARKS),
                        contentDescription = stringResource(R.string.media_library_add_web),
                        modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(rememberAppIconPainter(AppIcon.DELETE),
                        contentDescription = stringResource(R.string.common_delete),
                        modifier = Modifier.size(20.dp))
                }
            }
            Text(when {
                progress?.completed == true -> stringResource(R.string.web_watch_completed)
                progress != null && progress.durationMs > 0 -> stringResource(R.string.web_watch_position_total,
                    formatDuration(progress.positionMs), formatDuration(progress.durationMs))
                progress != null && progress.positionMs > 0 -> stringResource(R.string.web_watch_position,
                    formatDuration(progress.positionMs))
                else -> stringResource(R.string.web_watch_not_started)
            }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (progress != null && (progress.durationMs > 0 || progress.completed)) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            }
            item.nextEpisode?.let { next ->
                Text(stringResource(R.string.web_watch_continue_next, next.name),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onContinue, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (progress?.completed == true && item.nextEpisode == null)
                        R.string.web_watch_replay else R.string.web_watch_continue))
                }
                if (!WebSourceClassifier.isCandidate(entry.url)) {
                    OutlinedButton(onClick = onEpisodes, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.web_watch_view_episodes))
                    }
                }
            }
        }
    }
}
