package com.aiexile.animetrack.domain

import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource

class SearchUseCase(
    private val repository: AnimeRepository
) {
    suspend fun search(query: String, source: SearchSource): List<SearchResult> {
        return when (source) {
            SearchSource.BANGUMI -> {
                val bangumiResults = repository.searchBangumi(query)
                bangumiResults.map { subject ->
                    SearchResult(
                        source = SearchSource.BANGUMI,
                        sourceId = subject.id,
                        title = subject.displayName,
                        coverUrl = subject.coverUrl,
                        episodeCount = subject.episodeCount,
                        airDate = subject.date,
                        rating = subject.score?.toFloat(),
                        summary = subject.summary,
                        episodeCountText = subject.episodeCountText
                    )
                }
            }
            SearchSource.TMDB -> repository.searchTmdb(query)
            SearchSource.ALL -> repository.searchAll(query)
        }
    }

    /**
     * 按标题在 TMDB 中查找「完全对齐」的条目，找不到或同名候选无法消歧时返回 null。
     * @param title 本地番剧标题
     * @param airDate 本地首播日期，用于同名候选的年份消歧
     */
    suspend fun findExactTmdbMatch(title: String, airDate: String?): Int? {
        val candidates = repository.searchTmdbTvShows(title).map { show ->
            TmdbMatchCandidate(
                id = show.id,
                name = show.name,
                originalName = show.originalName ?: "",
                firstAirDate = show.firstAirDate
            )
        }
        return TmdbTitleMatcher.pickExact(title, airDate, candidates)?.id
    }
}
