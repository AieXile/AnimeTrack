package com.aiexile.animetrack.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSpecTest {

    @Test
    fun `支持常见外挂字幕扩展名与MIME映射`() {
        assertEquals("application/x-subrip", SubtitleSpec.mimeTypeForFileName("ep01.srt"))
        assertEquals("text/vtt", SubtitleSpec.mimeTypeForFileName("ep01.VTT"))
        assertEquals("text/x-ssa", SubtitleSpec.mimeTypeForFileName("ep01.ass"))
        assertEquals("text/x-ssa", SubtitleSpec.mimeTypeForFileName("ep01.SSA"))
        assertEquals("application/ttml+xml", SubtitleSpec.mimeTypeForFileName("ep01.ttml"))
        assertNull(SubtitleSpec.mimeTypeForFileName("ep01.mkv"))
        assertNull(SubtitleSpec.mimeTypeForFileName("noextension"))
        assertTrue(SubtitleSpec.isSupported("movie.ass"))
        assertFalse(SubtitleSpec.isSupported("movie.mp4"))
    }

    @Test
    fun `同名与前缀匹配区分精准度且防止EP01误匹配EP012`() {
        assertEquals(SubtitleSpec.MATCH_EXACT, SubtitleSpec.matchScore("Show - EP01.ass", "Show - EP01"))
        assertEquals(SubtitleSpec.MATCH_EXACT, SubtitleSpec.matchScore("show - ep01.srt", "Show - EP01"))
        assertEquals(SubtitleSpec.MATCH_PREFIX, SubtitleSpec.matchScore("Show - EP01.chs.ass", "Show - EP01"))
        assertEquals(SubtitleSpec.MATCH_PREFIX, SubtitleSpec.matchScore("Show - EP01 [简日].srt", "Show - EP01"))
        assertEquals(SubtitleSpec.MATCH_NONE, SubtitleSpec.matchScore("Show - EP012.ass", "Show - EP01"))
        assertEquals(SubtitleSpec.MATCH_NONE, SubtitleSpec.matchScore("Other.ass", "Show - EP01"))
        assertEquals(SubtitleSpec.MATCH_NONE, SubtitleSpec.matchScore("anything.ass", "   "))
    }

    @Test
    fun `语言推测覆盖简繁日英常见标识`() {
        assertEquals("zh-CN", SubtitleSpec.guessLanguage("Show.EP01.CHS.ass"))
        assertEquals("zh-CN", SubtitleSpec.guessLanguage("Show.EP01.简体.srt"))
        assertEquals("zh-TW", SubtitleSpec.guessLanguage("Show.EP01.cht.ass"))
        assertEquals("zh-TW", SubtitleSpec.guessLanguage("Show.EP01.繁体.srt"))
        assertEquals("ja", SubtitleSpec.guessLanguage("Show.EP01.jpn.ass"))
        assertEquals("en", SubtitleSpec.guessLanguage("Show.EP01.eng.vtt"))
        assertNull(SubtitleSpec.guessLanguage("Show.EP01.ass"))
    }

    @Test
    fun `语言识别只在后缀边界命中且混合双语不强猜`() {
        assertEquals("en", SubtitleSpec.guessLanguage("Show.E01.en.ass"))
        assertEquals("zh-CN", SubtitleSpec.guessLanguage("Show.E01.zh-CN.ass"))
        assertNull(SubtitleSpec.guessLanguage("Show.E01.engx.ass"))
        assertNull(SubtitleSpec.guessLanguage("Show.E01.简日.ass"))
    }
}
