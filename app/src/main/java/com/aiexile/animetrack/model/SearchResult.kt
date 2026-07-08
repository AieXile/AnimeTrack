package com.aiexile.animetrack.model

enum class SearchSource { BANGUMI, TMDB, ALL }

data class SearchResult(
    val source: SearchSource,
    val sourceId: Int,
    val title: String,
    val coverUrl: String?,
    val episodeCount: Int?,
    val airDate: String?,
    val rating: Float?,
    val summary: String?,
    val episodeCountText: String
)
