package com.aiexile.animetrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntSize
import com.aiexile.animetrack.ui.theme.isAppDarkTheme

/** 微光循环周期（ms）：光带扫过约 1.3s，两端各留一段空白停顿 */
internal const val ShimmerCycleDurationMs = 2200

/** 点击触发的一次扫光时长（ms） */
internal const val ShimmerSweepDurationMs = 750

/** 光带半宽占元素长边的比例：渐变在光带内从基础色过渡到高光再回到基础色 */
internal const val ShimmerBandWidthRatio = 0.4f

/** 循环扫光进度区间：光带从元素左侧外扫到右侧外，区间两端在元素之外 */
internal const val ShimmerProgressStart = -1f
internal const val ShimmerProgressEnd = 2f

/** 单次扫光进度区间：光带刚好从左侧外进入、右侧外离开 */
internal const val ShimmerSweepStart = -0.4f
internal const val ShimmerSweepEnd = 1.4f

/**
 * 光带中心的高光色：文字向白提亮，光带两端保持原色，循环重开时视觉无跳变。
 * 浅色主题下深色文字向白提亮太多会失去轮廓，高光幅度收窄；
 * 近白文字则直接取纯白，尽可能拉开与基础色的差值。
 */
@Composable
internal fun shimmerGlowColor(base: Color): Color {
    if (base.luminance() > 0.7f) return Color.White
    val amount = if (isAppDarkTheme()) 0.72f else 0.45f
    return lerp(base, Color.White, amount)
}

/**
 * 微光画笔：一道柔和高光沿对角线（左下 → 右上）扫过元素。
 *
 * [progress] 为光带中心在元素宽度上的归一化位置，取值可以小于 0 或大于 1；
 * 光带移出元素后渐变全是基础色，因此循环重新开始时视觉无跳变。
 * 文字以该画笔渲染（`TextStyle.brush`），图片类内容用它作为叠加层。
 */
internal fun shimmerBrush(
    size: IntSize,
    progress: Float,
    baseColor: Color,
    glowColor: Color,
    bandWidthRatio: Float = ShimmerBandWidthRatio,
): Brush {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    val centerX = width * progress
    val halfBand = maxOf(width, height) * bandWidthRatio
    return Brush.linearGradient(
        colorStops = arrayOf(
            0f to baseColor,
            0.5f to glowColor,
            1f to baseColor
        ),
        start = Offset(centerX - halfBand, height),
        end = Offset(centerX + halfBand, 0f)
    )
}
