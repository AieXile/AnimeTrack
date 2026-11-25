package com.aiexile.animetrack.ui.favorites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.favorites.FavoriteFolderSummary
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

internal enum class FavoriteFolderDialog { ADD, RENAME, REORDER, DELETE }

@Composable
internal fun FavoriteFolderManagementActions(
    folder: FavoriteFolderSummary,
    viewModel: FavoritesViewModel,
    onError: () -> Unit,
    onDialog: (FavoriteFolderDialog) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tint = if (folder.hasCover) Color.White else MaterialTheme.colorScheme.onTertiaryContainer
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.changeCover(folder.id, context.applicationContext, uri, onError)
    }

    IconButton(onClick = { onDialog(FavoriteFolderDialog.ADD) }) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.ADD),
            contentDescription = stringResource(R.string.favorite_folder_add),
            tint = tint
        )
    }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.MORE_VERT),
                contentDescription = stringResource(R.string.favorite_folder_manage),
                tint = tint
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = SquircleShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_folder_rename)) },
                onClick = {
                    menuOpen = false
                    onDialog(FavoriteFolderDialog.RENAME)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_folder_change_cover)) },
                onClick = {
                    menuOpen = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            if (folder.hasCover) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.favorite_folder_clear_cover)) },
                    onClick = {
                        menuOpen = false
                        viewModel.clearCover(folder.id, onError)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_folder_reorder_anime)) },
                enabled = folder.animeCount > 1,
                onClick = {
                    menuOpen = false
                    onDialog(FavoriteFolderDialog.REORDER)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.favorite_folder_delete)) },
                onClick = {
                    menuOpen = false
                    onDialog(FavoriteFolderDialog.DELETE)
                }
            )
        }
    }
}

@Composable
internal fun FavoriteFolderManagementDialogs(
    folder: FavoriteFolderSummary,
    animes: List<Anime>,
    favorites: List<Anime>,
    viewModel: FavoritesViewModel,
    dialog: FavoriteFolderDialog?,
    removingId: Int?,
    onDismiss: () -> Unit,
    onDismissRemoval: () -> Unit,
    onDeleted: () -> Unit,
    onError: () -> Unit
) {
    val folderId = folder.id
    when (dialog) {
        FavoriteFolderDialog.ADD -> {
            val included = remember(animes) { animes.map { it.id }.toSet() }
            FavoriteFolderSelectionDialog(
                title = R.string.favorite_folder_add,
                emptyMessage = R.string.favorite_folder_no_available,
                choices = favorites.filterNot { it.id in included }.map { FavoriteFolderChoice(it.id, it.title) },
                onDismiss = onDismiss,
                onAdd = {
                    viewModel.addToFolder(folderId, it, onError)
                    onDismiss()
                },
                searchable = true
            )
        }
        FavoriteFolderDialog.RENAME -> FavoriteFolderNameDialog(folder.name, onDismiss) {
            viewModel.renameFolder(folderId, it, onError)
            onDismiss()
        }
        FavoriteFolderDialog.REORDER -> FavoriteFolderReorderDialog(
            title = R.string.favorite_folder_reorder_anime,
            labels = animes.associate { it.id to it.title },
            onDismiss = onDismiss,
            onSave = {
                viewModel.reorderAnimes(folderId, it, onError)
                onDismiss()
            }
        )
        FavoriteFolderDialog.DELETE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.favorite_folder_delete)) },
            text = { Text(stringResource(R.string.favorite_folder_delete_message, folder.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    viewModel.deleteFolder(folderId, onError, onDeleted)
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
        )
        null -> Unit
    }

    animes.firstOrNull { it.id == removingId }?.let { anime ->
        AlertDialog(
            onDismissRequest = onDismissRemoval,
            title = { Text(stringResource(R.string.favorite_folder_remove)) },
            text = { Text(stringResource(R.string.favorite_folder_remove_message, anime.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromFolder(folderId, anime.id, onError)
                    onDismissRemoval()
                }) { Text(stringResource(R.string.favorite_folder_remove)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissRemoval) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
