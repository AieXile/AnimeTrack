package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository

import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.model.WebSourceEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPlaybackResumeTest {

    private fun entry(
        url: String = "https://example.com/ep/3",
        title: String = "Show · Ep 3",
        seriesUrl: String = "https://example.com/show",
        mediaId: String = "web:show|episode:3",
        episodeName: String = "第 3 集",
        next: WebSourceEpisode? = WebSourceEpisode("第 4 集", "https://example.com/ep/4"),
        nextMediaId: String? = "web:show|episode:4",
        animeId: Int? = 42,
        animeTitle: String? = "Show"
    ) = WebPlaybackHistoryRepository.Entry(
        url = url,
        title = title,
        lastPlayedAt = 1L,
        seriesUrl = seriesUrl,
        mediaId = mediaId,
        seriesTitle = "Show",
        episodeName = episodeName,
        nextEpisode = next,
        nextMediaId = nextMediaId,
        animeId = animeId,
        animeTitle = animeTitle
    )

    @Test
    fun `unfinished episode resumes the same page`() {
        val target = WebPlaybackResume.target(entry(), PlaybackProgress(positionMs = 500, durationMs = 1_000))
        assertEquals("https://example.com/ep/3", target.request.url)
        assertEquals("https://example.com/ep/3", target.request.pageUrl)
        assertFalse(target.isNextEpisode)
        assertEquals("第 3 集", target.episodeLabel)
        assertEquals(42, target.request.animeId)
        assertEquals("web:show|episode:3", target.request.progressMediaId)
    }

    @Test
    fun `completed episode moves to the next episode`() {
        val target = WebPlaybackResume.target(
            entry(),
            PlaybackProgress(positionMs = 1_000, durationMs = 1_000, completed = true)
        )
        assertEquals("https://example.com/ep/4", target.request.url)
        assertTrue(target.isNextEpisode)
        assertEquals("第 4 集", target.episodeLabel)
        assertEquals("web:show|episode:4", target.request.progressMediaId)
        assertEquals("Show · 第 4 集", target.request.title)
    }

    @Test
    fun `completed last episode replays itself`() {
        val target = WebPlaybackResume.target(
            entry(next = null, nextMediaId = null),
            PlaybackProgress(positionMs = 1_000, durationMs = 1_000, completed = true)
        )
        assertEquals("https://example.com/ep/3", target.request.url)
        assertFalse(target.isNextEpisode)
    }

    @Test
    fun `direct media url is not treated as a page`() {
        val target = WebPlaybackResume.target(
            entry(url = "https://cdn.example.com/video/ep3.m3u8", next = null),
            null
        )
        assertNull(target.request.pageUrl)
        assertFalse(target.request.restoreQueue)
        assertEquals("application/x-mpegURL", target.request.mimeType)
    }

    @Test
    fun `global history stays unbound`() {
        val target = WebPlaybackResume.target(entry(animeId = null, animeTitle = null), null)
        assertNull(target.request.animeId)
        assertNull(target.request.animeTitle)
    }
}
