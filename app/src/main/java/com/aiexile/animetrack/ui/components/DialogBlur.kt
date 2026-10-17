package com.aiexile.animetrack.ui.components

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

/** Dialog 背景模糊默认半径，与全局高级模糊默认值保持一致 */
private val DefaultDialogBlurRadius = 24.dp

/**
 * Dialog 窗口背景模糊（Android 12+）：通过系统级 [WindowManager.LayoutParams.FLAG_BLUR_BEHIND]
 * 由系统合成器在窗口层模糊 Dialog 背后的内容。
 *
 * 注意：Dialog 窗口内禁止使用 haze/backdrop 等跨节点 GraphicsLayer 采样模糊
 * （会在部分用户设备触发 RenderThread SIGSEGV），本扩展不经过应用渲染树，规避该风险。
 *
 * Android 12 以下、或系统禁用跨窗口模糊（省电模式等）时，
 * 系统自动回退为现有的 FLAG_DIM_BEHIND 半透明遮罩，无需额外处理。
 *
 * 用法：挂在 Dialog 内容根节点（或 AlertDialog/DatePickerDialog 的 modifier 参数）上，
 * 仅在其所属 Dialog 窗口内生效，主窗口内使用无效。
 */
fun Modifier.dialogBlurBehind(radius: Dp = DefaultDialogBlurRadius): Modifier = composed {
    val view = LocalView.current
    val density = LocalDensity.current
    DisposableEffect(view, radius) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.enableBlurBehind(with(density) { radius.toPx() }.roundToInt())
        }
        onDispose {
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
    this
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.enableBlurBehind(radiusPx: Int) {
    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    // dim 与 blur 在 Dialog 首帧绘制前一并设置：
    // dim 降至 0.3（默认约 0.6）避免与模糊叠加过暗，
    // 同时窗口显示时即为最终值，减轻 dim 渐入与 blur 生效的先后感
    attributes = attributes.apply {
        setBlurBehindRadius(radiusPx)
        dimAmount = 0.3f
    }
}
