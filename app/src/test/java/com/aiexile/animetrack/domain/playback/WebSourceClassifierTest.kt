package com.aiexile.animetrack.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSourceClassifierTest {
    @Test
    fun `识别 HLS 与带签名参数的清单`() {
        assertEquals(
            WebSourceClassifier.Kind.HLS,
            WebSourceClassifier.classify("https://cdn.example.com/a/index.m3u8?token=abc&exp=1")
        )
    }

    @Test
    fun `识别无扩展名的 m3u8 目录地址`() {
        assertEquals(
            WebSourceClassifier.Kind.HLS,
            WebSourceClassifier.classify("https://jx.example.com:8443/m3u8/zkzk_token_without_extension")
        )
        assertEquals(
            WebSourceClassifier.Kind.HLS,
            WebSourceClassifier.classify("https://cdn.example.com/hls/abc/seg-list")
        )
    }

    @Test
    fun `响应类型可识别无扩展名清单`() {
        assertEquals(
            WebSourceClassifier.Kind.HLS,
            WebSourceClassifier.classify(
                "https://cdn.example.com/play/abc",
                "application/vnd.apple.mpegurl"
            )
        )
        assertEquals(
            WebSourceClassifier.Kind.DASH,
            WebSourceClassifier.classify(
                "https://cdn.example.com/play/abc",
                "application/dash+xml"
            )
        )
    }

    @Test
    fun `识别 DASH 与普通视频`() {
        assertEquals(
            WebSourceClassifier.Kind.DASH,
            WebSourceClassifier.classify("https://cdn.example.com/a/manifest.mpd")
        )
        assertEquals(
            WebSourceClassifier.Kind.VIDEO,
            WebSourceClassifier.classify("https://cdn.example.com/a/video.mp4")
        )
    }

    @Test
    fun `分片和网页地址不作为候选`() {
        assertFalse(WebSourceClassifier.isCandidate("https://cdn.example.com/a/seg-001.ts"))
        assertFalse(WebSourceClassifier.isCandidate("https://example.com/watch/123"))
        assertFalse(WebSourceClassifier.isCandidate("blob:https://example.com/1234"))
    }

    @Test
    fun `候选清单返回对应类型`() {
        assertTrue(WebSourceClassifier.isCandidate("https://cdn.example.com/a/index.m3u8"))
        assertEquals("application/x-mpegURL", WebSourceClassifier.mimeType("https://x/a.m3u8"))
    }
}
