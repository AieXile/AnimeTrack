package com.aiexile.animetrack.data.sync

import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.network.CollectionStatusBody
import com.aiexile.animetrack.data.network.EpisodeProgressBody
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.util.computeIsFinished
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BangumiSyncManager(
    private val authManager: AuthManager,
    private val repository: AnimeRepository
) {
    companion object {
        private const val TAG = "BangumiSync"
        private const val PUSH_THROTTLE_MS = 200L

        fun animeStatusToBangumiType(status: AnimeStatus): Int = when (status) {
            AnimeStatus.WATCHING -> 3
            AnimeStatus.COMPLETED -> 2
            AnimeStatus.PLANNED -> 1
            AnimeStatus.DROPPED -> 4
        }

        fun bangumiTypeToAnimeStatus(type: Int): AnimeStatus = when (type) {
            1 -> AnimeStatus.PLANNED
            2 -> AnimeStatus.COMPLETED
            3 -> AnimeStatus.WATCHING
            4 -> AnimeStatus.DROPPED
            else -> AnimeStatus.WATCHING
        }
    }

    private val pushMutex = Mutex()

    /**
     * 统一处理「登录校验 + IO 调度 + 互斥锁」的推送模板。
     * 未登录时直接跳过 block。
     */
    private suspend fun withAuthAndLock(block: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            if (!authManager.isLoggedIn.first()) return@withContext
            pushMutex.withLock {
                block()
            }
        }
    }

    /** 更新收藏状态（读取现有评分/评论后回写），失败静默 */
    private suspend fun updateCollectionStatus(bangumiId: Int, status: AnimeStatus) {
        val type = animeStatusToBangumiType(status)
        val existing = try {
            RetrofitClient.bangumiApi.getCollectionStatus(bangumiId)
        } catch (_: Exception) { null }

        val body = CollectionStatusBody(
            type = type,
            rate = existing?.rate ?: 0,
            comment = existing?.comment ?: "",
            isPrivate = existing?.let { it.type == 0 } ?: false
        )
        RetrofitClient.bangumiApi.updateCollectionStatus(
            subjectId = bangumiId,
            body = body
        )
        if (BuildConfig.DEBUG) Log.d(TAG, "Pushed status: bangumiId=$bangumiId type=$type")
    }

    suspend fun syncRemoteToLocal() = withContext(Dispatchers.IO) {
        val isLoggedIn = authManager.isLoggedIn.first()
        if (!isLoggedIn) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Not logged in, skip sync")
            return@withContext
        }

        try {
            var offset = 0
            val limit = 100
            var hasMore = true

            // 一次性加载本地番剧，避免每条远程数据都查一次数据库
            val localMap = repository.getAllAnimes().first()
                .filter { it.bangumiId != null }
                .associateBy { it.bangumiId!! }

            // 批量收集待插入/待更新项，避免逐条触发 insertAnime 副作用
            val toInsert = mutableListOf<Anime>()
            val toUpdate = mutableListOf<Anime>()

            while (hasMore) {
                val response = RetrofitClient.bangumiApi.getUserCollections(
                    subjectType = 2,
                    limit = limit,
                    offset = offset
                )

                for (item in response.data) {
                    val (insertAnime, updateAnime) = buildMergeAction(item, localMap[item.subjectId])
                    if (insertAnime != null) toInsert.add(insertAnime)
                    if (updateAnime != null) toUpdate.add(updateAnime)
                }

                hasMore = response.offset + response.data.size < response.total
                offset += limit
            }

            // 批量插入新增番剧（单事务 + 单次去抖 reassign + 逐条 syncSubscriptionToServer）
            if (toInsert.isNotEmpty()) {
                val ids = repository.batchInsertAnimes(toInsert)
                for ((anime, id) in toInsert.zip(ids)) {
                    if (id > 0) {
                        repository.downloadCoverAsync(
                            animeId = id.toInt(),
                            coverUrl = anime.coverUrl,
                            bangumiId = anime.bangumiId,
                            tmdbId = anime.tmdbId
                        )
                        if (BuildConfig.DEBUG) Log.d(TAG, "Inserted new anime from remote: ${anime.title} ep=${anime.watchedEpisodes}")
                    }
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Batch inserted ${toInsert.size} new animes from remote")
            }

            // 逐条更新已有番剧（每条 updateAnime 内部会触发去抖 reassign + 条件性 sync）
            for (anime in toUpdate) {
                repository.updateAnime(anime)
                if (BuildConfig.DEBUG) Log.d(TAG, "Merged anime: ${anime.title} -> watched=${anime.watchedEpisodes}")
            }
            if (toUpdate.isNotEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Merged ${toUpdate.size} existing animes from remote")
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "syncRemoteToLocal completed")
        } catch (e: Exception) {
            Log.e(TAG, "syncRemoteToLocal failed", e)
        }
    }

    /**
     * 全量推送：将本地所有已绑定 bangumiId 的番剧同步到 Bangumi 收藏（本地为准）。
     *
     * 流程：先一次性拉取远程动画收藏建立索引，再逐条比对本地：
     * - 远程缺失 → POST 建收藏（必要时附带进度）
     * - 远程落后 → PATCH 推进度
     * - 远程状态与本地不符 → POST 推状态
     *
     * 每次请求间隔 [PUSH_THROTTLE_MS] 以避免触发 Bangumi 限流。
     * @return Result.success(推送条数) 或 Result.failure
     */
    suspend fun syncLocalToRemote(): Result<Int> = withContext(Dispatchers.IO) {
        val isLoggedIn = authManager.isLoggedIn.first()
        if (!isLoggedIn) {
            return@withContext Result.failure(IllegalStateException("Not logged in"))
        }

        pushMutex.withLock {
            try {
                val remoteMap = fetchRemoteCollectionMap()
                val localAnimes = repository.getAllAnimes().first()
                    .filter { it.bangumiId != null }

                var pushed = 0
                for (anime in localAnimes) {
                    val bangumiId = anime.bangumiId!!
                    val remote = remoteMap[bangumiId]
                    val desiredType = animeStatusToBangumiType(anime.status)

                    var didPush = false
                    if (remote == null) {
                        // 远程无收藏 → 建收藏（POST 为 upsert）
                        pushCollectionStatus(bangumiId, anime.status, null)
                        didPush = true
                        if (anime.watchedEpisodes > 0) {
                            delay(PUSH_THROTTLE_MS)
                            pushEpisodeProgress(bangumiId, anime.watchedEpisodes)
                        }
                    } else {
                        if (remote.epStatus < anime.watchedEpisodes) {
                            delay(PUSH_THROTTLE_MS)
                            pushEpisodeProgress(bangumiId, anime.watchedEpisodes)
                            didPush = true
                        }
                        if (remote.type != desiredType) {
                            delay(PUSH_THROTTLE_MS)
                            pushCollectionStatus(bangumiId, anime.status, remote)
                            didPush = true
                        }
                    }
                    if (didPush) pushed++
                    delay(PUSH_THROTTLE_MS)
                }

                if (BuildConfig.DEBUG) Log.d(TAG, "syncLocalToRemote completed: pushed=$pushed")
                Result.success(pushed)
            } catch (e: Exception) {
                Log.e(TAG, "syncLocalToRemote failed", e)
                Result.failure(e)
            }
        }
    }

    /** 一次性拉取远程所有动画收藏并建立 subjectId → item 索引 */
    private suspend fun fetchRemoteCollectionMap(): Map<Int, com.aiexile.animetrack.data.network.BangumiCollectionItem> {
        val map = mutableMapOf<Int, com.aiexile.animetrack.data.network.BangumiCollectionItem>()
        var offset = 0
        val limit = 100
        var hasMore = true
        while (hasMore) {
            val response = RetrofitClient.bangumiApi.getUserCollections(
                subjectType = 2,
                limit = limit,
                offset = offset
            )
            for (item in response.data) {
                map[item.subjectId] = item
            }
            hasMore = response.offset + response.data.size < response.total
            offset += limit
        }
        return map
    }

    /** 直接 POST 收藏状态（避免每条都 GET 现有状态，全量推送时复用 remoteMap） */
    private suspend fun pushCollectionStatus(
        bangumiId: Int,
        status: AnimeStatus,
        existing: com.aiexile.animetrack.data.network.BangumiCollectionItem?
    ) {
        val type = animeStatusToBangumiType(status)
        val body = CollectionStatusBody(
            type = type,
            rate = existing?.rate ?: 0,
            comment = existing?.comment ?: "",
            isPrivate = existing?.let { it.type == 0 } ?: false
        )
        RetrofitClient.bangumiApi.updateCollectionStatus(
            subjectId = bangumiId,
            body = body
        )
        if (BuildConfig.DEBUG) Log.d(TAG, "Pushed status: bangumiId=$bangumiId type=$type")
    }

    private suspend fun pushEpisodeProgress(bangumiId: Int, episode: Int) {
        try {
            RetrofitClient.bangumiApi.updateEpisodeProgress(
                subjectId = bangumiId,
                body = EpisodeProgressBody(epStatus = episode)
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "Pushed progress: bangumiId=$bangumiId ep=$episode")
        } catch (e: Exception) {
            Log.e(TAG, "Push progress failed: bangumiId=$bangumiId", e)
        }
    }

    /**
     * 根据远程收藏条目与本地番剧构建合并动作（不写库）。
     * @return (insertAnime, updateAnime) 二元组，二者至多一个非空；均为 null 表示无需操作
     */
    private fun buildMergeAction(
        item: com.aiexile.animetrack.data.network.BangumiCollectionItem,
        localAnime: Anime?
    ): Pair<Anime?, Anime?> {
        val bangumiId = item.subjectId
        val remoteEps = item.epStatus
        val subject = item.subject

        if (localAnime == null) {
            val status = bangumiTypeToAnimeStatus(item.type)
            val newAnime = Anime(
                title = subject?.displayName ?: "Unknown",
                totalEpisodes = subject?.resolvedEps ?: 0,
                watchedEpisodes = remoteEps,
                status = status,
                rating = subject?.rating?.score?.toFloat(),
                notes = "",
                coverUrl = subject?.coverUrl,
                airDate = subject?.date,
                summary = subject?.summary,
                bangumiId = bangumiId,
                airWeekday = subject?.airWeekday,
                isFinished = computeIsFinished(subject?.date, subject?.resolvedEps ?: 0, status)
            )
            return Pair(newAnime, null)
        }

        val mergedWatched = maxOf(localAnime.watchedEpisodes, remoteEps)
        val remoteStatus = bangumiTypeToAnimeStatus(item.type)
        val needsUpdate = localAnime.watchedEpisodes != mergedWatched ||
                localAnime.status != remoteStatus

        if (needsUpdate) {
            val updatedAnime = localAnime.copy(
                watchedEpisodes = mergedWatched,
                status = remoteStatus
            )
            return Pair(null, updatedAnime)
        }
        return Pair(null, null)
    }

    suspend fun pushProgressToRemote(bangumiId: Int, newEpisode: Int) {
        withAuthAndLock {
            try {
                RetrofitClient.bangumiApi.updateEpisodeProgress(
                    subjectId = bangumiId,
                    body = EpisodeProgressBody(epStatus = newEpisode)
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "Pushed progress: bangumiId=$bangumiId ep=$newEpisode")
            } catch (e: Exception) {
                Log.e(TAG, "Push progress failed: bangumiId=$bangumiId", e)
            }
        }
    }

    suspend fun pushStatusToRemote(bangumiId: Int, status: AnimeStatus) {
        withAuthAndLock {
            try {
                updateCollectionStatus(bangumiId, status)
            } catch (e: Exception) {
                Log.e(TAG, "Push status failed: bangumiId=$bangumiId", e)
            }
        }
    }

    suspend fun pushProgressThenStatus(bangumiId: Int, newEpisode: Int, newStatus: AnimeStatus) {
        withAuthAndLock {
            try {
                RetrofitClient.bangumiApi.updateEpisodeProgress(
                    subjectId = bangumiId,
                    body = EpisodeProgressBody(epStatus = newEpisode)
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "Pushed progress: bangumiId=$bangumiId ep=$newEpisode")
            } catch (e: Exception) {
                Log.e(TAG, "Push progress failed: bangumiId=$bangumiId", e)
                return@withAuthAndLock
            }

            try {
                updateCollectionStatus(bangumiId, newStatus)
            } catch (e: Exception) {
                Log.e(TAG, "Push status failed: bangumiId=$bangumiId", e)
            }
        }
    }
}
