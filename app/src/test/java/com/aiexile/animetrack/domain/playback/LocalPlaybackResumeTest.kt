package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalPlaybackResumeTest {

    private fun media(id: Long, episode: Int?) = LocalMediaEntity(
        id = id,
        sourceId = 1,
        documentUri = "content://demo/$id",
        relativePath = "show/$id.mkv",
        fileName = "$id.mkv",
        episodeNumber = episode
    )

    @Test
    fun `empty list returns null`() {
        assertNull(LocalPlaybackResume.pick(emptyList(), emptyMap(), emptySet()))
    }

    @Test
    fun `prefers unfinished episode with most recent progress`() {
        val episodes = listOf(media(1, 1), media(2, 2), media(3, 3))
        val progress = mapOf(
            "local:1" to PlaybackProgress(positionMs = 1000, durationMs = 1000, completed = true, updatedAt = 300),
            "local:2" to PlaybackProgress(positionMs = 500, durationMs = 1000, updatedAt = 100),
            "local:3" to PlaybackProgress(positionMs = 800, durationMs = 1000, updatedAt = 200)
        )
        assertEquals(3L, LocalPlaybackResume.pick(episodes, progress, emptySet())?.id)
    }

    @Test
    fun `ignores completed and zero position progress`() {
        val episodes = listOf(media(1, 1), media(2, 2))
        val progress = mapOf(
            "local:1" to PlaybackProgress(positionMs = 900, durationMs = 1000, completed = true, updatedAt = 999),
            "local:2" to PlaybackProgress(positionMs = 0, durationMs = 1000, updatedAt = 998)
        )
        assertEquals(1L, LocalPlaybackResume.pick(episodes, progress, emptySet())?.id)
    }

    @Test
    fun `falls back to first unfinished episode`() {
        val episodes = listOf(media(1, 1), media(2, 2), media(3, 3))
        assertEquals(2L, LocalPlaybackResume.pick(episodes, emptyMap(), setOf(1))?.id)
    }

    @Test
    fun `restarts from first episode when everything is completed`() {
        val episodes = listOf(media(1, 1), media(2, 2))
        assertEquals(1L, LocalPlaybackResume.pick(episodes, emptyMap(), setOf(1, 2))?.id)
    }

    @Test
    fun `episode number is optional for movies`() {
        val episodes = listOf(media(7, null))
        assertEquals(7L, LocalPlaybackResume.pick(episodes, emptyMap(), emptySet())?.id)
    }
}
