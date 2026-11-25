package com.aiexile.animetrack.ui.favorites

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.favorites.FavoriteFolderRepository
import com.aiexile.animetrack.data.favorites.FavoriteFolderSummary
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aiexile.animetrack.data.log.AppLogManager
import java.io.ByteArrayOutputStream

class FavoritesViewModel(
    repository: AnimeRepository,
    private val folderRepository: FavoriteFolderRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val favorites: StateFlow<List<Anime>> = repository.getFavoriteAnimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val folders: StateFlow<List<FavoriteFolderSummary>?> = folderRepository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    internal val folderPinchGuideSeen: StateFlow<Boolean?> = settingsRepository.favoriteFolderPinchGuideSeen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    internal fun finishFolderPinchGuide() {
        viewModelScope.launch { settingsRepository.setFavoriteFolderPinchGuideSeen(true) }
    }

    val folderPreviews: StateFlow<Map<String, List<String>>> = folderRepository.previews
        .map { previews ->
            previews.mapNotNull { preview -> preview.coverUrl?.let { preview.folderId to it } }
                .groupBy({ it.first }, { it.second })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun observeCover(id: String) = folderRepository.observeCover(id)
    fun observeAnimes(id: String) = folderRepository.observeAnimes(id)
    fun observeFolderIdsForAnime(id: Int) = folderRepository.observeFolderIdsForAnime(id)

    fun createFolder(name: String, onError: () -> Unit) = write(onError) { folderRepository.create(name) }
    fun renameFolder(id: String, name: String, onError: () -> Unit) = write(onError) { folderRepository.rename(id, name) }
    fun deleteFolder(id: String, onError: () -> Unit, onDeleted: () -> Unit) = write(onError) {
        folderRepository.delete(id)
        onDeleted()
    }
    fun addToFolder(id: String, animeIds: Set<Int>, onError: () -> Unit) = write(onError) { folderRepository.addAnimes(id, animeIds) }
    fun addToFolders(animeId: Int, ids: Set<String>, onError: () -> Unit) = write(onError) { folderRepository.addToFolders(animeId, ids) }
    fun removeFromFolder(id: String, animeId: Int, onError: () -> Unit) = write(onError) { folderRepository.removeAnime(id, animeId) }
    fun changeCover(id: String, context: Context, uri: Uri, onError: () -> Unit) = write(onError) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(640)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: error("Cannot load folder cover")
        val cover = withContext(Dispatchers.Default) {
            val bitmap = result.drawable.toBitmap()
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 84, output))
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        }
        folderRepository.setCover(id, cover)
    }

    fun clearCover(id: String, onError: () -> Unit) = write(onError) { folderRepository.setCover(id, null) }
    fun reorderFolders(ids: List<String>, onError: () -> Unit) = write(onError) { folderRepository.reorderFolders(ids) }
    fun reorderAnimes(id: String, animeIds: List<Int>, onError: () -> Unit) = write(onError) { folderRepository.reorderAnimes(id, animeIds) }

    private fun write(onError: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogManager.e("Favorites", "收藏夹写入失败", e)
                onError()
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(
                AppContainer.getAnimeRepository(),
                AppContainer.getFavoriteFolderRepository(),
                AppContainer.getSettingsRepository()
            ) as T
        }
    }
}
