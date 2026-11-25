package com.aiexile.animetrack.ui.favorites

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aiexile.animetrack.data.favorites.FavoriteFolderSummary
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.util.coverImageRequestForList

@Composable
internal fun FavoriteFolderCover(
    folder: FavoriteFolderSummary,
    viewModel: FavoritesViewModel,
    modifier: Modifier = Modifier
) {
    val cover = if (folder.hasCover) {
        val flow = remember(viewModel, folder.id) { viewModel.observeCover(folder.id) }
        val encoded by flow.collectAsStateWithLifecycle(initialValue = null)
        remember(encoded) { encoded?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() } }
    } else {
        null
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = rememberAppIconPainter(AppIcon.FOLDER),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
internal fun FavoriteFolderPreviewCover(
    coverUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(context, coverUrl) { coverImageRequestForList(context, coverUrl) }
    val placeholder = rememberAppIconPainter(AppIcon.IMAGE)
    AsyncImage(
        model = request,
        contentDescription = null,
        placeholder = placeholder,
        fallback = placeholder,
        error = placeholder,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}
