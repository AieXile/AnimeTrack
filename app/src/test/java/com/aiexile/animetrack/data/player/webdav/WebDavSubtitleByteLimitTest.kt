package com.aiexile.animetrack.data.player.webdav

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavSubtitleByteLimitTest {
    @Test
    fun `报告超限时不读取任何字节`() {
        val limit = WebDavSubtitleBudget.MAX_BYTES
        assertThrows(WebDavSubtitleException::class.java) {
            WebDavSubtitleBudget.readBounded(Buffer(), 0L, limit + 1) { _, _ -> error("Must not read") }
        }
    }

    @Test
    fun `无长度或少报长度时也只多读一个字节`() {
        val limit = WebDavSubtitleBudget.MAX_BYTES
        for (reported in listOf(-1L, 10L)) {
            var read = 0L
            val buffer = Buffer()
            try {
                assertThrows(WebDavSubtitleException::class.java) {
                    WebDavSubtitleBudget.readBounded(buffer, 0L, reported) { _, count ->
                        read += count
                        count
                    }
                }
                assertEquals(limit + 1, read)
                assertEquals(limit, buffer.size)
            } finally {
                buffer.clear()
            }
        }
    }

    @Test
    fun `未知长度读完后报告精确大小`() {
        val buffer = Buffer()
        try {
            val source = Buffer().writeUtf8("test")
            assertEquals(4L, WebDavSubtitleBudget.readBounded(buffer, 0L, -1L, { bytes, count ->
                source.read(bytes, 0, count)
            }))
            assertEquals("test", buffer.readUtf8())
        } finally {
            buffer.clear()
        }
    }
}
