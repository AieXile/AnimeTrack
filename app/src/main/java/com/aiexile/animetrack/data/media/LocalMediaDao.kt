package com.aiexile.animetrack.data.media

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    @Query(
        "UPDATE local_source SET displayName = :displayName, permissionState = 'GRANTED', updatedAt = :updatedAt " +
            "WHERE id = :sourceId"
    )
    suspend fun refreshSourceGrant(sourceId: Long, displayName: String, updatedAt: Long)

    @Query(
        "UPDATE local_source SET scanState = 'SCANNING', scanError = NULL, updatedAt = :now " +
            "WHERE id = :sourceId"
    )
    suspend fun beginScan(sourceId: Long, now: Long): Int

    @Query(
        "UPDATE local_source SET scanState = :state, scanError = :error, " +
            "permissionState = CASE WHEN :permissionRevoked THEN 'REVOKED' ELSE permissionState END, " +
            "updatedAt = :now WHERE id = :sourceId AND scanState = 'SCANNING'"
    )
    suspend fun stopScan(sourceId: Long, state: String, error: String?, permissionRevoked: Boolean, now: Long)

    @Query(
        "UPDATE local_source SET scanState = 'COMPLETED', scanError = NULL, permissionState = 'GRANTED', " +
            "lastScanAt = :now, updatedAt = :now, videoCount = :videoCount " +
            "WHERE id = :sourceId AND scanState = 'SCANNING'"
    )
    suspend fun completeScan(sourceId: Long, videoCount: Int, now: Long): Int

    @Delete
    suspend fun deleteSource(source: LocalMediaSourceEntity)

    @Query("UPDATE local_source SET permissionState = 'REVOKED' WHERE id = :sourceId AND permissionState != 'REVOKED'")
    suspend fun markPermissionRevoked(sourceId: Long)

    @Query("SELECT * FROM local_media WHERE sourceId = :sourceId AND isMissing = 0 ORDER BY relativePath COLLATE NOCASE, id")
    fun pagePresentMedia(sourceId: Long): PagingSource<Int, LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE sourceId = :sourceId ORDER BY relativePath COLLATE NOCASE")
    suspend fun getMedia(sourceId: Long): List<LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE id = :mediaId LIMIT 1")
    suspend fun getMediaById(mediaId: Long): LocalMediaEntity?

    @Query("SELECT * FROM local_media WHERE sourceId = :sourceId")
    suspend fun getScanSnapshot(sourceId: Long): List<LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE id IN (:ids)")
    suspend fun getMediaByIds(ids: List<Long>): List<LocalMediaEntity>

    @Insert
    suspend fun insertMedia(items: List<LocalMediaEntity>)

    @Update
    suspend fun updateMedia(items: List<LocalMediaEntity>)

    /** 在同一个写事务里合并最新的用户覆盖，不能使用扫描开始时的旧值。 */
    @Transaction
    suspend fun commitScanBatch(items: List<LocalMediaEntity>) {
        val (newItems, existingItems) = items.partition { it.id == 0L }
        if (newItems.isNotEmpty()) insertMedia(newItems)
        if (existingItems.isEmpty()) return
        val latest = getMediaByIds(existingItems.map { it.id }).associateBy { it.id }
        updateMedia(existingItems.map { item ->
            val stored = checkNotNull(latest[item.id]) { "本地媒体已移除" }
            when {
                stored.isUserOverride -> item.copy(
                    matchState = stored.matchState,
                    matchedAnimeId = stored.matchedAnimeId,
                    confidence = stored.confidence,
                    isUserOverride = true
                )
                // 同一内容只恢复可见性或更新 URI，不改写其间产生的自动匹配。
                item.hasSameScanContent(stored) -> item.copy(
                    matchState = stored.matchState,
                    matchedAnimeId = stored.matchedAnimeId,
                    confidence = stored.confidence
                )
                else -> item
            }
        })
    }

    @Query("UPDATE local_media SET isMissing = 1, updatedAt = :now WHERE sourceId = :sourceId AND id IN (:ids) AND isMissing = 0")
    suspend fun markMissing(sourceId: Long, ids: List<Long>, now: Long)

    @Query("SELECT COUNT(*) FROM local_media WHERE sourceId = :sourceId AND isMissing = 1")
    suspend fun countMissingMedia(sourceId: Long): Int

    /** 只有读完来源才调用；分批更新以遵守 SQLite 参数限制，整体仍原子提交。 */
    @Transaction
    suspend fun finishScan(sourceId: Long, missingIds: List<Long>, videoCount: Int, now: Long): Int {
        for (ids in missingIds.chunked(500)) {
            currentCoroutineContext().ensureActive()
            markMissing(sourceId, ids, now)
        }
        val missingCount = countMissingMedia(sourceId)
        currentCoroutineContext().ensureActive()
        check(completeScan(sourceId, videoCount, now) == 1) { "本地来源已移除" }
        currentCoroutineContext().ensureActive()
        return missingCount
    }

    @Query(
        "SELECT * FROM local_media WHERE sourceId = :sourceId AND isMissing = 0 " +
            "AND isUserOverride = 0 AND matchState = 'UNMATCHED' AND parsedTitle IS NOT NULL " +
            "ORDER BY relativePath COLLATE NOCASE, id"
    )
    suspend fun getPendingMatches(sourceId: Long): List<LocalMediaEntity>

    @Query(
        "UPDATE local_media SET matchedAnimeId = :animeId, confidence = :confidence, " +
            "matchState = 'AUTO_MATCHED', updatedAt = :now " +
            "WHERE id = :mediaId AND isUserOverride = 0 AND isMissing = 0 AND matchState = 'UNMATCHED' " +
            "AND parsedTitle = :parsedTitle AND seasonNumber IS :seasonNumber"
    )
    suspend fun applyAutoMatch(
        mediaId: Long,
        parsedTitle: String,
        seasonNumber: Int?,
        animeId: Int,
        confidence: Float,
        now: Long
    )

    @Transaction
    suspend fun applyAutoMatches(items: List<LocalMediaAutoMatch>, now: Long) {
        for (item in items) {
            currentCoroutineContext().ensureActive()
            applyAutoMatch(item.mediaId, item.parsedTitle, item.seasonNumber, item.animeId, item.confidence, now)
        }
    }

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

    /**
     * 详情页播放入口使用的剧集列表：只取用户确认过关联、文件仍在的正片。
     * 未确认的自动匹配结果不进入详情页，避免播错番。
     */
    @Query(
        "SELECT * FROM local_media WHERE matchedAnimeId = :animeId AND matchState = 'CONFIRMED' " +
            "AND isMissing = 0 AND (episodeType IS NULL OR episodeType != 'EXTRA') " +
            "AND (seasonNumber IS NULL OR seasonNumber != 99) " +
            "ORDER BY COALESCE(seasonNumber, 1), COALESCE(episodeNumber, 9999), id"
    )
    fun observeConfirmedEpisodesForAnime(animeId: Int): Flow<List<LocalMediaEntity>>

    @Query("SELECT episodeNumber FROM local_episode_progress WHERE animeId = :animeId")
    fun observeCompletedEpisodeNumbers(animeId: Int): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markEpisodeCompleted(progress: LocalEpisodeProgressEntity)

    @Query("SELECT episodeNumber FROM local_episode_progress WHERE animeId = :animeId")
    suspend fun getCompletedEpisodes(animeId: Int): List<Int>
}

data class LocalMediaAutoMatch(
    val mediaId: Long,
    val parsedTitle: String,
    val seasonNumber: Int?,
    val animeId: Int,
    val confidence: Float
)
