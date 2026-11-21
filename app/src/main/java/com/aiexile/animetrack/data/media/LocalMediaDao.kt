package com.aiexile.animetrack.data.media

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

// 所有媒体库查询使用同一有效关联条件：匹配状态正确，且番剧条目仍存在。
private const val MATCHED_ANIME_JOIN = "anime.id = media.matchedAnimeId AND media.matchState IN ('AUTO_MATCHED', 'CONFIRMED')"

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

    @Query(
        "SELECT * FROM (" +
            "SELECT media.sourceId, 0 AS animeId, archive.title, archive.coverUrl, COUNT(*) AS videoCount, " +
            "SUM(CASE WHEN media.matchState = 'AUTO_MATCHED' THEN 1 ELSE 0 END) AS pendingCount, " +
            "archive.`key` AS archiveKey, NULL AS reviewKey, 0 AS waiting, MAX(media.movie) AS movie " +
            "FROM local_media AS media JOIN local_media_archive AS archive ON archive.sourceId = media.sourceId " +
            "AND archive.`key` = media.archiveKey WHERE media.sourceId = :sourceId AND media.isMissing = 0 AND media.isHidden = 0 " +
            "GROUP BY media.sourceId, archive.`key` UNION ALL " +
            "SELECT media.sourceId, anime.id AS animeId, anime.title, anime.coverUrl, COUNT(*) AS videoCount, " +
            "SUM(CASE WHEN media.matchState = 'AUTO_MATCHED' THEN 1 ELSE 0 END) AS pendingCount, " +
            "NULL AS archiveKey, NULL AS reviewKey, 0 AS waiting, MAX(media.movie) AS movie " +
            "FROM local_media AS media JOIN anime ON " + MATCHED_ANIME_JOIN + " " +
            "WHERE media.sourceId = :sourceId AND media.archiveKey IS NULL AND media.isMissing = 0 AND media.isHidden = 0 " +
            "GROUP BY media.sourceId, anime.id, anime.title, anime.coverUrl UNION ALL " +
            "SELECT media.sourceId, 0 AS animeId, MIN(COALESCE(media.parsedTitle, media.fileName)) AS title, NULL AS coverUrl, " +
            "COUNT(*) AS videoCount, 0 AS pendingCount, NULL AS archiveKey, media.groupKey AS reviewKey, " +
            "MAX(CASE WHEN media.scrapeState = 'WAITING' THEN 1 ELSE 0 END) AS waiting, MAX(media.movie) AS movie " +
            "FROM local_media AS media LEFT JOIN anime ON " + MATCHED_ANIME_JOIN + " " +
            "WHERE media.sourceId = :sourceId AND media.isMissing = 0 AND media.isHidden = 0 AND media.archiveKey IS NULL " +
            "AND anime.id IS NULL AND media.groupKey IS NOT NULL AND media.scrapeState IN ('WAITING', 'REVIEW') " +
            "GROUP BY media.sourceId, media.groupKey) " +
            "ORDER BY reviewKey IS NOT NULL, title COLLATE NOCASE, archiveKey, reviewKey, animeId"
    )
    fun pageAnimeGroups(sourceId: Long): PagingSource<Int, LocalMediaAnimeGroup>

    // LEFT JOIN 的空侧正好是「已归类」的补集，包括清除关联和原番剧已被移除的文件。
    @Query(
        "SELECT media.* FROM local_media AS media LEFT JOIN anime ON " + MATCHED_ANIME_JOIN + " " +
            "WHERE media.sourceId = :sourceId AND media.isMissing = 0 AND media.isHidden = 0 AND anime.id IS NULL " +
            "AND media.archiveKey IS NULL AND (media.groupKey IS NULL OR media.scrapeState NOT IN ('WAITING', 'REVIEW')) " +
            "ORDER BY media.relativePath COLLATE NOCASE, media.id"
    )
    fun pageUnclassifiedMedia(sourceId: Long): PagingSource<Int, LocalMediaEntity>

    @Query(
        "SELECT media.* FROM local_media AS media JOIN anime ON " + MATCHED_ANIME_JOIN + " " +
            "WHERE media.sourceId = :sourceId AND media.archiveKey IS NULL AND media.isMissing = 0 AND media.isHidden = 0 AND anime.id = :animeId " +
            "ORDER BY CASE WHEN media.episodeType = 'EXTRA' OR media.seasonNumber = 99 THEN 1 ELSE 0 END, " +
            "COALESCE(media.seasonNumber, 1), media.episodeNumber IS NULL, media.episodeNumber, " +
            "media.episodeEndNumber, media.relativePath COLLATE NOCASE, media.id"
    )
    fun pageAnimeMedia(sourceId: Long, animeId: Int): PagingSource<Int, LocalMediaEntity>

    @Query(
        "SELECT * FROM local_media WHERE sourceId = :sourceId AND isMissing = 0 AND isHidden = 0 AND " +
            "((:archiveKey IS NOT NULL AND archiveKey = :archiveKey) OR " +
            "(:reviewKey IS NOT NULL AND archiveKey IS NULL AND groupKey = :reviewKey AND scrapeState IN ('WAITING', 'REVIEW'))) " +
            "ORDER BY CASE WHEN episodeType = 'EXTRA' THEN 1 ELSE 0 END, COALESCE(seasonNumber, 1), " +
            "COALESCE(episodeNumber, CAST(decimalEpisode AS REAL)) IS NULL, " +
            "COALESCE(episodeNumber, CAST(decimalEpisode AS REAL)), relativePath COLLATE NOCASE, id"
    )
    fun pageArchiveMedia(sourceId: Long, archiveKey: String?, reviewKey: String?): PagingSource<Int, LocalMediaEntity>

    @Query("SELECT * FROM local_media_archive WHERE sourceId = :sourceId AND `key` = :key LIMIT 1")
    suspend fun getArchive(sourceId: Long, key: String): LocalMediaArchiveEntity?

    @Query(
        "SELECT local_media_archive.* FROM local_media_archive JOIN local_media " +
            "ON local_media.sourceId = local_media_archive.sourceId AND local_media.archiveKey = local_media_archive.`key` " +
            "WHERE local_media.sourceId = :sourceId AND local_media.groupKey = :groupKey AND local_media.isMissing = 0 " +
            "AND local_media.isHidden = 0 AND local_media.scrapeState IN ('IDENTIFIED', 'ENRICH') " +
            "AND local_media.isUserOverride = 0 GROUP BY local_media_archive.`key`"
    )
    suspend fun findArchivesForEvidence(sourceId: Long, groupKey: String): List<LocalMediaArchiveEntity>

    @Query(
        "SELECT local_media_archive.* FROM local_media_archive JOIN local_media " +
            "ON local_media.sourceId = local_media_archive.sourceId AND local_media.archiveKey = local_media_archive.`key` " +
            "WHERE local_media.sourceId = :sourceId AND local_media.groupKey = :groupKey " +
            "AND local_media.groupOverride = 1 AND local_media.isMissing = 0 GROUP BY local_media_archive.`key`"
    )
    suspend fun findManualArchivesForEvidence(sourceId: Long, groupKey: String): List<LocalMediaArchiveEntity>

    @Upsert
    suspend fun putArchive(archive: LocalMediaArchiveEntity)

    @Query(
        "SELECT * FROM local_media WHERE sourceId = :sourceId AND isMissing = 0 AND isHidden = 0 AND isUserOverride = 0 " +
            "AND scrapeState IN ('WAITING', 'ENRICH') AND groupKey IS NOT NULL ORDER BY groupKey, id"
    )
    suspend fun getScrapeQueue(sourceId: Long): List<LocalMediaEntity>

    @Transaction
    suspend fun commitScrape(
        expected: List<LocalMediaEntity>,
        archive: LocalMediaArchiveEntity?,
        state: String,
        animeId: Int?,
        episodeMapping: Map<Long, Pair<Int, Int?>>,
        now: Long
    ) {
        val previous = expected.associateBy { it.id }
        val current = expected.map { it.id }.chunked(500).flatMap { getMediaByIds(it) }.filter { row ->
            !row.isHidden && !row.isMissing && !row.isUserOverride && previous[row.id]?.let { old ->
                old.hasSameScanContent(row) && old.scrapeState == row.scrapeState && old.archiveKey == row.archiveKey &&
                    old.updatedAt == row.updatedAt
            } == true
        }
        if (current.isEmpty()) return
        if (archive != null) {
            val old = getArchive(archive.sourceId, archive.key)
            putArchive(archive.copy(
                title = if (archive.coverUrl == null && old?.coverUrl != null) old.title else archive.title,
                coverUrl = archive.coverUrl ?: old?.coverUrl,
                bangumiId = archive.bangumiId ?: old?.bangumiId,
                tmdbId = archive.tmdbId ?: old?.tmdbId,
                year = archive.year ?: old?.year,
                summary = archive.summary ?: old?.summary,
                episodeCount = archive.episodeCount ?: old?.episodeCount
            ))
        }
        updateMedia(current.map { row ->
            val mapping = episodeMapping[row.id]
            row.copy(
                archiveKey = archive?.key ?: row.archiveKey,
                scrapeState = state,
                matchedAnimeId = if (archive != null) animeId else row.matchedAnimeId,
                matchState = if (archive != null) LocalMediaMatchState.AUTO_MATCHED else row.matchState,
                episodeNumber = mapping?.first ?: row.episodeNumber,
                episodeEndNumber = if (mapping != null) mapping.second else row.episodeEndNumber,
                updatedAt = now
            )
        })
    }

    @Query("SELECT * FROM local_media WHERE sourceId = :sourceId AND archiveKey = :archiveKey AND isMissing = 0 AND isHidden = 0 ORDER BY id")
    suspend fun getPlayableArchive(sourceId: Long, archiveKey: String): List<LocalMediaEntity>

    @Transaction
    suspend fun saveManualArchive(
        sourceId: Long,
        mediaId: Long?,
        archive: LocalMediaArchiveEntity?,
        animeId: Int?,
        now: Long,
        card: LocalMediaAnimeGroup? = null
    ) {
        val current = if (card != null) {
            check(card.sourceId == sourceId)
            getCardMedia(card)
        } else {
            listOfNotNull(getMediaById(requireNotNull(mediaId))?.takeIf { it.sourceId == sourceId && !it.isMissing && !it.isHidden })
        }
        check(current.isNotEmpty()) { "本地媒体已移除或已重新匹配" }
        if (archive != null) {
            val existing = getArchive(archive.sourceId, archive.key)
            putArchive(archive.copy(
                coverUrl = archive.coverUrl ?: existing?.coverUrl,
                bangumiId = archive.bangumiId ?: existing?.bangumiId,
                tmdbId = archive.tmdbId ?: existing?.tmdbId,
                year = archive.year ?: existing?.year,
                summary = archive.summary ?: existing?.summary,
                episodeCount = archive.episodeCount ?: existing?.episodeCount
            ))
        }
        updateMedia(current.map { row ->
            row.copy(
                archiveKey = archive?.key,
                scrapeState = if (archive == null) LocalMediaScrapeState.NONE else LocalMediaScrapeState.IDENTIFIED,
                matchedAnimeId = animeId,
                confidence = if (archive != null) 1f else 0f,
                matchState = LocalMediaMatchState.CONFIRMED,
                isUserOverride = true,
                groupOverride = mediaId == null && archive != null && row.groupKey != null,
                updatedAt = now
            )
        })
    }

    /** 按卡片身份读取整组，尤其不能按第一页选集或第一个目录截断。 */
    @Query(
        "SELECT media.* FROM local_media AS media LEFT JOIN anime ON " + MATCHED_ANIME_JOIN + " " +
            "WHERE media.sourceId = :sourceId AND media.isMissing = 0 AND media.isHidden = 0 AND (" +
            "(:archiveKey IS NOT NULL AND media.archiveKey = :archiveKey) OR " +
            "(media.archiveKey IS NULL AND :reviewKey IS NOT NULL AND anime.id IS NULL AND media.groupKey = :reviewKey " +
            "AND media.scrapeState IN ('WAITING', 'REVIEW')) OR " +
            "(media.archiveKey IS NULL AND :archiveKey IS NULL AND :reviewKey IS NULL AND anime.id = :animeId))"
    )
    suspend fun getCardMedia(sourceId: Long, archiveKey: String?, reviewKey: String?, animeId: Int): List<LocalMediaEntity>

    suspend fun getCardMedia(card: LocalMediaAnimeGroup): List<LocalMediaEntity> =
        getCardMedia(card.sourceId, card.archiveKey, card.reviewKey, card.animeId)

    @Transaction
    suspend fun confirmCardMatch(card: LocalMediaAnimeGroup, now: Long) {
        val pending = getCardMedia(card).filter { it.matchState == LocalMediaMatchState.AUTO_MATCHED }
        if (pending.isEmpty()) return
        updateMedia(pending.map { row ->
            row.copy(matchState = LocalMediaMatchState.CONFIRMED, confidence = 1f, isUserOverride = true,
                scrapeState = if (row.archiveKey != null) LocalMediaScrapeState.IDENTIFIED else LocalMediaScrapeState.NONE,
                groupOverride = row.archiveKey != null && row.groupKey != null, updatedAt = now)
        })
    }

    @Query(
        "SELECT * FROM local_media WHERE sourceId = :sourceId AND isHidden = 1 AND isMissing = 0 " +
            "ORDER BY relativePath COLLATE NOCASE, id"
    )
    fun pageHiddenMedia(sourceId: Long): PagingSource<Int, LocalMediaEntity>

    @Query("SELECT COUNT(*) FROM local_media WHERE sourceId = :sourceId AND isHidden = 1 AND isMissing = 0")
    fun observeHiddenCount(sourceId: Long): Flow<Int>

    @Query("UPDATE local_media SET isHidden = :hidden, updatedAt = :now WHERE sourceId = :sourceId AND id = :mediaId")
    suspend fun setHidden(sourceId: Long, mediaId: Long, hidden: Boolean, now: Long): Int

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
    suspend fun commitScanBatch(items: List<LocalMediaEntity>, reidentify: Boolean = false) {
        val (newItems, existingItems) = items.partition { it.id == 0L }
        if (newItems.isNotEmpty()) insertMedia(newItems)
        if (existingItems.isEmpty()) return
        val latest = getMediaByIds(existingItems.map { it.id }).associateBy { it.id }
        updateMedia(existingItems.map { item ->
            val stored = checkNotNull(latest[item.id]) { "本地媒体已移除" }
            val refreshed = item.copy(isHidden = stored.isHidden)
            when {
                stored.isUserOverride -> refreshed.copy(
                    matchState = stored.matchState,
                    matchedAnimeId = stored.matchedAnimeId,
                    archiveKey = stored.archiveKey,
                    groupKey = stored.groupKey.takeIf { stored.groupOverride } ?: refreshed.groupKey,
                    scrapeState = stored.scrapeState,
                    episodeNumber = stored.episodeNumber,
                    episodeEndNumber = stored.episodeEndNumber,
                    decimalEpisode = stored.decimalEpisode,
                    seasonNumber = stored.seasonNumber,
                    episodeType = stored.episodeType ?: refreshed.episodeType,
                    confidence = stored.confidence,
                    isUserOverride = true,
                    groupOverride = stored.groupOverride
                )
                // 解析器升级只重判证据有变化的条目；那些未变的归档结果无需重新联网。
                !(reidentify && stored.canRetryRecognition()) &&
                    refreshed.hasSameScanContent(stored, ignoreParserVersion = stored.parserVersion > 0) -> refreshed.copy(
                    matchState = stored.matchState,
                    matchedAnimeId = stored.matchedAnimeId,
                    archiveKey = stored.archiveKey,
                    scrapeState = stored.scrapeState,
                    episodeNumber = stored.episodeNumber,
                    episodeEndNumber = stored.episodeEndNumber,
                    confidence = stored.confidence
                )
                // 迁移只更新证据；旧媒体卡片在识别出新结果前仍可用。
                stored.parserVersion == 0 && stored.matchedAnimeId != null && refreshed.groupKey != null -> refreshed.copy(
                    matchState = stored.matchState,
                    matchedAnimeId = stored.matchedAnimeId
                )
                else -> refreshed
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
            "AND isHidden = 0 AND isUserOverride = 0 AND matchState = 'UNMATCHED' AND parsedTitle IS NOT NULL " +
            "ORDER BY relativePath COLLATE NOCASE, id"
    )
    suspend fun getPendingMatches(sourceId: Long): List<LocalMediaEntity>

    @Query(
        "UPDATE local_media SET matchedAnimeId = :animeId, confidence = :confidence, " +
            "matchState = 'AUTO_MATCHED', updatedAt = :now " +
            "WHERE id = :mediaId AND isHidden = 0 AND isUserOverride = 0 AND isMissing = 0 AND matchState = 'UNMATCHED' " +
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
            "matchState = :matchState, isUserOverride = :isUserOverride, updatedAt = :updatedAt, " +
            "scrapeState = CASE WHEN :animeId IS NULL OR archiveKey IS NULL THEN 'NONE' ELSE 'IDENTIFIED' END, " +
            "archiveKey = CASE WHEN :animeId IS NULL THEN NULL ELSE archiveKey END WHERE id = :mediaId"
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
        "SELECT * FROM local_media WHERE matchedAnimeId = :animeId AND isHidden = 0 AND episodeType != 'EXTRA' " +
            "AND episodeNumber IS NOT NULL ORDER BY seasonNumber, episodeNumber, id"
    )
    suspend fun getEpisodesForAnime(animeId: Int): List<LocalMediaEntity>

    /**
     * 详情页播放入口使用的剧集列表：只取用户确认过关联、文件仍在的正片。
     * 未确认的自动匹配结果不进入详情页，避免播错番。
     */
    @Query(
        "SELECT * FROM local_media WHERE matchedAnimeId = :animeId AND matchState = 'CONFIRMED' " +
            "AND isMissing = 0 AND isHidden = 0 AND (episodeType IS NULL OR episodeType != 'EXTRA') " +
            "AND (seasonNumber IS NULL OR seasonNumber NOT IN (0, 99)) AND decimalEpisode IS NULL " +
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

/** 媒体库按来源呈现的番剧摘要，不加载组内文件。 */
data class LocalMediaAnimeGroup(
    val sourceId: Long,
    val animeId: Int,
    val title: String,
    val coverUrl: String?,
    val videoCount: Int,
    val pendingCount: Int,
    val archiveKey: String? = null,
    val reviewKey: String? = null,
    val waiting: Boolean = false,
    val movie: Boolean = false
) {
    val hasMatch: Boolean get() = archiveKey != null || animeId > 0
    val displayKey: String get() = archiveKey?.let { "archive:$it" } ?: reviewKey?.let { "review:$it" } ?: "anime:$animeId"
}

data class LocalMediaAutoMatch(
    val mediaId: Long,
    val parsedTitle: String,
    val seasonNumber: Int?,
    val animeId: Int,
    val confidence: Float
)
