package com.aiexile.animetrack.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPlaybackSessionTest {
    @Test
    fun `UA 与 Referer 应用于任意主机`() {
        val headers = WebPlaybackSession.buildHeaders(
            userAgent = "TestUA",
            referer = "https://example.com/watch/1",
            cookiesByHost = emptyMap(),
            host = "cdn.example.net"
        )

        assertEquals("TestUA", headers["User-Agent"])
        assertEquals("https://example.com/watch/1", headers["Referer"])
        assertFalse(headers.containsKey("Cookie"))
    }

    @Test
    fun `Cookie 只发给记录过的主机`() {
        val cookies = mapOf("example.com" to "sid=1", "cdn.example.com" to "cdn=2")

        assertTrue(
            WebPlaybackSession.buildHeaders("UA", "https://example.com", cookies, "example.com")
                .containsKey("Cookie")
        )
        assertFalse(
            WebPlaybackSession.buildHeaders("UA", "https://example.com", cookies, "third-party.net")
                .containsKey("Cookie")
        )
    }

    @Test
    fun `未捕获请求时使用规则 UA 和 Referer`() {
        val headers = WebPlaybackSession.buildHeaders(
            "RuleUA", "https://source.example/", emptyMap(), "media.example"
        )
        assertEquals("RuleUA", headers["User-Agent"])
        assertEquals("https://source.example/", headers["Referer"])
    }

    @Test
    fun `实际媒体请求头优先且大小写不敏感`() {
        val headers = WebPlaybackSession.buildHeaders(
            userAgent = "RuleUA",
            referer = "https://source.example/",
            cookiesByHost = mapOf("media.example" to "stored=1"),
            host = "media.example",
            captured = mapOf(
                "user-agent" to "ActualUA",
                "referer" to "https://iframe.example/",
                "cookie" to "actual=1"
            )
        )
        assertEquals("ActualUA", headers["user-agent"])
        assertEquals("https://iframe.example/", headers["referer"])
        assertEquals("actual=1", headers["cookie"])
        assertEquals(1, headers.keys.count { it.equals("User-Agent", true) })
        assertEquals(1, headers.keys.count { it.equals("Referer", true) })
        assertEquals(1, headers.keys.count { it.equals("Cookie", true) })
    }

    @Test
    fun `捕获请求未发送 Referer 时不强加规则 Referer 和 Origin`() {
        val headers = WebPlaybackSession.buildHeaders(
            "RuleUA", "https://source.example/", emptyMap(), "media.example",
            origin = "https://source.example",
            captured = mapOf("Accept" to "*/*")
        )
        assertEquals("RuleUA", headers["User-Agent"])
        assertFalse(headers.keys.any { it.equals("Referer", true) || it.equals("Origin", true) })
    }

    @Test
    fun `捕获请求缺少 Cookie 时仅补充当前主机的 Cookie`() {
        val headers = WebPlaybackSession.buildHeaders(
            "UA", null, mapOf("media.example" to "sid=1"), "media.example",
            captured = mapOf("Accept" to "*/*", "Range" to "bytes=0-", "Host" to "old.example")
        )
        assertEquals("sid=1", headers["Cookie"])
        assertFalse(headers.containsKey("Range"))
        assertFalse(headers.containsKey("Host"))
        val otherHost = WebPlaybackSession.buildHeaders(
            "UA", null, mapOf("media.example" to "sid=1"), "other.example"
        )
        assertFalse(otherHost.containsKey("Cookie"))
    }
}
