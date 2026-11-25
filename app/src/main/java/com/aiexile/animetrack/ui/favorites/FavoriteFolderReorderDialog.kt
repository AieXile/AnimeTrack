package com.aiexile.animetrack.ui.favorites

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.Serializable

@Composable
internal fun <K : Serializable> FavoriteFolderReorderDialog(
    @StringRes title: Int,
    labels: Map<K, String>,
    onDismiss: () -> Unit,
    onSave: (List<K>) -> Unit
) {
    var order by rememberSaveable { mutableStateOf(ArrayList(labels.keys)) }
    val ids = (order.filter { it in labels } + labels.keys).distinct()
    fun move(from: Int, to: Int): Boolean {
        if (from !in ids.indices || to !in ids.indices || from == to) return false
        order = ArrayList(ids).apply { add(to, removeAt(from)) }
        return true
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        move(from.index, to.index)
    }
    val up = stringResource(R.string.favorite_folder_up)
    val down = stringResource(R.string.favorite_folder_down)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(R.string.favorite_folder_drag_hint), style = MaterialTheme.typography.bodySmall)
                LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(ids, key = { _, id -> id }) { index, id ->
                        ReorderableItem(reorderState, key = id) { isDragging ->
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isDragging) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                trailingContent = {
                                    Icon(
                                        painter = rememberAppIconPainter(AppIcon.DRAG_HANDLE),
                                        contentDescription = stringResource(R.string.favorite_folder_drag),
                                        modifier = Modifier.draggableHandle().padding(12.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().semantics {
                                    customActions = buildList {
                                        if (index > 0) add(CustomAccessibilityAction(up) { move(index, index - 1) })
                                        if (index < ids.lastIndex) add(CustomAccessibilityAction(down) { move(index, index + 1) })
                                    }
                                }
                            ) {
                                Text(labels.getValue(id), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ids) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
