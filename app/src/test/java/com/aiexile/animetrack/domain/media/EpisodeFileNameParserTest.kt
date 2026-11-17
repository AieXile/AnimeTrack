package com.aiexile.animetrack.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeFileNameParserTest {
    @Test
    fun `解析字幕组中文标题与集数`() {
        val result = EpisodeFileNameParser.parse("[字幕组] 葬送的芙莉莲 - 03 [1080p][x264].mkv")

        assertEquals("葬送的芙莉莲", result.parsedTitle)
        assertEquals(3, result.episodeNumber)
        assertEquals(null, result.seasonNumber)
        assertTrue(result.isMainFeature)
        assertTrue(result.confidence >= 0.8f)
    }

    @Test
    fun `解析 SxxExx 和季数`() {
        val result = EpisodeFileNameParser.parse("SPY FAMILY S02E04 1080p.mp4")

        assertEquals("SPY FAMILY", result.parsedTitle)
        assertEquals(2, result.seasonNumber)
        assertEquals(4, result.episodeNumber)
    }

    @Test
    fun `解析中文集数范围`() {
        val result = EpisodeFileNameParser.parse("进击的巨人 第01-02集.mkv")

        assertEquals("进击的巨人", result.parsedTitle)
        assertEquals(1, result.episodeNumber)
        assertEquals(2, result.episodeEndNumber)
    }

    @Test
    fun `片头文件标记为非正片`() {
        val result = EpisodeFileNameParser.parse("[Group] 番剧 NCOP.mkv")

        assertFalse(result.isMainFeature)
        assertEquals(0f, result.confidence, 0f)
    }

    @Test
    fun `目录名可作为空标题回退`() {
        val result = EpisodeFileNameParser.parse("01.mkv", parentPath = "番剧名/Season 2")

        assertEquals("番剧名", result.parsedTitle)
        assertEquals(1, result.episodeNumber)
    }
}
