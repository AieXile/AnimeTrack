package com.aiexile.animetrack.ui.favorites

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import java.io.Serializable

@Composable
internal fun FavoriteFolderNameDialog(
    initialName: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initialName == null) R.string.favorite_folder_new else R.string.favorite_folder_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.favorite_folder_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) {
                Text(stringResource(if (initialName == null) R.string.common_add else R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

internal data class FavoriteFolderChoice<K : Serializable>(
    val id: K,
    val label: String,
    val alreadyAdded: Boolean = false
)

@Composable
internal fun <K : Serializable> FavoriteFolderSelectionDialog(
    @StringRes title: Int,
    @StringRes emptyMessage: Int,
    choices: List<FavoriteFolderChoice<K>>,
    onDismiss: () -> Unit,
    onAdd: (Set<K>) -> Unit,
    searchable: Boolean = false
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<K>()) }
    val available = remember(choices) { choices.filterNot { it.alreadyAdded }.map { it.id }.toSet() }
    val visible = remember(choices, query) {
        choices.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                if (searchable && choices.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.favorite_folder_search)) },
                        singleLine = true
                    )
                }
                when {
                    choices.isEmpty() -> Text(stringResource(emptyMessage))
                    visible.isEmpty() -> Text(
                        stringResource(R.string.favorite_folder_no_matches),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(visible, key = { it.id }) { choice ->
                            val checked = choice.alreadyAdded || choice.id in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        enabled = !choice.alreadyAdded,
                                        role = Role.Checkbox,
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        selected = ArrayList(if (it) selected + choice.id else selected - choice.id)
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = !choice.alreadyAdded)
                                Text(
                                    text = choice.label,
                                    modifier = Modifier.padding(start = 12.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(selected.intersect(available)) },
                enabled = selected.any { it in available }
            ) { Text(stringResource(R.string.common_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
