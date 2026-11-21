package com.aiexile.animetrack.data.player.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.upstream.Allocation
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackBufferControlTest {
    @Test
    fun webDavStopsAtByteBudgetAndResumesWhenSamplesAreDiscarded() {
        val control = PlaybackBufferControl()
        val parameters = parameters("webdav:episode", "https://example.invalid/episode.mkv")
        val allocator = control.getAllocator(parameters.playerId)
        val samples = mutableListOf<Allocation>()
        control.onPrepared(parameters.playerId)
        try {
            assertTrue(control.shouldContinueLoading(parameters))
            val count = PlaybackBufferControl.WEBDAV_BUFFER_BYTES / allocator.individualAllocationLength
            repeat(count) { samples += allocator.allocate() }
            assertFalse(control.shouldContinueLoading(parameters))
            control.onTracksSelected(parameters, TrackGroupArray.EMPTY, emptyArray())
            assertTrue(control.shouldStartPlayback(parameters))
            allocator.release(samples.removeAt(samples.lastIndex))
            assertTrue(control.shouldContinueLoading(parameters))
        } finally {
            samples.forEach(allocator::release)
            control.onReleased(parameters.playerId)
        }
    }

    @Test
    fun ordinaryWebPlaybackKeepsItsExistingBufferBudget() {
        val control = PlaybackBufferControl()
        val parameters = parameters("web:episode", "https://example.invalid/episode.mp4")
        val allocator = control.getAllocator(parameters.playerId)
        val samples = mutableListOf<Allocation>()
        control.onPrepared(parameters.playerId)
        try {
            control.onTracksSelected(parameters, TrackGroupArray.EMPTY, emptyArray())
            val segmentSize = allocator.individualAllocationLength
            val webDavSegments = PlaybackBufferControl.WEBDAV_BUFFER_BYTES / segmentSize
            repeat(webDavSegments) { samples += allocator.allocate() }
            assertTrue(control.shouldContinueLoading(parameters))
            repeat(PlaybackBufferControl.STANDARD_BUFFER_BYTES / segmentSize - webDavSegments) {
                samples += allocator.allocate()
            }
            assertFalse(control.shouldContinueLoading(parameters))
        } finally {
            samples.forEach(allocator::release)
            control.onReleased(parameters.playerId)
        }
    }

    private fun parameters(mediaId: String, url: String): LoadControl.Parameters {
        val item = MediaItem.Builder().setMediaId(mediaId).setUri(url).build()
        val timeline = SinglePeriodTimeline(120_000_000L, true, false, false, null, item)
        return LoadControl.Parameters(
            PlayerId.UNSET, timeline, MediaPeriodId(timeline.getUidOfPeriod(0)),
            0L, 0L, 1f, true, false, C.TIME_UNSET, C.TIME_UNSET
        )
    }
}
