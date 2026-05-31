package com.aiexile.animetrack.data.sync

import android.util.Log
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.network.CollectionStatusBody
import com.aiexile.animetrack.data.network.EpisodeProgressBody
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
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

            while (hasMore) {
                val response = RetrofitClient.bangumiApi.getUserCollections(
                    type = 3,
                    limit = limit,
                    offset = offset
                )

                for (item in response.data) {
                    mergeCollectionItem(item)
                }

                hasMore = response.offset + response.data.size < response.total
                offset += limit
            }

            Log.d(TAG, "syncRemoteToLocal completed")
        } catch (e: Exception) {
            Log.e(TAG, "syncRemoteToLocal failed", e)
        }
    }

    private suspend fun mergeCollectionItem(item: com.aiexile.animetrack.data.network.BangumiCollectionItem) {
        val bangumiId = item.subjectId
        val remoteEps = item.epStatus
        val subject = item.subject

        val localAnime = repository.getAnimeByBangumiId(bangumiId)

        if (localAnime == null) {
            val newAnime = Anime(
                title = subject?.displayName ?: "Unknown",
                totalEpisodes = subject?.resolvedEps ?: 0,
                watchedEpisodes = remoteEps,
                status = bangumiTypeToAnimeStatus(item.type),
                rating = subject?.rating?.score?.toFloat(),
                notes = "",
                coverUrl = subject?.coverUrl,
                airDate = subject?.date,
                summary = subject?.summary,
                bangumiId = bangumiId,
                airWeekday = subject?.airWeekday
            )
            repository.insertAnime(newAnime)
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
        withContext(Dispatchers.IO) {
            val isLoggedIn = authManager.isLoggedIn.first()
            if (!isLoggedIn) return@withContext

            pushMutex.withLock {
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
    }

    suspend fun pushStatusToRemote(bangumiId: Int, status: AnimeStatus) {
        withContext(Dispatchers.IO) {
            val isLoggedIn = authManager.isLoggedIn.first()
            if (!isLoggedIn) return@withContext

            pushMutex.withLock {
                try {
                    val type = animeStatusToBangumiType(status)
                    val existing = try {
                        RetrofitClient.bangumiApi.getCollectionStatus(bangumiId)
                    } catch (_: Exception) { null }

                    val body = CollectionStatusBody(
                        type = type,
                        rate = existing?.rate ?: 0,
                        comment = existing?.comment ?: "",
                        isPrivate = existing?.let {
                            it.type == 0
                        } ?: false
                    )
                    RetrofitClient.bangumiApi.updateCollectionStatus(
                        subjectId = bangumiId,
                        body = body
                    )
                    Log.d(TAG, "Pushed status: bangumiId=$bangumiId type=$type")
                } catch (e: Exception) {
                    Log.e(TAG, "Push status failed: bangumiId=$bangumiId", e)
                }
            }
        }
    }

    suspend fun pushProgressThenStatus(bangumiId: Int, newEpisode: Int, newStatus: AnimeStatus) {
        withContext(Dispatchers.IO) {
            val isLoggedIn = authManager.isLoggedIn.first()
            if (!isLoggedIn) return@withContext

            pushMutex.withLock {
                try {
                    RetrofitClient.bangumiApi.updateEpisodeProgress(
                        subjectId = bangumiId,
                        body = EpisodeProgressBody(epStatus = newEpisode)
                    )
                    Log.d(TAG, "Pushed progress: bangumiId=$bangumiId ep=$newEpisode")
                } catch (e: Exception) {
                    Log.e(TAG, "Push progress failed: bangumiId=$bangumiId", e)
                    return@withContext
                }

                try {
                    val type = animeStatusToBangumiType(newStatus)
                    val existing = try {
                        RetrofitClient.bangumiApi.getCollectionStatus(bangumiId)
                    } catch (_: Exception) { null }

                    val body = CollectionStatusBody(
                        type = type,
                        rate = existing?.rate ?: 0,
                        comment = existing?.comment ?: "",
                        isPrivate = existing?.let {
                            it.type == 0
                        } ?: false
                    )
                    RetrofitClient.bangumiApi.updateCollectionStatus(
                        subjectId = bangumiId,
                        body = body
                    )
                    Log.d(TAG, "Pushed status: bangumiId=$bangumiId type=$type")
                } catch (e: Exception) {
                    Log.e(TAG, "Push status failed: bangumiId=$bangumiId", e)
                }
            }
        }
    }
}
