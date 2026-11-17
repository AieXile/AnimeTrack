package com.aiexile.animetrack.ui.home

import com.aiexile.animetrack.model.Anime

sealed class AnimeListItem {
    abstract val stableKey: String

    /** 取出该项（含系列堆叠里的分季）中指定 id 的番剧，不存在则返回 null */
    fun findAnime(animeId: Long): Anime? = when (this) {
        is Single -> anime.takeIf { it.id.toLong() == animeId }
        is Series -> animeList.firstOrNull { it.id.toLong() == animeId }
        is ExpandedSeriesCard -> anime.takeIf { it.id.toLong() == animeId }
    }

    data class Single(
        val anime: Anime,
        override val stableKey: String = "single_${anime.id}"
    ) : AnimeListItem()

    data class Series(
        val baseTitle: String,
        val animeList: List<Anime>,
        override val stableKey: String = "series_${animeList.first().id}"
    ) : AnimeListItem()

    /**
     * 已展开系列中的单季卡片。由 HomeScreen 在展开时将 [Series] 拆分得到，
     * 每一项占据一个正常网格格子，与其他卡片一起在主网格中排列。
     */
    data class ExpandedSeriesCard(
        val anime: Anime,
        val baseTitle: String,
        val seasonIndex: Int,
        val totalSeasons: Int,
        val seriesStableKey: String,
        override val stableKey: String = "expanded_${anime.id}"
    ) : AnimeListItem()
}
