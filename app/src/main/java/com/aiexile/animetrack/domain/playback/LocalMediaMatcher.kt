package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.model.Anime
import java.util.Locale

/** 本地媒体与已有 Anime 的匹配结果。 */
data class LocalMediaMatch(
    val anime: Anime?,
    val confidence: Float,
    val reason: String
)

object LocalMediaMatcher {
    fun match(parsedTitle: String, seasonNumber: Int?, animes: List<Anime>): LocalMediaMatch {
        if (parsedTitle.isBlank() || animes.isEmpty()) {
            return LocalMediaMatch(null, 0f, "empty")
        }

        val normalized = normalize(parsedTitle)
        val exactCandidates = animes.filter { anime ->
            val title = normalize(anime.title)
            val base = normalize(com.aiexile.animetrack.ui.home.SeriesMatcher.extractBaseTitle(anime.title))
            normalized == title || normalized == base
        }
        val exact = exactCandidates
            .sortedWith(
                compareByDescending<Anime> { anime ->
                    val animeSeason = anime.seasonNumber
                        ?: com.aiexile.animetrack.ui.home.SeriesMatcher.extractSeasonNumber(anime.title)
                    seasonNumber != null && animeSeason == seasonNumber
                }.thenBy { it.id }
            )
            .firstOrNull()
        if (exact != null) {
            val exactSeason = exact.seasonNumber
                ?: com.aiexile.animetrack.ui.home.SeriesMatcher.extractSeasonNumber(exact.title)
            val confidence = if (seasonNumber != null && exactSeason != seasonNumber) 0.88f else 0.98f
            return LocalMediaMatch(exact, confidence, "exact_title")
        }

        val candidates = animes.mapNotNull { anime ->
            val title = normalize(anime.title)
            val base = normalize(com.aiexile.animetrack.ui.home.SeriesMatcher.extractBaseTitle(anime.title))
            val score = when {
                normalized.contains(title) || title.contains(normalized) -> 0.86f
                normalized.contains(base) || base.contains(normalized) -> 0.82f
                else -> 0f
            }
            if (score <= 0f) return@mapNotNull null
            val animeSeason = anime.seasonNumber
                ?: com.aiexile.animetrack.ui.home.SeriesMatcher.extractSeasonNumber(anime.title)
            val seasonBonus = if (seasonNumber != null && animeSeason == seasonNumber) 0.06f else 0f
            anime to (score + seasonBonus).coerceAtMost(0.95f)
        }

        val best = candidates.maxByOrNull { it.second }
        return if (best == null) {
            LocalMediaMatch(null, 0f, "no_candidate")
        } else {
            LocalMediaMatch(best.first, best.second, "title_similarity")
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\s._·•:：,，!！?？()（）\\[\\]{}【】\\-–—~～]+"), "")
}
