package com.aiexile.animetrack.ui.player.coordinator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTrackerTest {

    @Test
    fun `正常倍速和慢放有最小抖动容忍下限`() {
        assertEquals(3_000L, PlaybackProgressTracker.maxContinuousWatchDeltaMs(0.5f))
        assertEquals(3_000L, PlaybackProgressTracker.maxContinuousWatchDeltaMs(1.0f))
    }

    @Test
    fun `高倍速播放按比例放宽连续观看采样阈值`() {
        val at2x = PlaybackProgressTracker.maxContinuousWatchDeltaMs(2.0f)
        val at3x = PlaybackProgressTracker.maxContinuousWatchDeltaMs(3.0f)
        assertEquals(4_000L, at2x)
        assertEquals(6_000L, at3x)
        assertTrue(at3x > at2x)
    }
}