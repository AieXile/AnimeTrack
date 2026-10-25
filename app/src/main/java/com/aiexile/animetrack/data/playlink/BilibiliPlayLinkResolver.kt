package com.aiexile.animetrack.data.playlink

import com.aiexile.animetrack.data.network.BilibiliSeasonEpisode
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.WbiKeyManager
import com.aiexile.animetrack.domain.externalplayer.BilibiliPlayLink
import com.aiexile.animetrack.domain.externalplayer.BilibiliSeasonCandidate
import com.aiexile.animetrack.domain.externalplayer.BilibiliTitleMatcher
import com.aiexile.animetrack.util.WbiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把本地番剧解析为 B 站播放定位（season_id + 目标集 ep_id）。
 *
 * 解析链：
 * 1. 已有 season_id（B 站同步写入或此前匹配成功持久化）→ 直接取分集；
 * 2. 没有 season_id → WBI 签名搜索 `media_bangumi`，按标题匹配；
 * 3. 任一步失败返回 null，由调用方回退到 `bilibili://search` 搜索页。
 *
 * 分集列表只在内存缓存，避免同一部番剧反复点击时重复请求。
 */
object BilibiliPlayLinkResolver {

    private const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
    private const val EPISODE_CACHE_TTL_MS = 30 * 60 * 1000L
    private const val EPISODE_CACHE_MAX = 20

    private data class CacheEntry(
        val timestamp: Long,
        val episodes: List<BilibiliSeasonEpisode>
    )

    private val episodeCache = object : LinkedHashMap<Int, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CacheEntry>?): Boolean =
            size > EPISODE_CACHE_MAX
    }

    suspend fun resolve(
        title: String,
        storedSeasonId: Int?,
        watchedEpisodes: Int,
        totalEpisodes: Int?,
        seasonNumber: Int?
    ): BilibiliPlayLink? = withContext(Dispatchers.IO) {
        val stored = storedSeasonId?.takeIf { it > 0 }
        if (stored != null) {
            return@withContext BilibiliPlayLink(
                seasonId = stored,
                episodeId = pickEpisodeId(fetchEpisodes(stored), watchedEpisodes, totalEpisodes)
            )
        }
        if (title.isBlank()) return@withContext null

        val matched = searchSeason(title, totalEpisodes, seasonNumber) ?: return@withContext null
        BilibiliPlayLink(
            seasonId = matched.seasonId,
            episodeId = pickEpisodeId(fetchEpisodes(matched.seasonId), watchedEpisodes, totalEpisodes)
        )
    }

    private suspend fun searchSeason(
        title: String,
        totalEpisodes: Int?,
        seasonNumber: Int?
    ): BilibiliSeasonCandidate? {
        val keys = WbiKeyManager.getWbiKeys().getOrNull() ?: return null
        val query = WbiUtils.buildSignedQuery(
            params = mapOf(
                "search_type" to "media_bangumi",
                "keyword" to title,
                "page" to "1"
            ),
            imgKey = keys.first,
            subKey = keys.second
        )
        val response = runCatching {
            RetrofitClient.bilibiliApi.searchBangumiSigned("$SEARCH_URL?$query")
        }.getOrNull() ?: return null
        if (response.code != 0) return null

        val candidates = response.data?.result.orEmpty().mapNotNull { item ->
            val seasonId = (if (item.seasonId > 0) item.seasonId else item.pgcSeasonId)
                .toInt()
                .takeIf { it > 0 }
                ?: return@mapNotNull null
            BilibiliSeasonCandidate(
                seasonId = seasonId,
                title = item.title,
                orgTitle = item.orgTitle,
                epSize = item.epSize
            )
        }
        return BilibiliTitleMatcher.pick(title, totalEpisodes, seasonNumber, candidates)
    }

    private suspend fun fetchEpisodes(seasonId: Int): List<BilibiliSeasonEpisode>? {
        synchronized(episodeCache) {
            val cached = episodeCache[seasonId]
            if (cached != null && System.currentTimeMillis() - cached.timestamp < EPISODE_CACHE_TTL_MS) {
                return cached.episodes
            }
        }
        val response = runCatching {
            RetrofitClient.bilibiliApi.getSeasonDetail(seasonId)
        }.getOrNull() ?: return null
        if (response.code != 0) return null
        val episodes = response.result?.episodes ?: return null
        synchronized(episodeCache) {
            episodeCache[seasonId] = CacheEntry(System.currentTimeMillis(), episodes)
        }
        return episodes
    }

    /**
     * 「接着看」语义与 Animeko 一致：看完/越界回退番剧级（null），
     * 否则按序号定位第 watched+1 集正片（section_type=0）。
     */
    private fun pickEpisodeId(
        episodes: List<BilibiliSeasonEpisode>?,
        watchedEpisodes: Int,
        totalEpisodes: Int?
    ): Long? {
        if (episodes == null) return null
        if (totalEpisodes != null && totalEpisodes > 0 && watchedEpisodes >= totalEpisodes) return null

        val mainEpisodes = episodes.filter { it.sectionType == 0 }
        val target = watchedEpisodes + 1
        val episode = mainEpisodes.firstOrNull { it.title.trim().toIntOrNull() == target }
            ?: mainEpisodes.getOrNull(target - 1)
            ?: return null
        return episode.epId.takeIf { it > 0 } ?: episode.id.takeIf { it > 0 }
    }
}
