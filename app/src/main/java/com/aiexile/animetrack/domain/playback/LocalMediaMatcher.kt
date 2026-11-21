package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.home.SeriesMatcher
import com.aiexile.animetrack.util.normalizeTitleForMatch

/** 文件证据与站点候选分离：不依赖列表顺序，也不取模糊搜索首条。 */
internal data class MediaTitleEvidence(
    val titles: List<String>, val baseTitle: String?, val season: Int?, val year: Int?, val movie: Boolean
) {
    val normalizedTitles = titles.map(::normalizeTitleForMatch).filter(String::isNotEmpty).toSet()
    val normalizedBase = baseTitle?.let(::normalizeTitleForMatch)?.takeIf(String::isNotBlank)
}

internal data class MediaTitleCandidate<T>(
    val value: T, val titles: List<String>, val year: Int?, val season: Int?, val movie: Boolean,
    val baseTitles: List<String> = emptyList(), val special: Boolean = false
)

internal data class LocalMediaMatch(val anime: Anime?, val confidence: Float, val reason: String)

internal object LocalMediaMatcher {
    /** 基础名仅在候选确实具备对应季号时可用。唯一是必要条件，不使用站点搜索排序消歧。 */
    fun <T> candidates(evidence: MediaTitleEvidence, items: List<MediaTitleCandidate<T>>): List<T> {
        val full = evidence.normalizedTitles
        val base = evidence.normalizedBase
        val compatible = items.mapNotNull { candidate ->
            if (evidence.year != null && candidate.year != evidence.year) return@mapNotNull null
            if (candidate.movie != evidence.movie) return@mapNotNull null
            if (evidence.season != null && candidate.season != null && candidate.season != evidence.season) {
                return@mapNotNull null
            }
            val titles = candidate.titles.map(::normalizeTitleForMatch).filter(String::isNotEmpty).toSet()
            // 完整标题中的独立副标题可直接证明分篇；泛标题要有明确的季证据。
            val fullWithSeason = titles.any { it in full && it != base }
            val matchingSeason = evidence.season != null && (candidate.season == evidence.season ||
                (evidence.season == 1 && candidate.season == null && !candidate.special && titles.any(full::contains)))
            val exact = titles.any(full::contains) && when {
                evidence.season == null -> candidate.season == null || candidate.season == 1 || base == null ||
                    candidate.baseTitles.none { normalizeTitleForMatch(it) == base } || fullWithSeason
                else -> matchingSeason || fullWithSeason
            }
            val sameBase = base != null && matchingSeason && !candidate.special && (base in titles ||
                candidate.baseTitles.any { normalizeTitleForMatch(it) == base })
            when {
                exact && fullWithSeason -> candidate.value to 3
                exact -> candidate.value to 2
                sameBase -> candidate.value to 1
                else -> null
            }
        }
        val best = compatible.maxOfOrNull { it.second } ?: return emptyList()
        return compatible.filter { it.second == best }.map { it.first }.distinct()
    }

    fun match(parsedTitle: String, seasonNumber: Int?, animes: List<Anime>): LocalMediaMatch {
        val hit = candidates(
            MediaTitleEvidence(listOf(parsedTitle), SeriesMatcher.extractBaseTitle(parsedTitle), seasonNumber, null, false),
            animes.map { anime -> MediaTitleCandidate(
                anime, listOf(anime.title), anime.airDate?.take(4)?.toIntOrNull(),
                anime.seasonNumber ?: EpisodeFileNameParser.titleSeason(anime.title), movieTitle(anime.title),
                listOf(SeriesMatcher.extractBaseTitle(anime.title))
            ) }
        ).singleOrNull()
        return LocalMediaMatch(hit, if (hit != null) 0.98f else 0f, if (hit != null) "exact_title" else "no_unique_candidate")
    }

    fun movieTitle(title: String): Boolean = title.contains("剧场版") || title.contains("劇場版") ||
        MOVIE.containsMatchIn(title)

    private val MOVIE = Regex("(?i)(?:^|[ ._-])movie(?:$|[ ._-])")
}
