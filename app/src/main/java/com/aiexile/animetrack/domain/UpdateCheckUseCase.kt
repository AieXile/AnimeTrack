package com.aiexile.animetrack.domain

import android.util.Log
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.log.AppLogManager
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
            // 先做纯本地兜底重算，修正历史上各写入路径遗留的 isFinished=false 脏数据
            recalcFinishStatusLocally()

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

                            // 解析 infobox「播放结束」日期（仅番剧完结后 Bangumi 才会填充）
                            val parsedAirEndDate = detail.parseEndDate()

                            when {
                                remoteEps > anime.currentEpisodes -> {
                                    Log.d(TAG, "New episode found: ${anime.title} local=${anime.currentEpisodes} remote=$remoteEps")
                                    CheckOutcome(
                                        updatedAnime = anime.copy(
                                            currentEpisodes = remoteEps,
                                            hasNewUpdate = true,
                                            airEndDate = parsedAirEndDate ?: anime.airEndDate,
                                            isFinished = computeIsFinished(anime.airDate, resolvedTotal, anime.status, parsedAirEndDate, anime.airingStatusOverride)
                                        ),
                                        result = UpdateResult(anime.title, remoteEps)
                                    )
                                }
                                resolvedTotal > 0 && anime.totalEpisodes == 0 -> {
                                    CheckOutcome(
                                        updatedAnime = anime.copy(
                                            totalEpisodes = resolvedTotal,
                                            hasNewUpdate = false,
                                            airEndDate = parsedAirEndDate ?: anime.airEndDate,
                                            isFinished = computeIsFinished(anime.airDate, resolvedTotal, anime.status, parsedAirEndDate, anime.airingStatusOverride)
                                        ),
                                        result = null
                                    )
                                }
                                // 仅 airEndDate 更新（如番剧刚完结但集数未变）也写入 DB
                                parsedAirEndDate != null && parsedAirEndDate != anime.airEndDate -> {
                                    CheckOutcome(
                                        updatedAnime = anime.copy(
                                            airEndDate = parsedAirEndDate,
                                            isFinished = computeIsFinished(anime.airDate, resolvedTotal, anime.status, parsedAirEndDate, anime.airingStatusOverride)
                                        ),
                                        result = null
                                    )
                                }
                                else -> null
                            }
                        } catch (e: Exception) {
                            AppLogManager.w(TAG, "更新检查失败: ${anime.title}", e)
                            null
                        }
                    }
                }
            }

            val outcomes = deferredOutcomes.awaitAll().filterNotNull()

            // 第二阶段：单事务批量写入本地数据库（纯 DAO，不触发逐条网络同步，
            // 避免 N 次独立事务引发 N 次 Room 失效与列表重算）
            repository.batchUpdateAnimesInternal(outcomes.map { it.updatedAnime })

            val results = outcomes.mapNotNull { it.result }
            if (results.isNotEmpty()) {
                results.forEach { (title, eps) ->
                    Log.d(TAG, "Update detected: $title -> $eps episodes")
                }
            }
            results
        } catch (e: Exception) {
            AppLogManager.e(TAG, "checkAiringAnimeUpdates failed", e)
            emptyList()
        }
    }

    /**
     * 本地兜底重算完结状态（无网络请求）。
     *
     * 历史上多个写入路径可能把已完结番剧错误标记为连载中：
     * DB v4 迁移新增 isFinished 列时默认 0、B 站同步新建时 airDate 传参为 null、
     * Bangumi 同步更新已有条目时不重算 isFinished 等。
     * 这里对全部「连载中」的在追番剧，用已有的 airDate / totalEpisodes / airEndDate
     * 重新判定并写回，仅做 false→true，且尊重用户手动覆盖 airingStatusOverride。
     */
    private suspend fun recalcFinishStatusLocally() {
        try {
            val airing = repository.getAiringAnimesList()
            val corrected = airing.mapNotNull { anime ->
                val recalculated = computeIsFinished(
                    anime.airDate,
                    anime.totalEpisodes,
                    anime.status,
                    anime.airEndDate,
                    anime.airingStatusOverride
                )
                if (recalculated && !anime.isFinished) anime.copy(isFinished = true) else null
            }
            if (corrected.isNotEmpty()) {
                AppLogManager.i(TAG, "完结状态兜底重算: 修正 ${corrected.size} 部（此前被误标为连载中）")
                repository.batchUpdateAnimesInternal(corrected)
            }
        } catch (e: Exception) {
            AppLogManager.e(TAG, "recalcFinishStatusLocally failed", e)
        }
    }
}
