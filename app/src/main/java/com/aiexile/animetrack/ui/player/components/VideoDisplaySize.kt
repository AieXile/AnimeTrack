package com.aiexile.animetrack.ui.player.components

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import kotlin.math.roundToInt

/** 视频在屏幕上的实际宽高，已考虑旋转与像素宽高比。 */
internal data class VideoDisplaySize(val width: Float, val height: Float) {
    val aspectRatio: Float get() = width / height
    val isLandscape: Boolean get() = width > height

    val bufferWidth: Int get() = width.roundToInt().coerceAtLeast(1)
    val bufferHeight: Int get() = height.roundToInt().coerceAtLeast(1)

    companion object {
        fun from(width: Int, height: Int, pixelRatio: Float, rotationDegrees: Int = 0): VideoDisplaySize? {
            if (width <= 0 || height <= 0) return null
            val displayWidth = width * (pixelRatio.takeIf { it.isFinite() && it > 0f } ?: 1f)
            return when (rotationDegrees.mod(360)) {
                90, 270 -> VideoDisplaySize(height.toFloat(), displayWidth)
                else -> VideoDisplaySize(displayWidth, height.toFloat())
            }
        }
    }
}

internal fun Player.renderedVideoDisplaySize(): VideoDisplaySize? =
    videoSize.let { VideoDisplaySize.from(it.width, it.height, it.pixelWidthHeightRatio) }

/** 特效字幕管线可能没有渲染尺寸，此时使用当前选中视频轨。 */
internal fun Player.trackVideoDisplaySize(): VideoDisplaySize? =
    currentTracks.selectedVideoFormats().firstNotNullOfOrNull(Format::displaySize)

/** 自适应播放可能选中多个清晰度，未知实际渲染轨道时仅使用一致的方向。 */
internal fun Tracks.selectedVideoLandscape(): Boolean? =
    commonVideoLandscape(selectedVideoFormats().map(Format::displaySize))

internal fun commonVideoLandscape(sizes: Sequence<VideoDisplaySize?>): Boolean? =
    sizes.map { it?.isLandscape }.distinct().singleOrNull()

private fun Tracks.selectedVideoFormats(): Sequence<Format> = groups.asSequence()
    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
    .flatMap { group ->
        (0 until group.length).asSequence().filter(group::isTrackSelected).map(group::getTrackFormat)
    }

private fun Format.displaySize(): VideoDisplaySize? =
    VideoDisplaySize.from(width, height, pixelWidthHeightRatio, rotationDegrees)
