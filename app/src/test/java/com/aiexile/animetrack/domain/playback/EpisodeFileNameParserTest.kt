package com.aiexile.animetrack.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeFileNameParserTest {
    @Test
    fun `DBD Raws 的 Grand Blue 集号不受音轨和编码标签影响`() {
        for ((token, number) in listOf("01" to 1, "02" to 2)) {
            val result = EpisodeFileNameParser.parse(
                "[DBD-Raws][Grand Blue][$token][1080P][BDRip][HEVC-10bit][FLACx2].mkv",
                parentPath = "番剧/Grand Blue 全12集 + 特典"
            )
            assertEquals("Grand Blue", result.parsedTitle)
            assertEquals(number, result.episodeNumber)
            assertEquals(null, result.episodeEndNumber)
            assertTrue(result.isMainFeature)
            assertFalse(result.isMovie)
        }
    }

    @Test
    fun `专门的特典预告目录仍然不算正片`() {
        for (folder in listOf("特典", "[映像特典]", "PV 2", "預告片")) {
            val result = EpisodeFileNameParser.parse("01.mkv", parentPath = "Grand Blue/$folder")
            assertFalse(folder, result.isMainFeature)
        }
    }

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
        assertEquals(2, result.seasonNumber)
    }

    @Test
    fun `全括号中文名和校验码不会污染标题`() {
        val result = EpisodeFileNameParser.parse("[Group][葬送的芙莉莲][03v2][1080p][A1B2C3D4].mkv")
        assertEquals("葬送的芙莉莲", result.parsedTitle)
        assertEquals(3, result.episodeNumber)
    }

    @Test
    fun `单独括号集数使用番剧文件夹且不把年份误作集号`() {
        val first = EpisodeFileNameParser.parse("[01][1080p][A1B2C3D4].mkv", "作品/Season 2")
        val second = EpisodeFileNameParser.parse("Episode 02v2.mkv", "作品/Season 2")
        val withYear = EpisodeFileNameParser.parse("[字幕组][作品][2024][03][1080p].mkv")
        assertEquals(listOf(1, 2, 3), listOf(first.episodeNumber, second.episodeNumber, withYear.episodeNumber))
        assertEquals("作品", first.parsedTitle)
        assertEquals("作品", second.parsedTitle)
        assertEquals(2, first.seasonNumber)
    }

    @Test
    fun `小数集不变成整数集也保留正片类型证据`() {
        val result = EpisodeFileNameParser.parse("作品 - 03.5.mkv")
        assertEquals(null, result.episodeNumber)
        assertEquals("03.5", result.decimalEpisode)
    }

    @Test
    fun `保留副标题与多语候选`() {
        val result = EpisodeFileNameParser.parse("[Group][葬送的芙莉莲][Frieren][03][1080p].mkv")
        assertEquals(3, result.episodeNumber)
        assertTrue("葬送的芙莉莲" in result.titleCandidates)
        assertTrue("Frieren" in result.titleCandidates)
    }

    @Test
    fun `中文和日文集数不依赖目录名`() {
        val result = EpisodeFileNameParser.parse("作品 第03話.mkv")
        assertEquals("作品", result.parsedTitle)
        assertEquals(3, result.episodeNumber)
    }

    @Test
    fun `显式季集号允许版本号和小数集`() {
        assertEquals(4, EpisodeFileNameParser.parse("Show S02E04v2.mkv").episodeNumber)
        val decimal = EpisodeFileNameParser.parse("Show Season 2 Episode 04.5.mkv")
        assertEquals(2, decimal.seasonNumber)
        assertEquals("04.5", decimal.decimalEpisode)
        assertEquals(null, decimal.episodeNumber)
    }

    @Test
    fun `录屏和相机文件名不会产生刮削候选`() {
        for (name in listOf("手机拍摄_海边 [720p][x264].mp4", "屏幕录制 [480p][x264].mp4",
            "Screen_Recording_20261121.mp4", "VID_20261121_150925.mp4", "IMG_1234.MOV", "螢幕錄影.mp4")) {
            val result = EpisodeFileNameParser.parse(name, "作品/Season 2")
            assertTrue(name, result.isPersonalRecording)
            assertTrue(name, result.titleCandidates.isEmpty())
            assertEquals(name, null, result.episodeNumber)
        }
        val movie = EpisodeFileNameParser.parse("剧场版 作品.mkv", "杂项")
        assertFalse(movie.isPersonalRecording)
        assertTrue(movie.titleCandidates.isNotEmpty())
    }

    @Test
    fun `显式复合集保留起止集`() {
        val result = EpisodeFileNameParser.parse("Show S02E03-E04.mkv")
        assertEquals(2, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertEquals(4, result.episodeEndNumber)
    }

    @Test
    fun `目录和文件中的媒体库提供商 ID 不污染标题并保留确定匹配`() {
        val result = EpisodeFileNameParser.parse(
            "01.mkv",
            "葬送的芙莉莲 (2023) [tmdbid-209867] [bgm-414445]/Season 01"
        )

        assertEquals("葬送的芙莉莲", result.parsedTitle)
        assertEquals(1, result.episodeNumber)
        assertEquals(1, result.seasonNumber)
        assertEquals(414445, result.bangumiId)
        assertEquals(209867, result.tmdbId)
    }
}
