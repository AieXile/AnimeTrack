package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import com.aiexile.animetrack.data.ChromeStyle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.chrisbanes.haze.HazeState

/**
 * FAB → 卡片形变表面：同一块表面随 [progress]（0 = FAB，1 = 卡片）连续变形。
 *
 * 尺寸由调用方通过 [modifier] 驱动（宽度/高度连续插值），本组件只负责材质：
 * - 普通档：容器色从 surfaceContainer 过渡到卡片色 + 方向投影；
 * - 高级模糊档：`advancedHazeEffect` 毛玻璃 + 方向投影；
 * - 液态玻璃档：`drawLiquidGlass` 的 Fab 规格过渡到 Menu 规格（大面积卡片上
 *   lens / 边缘高光 / kyant 外阴影是已知的 RenderThread 崩溃点，随形变渐隐），
 *   阴影改由兄弟层框架阴影承担。
 *
 * 材质差异只允许存在于组件层，详情页等调用方不再判断玻璃档。
 */
@Composable
fun AppFabMorphSurface(
    progress: Float,
    style: ChromeStyle,
    hazeState: HazeState?,
    backdrop: Backdrop?,
    blurConfig: AdvancedBlurConfig,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = FabCornerRadius,
    fabContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    cardContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable BoxScope.() -> Unit
) {
    val fraction = progress.coerceIn(0f, 1f)
    val liquidShape = RoundedCornerShape(cornerRadius)
    // 普通 / 毛玻璃档沿用 FAB 的 Squircle；液态玻璃的 lens 仅支持 CornerBasedShape
    val shape: Shape = if (style == ChromeStyle.LIQUID_GLASS) liquidShape else SquircleShape(cornerRadius)
    val containerColor = lerpColor(fabContainerColor, cardContainerColor, fraction)
    val liquidBackdrop = backdrop.takeIf { style == ChromeStyle.LIQUID_GLASS }
    // FAB 的边缘高光/外阴影在卡片尺寸下有崩溃风险，前 35% 形变内渐隐
    val fabEdgeAlpha = (1f - fraction / FabEdgeFadeOutFraction).coerceIn(0f, 1f)
    // 卡片态的柔和框架阴影（兄弟层，不能包在玻璃图层外层，否则模糊失效）
    val cardShadowElevation = 2.dp * fraction

    Box(modifier = modifier) {
        when {
            liquidBackdrop != null -> {
                val fabSpec = liquidGlassSpec(LiquidGlassKind.Fab)
                val cardSpec = liquidGlassSpec(LiquidGlassKind.Menu)
                val spec = lerpGlassSpec(fabSpec, cardSpec, fraction)
                if (cardShadowElevation > 0.dp) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .shadow(elevation = cardShadowElevation, shape = liquidShape, clip = false)
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawLiquidGlass(
                            backdrop = liquidBackdrop,
                            shape = { liquidShape },
                            spec = spec,
                            highlight = {
                                if (fabEdgeAlpha <= 0f) {
                                    null
                                } else {
                                    Highlight.Default.copy(alpha = fabEdgeAlpha)
                                }
                            },
                            shadow = {
                                if (fabEdgeAlpha <= 0f) {
                                    null
                                } else {
                                    Shadow.Default.copy(alpha = fabEdgeAlpha)
                                }
                            }
                        )
                )
            }

            style == ChromeStyle.HAZE && hazeState != null -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .directionalFabShadow(shape = shape)
                        .then(
                            fabBlurModifier(
                                hazeState = hazeState,
                                enabled = true,
                                config = blurConfig,
                                shape = shape
                            )
                        )
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .directionalFabShadow(shape = shape)
                        .background(color = containerColor, shape = shape)
                )
            }
        }
        // 内容统一裁在圆角内，形变过程中文字/图标不会越出卡片
        Box(modifier = Modifier.matchParentSize().clip(shape)) {
            content()
        }
    }
}

/** FAB 边缘高光/外阴影的渐隐区间：占总形变进度的前 35% */
private const val FabEdgeFadeOutFraction = 0.35f

/**
 * 液态玻璃规格插值：折射（lens）在大面积卡片上会触发渲染崩溃，
 * 因此折射参数提前在形变前 35% 归零；底色/模糊随整体进度过渡。
 */
private fun lerpGlassSpec(fab: LiquidGlassSpec, card: LiquidGlassSpec, fraction: Float): LiquidGlassSpec {
    val lensFraction = (fraction / FabEdgeFadeOutFraction).coerceIn(0f, 1f)
    return LiquidGlassSpec(
        containerColor = lerpColor(fab.containerColor, card.containerColor, fraction),
        blurRadius = lerpDp(fab.blurRadius, card.blurRadius, fraction),
        lensRefractionHeight = lerpDp(fab.lensRefractionHeight, card.lensRefractionHeight, lensFraction),
        lensRefractionAmount = lerpDp(fab.lensRefractionAmount, card.lensRefractionAmount, lensFraction),
        vibrancy = fab.vibrancy && card.vibrancy
    )
}
