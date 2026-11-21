package com.aiexile.animetrack.data.media

import com.aiexile.animetrack.data.network.BangumiEpisode
import com.aiexile.animetrack.domain.playback.mapAniRssEpisode
import com.aiexile.animetrack.domain.playback.mapBangumiAbsoluteEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaSidecarParserTest {
    @Test
    fun readsNfoIdsAndScopedFields() {
        val text = """<tvshow><title>作品 &amp; 续篇</title><uniqueid type="bangumi">42</uniqueid>
            <tmdbid>91</tmdbid><year>2024</year><thumb>https://invalid.local/cover</thumb></tvshow>"""
        val show = LocalMediaSidecarParser.parseNfo(text.toByteArray(), "tvshow.nfo")!!
        assertEquals("作品 & 续篇", show.title)
        assertEquals(42, show.bangumiId)
        assertEquals(91, show.tmdbId)
        assertEquals(2024, show.year)
        val episode = LocalMediaSidecarParser.parseNfo(
            "<episodedetails><uniqueid type='tmdb'>12345</uniqueid><season>2</season><episode>3.5</episode></episodedetails>".toByteArray(),
            "S02E03.nfo"
        )!!
        assertNull(episode.tmdbId) // 集 ID 不是整剧 ID。
        assertEquals(2, episode.season)
        assertEquals("3.5", episode.episode)
    }

    @Test
    fun rejectsDtdAndRecordsSidecarConflicts() {
        val dtd = """<!DOCTYPE tvshow [<!ENTITY code "42">]><tvshow><bangumiid>&code;</bangumiid></tvshow>"""
        assertNull(LocalMediaSidecarParser.parseNfo(dtd.toByteArray(), "tvshow.nfo"))
        assertNull(LocalMediaSidecarParser.parseNfo(dtd.toByteArray(Charsets.UTF_16), "tvshow.nfo"))
        assertNull(LocalMediaSidecarParser.parseNfo(ByteArray(LocalMediaSidecarParser.MAX_BYTES + 1), "tvshow.nfo"))
        val ids = "<tvshow><tmdbid>1</tmdbid><uniqueid type='tmdb'>2</uniqueid></tvshow>"
        assertTrue(LocalMediaSidecarParser.parseNfo(ids.toByteArray(), "tvshow.nfo")!!.conflict)
        assertTrue(LocalMediaSidecarParser.parseIni("[Bangumi]\nid=42\noffset=0\noffset=-10".toByteArray())!!.conflict)
    }

    @Test
    fun aniRssOffsetRequiresEpisodeEvidenceAndNeverAppliesTwice() {
        val raw = LocalMediaEntity(sourceId = 1, documentUri = "", relativePath = "", fileName = "",
            originalEpisode = "13", episodeNumber = 13, episodeOffset = -12)
        val episodes = (1..12).map { BangumiEpisode(it, 0, (it + 12).toDouble(), it.toDouble()) }
        assertEquals(1 to null, mapAniRssEpisode(raw, episodes))
        assertNull(mapAniRssEpisode(raw.copy(originalEpisode = "1", episodeNumber = 1), episodes))
        assertNull(mapAniRssEpisode(raw.copy(originalEpisode = "13.5", episodeNumber = null), episodes))
        assertNull(mapAniRssEpisode(raw, episodes + BangumiEpisode(13, 0, 13.0, 13.0)))
        assertEquals(1 to 2, mapAniRssEpisode(raw.copy(episodeEndNumber = 14), episodes))
    }

    @Test
    fun usesBangumiAbsoluteNumbersOnlyForUnambiguousMappings() {
        val file = LocalMediaEntity(sourceId = 1, documentUri = "", relativePath = "", fileName = "",
            originalEpisode = "13", episodeNumber = 13, episodeEndNumber = 14, episodeType = LocalEpisodeType.MAIN)
        val eps = (1..12).map { BangumiEpisode(it, 0, (it + 12).toDouble(), it.toDouble()) }
        assertEquals(1 to 2, mapBangumiAbsoluteEpisode(file, eps))
        assertNull(mapBangumiAbsoluteEpisode(file.copy(explicitEpisodeNumbers = true), eps))
        assertNull(mapBangumiAbsoluteEpisode(file, eps + BangumiEpisode(99, 0, 13.0, 2.0)))
    }
}
