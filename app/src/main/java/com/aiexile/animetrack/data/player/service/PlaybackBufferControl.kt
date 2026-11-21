package com.aiexile.animetrack.data.player.service

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

/** WebDAV 提前停止补缓冲；本地和普通网页播放沿用既有预算。 */
@UnstableApi
internal class PlaybackBufferControl(
    private val sampleAllocator: DefaultAllocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
) : DefaultLoadControl(
    sampleAllocator,
    BUFFER_MIN_MS, BUFFER_MIN_MS,
    BUFFER_MAX_MS, BUFFER_MAX_MS,
    DEFAULT_BUFFER_FOR_PLAYBACK_MS, DEFAULT_BUFFER_FOR_PLAYBACK_MS,
    DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    STANDARD_BUFFER_BYTES, false, false, 0, false
) {
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        isWebDav(parameters) && sampleAllocator.totalBytesAllocated >= WEBDAV_BUFFER_BYTES ||
            super.shouldStartPlayback(parameters)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        // 选轨前提取器也可能已占用样本缓冲；超过预算不再请求更多数据。
        return !(isWebDav(parameters) && sampleAllocator.totalBytesAllocated >= WEBDAV_BUFFER_BYTES) &&
            super.shouldContinueLoading(parameters)
    }

    private fun isWebDav(parameters: LoadControl.Parameters): Boolean {
        val index = parameters.timeline.getIndexOfPeriod(parameters.mediaPeriodId.periodUid)
        if (index == C.INDEX_UNSET) return false
        return parameters.timeline.getWindow(parameters.timeline.getPeriod(index, period).windowIndex, window)
            .mediaItem.mediaId.startsWith("webdav:")
    }

    companion object {
        private const val BUFFER_MIN_MS = 15_000
        private const val BUFFER_MAX_MS = 30_000
        const val WEBDAV_BUFFER_BYTES = 32 * 1024 * 1024
        const val STANDARD_BUFFER_BYTES = 48 * 1024 * 1024
    }
}
