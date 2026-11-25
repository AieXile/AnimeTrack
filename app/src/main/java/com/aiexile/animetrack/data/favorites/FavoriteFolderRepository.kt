package com.aiexile.animetrack.data.favorites

import androidx.room.withTransaction
import com.aiexile.animetrack.data.AnimeDatabase
import java.util.UUID

class FavoriteFolderRepository(
    private val database: AnimeDatabase,
    private val notifyDataChanged: () -> Unit
) {
    private val dao = database.favoriteFolderDao()

    val folders = dao.observeFolders()
    val previews = dao.observePreviews()

    fun observeCover(id: String) = dao.observeCover(id)

    fun observeAnimes(id: String) = dao.observeAnimes(id)

    fun observeFolderIdsForAnime(animeId: Int) = dao.observeFolderIdsForAnime(animeId)

    suspend fun getFolderNamesForAnime(animeId: Int) = dao.getFolderNamesForAnime(animeId)

    suspend fun create(name: String) = write {
        dao.insertFolder(FavoriteFolder(UUID.randomUUID().toString(), name.trim(), dao.nextFolderPosition()))
    }

    suspend fun rename(id: String, name: String) = write { dao.renameFolder(id, name.trim()) }

    suspend fun setCover(id: String, cover: String?) = write { dao.setCover(id, cover) }

    suspend fun delete(id: String) = write { dao.deleteFolder(id) }

    suspend fun addAnimes(folderId: String, animeIds: Set<Int>) = write {
        animeIds.forEach { dao.addAnime(folderId, it) }
    }

    suspend fun addToFolders(animeId: Int, folderIds: Set<String>) = write {
        folderIds.forEach { dao.addAnime(it, animeId) }
    }

    suspend fun removeAnime(folderId: String, animeId: Int) = write { dao.removeAnime(folderId, animeId) }

    suspend fun reorderFolders(ids: List<String>) = write {
        val current = dao.getFolderIds()
        orderedIds(ids, current).forEachIndexed { index, id ->
            dao.setFolderPosition(id, index.toLong())
        }
    }

    suspend fun reorderAnimes(folderId: String, ids: List<Int>) = write {
        val current = dao.getAnimeIds(folderId)
        orderedIds(ids, current).forEachIndexed { index, id ->
            dao.setAnimePosition(folderId, id, index.toLong())
        }
    }

    private fun <T> orderedIds(requested: List<T>, current: List<T>): List<T> {
        val existing = current.toSet()
        return (requested.filter { it in existing } + current).distinct()
    }

    private suspend fun write(block: suspend () -> Unit) {
        database.withTransaction { block() }
        notifyDataChanged()
    }
}
