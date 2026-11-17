package com.aiexile.animetrack.domain.media

import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaMatcherTest {
    private fun anime(id: Int, title: String, season: Int? = null) = Anime(
        id = id,
        title = title,
        totalEpisodes = 12,
        watchedEpisodes = 0,
        status = AnimeStatus.PLANNED,
        rating = null,
        notes = "",
        seasonNumber = season
    )

    @Test
    fun `精确标题优先并获得高置信度`() {
        val result = LocalMediaMatcher.match(
            parsedTitle = "葬送的芙莉莲",
            seasonNumber = null,
            animes = listOf(anime(1, "葬送的芙莉莲"))
        )

        assertEquals(1, result.anime?.id)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun `同系列季数可提高候选置信度`() {
        val result = LocalMediaMatcher.match(
            parsedTitle = "间谍过家家",
            seasonNumber = 2,
            animes = listOf(
                anime(1, "间谍过家家", season = 1),
                anime(2, "间谍过家家 第二季", season = 2)
            )
        )

        assertEquals(2, result.anime?.id)
    }
}
