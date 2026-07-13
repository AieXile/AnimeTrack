package com.aiexile.animetrack.data.sync

import android.util.Log
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.network.CollectionStatusBody
import com.aiexile.animetrack.data.network.EpisodeProgressBody
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.util.computeIsFinished
import kotlinx.coroutines.Dispatchers
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
        Log.d(TAG, "Pushed status: bangumiId=$bangumiId type=$type")
    }

    suspend fun syncRemoteToLocal() = withContext(Dispatchers.IO) {
        val isLoggedIn = authManager.isLoggedIn.first()
        if (!isLoggedIn) {
            Log.d(TAG, "Not logged in, skip sync")
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

            while (hasMore) {
                val response = RetrofitClient.bangumiApi.getUserCollections(
                    type = 3,
                    limit = limit,
                    offset = offset
                )

                for (item in response.data) {
                    mergeCollectionItem(item, localMap[item.subjectId])
                }

                hasMore = response.offset + response.data.size < response.total
                offset += limit
            }

            Log.d(TAG, "syncRemoteToLocal completed")
        } catch (e: Exception) {
            Log.e(TAG, "syncRemoteToLocal failed", e)
        }
    }

    private suspend fun mergeCollectionItem(
        item: com.aiexile.animetrack.data.network.BangumiCollectionItem,
        localAnime: Anime?
    ) {
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
            val id = repository.insertAnime(newAnime)
            repository.downloadCoverAsync(
                animeId = id.toInt(),
                coverUrl = newAnime.coverUrl,
                bangumiId = newAnime.bangumiId,
                tmdbId = newAnime.tmdbId
            )
            Log.d(TAG, "Inserted new anime from remote: ${newAnime.title} ep=$remoteEps")
        } else {
            val mergedWatched = maxOf(localAnime.watchedEpisodes, remoteEps)
            val remoteStatus = bangumiTypeToAnimeStatus(item.type)

            val needsUpdate = localAnime.watchedEpisodes != mergedWatched ||
                    localAnime.status != remoteStatus

            if (needsUpdate) {
                val updatedAnime = localAnime.copy(
                    watchedEpisodes = mergedWatched,
                    status = remoteStatus
                )
                repository.updateAnime(updatedAnime)
                Log.d(TAG, "Merged anime: ${localAnime.title} local=${localAnime.watchedEpisodes} remote=$remoteEps -> $mergedWatched")
            }
        }
    }

    suspend fun pushProgressToRemote(bangumiId: Int, newEpisode: Int) {
        withAuthAndLock {
            try {
                RetrofitClient.bangumiApi.updateEpisodeProgress(
                    subjectId = bangumiId,
                    body = EpisodeProgressBody(epStatus = newEpisode)
                )
                Log.d(TAG, "Pushed progress: bangumiId=$bangumiId ep=$newEpisode")
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
                Log.d(TAG, "Pushed progress: bangumiId=$bangumiId ep=$newEpisode")
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
