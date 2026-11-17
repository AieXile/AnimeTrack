package com.aiexile.animetrack.domain.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressRulesTest {
    @Test
    fun `连续完成集数从第一集开始计算`() {
        assertEquals(0, WatchProgressRules.contiguousCompleted(emptySet()))
        assertEquals(2, WatchProgressRules.contiguousCompleted(setOf(1, 2, 5)))
        assertEquals(4, WatchProgressRules.contiguousCompleted(setOf(1, 2, 3, 4)))
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
