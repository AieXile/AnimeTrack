package com.aiexile.animetrack.ui.player.components

import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.player.PlaybackTrace
import android.view.SurfaceHolder

/** 播放器画面容器：绑定 Media3 PlayerView，并在 libass 特效渲染下维持视频比例与暂停缓冲帧。 */
@UnstableApi
@Composable
internal fun PlayerViewContainer(
    player: Player,
    videoScale: Int,
    hideSurface: Boolean = false,
    modifier: Modifier = Modifier
) {
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    if (BuildConfig.DEBUG) {
        DisposableEffect(player) {
            val observer = object : Player.Listener {
                override fun onRenderedFirstFrame() = PlaybackTrace.event {
                    "ui.firstFrame pos=${player.currentPosition} view=${playerView?.let(System::identityHashCode)}"
                }
            }
            player.addListener(observer)
            PlaybackTrace.event { "ui.observeFrames pos=${player.currentPosition}" }
            onDispose { player.removeListener(observer) }
        }
    }
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx)
                .inflate(R.layout.player_view, null, false) as PlayerView).apply {
                // API 34 上 SurfaceView 在 Compose AndroidView 内可能丢失尺寸事务（画面拉伸/裁切），
                // media3 提供了官方 workaround（更高版本系统已修复，仅 API 34 生效）
                setEnableComposeSurfaceSyncWorkaround(true)
                PlaybackTrace.event { "ui.bind view=${System.identityHashCode(this)} hidden=$hideSurface pos=${player.currentPosition}" }
                this.player = player
                visibility = if (hideSurface) View.INVISIBLE else View.VISIBLE
            }.also { playerView = it }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.resizeMode = when (videoScale) {
                1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            val targetVisibility = if (hideSurface) View.INVISIBLE else View.VISIBLE
            if (view.visibility != targetVisibility) {
                PlaybackTrace.event { "ui.visibility view=${System.identityHashCode(view)} hidden=$hideSurface" }
                view.visibility = targetVisibility
            }
        },
        onRelease = { view ->
            // 退出或错误页替换画面时卸下监听和 Surface；全屏切换仍复用原视图。
            PlaybackTrace.event { "ui.unbind view=${System.identityHashCode(view)}" }
            view.player = null
            if (playerView === view) playerView = null
        },
        modifier = modifier
    )

    // 特效字幕（ass-media 调用 setVideoEffects）会让 media3 停止上报 VideoSize，
    // PlayerView 的内容框比例保持为 0，画面被拉伸填满任意容器（全屏与旋转时尤其明显）。
    // 这里退回当前视频轨的 Format 计算显示比例，比例正确后全屏/竖屏过渡按原比例留黑边。
    val view = playerView
    if (view != null) {
        DisposableEffect(view, player) {
            val listener = object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) =
                    applyVideoAspectFallback(view, player)

                override fun onVideoSizeChanged(videoSize: VideoSize) =
                    applyVideoAspectFallback(view, player)
            }
            player.addListener(listener)
            applyVideoAspectFallback(view, player)
            onDispose { player.removeListener(listener) }
        }
    }

    // 暂停时固定 SurfaceView 缓冲区：特效字幕管线暂停不重绘，
    // 布局变化（进/出全屏）若让缓冲区跟随尺寸，会裁切/偏移最后一帧
    val surfaceView = playerView?.videoSurfaceView as? SurfaceView
    if (surfaceView != null) {
        if (BuildConfig.DEBUG) {
            DisposableEffect(surfaceView) {
                val holder = surfaceView.holder
                val id = System.identityHashCode(surfaceView)
                val observer = object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = PlaybackTrace.event { "surface.created id=$id" }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                        PlaybackTrace.event { "surface.changed id=$id size=${width}x$height" }
                    override fun surfaceDestroyed(holder: SurfaceHolder) = PlaybackTrace.event { "surface.destroyed id=$id" }
                }
                holder.addCallback(observer)
                PlaybackTrace.event { "surface.observe id=$id valid=${holder.surface.isValid} size=${holder.surfaceFrame.width()}x${holder.surfaceFrame.height()}" }
                onDispose { holder.removeCallback(observer) }
            }
        }
        DisposableEffect(surfaceView, player, videoScale) {
            val keeper = VideoSurfaceBuffer(surfaceView, player) { videoScale == 0 }
            keeper.refresh()
            onDispose { keeper.release() }
        }
    }
}

/**
 * 特效字幕管线下 PlayerView 收不到 VideoSize，内容框比例会停在 0（画面填满容器被拉伸）。
 * 用当前选中视频轨的 Format 计算显示宽高比补上；media3 正常上报尺寸时不做处理。
 */
@UnstableApi
private fun applyVideoAspectFallback(view: PlayerView, player: Player) {
    if (player.renderedVideoDisplaySize() != null) return
    val size = player.trackVideoDisplaySize() ?: return
    val contentFrame = view.findViewById<AspectRatioFrameLayout>(
        androidx.media3.ui.R.id.exo_content_frame
    ) ?: return
    contentFrame.setAspectRatio(size.aspectRatio)
}
