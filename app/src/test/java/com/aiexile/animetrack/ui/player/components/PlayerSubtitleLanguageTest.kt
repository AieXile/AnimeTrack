package com.aiexile.animetrack.ui.player.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PlayerSubtitleLanguageTest {
    @Test
    fun `使用界面语言显示字幕语言而不是原始代码`() {
        assertEquals("Japanese", subtitleLanguageLabel("jpn", Locale.ENGLISH))
        assertEquals("日语", subtitleLanguageLabel("ja", Locale.SIMPLIFIED_CHINESE))
        assertEquals("English", subtitleLanguageLabel("en", Locale.ENGLISH))
    }

    @Test
    fun `中文地区与书写体系保留各自的显示名`() {
        for ((first, second) in listOf("zh-CN" to "zh-TW", "zh-Hans" to "zh-Hant")) {
            assertEquals(Locale.forLanguageTag(first).getDisplayName(Locale.ENGLISH),
                subtitleLanguageLabel(first, Locale.ENGLISH))
            assertNotEquals(subtitleLanguageLabel(first, Locale.ENGLISH), subtitleLanguageLabel(second, Locale.ENGLISH))
        }
    }

    @Test
    fun `没有明确语言时不显示语言标题`() {
        listOf(null, "", "und", "mul", "zxx").forEach { tag ->
            assertNull(subtitleLanguageLabel(tag, Locale.ENGLISH))
        }
    }
}
