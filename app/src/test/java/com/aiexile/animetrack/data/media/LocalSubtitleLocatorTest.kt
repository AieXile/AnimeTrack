package com.aiexile.animetrack.data.media

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSubtitleLocatorTest {
    @Test
    fun `一个目录只读取一次且字幕只归到对应的集`() = runBlocking {
        val calls = mutableListOf<Pair<Long, String>>()
        val result = LocalSubtitleLocator.findAll(
            listOf(video(1, "EP01.mkv"), video(2, "EP012.mkv"), video(3, "EP01.mkv", source = 2),
                video(4, "EP01.mkv", parent = null)),
            directoryTimeoutMs = 1_000
        ) { source, parent ->
            calls += source to parent
            if (source == 1L) listOf(subtitle("EP01.chs.ass"), subtitle("EP012.srt"), subtitle("EP01.srt"))
            else listOf(subtitle("EP01.en.ass"))
        }

        assertEquals(listOf(1L to "season", 2L to "season"), calls)
        assertEquals(listOf("EP01.srt", "EP01.chs.ass"), result.getValue(1).map { it.uri })
        assertEquals("zh-CN", result.getValue(1)[1].language)
        assertEquals(listOf("EP012.srt"), result.getValue(2).map { it.uri })
        assertEquals(listOf("EP01.en.ass"), result.getValue(3).map { it.uri })
        assertTrue(4L !in result)
    }

    @Test
    fun `慢目录超时只跳过该目录的字幕`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = LocalSubtitleLocator.findAll(
            listOf(video(1, "EP01.mkv", parent = "slow"), video(2, "EP02.mkv", parent = "slow"), video(3, "EP03.mkv")),
            directoryTimeoutMs = 50
        ) { _, parent ->
            calls += parent
            if (parent == "slow") awaitCancellation()
            listOf(subtitle("EP03.srt"))
        }

        assertEquals(listOf("slow", "season"), calls)
        assertEquals(setOf(3L), result.keys)
    }

    private fun video(id: Long, name: String, source: Long = 1, parent: String? = "season") = LocalMediaEntity(
        id = id, sourceId = source, fileName = name, relativePath = name,
        documentUri = "content://test/$id", parentDocumentId = parent
    )

    private fun subtitle(name: String) = LocalSubtitleLocator.SubtitleFile(
        name, name, if (name.endsWith(".ass")) "text/x-ssa" else "application/x-subrip"
    )
}
