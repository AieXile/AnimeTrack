package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState

/** 屏幕内可见的模糊区高度：底部 24dp 边距 + 接近胶囊顶部（78dp），顶部为渐隐区 */
private val NavBackdropBlurVisibleHeight = 72.dp

/** 上边缘渐隐区间占比：0.3 × 72dp ≈ 21.6dp 内由无模糊过渡到满强度（沿用重构前参数） */
private const val NavBackdropBlurFadeFraction = 0.3f

/** 导航栏磨砂层：顶部 0→满强度，其余高度满强度；两种材质共用同一过渡带参数 */
private val NavBackdropBlurSpan = ProgressiveBlurSpan(
    startFraction = 0f,
    endFraction = NavBackdropBlurFadeFraction,
    startIntensity = 0f,
    endIntensity = 1f
)

/**
 * 液态玻璃模式下导航栏四周的背景模糊层（类 iOS Tab Bar 的底部磨砂区域）。
 *
 * 渲染在悬浮胶囊下层：胶囊自身采样页面内容层（navBackdrop 不含本层），
 * 因此胶囊保持清晰的折射玻璃，仅四周区域呈现磨砂模糊。
 *
 * 底边必须与屏幕底边严格齐平：采样源只覆盖屏幕范围，
 * 若向下超出屏幕，超出的部分在采样源中为透明像素，经 blur 的 TileMode.Clamp
 * 延拓后会把透明混入屏幕内最底部，表现为屏幕最下边缘模糊「消失」。
 */
@Composable
internal fun LiquidGlassNavBackdropBlur(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    // 淡底色与胶囊/状态栏玻璃条同源（LiquidGlassKind.Scrim），仅低一档 alpha：
    // 提升磨砂区域实感，避免纯模糊在亮色内容下发透
    val scrimSpec = liquidGlassSpec(LiquidGlassKind.Scrim)
    NavBackdropBlurFrame(
        tint = scrimSpec.containerColor,
        modifier = modifier
    ) {
        ProgressiveBackdropBlur(
            backdrop = backdrop,
            maxBlurRadius = scrimSpec.blurRadius,
            span = NavBackdropBlurSpan,
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * 高级模糊（haze）模式下导航栏四周的磨砂层。
 *
 * 视觉参数与 [LiquidGlassNavBackdropBlur] 对齐：72dp 高、8dp 满强度、顶部 30% 渐隐、
 * 30% 淡底色。模糊只走 haze；胶囊本体的 hazeEffect 采样源与液态玻璃档一致
 * （只录页面内容层，不含本层），因此不会被这层磨砂反向污染。
 */
@Composable
internal fun HazeNavBackdropBlur(
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val scrimSpec = liquidGlassSpec(LiquidGlassKind.Scrim)
    NavBackdropBlurFrame(
        tint = scrimSpec.containerColor,
        modifier = modifier
    ) {
        ProgressiveHazeBlur(
            hazeState = hazeState,
            maxBlurRadius = scrimSpec.blurRadius,
            span = NavBackdropBlurSpan,
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * 磨砂层骨架：模糊本体（子节点）+ 淡底色（独立节点，按过渡带淡入，与旧 DstIn 遮罩等价）。
 * 遮罩不再套在模糊节点上，避免 DstIn 与 RenderEffect 同节点互相干扰。
 */
@Composable
private fun NavBackdropBlurFrame(
    tint: Color,
    modifier: Modifier,
    blurLayer: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(NavBackdropBlurVisibleHeight)
    ) {
        blurLayer()
        Box(
            Modifier
                .matchParentSize()
                .background(verticalFadeTintBrush(tint, NavBackdropBlurFadeFraction))
        )
    }
}
