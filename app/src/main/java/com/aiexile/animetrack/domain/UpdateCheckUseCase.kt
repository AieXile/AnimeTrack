package com.aiexile.animetrack.domain

import android.util.Log
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.util.computeIsFinished
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class UpdateCheckUseCase(
    private val repository: AnimeRepository
) {
    companion object {
        private const val TAG = "AnimeTrack"
        /** 更新检查并发上限，避免同时对 Bangumi 发起大量请求 */
        private const val MAX_CONCURRENT = 5
    }

    /** 单部番剧的检查结果：待写入的更新对象 + 可选的用户可见更新提示 */
    private data class CheckOutcome(
        val updatedAnime: Anime,
        val result: UpdateResult?
    )

    data class UpdateResult(
        val title: String,
        val newEpisodes: Int
    )

    suspend fun checkAiringAnimeUpdates(): List<UpdateResult> = withContext(Dispatchers.IO) {
        try {
            val airingAnimes = repository.getAiringAnimesWithBangumiId()
            if (airingAnimes.isEmpty()) return@withContext emptyList()

            Log.d(TAG, "Checking updates for ${airingAnimes.size} airing animes")

            val semaphore = Semaphore(MAX_CONCURRENT)

            // 第一阶段：限并发地并行拉取远程详情并计算更新对象（不写库）
            val deferredOutcomes = airingAnimes.map { anime ->
                async {
                    semaphore.withPermit {
                        try {
                            val bangumiId = anime.bangumiId ?: return@withPermit null
                            val detail = repository.fetchBangumiDetail(bangumiId) ?: return@withPermit null

                            val remoteEps = detail.eps ?: 0
                            val remoteTotal = detail.totalEpisodes ?: 0

                            val resolvedTotal = when {
                                remoteEps > 0 -> remoteEps
                                remoteTotal > 0 -> remoteTotal
                                else -> anime.totalEpisodes
                            }

                            when {
                                remoteEps > anime.currentEpisodes -> {
                                    Log.d(TAG, "New episode found: ${anime.title} local=${anime.currentEpisodes} remote=$remoteEps")
                                    CheckOutcome(
                                        updatedAnime = anime.copy(
                                            currentEpisodes = remoteEps,
                                            hasNewUpdate = true,
                                            isFinished = computeIsFinished(anime.airDate, resolvedTotal, anime.status)
                                        ),
                                        result = UpdateResult(anime.title, remoteEps)
                                    )
                                }
                                resolvedTotal > 0 && anime.totalEpisodes == 0 -> {
                                    CheckOutcome(
                                        updatedAnime = anime.copy(
                                            totalEpisodes = resolvedTotal,
                                            hasNewUpdate = false,
                                            isFinished = computeIsFinished(anime.airDate, resolvedTotal, anime.status)
                                        ),
                                        result = null
                                    )
                                }
                                else -> null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to check update for: ${anime.title}", e)
                            null
                        }
                    }
                }
            }

            val outcomes = deferredOutcomes.awaitAll().filterNotNull()

            // 第二阶段：统一写入本地数据库（纯 DAO，不触发逐条网络同步）
            outcomes.forEach { repository.updateAnimeInternal(it.updatedAnime) }

            val results = outcomes.mapNotNull { it.result }
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
