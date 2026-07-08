package com.aiexile.animetrack.domain

import android.util.Log
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.util.computeIsFinished
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class UpdateCheckUseCase(
    private val repository: AnimeRepository
) {
    companion object {
        private const val TAG = "AnimeTrack"
    }

    data class UpdateResult(
        val title: String,
        val newEpisodes: Int
    )

    suspend fun checkAiringAnimeUpdates(): List<UpdateResult> = withContext(Dispatchers.IO) {
        try {
            val airingAnimes = repository.getAiringAnimesWithBangumiId()
            if (airingAnimes.isEmpty()) return@withContext emptyList()

            Log.d(TAG, "Checking updates for ${airingAnimes.size} airing animes")

            val deferredResults = airingAnimes.map { anime ->
                async {
                    try {
                        val bangumiId = anime.bangumiId ?: return@async null
                        val detail = repository.fetchBangumiDetail(bangumiId) ?: return@async null

                        val remoteEps = detail.eps ?: 0
                        val remoteTotal = detail.totalEpisodes ?: 0

                        val resolvedTotal = when {
                            remoteEps > 0 -> remoteEps
                            remoteTotal > 0 -> remoteTotal
                            else -> anime.totalEpisodes
                        }

                        if (remoteEps > anime.currentEpisodes) {
                            Log.d(TAG, "New episode found: ${anime.title} local=${anime.currentEpisodes} remote=$remoteEps")
                            val updatedAnime = anime.copy(
                                currentEpisodes = remoteEps,
                                hasNewUpdate = true,
                                isFinished = computeIsFinished(anime.airDate, resolvedTotal, anime.status)
                            )
                            repository.updateAnime(updatedAnime)
                            return@async UpdateResult(anime.title, remoteEps)
                        } else if (resolvedTotal > 0 && anime.totalEpisodes == 0) {
                            val updatedAnime = anime.copy(
                                totalEpisodes = resolvedTotal,
                                hasNewUpdate = false,
                                isFinished = computeIsFinished(anime.airDate, resolvedTotal, anime.status)
                            )
                            repository.updateAnime(updatedAnime)
                        }
                        null
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to check update for: ${anime.title}", e)
                        null
                    }
                }
            }

            val results = deferredResults.awaitAll().filterNotNull()
            if (results.isNotEmpty()) {
                results.forEach { (title, eps) ->
                    Log.d(TAG, "Update detected: $title -> $eps episodes")
                }
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "checkAiringAnimeUpdates failed", e)
            emptyList()
        }
    }
}
