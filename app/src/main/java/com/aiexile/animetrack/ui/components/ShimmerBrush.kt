package com.aiexile.animetrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
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

/**
 * 微光的可移动画笔（文字和内容叠加层共用）：原渐变的两个端点仅沿 X 轴平移，
 * 因此复用 progress=0 的 shader 并平移其局部矩阵，与每帧重建渐变等价，不需要额外离屏合成。
 *
 * 必须在 drawWithContent 中先 [updateProgress] 再 drawContent；Compose 文字会直接调用
 * createShader（不经过 Brush.applyTo），因此更新原生 shader，而非 ShaderBrush.transform。
 * 每个文字节点独占实例，按实测尺寸和颜色重建，避免不同文字互相覆盖光带位置。
 */
internal class AnimatedShimmerBrush(
    private val textSize: IntSize,
    baseColor: Color,
    glowColor: Color,
    bandWidthRatio: Float = ShimmerBandWidthRatio,
) : ShaderBrush() {
    private val shader = (shimmerBrush(textSize, 0f, baseColor, glowColor, bandWidthRatio) as ShaderBrush)
        .createShader(Size(textSize.width.toFloat(), textSize.height.toFloat()))
    private val translation = android.graphics.Matrix()

    override fun createShader(size: Size): Shader = shader

    fun updateProgress(progress: Float) {
        translation.setTranslate(textSize.width * progress, 0f)
        shader.setLocalMatrix(translation)
    }
}

/** 未测量时保留原色；尺寸、基础色或应用明暗导致的高光色变化时重建 shader。 */
@Composable
internal fun rememberAnimatedShimmerBrush(size: IntSize, baseColor: Color): AnimatedShimmerBrush? {
    val glowColor = shimmerGlowColor(baseColor)
    return remember(size, baseColor, glowColor) {
        if (size.width > 0 && size.height > 0) AnimatedShimmerBrush(size, baseColor, glowColor) else null
    }
}
