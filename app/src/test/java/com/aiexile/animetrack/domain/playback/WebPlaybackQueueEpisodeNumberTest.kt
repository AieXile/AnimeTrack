package com.aiexile.animetrack.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebPlaybackQueueEpisodeNumberTest {

    @Test
    fun `parses common episode labels`() {
        assertEquals(3, WebPlaybackQueue.episodeNumberOf("第 3 集"))
        assertEquals(3, WebPlaybackQueue.episodeNumberOf("第3话"))
        assertEquals(3, WebPlaybackQueue.episodeNumberOf("EP03"))
        assertEquals(3, WebPlaybackQueue.episodeNumberOf("ep.03"))
        assertEquals(3, WebPlaybackQueue.episodeNumberOf("003"))
        assertEquals(24, WebPlaybackQueue.episodeNumberOf("Episode 24"))
    }

    @Test
    fun `rejects labels with extra text`() {
        assertNull(WebPlaybackQueue.episodeNumberOf("第 3 集 高清"))
        assertNull(WebPlaybackQueue.episodeNumberOf("特别篇"))
        assertNull(WebPlaybackQueue.episodeNumberOf(""))
    }
}
