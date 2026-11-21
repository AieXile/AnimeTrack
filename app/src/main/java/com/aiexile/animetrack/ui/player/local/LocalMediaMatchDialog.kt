package com.aiexile.animetrack.ui.player.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.ui.components.AppOutlinedTextField

/** 同一对话框用于组级纠正和保留的逐文件覆盖。 */
@Composable
internal fun LocalMediaMatchDialog(
    target: LocalMediaMatchTarget,
    options: List<Pair<Int, String>>,
    search: LocalMatchSearch,
    onSearch: (String, Boolean) -> Unit,
    onLocal: (Int) -> Unit,
    onRemote: (SearchResult) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember(target) { mutableStateOf(target.query) }
    LaunchedEffect(target, query) { onSearch(query, target.movie) }
    val matches = remember(options, query) {
        options.filter { query.isBlank() || it.second.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (target.mediaId != null) R.string.local_media_match_title else R.string.local_media_match_group)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                Text(target.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                AppOutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text(stringResource(R.string.local_media_match_search_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(matches, key = { "local:${it.first}" }) { (id, title) ->
                        MatchRow(title, stringResource(R.string.local_media_match_tracking)) { onLocal(id) }
                    }
                    items(search.results, key = { "${it.source}:${it.sourceId}" }) { result ->
                        val provider = if (result.source == SearchSource.BANGUMI) "Bangumi" else "TMDB"
                        MatchRow(result.title, listOfNotNull(provider, result.airDate).joinToString(" · ")) { onRemote(result) }
                    }
                    if (search.failed) item {
                        Text(stringResource(R.string.local_media_match_search_failed), modifier = Modifier.padding(vertical = 12.dp))
                        TextButton(onClick = { onSearch(query, target.movie) }) { Text(stringResource(R.string.local_media_scan_again)) }
                    } else if (matches.isEmpty() && search.results.isEmpty() && search.searched) item {
                        Text(stringResource(R.string.local_media_match_none), modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        dismissButton = {
            if (target.mediaId != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.local_media_match_clear)) }
            }
        }
    )
}

@Composable
private fun MatchRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
