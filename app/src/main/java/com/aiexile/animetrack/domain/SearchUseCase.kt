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
}
