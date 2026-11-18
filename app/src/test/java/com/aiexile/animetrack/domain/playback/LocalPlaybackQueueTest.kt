package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.data.media.LocalEpisodeType
import com.aiexile.animetrack.data.media.LocalMediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackQueueTest {
    private fun media(
        id: Long,
        episode: Int?,
        season: Int? = 1,
        animeId: Int? = 7,
        type: String? = LocalEpisodeType.MAIN,
        end: Int? = null,
        path: String = "$id.mkv"
    ) = LocalMediaEntity(
        id = id,
        sourceId = 1,
        documentUri = "content://test/$id",
        relativePath = path,
        fileName = path,
        parsedTitle = "测试番剧",
        seasonNumber = season,
        episodeNumber = episode,
        episodeEndNumber = end,
        matchedAnimeId = animeId,
        episodeType = type
    )

    @Test
    fun `按集数排序且同一集只保留一个版本`() {
        val selected = media(3, 2, path = "2-b.mkv")
        val queue = LocalPlaybackQueue.build(
            selected,
            listOf(
                media(1, 10),
                selected,
                media(2, 2, path = "2-a.mkv"),
                media(4, 1)
            )
        )

        assertEquals(listOf(1, 2, 10), queue.map { it.episodeNumber })
        assertEquals(3L, queue[1].id)
    }

    @Test
    fun `特典和缺失文件不进入连播`() {
        val selected = media(1, 1)
        val queue = LocalPlaybackQueue.build(
            selected,
            listOf(
                selected,
                media(2, 2, type = LocalEpisodeType.EXTRA),
                media(3, 3).copy(isMissing = true),
                media(4, 4, animeId = 9)
            )
        )

        assertEquals(listOf(1L), queue.map { it.id })
    }

    @Test
    fun `多集合集不参与自动连播`() {
        val selected = media(1, 1)
        val queue = LocalPlaybackQueue.build(
            selected,
            listOf(selected, media(2, 2, end = 3))
        )

        assertEquals(listOf(1L), queue.map { it.id })
    }

    @Test
    fun `未匹配或没有集数的文件只单独播放`() {
        val unmatched = media(1, 1, animeId = null)
        assertEquals(listOf(unmatched), LocalPlaybackQueue.build(unmatched, listOf(unmatched, media(2, 2))))

        val noEpisode = media(3, null)
        assertEquals(listOf(noEpisode), LocalPlaybackQueue.build(noEpisode, listOf(noEpisode, media(4, 2))))
    }

    @Test
    fun `稳定媒体键带来源前缀`() {
        assertTrue(LocalPlaybackQueue.mediaKey(42).startsWith("local:"))
    }
}
