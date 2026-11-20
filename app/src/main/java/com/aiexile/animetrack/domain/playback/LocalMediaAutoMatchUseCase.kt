package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.media.LocalMediaAutoMatch
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 提交扫描后匹配待处理媒体；离线时仍提交本地匹配。 */
internal class LocalMediaAutoMatchUseCase(
    private val mediaRepository: LocalMediaRepository,
    private val animeRepository: AnimeRepository
) {
    suspend fun run(sourceId: Long) = withContext(Dispatchers.Default) {
        val media = mediaRepository.getPendingMatches(sourceId)
        if (media.isEmpty()) return@withContext
        val animes = animeRepository.getAllAnimes().first()
        if (animes.isEmpty()) return@withContext
        val localCache = mutableMapOf<Pair<String, Int?>, LocalMediaMatch>()
        val remoteCache = mutableMapOf<String, Anime?>()
        val normalizedTitles = animes.associate { it.id to LocalMediaMatcher.normalize(it.title) }
        val updates = ArrayList<LocalMediaAutoMatch>(BATCH_SIZE)
        val remoteDeadline = android.os.SystemClock.elapsedRealtime() + REMOTE_BUDGET_MS

        for (item in media) {
            ensureActive()
            val title = item.parsedTitle?.takeIf { it.isNotBlank() } ?: continue
            val local = localCache.getOrPut(title to item.seasonNumber) {
                LocalMediaMatcher.match(title, item.seasonNumber, animes)
            }
            val localAnime = local.anime?.takeIf { local.confidence >= 0.90f }
            val anime = localAnime ?: run {
                val budget = remoteDeadline - android.os.SystemClock.elapsedRealtime()
                if (budget > 0 && title !in remoteCache && remoteCache.size < MAX_REMOTE_QUERIES) {
                    remoteCache[title] = search(title, budget.coerceAtMost(REQUEST_TIMEOUT_MS)).firstNotNullOfOrNull { candidate ->
                        val name = LocalMediaMatcher.normalize(candidate.displayName)
                        animes.firstOrNull { it.bangumiId == candidate.id || normalizedTitles[it.id] == name }
                    }
                }
                remoteCache[title]
            }
            if (anime != null) {
                updates += LocalMediaAutoMatch(
                    item.id, title, item.seasonNumber, anime.id, if (localAnime != null) local.confidence else 0.91f
                )
            }
            if (updates.size >= BATCH_SIZE) {
                mediaRepository.applyAutoMatches(updates)
                updates.clear()
            }
        }
        mediaRepository.applyAutoMatches(updates)
    }

    private suspend fun search(title: String, timeoutMs: Long): List<BangumiSubject> = try {
        withTimeoutOrNull(timeoutMs) { animeRepository.searchBangumi(title) }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val MAX_REMOTE_QUERIES = 30
        const val REQUEST_TIMEOUT_MS = 3_000L
        const val REMOTE_BUDGET_MS = 20_000L
    }
}
