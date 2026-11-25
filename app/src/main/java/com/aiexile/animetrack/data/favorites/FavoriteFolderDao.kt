package com.aiexile.animetrack.data.favorites

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FavoriteFolderDao {
    @Query("""
        SELECT f.id, f.name, f.cover IS NOT NULL AS hasCover, COUNT(e.animeId) AS animeCount
        FROM favorite_folder f LEFT JOIN favorite_folder_entry e ON f.id = e.folderId
        GROUP BY f.id ORDER BY f.position, f.id
    """)
    abstract fun observeFolders(): Flow<List<FavoriteFolderSummary>>

    // 以收藏夹为外层循环，每个收藏夹只选一次前三张封面。
    @Query("""
        SELECT f.id AS folderId, a.coverUrl
        FROM favorite_folder f
        CROSS JOIN favorite_folder_entry e ON e.folderId = f.id AND e.animeId IN (
            SELECT p.animeId FROM favorite_folder_entry p JOIN anime preview ON preview.id = p.animeId
            WHERE p.folderId = f.id AND TRIM(preview.coverUrl) <> ''
            ORDER BY p.position, p.animeId LIMIT 3
        )
        JOIN anime a ON a.id = e.animeId
        ORDER BY f.id, e.position, e.animeId
    """)
    abstract fun observePreviews(): Flow<List<FavoriteFolderPreview>>

    @Query("SELECT * FROM favorite_folder ORDER BY position, id")
    abstract suspend fun getFolders(): List<FavoriteFolder>

    @Query("SELECT id FROM favorite_folder ORDER BY position, id")
    abstract suspend fun getFolderIds(): List<String>

    @Query("SELECT cover FROM favorite_folder WHERE id = :id")
    abstract fun observeCover(id: String): Flow<String?>

    @Query("""
        SELECT anime.* FROM favorite_folder_entry e JOIN anime ON anime.id = e.animeId
        WHERE e.folderId = :folderId ORDER BY e.position, e.animeId
    """)
    abstract fun observeAnimes(folderId: String): Flow<List<Anime>>

    @Query("SELECT * FROM favorite_folder_entry ORDER BY folderId, position, animeId")
    abstract suspend fun getEntries(): List<FavoriteFolderEntry>

    @Query("SELECT animeId FROM favorite_folder_entry WHERE folderId = :folderId ORDER BY position, animeId")
    abstract suspend fun getAnimeIds(folderId: String): List<Int>

    @Query("SELECT folderId FROM favorite_folder_entry WHERE animeId = :animeId")
    abstract fun observeFolderIdsForAnime(animeId: Int): Flow<List<String>>

    @Query("""
        SELECT f.name FROM favorite_folder f JOIN favorite_folder_entry e ON f.id = e.folderId
        WHERE e.animeId = :animeId ORDER BY f.position, f.id
    """)
    abstract suspend fun getFolderNamesForAnime(animeId: Int): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM favorite_folder")
    abstract suspend fun nextFolderPosition(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFolder(folder: FavoriteFolder): Long

    @Query("UPDATE favorite_folder SET name = :name WHERE id = :id")
    abstract suspend fun renameFolder(id: String, name: String)

    @Query("UPDATE favorite_folder SET cover = :cover WHERE id = :id")
    abstract suspend fun setCover(id: String, cover: String?)

    @Query("UPDATE favorite_folder SET position = :position WHERE id = :id")
    abstract suspend fun setFolderPosition(id: String, position: Long)

    @Query("""
        INSERT OR IGNORE INTO favorite_folder_entry (folderId, animeId, position)
        SELECT f.id, a.id, (
            SELECT COALESCE(MAX(position), -1) + 1 FROM favorite_folder_entry WHERE folderId = :folderId
        )
        FROM favorite_folder f, anime a WHERE f.id = :folderId AND a.id = :animeId AND a.isFavorite = 1
    """)
    abstract suspend fun addAnime(folderId: String, animeId: Int)

    @Query("UPDATE favorite_folder_entry SET position = :position WHERE folderId = :folderId AND animeId = :animeId")
    abstract suspend fun setAnimePosition(folderId: String, animeId: Int, position: Long)

    @Query("DELETE FROM favorite_folder_entry WHERE folderId = :folderId AND animeId = :animeId")
    abstract suspend fun removeAnime(folderId: String, animeId: Int)

    @Query("DELETE FROM favorite_folder_entry WHERE animeId = :animeId")
    abstract suspend fun removeAnimeFromFolders(animeId: Int)

    @Query("DELETE FROM favorite_folder WHERE id = :id")
    abstract suspend fun deleteFolder(id: String)

    @Query("DELETE FROM favorite_folder")
    abstract suspend fun deleteFolders()
}
