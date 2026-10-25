package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * 圆角进度的收敛阈值。
 *
 * 共享元素默认的 bounds 动画是 `spring(stiffness = 400, damping = 1,
 * visibilityThreshold = 1px/分量)`（见 Compose `SharedTransitionDefaults`）。
 * 封面飞行距离通常几百 px，对应约 0.2% 的相对精度；圆角进度是 0~1 的
 * 归一化值，取 0.002 让两条曲线同时起步、同时收尾，避免圆角先变完、
 * 封面还在继续飞行。
 */
private const val CornerProgressVisibilityThreshold = 0.002f

/**
 * 封面共享元素转场专用 Shape：圆角随转场进度连续插值。
 *
 * 背景：卡片侧封面仅顶部两角圆角（[cornerRadius]），底部直角；
 * 详情页封面四角全圆。若两侧各自静态 clip，转场 overlay 在两侧内容间
 * 切换时底部圆角 0 ↔ cornerRadius 硬跳变，产生明显闪变。
 *
 * 本 Shape 以转场进度 p（0=卡片形态，1=详情形态）驱动：
 * 顶部两角恒为 [cornerRadius]，底部两角 = cornerRadius × p，
 * 任意时刻两侧计算出的 p 完全一致（见 [rememberSharedCoverClipShape]），
 * 无论 overlay 渲染哪一侧内容，裁剪形状都逐帧连续。
 *
 * 进度在 [createOutline]（draw 阶段）读取 State，仅触发 draw 失效，
 * 不引起逐帧重组。
 */
private class SharedCoverClipShape(
    private val cornerRadius: Dp,
    private val progressState: State<Float>
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val bottom = cornerRadius * progressState.value.coerceIn(0f, 1f)
        return SquircleShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomEnd = bottom,
            bottomStart = bottom
        ).createOutline(size, layoutDirection, density)
    }
}

/**
 * 构建随共享元素转场进度连续变化的封面裁剪 Shape。
 *
 * @param animatedVisibilityScope 当前页面所在的 AnimatedVisibilityScope
 *   （与 sharedElement 使用同一个），其 transition 同时驱动 bounds 与圆角，
 *   手势中断/动画反向时自动从当前进度平滑重定向。
 * @param cornerRadius 圆角半径（需与两侧静态圆角一致，目前均为 16dp）
 * @param detailSide true=详情页侧（可见时四角全圆）；false=卡片侧（可见时仅顶部圆角）
 *
 * 两侧映射互补：卡片侧 Visible→0、PreEnter/PostExit→1；详情侧 Visible→1、
 * PreEnter/PostExit→0。转场任意时刻（含被打断的中间态）两侧 p 相同，
 * 保证 overlay 内容切换时形状无缝衔接。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberSharedCoverClipShape(
    animatedVisibilityScope: AnimatedVisibilityScope,
    cornerRadius: Dp,
    detailSide: Boolean
): Shape {
    val transition: Transition<EnterExitState> = animatedVisibilityScope.transition
    val progressState = transition.animateFloat(
        transitionSpec = {
            // 与默认 bounds 弹簧同参数（damping=1, stiffness=400），只收窄阈值，
            // 使圆角变化和封面位移同时收尾
            spring(visibilityThreshold = CornerProgressVisibilityThreshold)
        },
        label = "sharedCoverCorner"
    ) { state ->
        val visibleProgress = if (detailSide) 1f else 0f
        if (state == EnterExitState.Visible) visibleProgress else 1f - visibleProgress
    }
    return remember(cornerRadius) { SharedCoverClipShape(cornerRadius, progressState) }
}
