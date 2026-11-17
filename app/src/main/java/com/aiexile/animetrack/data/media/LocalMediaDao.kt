package com.aiexile.animetrack.data.media

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMediaDao {
    @Query("SELECT * FROM local_source ORDER BY updatedAt DESC")
    fun observeSources(): Flow<List<LocalMediaSourceEntity>>

    @Query("SELECT * FROM local_source WHERE id = :sourceId LIMIT 1")
    suspend fun getSource(sourceId: Long): LocalMediaSourceEntity?

    @Query("SELECT * FROM local_source WHERE treeUri = :treeUri LIMIT 1")
    suspend fun getSourceByTreeUri(treeUri: String): LocalMediaSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: LocalMediaSourceEntity): Long

    @Update
    suspend fun updateSource(source: LocalMediaSourceEntity)

    @Delete
    suspend fun deleteSource(source: LocalMediaSourceEntity)

    @Query("SELECT * FROM local_media WHERE sourceId = :sourceId ORDER BY relativePath COLLATE NOCASE")
    fun observeMedia(sourceId: Long): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM local_media WHERE sourceId = :sourceId ORDER BY relativePath COLLATE NOCASE")
    suspend fun getMedia(sourceId: Long): List<LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE id = :mediaId LIMIT 1")
    suspend fun getMediaById(mediaId: Long): LocalMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<LocalMediaEntity>)

    @Query("UPDATE local_media SET isMissing = 1, updatedAt = :updatedAt WHERE sourceId = :sourceId")
    suspend fun markAllMissing(sourceId: Long, updatedAt: Long)

    @Query(
        "UPDATE local_media SET isMissing = 0, updatedAt = :updatedAt " +
            "WHERE sourceId = :sourceId AND relativePath = :relativePath"
    )
    suspend fun markPresent(sourceId: Long, relativePath: String, updatedAt: Long)

    @Query(
        "UPDATE local_media SET matchedAnimeId = :animeId, confidence = :confidence, " +
            "matchState = :matchState, isUserOverride = :isUserOverride, updatedAt = :updatedAt " +
            "WHERE id = :mediaId"
    )
    suspend fun updateMatch(
        mediaId: Long,
        animeId: Int?,
        confidence: Float,
        matchState: String,
        isUserOverride: Boolean,
        updatedAt: Long
    )

    @Query("SELECT COUNT(*) FROM local_media WHERE sourceId = :sourceId AND isMissing = 0")
    suspend fun countPresentMedia(sourceId: Long): Int

    /** 同一集可能有多个版本，连播只取排序后的第一个。 */
    @Query(
        "SELECT * FROM local_media WHERE matchedAnimeId = :animeId AND episodeType != 'EXTRA' " +
            "AND episodeNumber IS NOT NULL ORDER BY seasonNumber, episodeNumber, id"
    )
    suspend fun getEpisodesForAnime(animeId: Int): List<LocalMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markEpisodeCompleted(progress: LocalEpisodeProgressEntity)

    @Query("SELECT episodeNumber FROM local_episode_progress WHERE animeId = :animeId")
    suspend fun getCompletedEpisodes(animeId: Int): List<Int>
}
