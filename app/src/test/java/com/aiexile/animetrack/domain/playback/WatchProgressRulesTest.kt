package com.aiexile.animetrack.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressRulesTest {
    @Test
    fun `无既有进度时从第一集开始连续计算`() {
        assertEquals(0, WatchProgressRules.advanceCount(emptySet(), 0))
        assertEquals(2, WatchProgressRules.advanceCount(setOf(1, 2, 5), 0))
        assertEquals(4, WatchProgressRules.advanceCount(setOf(1, 2, 3, 4), 0))
    }

    @Test
    fun `有既有进度时从下一集继续推进`() {
        assertEquals(7, WatchProgressRules.advanceCount(setOf(6, 7), 5))
        assertEquals(6, WatchProgressRules.advanceCount(setOf(1, 2, 3, 4, 5, 6), 5))
    }

    @Test
    fun `跳看不会越过中间缺失的集`() {
        assertEquals(5, WatchProgressRules.advanceCount(setOf(7), 5))
    }

    @Test
    fun `直接拖到片尾不算完成`() {
        assertFalse(
            WatchProgressRules.isCompleted(
                positionMs = 1_440_000,
                durationMs = 1_440_000,
                watchedMs = 5_000
            )
        )
    }

    @Test
    fun `自然播完或有效观看达到阈值算完成`() {
        assertTrue(
            WatchProgressRules.isCompleted(
                positionMs = 1_440_000,
                durationMs = 1_440_000,
                watchedMs = 1_400_000
            )
        )
        assertTrue(
            WatchProgressRules.isCompleted(
                positionMs = 100_000,
                durationMs = 1_440_000,
                watchedMs = 1_300_000
            )
        )
    }

    @Test
    fun `看过大部分再快进结尾仍算完成`() {
        assertTrue(
            WatchProgressRules.isCompleted(
                positionMs = 1_440_000,
                durationMs = 1_440_000,
                watchedMs = 900_000
            )
        )
    }

    @Test
    fun `时长未知时不做完成判定`() {
        assertFalse(WatchProgressRules.isCompleted(0, 0, 0))
    }
}
