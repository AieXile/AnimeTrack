package com.aiexile.animetrack.domain.playback

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
    fun `同名候选不按返回顺序绑定且年份消歧`() {
        val input = MediaTitleEvidence(listOf("作品"), "作品", null, null, false)
        val a = MediaTitleCandidate(1, listOf("作品"), 2004, null, false)
        val b = a.copy(value = 2, year = 2024)
        assertEquals(setOf(1, 2), LocalMediaMatcher.candidates(input, listOf(a, b)).toSet())
        assertEquals(listOf(2), LocalMediaMatcher.candidates(input.copy(year = 2024), listOf(b, a)))
        assertTrue(LocalMediaMatcher.candidates(input.copy(year = 2020), listOf(a, b)).isEmpty())
    }

    @Test
    fun `季号类型冲突以及包含关系不自动绑定`() {
        val input = MediaTitleEvidence(listOf("作品"), "作品", 2, null, false)
        val hits = LocalMediaMatcher.candidates(input, listOf(
            MediaTitleCandidate(1, listOf("作品"), null, 1, false),
            MediaTitleCandidate(2, listOf("作品"), null, 2, true),
            MediaTitleCandidate(3, listOf("异世界作品"), null, 2, false),
            MediaTitleCandidate(4, listOf("作品 II"), null, 2, false, listOf("作品"))
        ))
        assertEquals(listOf(4), hits)
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
