package com.aiexile.animetrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.PendingDeletion
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    
    @Query("SELECT * FROM anime ORDER BY id DESC")
    fun getAllAnimes(): Flow<List<Anime>>

    @Query("SELECT * FROM anime ORDER BY id DESC")
    suspend fun getAllAnimesList(): List<Anime>

    /** 番剧总数（导出诊断快照用，避免为此拉全表） */
    @Query("SELECT COUNT(*) FROM anime")
    suspend fun countAnimes(): Int

    /** 置顶番剧数（导出诊断快照用） */
    @Query("SELECT COUNT(*) FROM anime WHERE isPinned = 1")
    suspend fun countPinnedAnimes(): Int

    /** 未参与过手动排序的条目数（manualOrder 为 null，即新条目；导出诊断快照用） */
    @Query("SELECT COUNT(*) FROM anime WHERE manualOrder IS NULL")
    suspend fun countUnorderedAnimes(): Int
    
    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun getAnimeById(id: Int): Anime?

    @Query("SELECT * FROM anime WHERE id = :id")
    fun observeAnimeById(id: Int): Flow<Anime?>
    
    @Query("SELECT * FROM anime WHERE status = :status ORDER BY id DESC")
    fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>>

    /** 按状态分组统计数量，供个人页追番统计栏使用 */
    @Query("SELECT status, COUNT(*) as count FROM anime GROUP BY status")
    fun getStatusCounts(): Flow<List<StatusCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnime(anime: Anime): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnimes(animes: List<Anime>)
    
    @Update
    suspend fun updateAnime(anime: Anime)
    
    @Delete
    suspend fun deleteAnime(anime: Anime)
    
    @Query("SELECT * FROM anime WHERE title = :title LIMIT 1")
    suspend fun getAnimeByTitle(title: String): Anime?

    @Query("SELECT * FROM anime WHERE bangumiId = :bangumiId LIMIT 1")
    suspend fun getAnimeByBangumiId(bangumiId: Int): Anime?

    @Query("SELECT * FROM anime WHERE tmdbId = :tmdbId LIMIT 1")
    suspend fun getAnimeByTmdbId(tmdbId: Int): Anime?

    @Query("SELECT * FROM anime WHERE remoteSyncId = :remoteSyncId LIMIT 1")
    suspend fun getAnimeByRemoteSyncId(remoteSyncId: String): Anime?

    @Query("UPDATE anime SET remoteSyncId = :remoteSyncId WHERE id = :id")
    suspend fun updateRemoteSyncId(id: Int, remoteSyncId: String)

    /**
     * 清洗历史脏数据：bangumiId<=0（B站导入经服务端同步曾产生 animeId='0'）
     * 不是有效 Bangumi ID，置空并改用稳定的 remoteSyncId 标识。
     */
    @Query("UPDATE anime SET bangumiId = NULL, remoteSyncId = :remoteSyncId WHERE id = :id")
    suspend fun clearDirtyBangumiId(id: Int, remoteSyncId: String)

    /** 标记「Bangumi 未关联」提示已展示过（同一部番剧仅提示一次） */
    @Query("UPDATE anime SET bangumiMatchHintShown = 1 WHERE id = :id")
    suspend fun markBangumiMatchHintShown(id: Int)
    
    @Query("SELECT * FROM anime WHERE coverUrl IS NULL OR coverUrl = ''")
    suspend fun getAnimesWithoutCover(): List<Anime>

    @Query("SELECT * FROM anime WHERE isFinished = 0 AND status IN ('WATCHING', 'PLANNED') ORDER BY airWeekday ASC, title ASC")
    fun getAiringAnimes(): Flow<List<Anime>>

    @Query("SELECT * FROM anime WHERE isFinished = 0 AND status IN ('WATCHING', 'PLANNED') ORDER BY airWeekday ASC, title ASC")
    suspend fun getAiringAnimesList(): List<Anime>

    @Query("SELECT * FROM anime WHERE totalEpisodes = 0 AND bangumiId IS NOT NULL")
    suspend fun getAiringAnimesWithBangumiId(): List<Anime>

    /** 组件后台同步的全量更新检查范围：全部连载中且关联 Bangumi 的番剧 */
    @Query("SELECT * FROM anime WHERE isFinished = 0 AND status IN ('WATCHING', 'PLANNED') AND bangumiId IS NOT NULL")
    suspend fun getAiringAnimesWithBangumiIdAll(): List<Anime>

    @Query("UPDATE anime SET hasNewUpdate = 0 WHERE id = :id")
    suspend fun clearNewUpdate(id: Int)

    @Query("UPDATE anime SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: Int, isPinned: Boolean)

    @Query("UPDATE anime SET manualOrder = :manualOrder WHERE id = :id")
    suspend fun updateManualOrder(id: Int, manualOrder: Int)

    /**
     * 批量写入手动排序位置（单事务，仅 anime 表失效一次触发列表重算）。
     * 纯本地字段，不走 Repository 的同步/统计副作用链。
     */
    @Transaction
    suspend fun updateManualOrders(orders: List<Pair<Int, Int>>) {
        orders.forEach { (id, manualOrder) -> updateManualOrder(id, manualOrder) }
    }

    @Query("UPDATE anime SET coverUrl = :coverUrl WHERE id = :id")
    suspend fun updateCoverUrl(id: Int, coverUrl: String?)

    @Query("DELETE FROM anime")
    suspend fun deleteAllAnimes()

    // ========== 待删除墓碑（服务端订阅删除补偿） ==========

    /** 记录待删除行键（ownerKey+animeId 唯一，重复删除同键幂等） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingDeletion(pending: PendingDeletion)

    @Query("SELECT * FROM pending_deletions WHERE ownerKey = :ownerKey")
    suspend fun getPendingDeletions(ownerKey: String): List<PendingDeletion>

    /** 删除确认成功后清除对应墓碑 */
    @Query("DELETE FROM pending_deletions WHERE ownerKey = :ownerKey AND animeId = :animeId")
    suspend fun clearPendingDeletion(ownerKey: String, animeId: String)

    /** 清理其他账号遗留的墓碑（换账号后无法安全执行，直接丢弃） */
    @Query("DELETE FROM pending_deletions WHERE ownerKey != :ownerKey")
    suspend fun clearPendingDeletionsForOtherOwners(ownerKey: String)

    /** 清理超过 TTL 的过期墓碑（长期补偿失败，放弃以防永久阻断该行合并） */
    @Query("DELETE FROM pending_deletions WHERE createdAt < :threshold")
    suspend fun clearExpiredPendingDeletions(threshold: Long)

    @Query("SELECT * FROM anime WHERE airWeekday = :weekday AND status IN ('WATCHING', 'PLANNED') AND isFinished = 0 ORDER BY title ASC")
    suspend fun getAiringAnimesByWeekday(weekday: Int): List<Anime>
}

/** 状态统计投影（[AnimeDao.getStatusCounts] 使用） */
data class StatusCount(
    val status: AnimeStatus,
    val count: Int
)
