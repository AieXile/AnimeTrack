package com.aiexile.animetrack.ui.player.components

import android.util.Size
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import kotlin.math.roundToInt

/**
 * 把 [surfaceView] 的输出缓冲区固定为视频自身的显示分辨率（而非控件尺寸）。
 *
 * 特效字幕（EFFECTS_OPEN_GL）管线按输出 Surface 的缓冲区尺寸做 GL 渲染：
 * - 缓冲区跟随控件尺寸时，暂停下的进/出全屏会重排缓冲区，最后一帧停在左上角（画面偏移）；
 * - 每次改尺寸都会让管线重建输出，恢复播放时首帧短暂按旧尺寸绘制（画面缩小再放大）。
 *
 * 固定成视频原生尺寸后，Surface 在播放、暂停、进出全屏过程中都不再变化，由系统合成器
 * 等比缩放到控件边界；这与不走特效时 MediaCodec 直接输出到视频分辨率的行为一致。
 *
 * 仅在「适应屏幕」（FIT）模式下启用；填满/裁剪依赖控件区域比例，仍跟随布局尺寸。
 */
internal class VideoSurfaceBuffer(
    private val surfaceView: SurfaceView,
    private val player: Player,
    private val useNativeSize: () -> Boolean
) : Player.Listener {

    init {
        player.addListener(this)
        applySize()
    }

    fun release() {
        player.removeListener(this)
        surfaceView.holder.setSizeFromLayout()
    }

    /** 视频缩放模式变化时重新套用固定尺寸 */
    fun refresh() = applySize()

    override fun onTracksChanged(tracks: Tracks) = applySize()

    override fun onVideoSizeChanged(videoSize: VideoSize) = applySize()

    private fun applySize() {
        val holder = surfaceView.holder
        val size = if (useNativeSize()) resolveDisplaySize() else null
        if (size != null) {
            holder.setFixedSize(size.width, size.height)
        } else {
            holder.setSizeFromLayout()
        }
    }

    /** 当前视频的显示分辨率；特效管线下 player.videoSize 通常不可用，退回轨道 Format。 */
    private fun resolveDisplaySize(): Size? {
        val video = player.videoSize
        if (video.width > 0 && video.height > 0) {
            val ratio = video.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
            return Size((video.width * ratio).roundToInt(), video.height)
        }
        val group = player.currentTracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_VIDEO && it.isSelected
        } ?: return null
        val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
        val format = group.getTrackFormat(index)
        if (format.width <= 0 || format.height <= 0) return null
        val ratio = format.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
        val width = (format.width * ratio).roundToInt()
        return if (format.rotationDegrees % 180 != 0) Size(format.height, width)
        else Size(width, format.height)
    }
}
