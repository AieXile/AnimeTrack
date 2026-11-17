package com.aiexile.animetrack.ui.player

import com.aiexile.animetrack.data.player.WebPlaybackHistoryRepository
import com.aiexile.animetrack.domain.media.WebPlaybackQueue
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.model.WebSourceChannel
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourcePage
import org.junit.Assert.*
import org.junit.Test

class WebContinueWatchingTest {
    private val first = WebSourceEpisode("第1集", "https://example.com/play/1")
    private val second = WebSourceEpisode("第2集", "https://example.com/play/2")
    private val entry = WebPlaybackHistoryRepository.Entry(
        first.url, "示例 · 第1集", 10L, seriesUrl = "https://example.com/detail/1",
        mediaId = "episode:1", seriesTitle = "示例", episodeName = first.name,
        nextEpisode = second, nextMediaId = "episode:2")

    @Test
    fun `尚未看完时保留本集即使列表存在下一集`() {
        val item = WebPlaybackHistoryItem(entry, PlaybackProgress(40, 100))
        assertEquals(first.url, item.continueUrl)
        assertEquals("episode:1", item.continueMediaId)
        assertNull(item.nextEpisode)
    }

    @Test
    fun `拖到片尾但未完成时仍续播本集`() {
        val item = WebPlaybackHistoryItem(entry, PlaybackProgress(100, 100, completed = false))
        assertEquals(first.url, item.continueUrl)
    }

    @Test
    fun `看完本集后使用下一集页面和进度标识`() {
        val item = WebPlaybackHistoryItem(entry, PlaybackProgress(100, 100, completed = true))
        assertEquals(second.url, item.continueUrl)
        assertEquals("episode:2", item.continueMediaId)
        assertEquals("示例 · 第2集", item.continueTitle)
    }

    @Test
    fun `末集看完后重播本集不会回绕第一集`() {
        val last = entry.copy(url = second.url, mediaId = "episode:2", nextEpisode = null, nextMediaId = null)
        val item = WebPlaybackHistoryItem(last, PlaybackProgress(100, 100, completed = true))
        assertEquals(second.url, item.continueUrl)
        assertEquals("episode:2", item.continueMediaId)
        assertNull(item.nextEpisode)
    }

    @Test
    fun `旧记录没有新字段时仍保留原网页续播`() {
        val old = WebPlaybackHistoryRepository.Entry(first.url, "示例", 1)
        val item = WebPlaybackHistoryItem(old, null)
        assertEquals(first.url, item.continueUrl)
        assertNull(item.continueMediaId)
    }

    @Test
    fun `迟到的旧进度不能覆盖新进度或完成状态`() {
        val done = PlaybackProgress(100, 100, completed = true, updatedAt = 20)
        assertEquals(done, done.merge(PlaybackProgress(50, 100, updatedAt = 10)))
    }

    @Test
    fun `片尾暂停和退出的重复保存保留看完标记`() {
        val done = PlaybackProgress(100, 100, completed = true, updatedAt = 20)
        assertTrue(done.merge(PlaybackProgress(99, 100, updatedAt = 21)).completed)
    }

    @Test
    fun `重新从头观看时恢复未完成状态`() {
        val done = PlaybackProgress(100, 100, completed = true, updatedAt = 20)
        val replay = done.merge(PlaybackProgress(5, 100, updatedAt = 21))
        assertFalse(replay.completed)
        assertEquals(5L, replay.positionMs)
    }

    @Test
    fun `线路倒序和标签写法不同时仍共享同一集的记录`() {
        val page = WebSourcePage("示例", "https://example.com/detail/1", listOf(
            WebSourceChannel("A", listOf(first, second)),
            WebSourceChannel("B", listOf(
                WebSourceEpisode("02", "https://example.com/b/2"),
                WebSourceEpisode("01", "https://example.com/b/1")
            ))))
        val queue = WebPlaybackQueue.from(page, first.url)!!
        assertEquals(queue.mediaKey(), queue.switchChannel(1)!!.mediaKey())
        assertEquals(1, queue.switchChannel(1)!!.episodeIndex)
        assertEquals(queue.mediaKeys[1], queue.select(1)!!.mediaKey())
    }

    @Test
    fun `重复标签使用不同网页标识避免错误共享记录`() {
        val page = WebSourcePage("示例", "https://example.com/detail/1", listOf(
            WebSourceChannel("A", listOf(first, second.copy(name = first.name)))))
        val queue = WebPlaybackQueue.from(page, first.url)!!
        assertNotEquals(queue.mediaKeys[0], queue.mediaKeys[1])
        assertNull(queue.switchChannel(0))
    }
}
