package com.aiexile.animetrack.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import com.aiexile.animetrack.model.WebSourceEpisode

class WebPlaybackHistoryCodecTest {
    @Test
    fun `历史记录编码后可以完整解码`() {
        val entries = listOf(
            WebPlaybackHistoryRepository.Entry(
                "https://a.example/watch/1", "第一集", 100L, "RuleUA", "https://source.example/"
            ),
            WebPlaybackHistoryRepository.Entry("https://b.example/video.mp4", null, 200L)
        )

        val decoded = WebPlaybackHistoryRepository.decode(
            WebPlaybackHistoryRepository.encode(entries)
        )

        assertEquals(entries, decoded)
    }

    @Test
    fun `损坏或空数据返回空列表`() {
        assertTrue(WebPlaybackHistoryRepository.decode("").isEmpty())
        assertTrue(WebPlaybackHistoryRepository.decode("{not-json").isEmpty())
    }

    @Test
    fun `旧历史记录缺少规则配置仍可读取`() {
        val entry = WebPlaybackHistoryRepository.decode(
            """[{"url":"https://a.example/watch/1","title":"第一集","lastPlayedAt":100}]"""
        ).single()
        assertEquals("https://a.example/watch/1", entry.url)
        assertNull(entry.userAgent)
        assertNull(entry.referer)
        assertNull(entry.mediaId)
        assertNull(entry.nextEpisode)
    }

    @Test
    fun `续播元数据和下一集地址可完整往返`() {
        val entry = WebPlaybackHistoryRepository.Entry("https://a.example/watch/1", "示例 · 第1集", 100L,
            seriesUrl = "https://a.example/detail/1", mediaId = "web:series|episode:1",
            seriesTitle = "示例", episodeName = "第1集",
            nextEpisode = WebSourceEpisode("第2集", "https://a.example/watch/2"),
            nextMediaId = "web:series|episode:2")
        assertEquals(entry, WebPlaybackHistoryRepository.decode(
            WebPlaybackHistoryRepository.encode(listOf(entry))).single())
    }
}
