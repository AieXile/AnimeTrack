package com.aiexile.animetrack.ui.favorites

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun rememberFavoriteToggle(onChange: (Anime, Boolean) -> Unit): (Anime, Boolean) -> Unit {
    val change by rememberUpdatedState(onChange)
    val scope = rememberCoroutineScope()
    val onError = rememberFolderWriteError()
    var pending by remember { mutableStateOf<Pair<Anime, List<String>>?>(null) }

    pending?.let { (anime, names) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.common_unfavorite)) },
            text = {
                Text(stringResource(R.string.favorite_folder_unfavorite_message, anime.title, names.joinToString("\n", prefix = "\n")))
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    change(anime, false)
                }) { Text(stringResource(R.string.common_unfavorite)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    return { anime, checked ->
        if (checked) {
            change(anime, true)
        } else {
            scope.launch {
                try {
                    val names = AppContainer.getFavoriteFolderRepository().getFolderNamesForAnime(anime.id)
                    if (names.isEmpty()) change(anime, false) else pending = anime to names
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    onError()
                }
            }
        }
    }
}

@Composable
internal fun rememberFolderWriteError(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { Toast.makeText(context, R.string.favorite_folder_error, Toast.LENGTH_SHORT).show() }
    }
}
