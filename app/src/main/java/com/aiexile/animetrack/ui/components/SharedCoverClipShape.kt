package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope.SharedContentState
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
 * 详情页封面角标的淡出区间（进度占比）。
 *
 * 角标与封面同属一个 sharedElement，飞行中由 overlay 一起绘制；详情页
 * 静止态不在封面上显示角标（状态徽章在标题行），所以到达目标前需淡出。
 * 只在转场末段（进度 > 1 - 本值）淡出，前半程保持完整不透明，视觉上
 * 仍是"角标随封面飞出"而不是一路变淡。
 */
private const val BadgeFadeOutPortion = 0.35f

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
    private val progressState: State<Float>,
    private val detailSide: Boolean,
    private val animationActive: () -> Boolean
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // 只有「本元素确实处于共享元素转场中」才使用进度值。转场被导航切换打断、
        // 或页面在飞行途中被替换/重建时，animateFloat 的进度可能停留在中途值上
        // （卡片侧常见为停留在 1，封面底部圆角由此一直保留）。静止态按所在侧的
        // 固定形态裁剪，保证任何残留进度都不会漏到画面上。
        val progress = if (animationActive()) {
            progressState.value
        } else if (detailSide) {
            1f
        } else {
            0f
        }
        val bottom = cornerRadius * progress.coerceIn(0f, 1f)
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
 * @param animationActive 当前是否真的处于这条封面共享元素的转场中。为 false
 *   （静止态、转场被打断后残留、页面切换前后）时不使用进度值，直接按所在侧
 *   的固定圆角裁剪，避免残留进度把卡片封面底部圆角永久留在画面上。
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
    detailSide: Boolean,
    animationActive: () -> Boolean = { true }
): Shape {
    val progressState = rememberSharedCoverProgress(animatedVisibilityScope, detailSide)
    return remember(cornerRadius, detailSide) {
        SharedCoverClipShape(cornerRadius, progressState, detailSide, animationActive)
    }
}

/**
 * 详情页封面上"角标内容"的透明度（1=完整显示，0=隐藏）。
 *
 * 由封面共享元素内容绘制，与封面同层、同步位移缩放，到达目标前在末段
 * 线性淡出，静止态不显示。适用的角标：
 * - 置顶/收藏小图标角标（两种转场模式下都并入封面共享元素内容）
 * - FOLLOW_COVER 模式下的状态角标（角标随封面一起飞出，不单独落位）；
 *   FLY_TO_TITLE 模式的状态角标是独立 sharedBounds，落在标题行，不走这里。
 *
 * 返回 lambda 而非 State：调用方在 `graphicsLayer`/draw 阶段读取，
 * 逐帧变化只触发重绘，不引起重组。
 *
 * @param sharedContentState 封面共享元素状态，用于确认是否匹配到卡片侧
 *   封面。从没有共享元素的入口进入详情（无匹配）时不显示封面角标，
 *   避免角标在详情封面上闪现一次。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberSharedCoverBadgeAlpha(
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedContentState: SharedContentState?
): () -> Float {
    val progressState = rememberSharedCoverProgress(animatedVisibilityScope, detailSide = true)
    return remember(progressState, sharedContentState) {
        {
            if (sharedContentState?.isMatchFound != true) {
                0f
            } else {
                ((1f - progressState.value) / BadgeFadeOutPortion).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * 封面共享元素转场进度：0=卡片形态，1=详情形态。
 *
 * 弹簧规格与默认 sharedElement bounds 动画一致（damping=1、stiffness=400，
 * 仅收窄 visibilityThreshold），保证圆角/角标透明度与封面位移同时收尾。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberSharedCoverProgress(
    animatedVisibilityScope: AnimatedVisibilityScope,
    detailSide: Boolean
): State<Float> {
    val transition: Transition<EnterExitState> = animatedVisibilityScope.transition
    return transition.animateFloat(
        transitionSpec = {
            // 与默认 bounds 弹簧同参数（damping=1, stiffness=400），只收窄阈值，
            // 使圆角变化和封面位移同时收尾
            spring(visibilityThreshold = CornerProgressVisibilityThreshold)
        },
        label = "sharedCoverProgress"
    ) { state ->
        val visibleProgress = if (detailSide) 1f else 0f
        if (state == EnterExitState.Visible) visibleProgress else 1f - visibleProgress
    }
}
