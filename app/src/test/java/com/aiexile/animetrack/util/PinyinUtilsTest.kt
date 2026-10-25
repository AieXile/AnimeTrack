package com.aiexile.animetrack.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinUtilsTest {

    @Test
    fun `汉字转无声调全拼`() {
        assertEquals("jinjidejuren", titleSortKey("进击的巨人"))
        assertEquals("wotuidehaizi", titleSortKey("我推的孩子"))
    }

    @Test
    fun `标点与空白不参与排序键`() {
        assertEquals("wotuidehaizi", titleSortKey("【我推的孩子】"))
        assertEquals("86bucunzaidezhanqu", titleSortKey("86 - 不存在的战区"))
    }

    @Test
    fun `拉丁字母统一小写并支持全角`() {
        assertEquals("steinsgate", titleSortKey("Steins;Gate"))
        assertEquals("trigun", titleSortKey("ＴＲＩＧＵＮ"))
    }

    @Test
    fun `拼音键让中英文标题按同一字母序交错`() {
        val keys = listOf("进击的巨人", "AIR", "白箱", "CLANNAD").map(::titleSortKey)
        assertTrue(keys[0] > keys[1]) // jin... 排在 air 之后
        assertTrue(keys[2] < keys[3]) // bai... 排在 clannad 之前
    }
}
