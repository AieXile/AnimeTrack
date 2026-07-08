package com.aiexile.animetrack.data.sync

import android.util.Log
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.network.BilibiliFollowItem
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.util.computeIsFinished
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BilibiliSyncManager(
    private val bilibiliAuthManager: BilibiliAuthManager,
    private val repository: AnimeRepository
) {
    companion object {
        private const val TAG = "BilibiliSync"

        fun bilibiliFollowStatusToAnimeStatus(followStatus: Int): AnimeStatus = when (followStatus) {
            2 -> AnimeStatus.WATCHING
            3 -> AnimeStatus.COMPLETED
            1 -> AnimeStatus.PLANNED
            4 -> AnimeStatus.DROPPED
            else -> AnimeStatus.WATCHING
        }

        fun parseProgressToWatchedEpisodes(progress: String): Pair<Int, String?> {
            if (progress.isBlank()) return Pair(0, null)

            val nonStandardIndicators = listOf(".", "OVA", "SP", "OAD", "特别", "MV", "CM", "PV", "ED", "OP", "NC", "番外")
            val hasNonStandard = nonStandardIndicators.any { progress.contains(it, ignoreCase = true) }

            if (hasNonStandard) {
                return Pair(0, progress)
            }

            val regex = Regex("\\d+")
            val match = regex.find(progress)
            return if (match != null) {
                val number = match.value.toIntOrNull() ?: 0
                Pair(number, null)
            } else {
                Pair(0, progress)
            }
        }
    }

    /**
     * 第一步：拉取B站追番列表（纯网络请求，不写数据库）
     * 返回所有追番条目
     */
    suspend fun fetchFollowList(): Result<List<BilibiliFollowItem>> = withContext(Dispatchers.IO) {
        try {
            val isLoggedIn = bilibiliAuthManager.isLoggedIn.first()
            if (!isLoggedIn) {
                return@withContext Result.failure(Exception("未登录Bilibili"))
            }

            var mid = bilibiliAuthManager.mid.first()
            if (mid == null || mid <= 0) {
                val navResp = RetrofitClient.bilibiliApi.getNavInfo()
                if (navResp.code == 0 && navResp.data != null && navResp.data.mid > 0) {
                    mid = navResp.data.mid
                    val sessData = bilibiliAuthManager.getCachedSessData() ?: ""
                    val biliJct = bilibiliAuthManager.getCachedBiliJct() ?: ""
                    bilibiliAuthManager.saveSession(
                        sessData, biliJct, mid,
                        avatar = navResp.data.face,
                        nickname = navResp.data.uname
                    )
                } else {
                    return@withContext Result.failure(Exception("无法获取用户mid"))
                }
            }

            val allItems = mutableListOf<BilibiliFollowItem>()
            var pn = 1
            val ps = 30
            var hasMore = true

            while (hasMore) {
                val response = RetrofitClient.bilibiliApi.getMyFollowBangumi(
                    vmid = mid,
                    type = 1,
                    pn = pn,
                    ps = ps
                )

                if (response.code != 0 || response.data?.list == null) {
                    Log.e(TAG, "获取追番列表失败: code=${response.code} msg=${response.message}")
                    break
                }

                allItems.addAll(response.data.list)
                val total = response.data.total
                hasMore = (pn * ps) < total
                pn++
            }

            Log.d(TAG, "fetchFollowList completed: ${allItems.size} items")
            Result.success(allItems)
        } catch (e: Exception) {
            Log.e(TAG, "fetchFollowList failed", e)
            Result.failure(e)
        }
    }

    /**
     * 第二步：将选中的追番条目写入数据库
     * @param items 用户选择要添加的条目列表
     * @return 成功写入的数量
     */
    suspend fun syncSelectedItems(items: List<BilibiliFollowItem>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var syncedCount = 0
            for (item in items) {
                mergeFollowItem(item)
                syncedCount++
            }

            Log.d(TAG, "syncSelectedItems completed: $syncedCount items")
            bilibiliAuthManager.saveLastSyncTime(System.currentTimeMillis())
            // 批量同步完成后，防抖触发后端订阅同步
            repository.triggerSyncSubscriptionsFromServerDebounced()
            Result.success(syncedCount)
        } catch (e: Exception) {
            Log.e(TAG, "syncSelectedItems failed", e)
            Result.failure(e)
        }
    }

    /**
     * 兼容旧接口：直接同步全部
     */
    suspend fun syncFollowListToDb(): Result<Int> {
        val fetchResult = fetchFollowList()
        if (fetchResult.isFailure) return Result.failure(fetchResult.exceptionOrNull()!!)
        return syncSelectedItems(fetchResult.getOrDefault(emptyList()))
    }

    /**
     * 自动同步：仅拉取并合并连载中和已完结还在看的番剧
     * 过滤条件：isFinish==0（连载中）或 isFinish==1 && followStatus==2（已完结在看）
     * @return 同步的条目数量
     */
    suspend fun fetchAndSyncFiltered(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val isLoggedIn = bilibiliAuthManager.isLoggedIn.first()
            if (!isLoggedIn) {
                return@withContext Result.failure(Exception("未登录Bilibili"))
            }

            val fetchResult = fetchFollowList()
            if (fetchResult.isFailure) return@withContext Result.failure(fetchResult.exceptionOrNull()!!)

            val allItems = fetchResult.getOrDefault(emptyList())
            val filteredItems = allItems.filter { item ->
                // 连载中
                item.isFinish == 0 ||
                // 已完结但在看
                (item.isFinish == 1 && item.followStatus == 2)
            }

            if (filteredItems.isEmpty()) {
                Log.d(TAG, "fetchAndSyncFiltered: no matching items")
                return@withContext Result.success(0)
            }

            val syncResult = syncSelectedItems(filteredItems)
            if (syncResult.isSuccess) {
                Log.d(TAG, "fetchAndSyncFiltered: synced ${syncResult.getOrNull()} items (filtered from ${allItems.size})")
            }
            syncResult
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndSyncFiltered failed", e)
            Result.failure(e)
        }
    }

    private suspend fun mergeFollowItem(item: BilibiliFollowItem) {
        val title = item.title
        val (watchedEps, progressRemarks) = parseProgressToWatchedEpisodes(item.progress)
        var status = bilibiliFollowStatusToAnimeStatus(item.followStatus)
        val isFinished = item.isFinish == 1

        // 一集没看过的，自动改为计划观看
        if (watchedEps <= 0 && status == AnimeStatus.WATCHING) {
            status = AnimeStatus.PLANNED
        }

        val totalEps = when {
            item.formalEpCount > 0 -> item.formalEpCount
            item.totalCount > 0 -> item.totalCount
            item.total > 0 -> item.total
            else -> 12
        }

        val summary = item.summary.ifBlank { item.evaluate }.ifBlank { null }
        val coverUrl = item.cover.ifBlank { null }
        val rating = item.rating?.let { if (it.score > 0f) it.score else null }
        val airDate = item.publish?.releaseDate?.ifBlank { null }
        val airWeekday = parseRenewalTimeToWeekday(item.renewalTime)

        val localAnime = repository.getAnimeByTitle(title)

        if (localAnime == null) {
            val newAnime = Anime(
                title = title,
                totalEpisodes = totalEps,
                watchedEpisodes = watchedEps,
                status = status,
                rating = rating,
                notes = "",
                coverUrl = coverUrl,
                summary = summary,
                bangumiId = null,
                airDate = airDate,
                airWeekday = airWeekday,
                isFinished = isFinished || computeIsFinished(null, totalEps, status),
                syncRemarks = progressRemarks
            )
            val id = repository.insertAnime(newAnime)
            if (coverUrl != null) {
                repository.downloadCoverAsync(
                    animeId = id.toInt(),
                    coverUrl = coverUrl,
                    bangumiId = null,
                    tmdbId = null
                )
            }
            Log.d(TAG, "Inserted from Bilibili: $title ep=$watchedEps total=$totalEps")
        } else {
            val mergedWatched = maxOf(localAnime.watchedEpisodes, watchedEps)
            val needsUpdate = localAnime.watchedEpisodes != mergedWatched
                    || localAnime.status != status
                    || (localAnime.totalEpisodes != totalEps && localAnime.totalEpisodes == 12 && totalEps != 12)
                    || (localAnime.isFinished != isFinished && !localAnime.isFinished)
                    || (localAnime.airDate == null && airDate != null)
                    || (localAnime.airWeekday == null && airWeekday != null)
                    || (localAnime.summary.isNullOrBlank() && !summary.isNullOrBlank())
                    || (localAnime.rating == null && rating != null)

            if (needsUpdate) {
                val updatedAnime = localAnime.copy(
                    watchedEpisodes = mergedWatched,
                    status = status,
                    isFinished = isFinished || localAnime.isFinished,
                    totalEpisodes = if (localAnime.totalEpisodes == 12 && totalEps != 12) totalEps else localAnime.totalEpisodes,
                    airDate = localAnime.airDate ?: airDate,
                    airWeekday = localAnime.airWeekday ?: airWeekday,
                    summary = if (localAnime.summary.isNullOrBlank()) summary else localAnime.summary,
                    rating = localAnime.rating ?: rating,
                    syncRemarks = progressRemarks ?: localAnime.syncRemarks
                )
                repository.updateAnime(updatedAnime)
                Log.d(TAG, "Merged from Bilibili: $title local=${localAnime.watchedEpisodes} remote=$watchedEps -> $mergedWatched")
            }
        }
    }

    private fun parseRenewalTimeToWeekday(renewalTime: String): Int? {
        if (renewalTime.isBlank()) return null
        return when {
            renewalTime.contains("周一") -> 1
            renewalTime.contains("周二") -> 2
            renewalTime.contains("周三") -> 3
            renewalTime.contains("周四") -> 4
            renewalTime.contains("周五") -> 5
            renewalTime.contains("周六") -> 6
            renewalTime.contains("周日") || renewalTime.contains("星期日") -> 7
            else -> null
        }
    }
}
