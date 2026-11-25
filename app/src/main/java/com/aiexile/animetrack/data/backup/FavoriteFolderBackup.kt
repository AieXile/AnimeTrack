package com.aiexile.animetrack.data.backup

import android.database.sqlite.SQLiteDatabase
import com.aiexile.animetrack.data.favorites.FavoriteFolder
import com.aiexile.animetrack.data.favorites.FavoriteFolderDao
import com.aiexile.animetrack.data.favorites.FavoriteFolderEntry
import com.aiexile.animetrack.model.Anime

internal data class AnimeBackup(
    val animes: List<Anime>,
    val folders: List<FavoriteFolder> = emptyList(),
    val folderEntries: List<FavoriteFolderEntry> = emptyList()
)

internal fun readFavoriteFolders(db: SQLiteDatabase, animes: List<Anime>): AnimeBackup {
    val hasFolders = db.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'favorite_folder'",
        null
    ).use { it.moveToFirst() }
    if (!hasFolders) return AnimeBackup(animes)

    val folders = db.rawQuery("SELECT id, name, position, cover FROM favorite_folder ORDER BY position, id", null)
        .use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(FavoriteFolder(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getString(3)))
                }
            }
        }
    val entries = db.rawQuery(
        "SELECT folderId, animeId, position FROM favorite_folder_entry ORDER BY folderId, position, animeId",
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(FavoriteFolderEntry(cursor.getString(0), cursor.getInt(1), cursor.getLong(2)))
            }
        }
    }
    return AnimeBackup(animes, folders, entries)
}

internal suspend fun restoreFavoriteFolders(
    backup: AnimeBackup,
    dao: FavoriteFolderDao,
    animeIds: Map<Int, Int>,
    overwrite: Boolean
) {
    val known = dao.getFolderIds().toMutableSet()
    val restoredIds = mutableSetOf<String>()
    var position = dao.nextFolderPosition()
    for (folder in backup.folders.sortedBy { it.position }) {
        if (known.add(folder.id)) {
            dao.insertFolder(if (overwrite) folder else folder.copy(position = position++))
        }
        restoredIds.add(folder.id)
    }
    for (entry in backup.folderEntries.sortedBy { it.position }) {
        val animeId = animeIds[entry.animeId] ?: continue
        if (entry.folderId in restoredIds) dao.addAnime(entry.folderId, animeId)
    }
}
