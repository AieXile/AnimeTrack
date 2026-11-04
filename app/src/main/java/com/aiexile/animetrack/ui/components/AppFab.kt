package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import com.aiexile.animetrack.ui.components.liquidglass.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** 悬浮按钮默认边长（M3 FAB 标准尺寸） */
val FabSize = 56.dp

/** 悬浮按钮默认圆角（普通 / 毛玻璃档）；液态玻璃档因 lens 仅支持 CornerBasedShape，改用等值普通圆角 */
val FabCornerRadius = 16.dp

/**
 * 液态玻璃 FAB：折射玻璃渲染 + LiquidButton 同款交互物理
 * （按压放大、拖动跟随形变、指尖高光）。
 * lens 仅支持 CornerBasedShape，故圆角默认 16dp 普通圆角（视觉与 Squircle 差异可忽略）。
 *
 * [content] 为按钮内容槽（图标 / 加载指示器），尺寸由调用方自行约束。
 */
@Composable
fun AppLiquidGlassFab(
    backdrop: Backdrop,
    spec: LiquidGlassSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fabSize: Dp = FabSize,
    shape: Shape = RoundedCornerShape(FabCornerRadius),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(fabSize)
            .drawLiquidGlass(
                backdrop = backdrop,
                shape = { shape },
                spec = spec,
                layerBlock = {
                    // 与顶栏玻璃 FAB 同款交互物理（Kyant0 LiquidButton）
                    val width = size.width
                    val height = size.height
                    val progress = interactiveHighlight.pressProgress
                    val scale = 1f + 4.dp.toPx() / height * progress
                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX =
                        maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY =
                        maxOffset * tanh(initialDerivative * offset.y / maxOffset)
                    val maxDragScale = 4.dp.toPx() / height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX = scale + maxDragScale *
                        abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                        (width / height).fastCoerceAtMost(1f)
                    scaleY = scale + maxDragScale *
                        abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                        (height / width).fastCoerceAtMost(1f)
                }
            )
            // 高光层绘制在玻璃之上、内容之下；手势仅观察不消费，不影响点击
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * 悬浮按钮「方向投影」：右下方向的柔和投影，替代 M3 默认环绕阴影。
 * 主体投影用 [shadow]，额外的向下/向右渐变扩展营造单向光照感。
 */
internal fun Modifier.directionalFabShadow(
    shape: Shape,
    elevation: Dp = 2.dp,
    shadowSpread: Dp = 8.dp,
    enabled: Boolean = true
): Modifier = this
    .then(if (enabled) Modifier.shadow(elevation = elevation, shape = shape) else Modifier)
    .drawBehind {
        if (!enabled) return@drawBehind
        val spread = shadowSpread.toPx()
        val shadowColor = Color.Black.copy(alpha = 0.18f)

        // 底部阴影
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(shadowColor, Color.Transparent),
                startY = size.height,
                endY = size.height + spread
            ),
            topLeft = Offset(0f, size.height),
            size = androidx.compose.ui.geometry.Size(size.width, spread)
        )

        // 右侧阴影
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(shadowColor, Color.Transparent),
                startX = size.width,
                endX = size.width + spread
            ),
            topLeft = Offset(size.width, 0f),
            size = androidx.compose.ui.geometry.Size(spread, size.height)
        )

        // 右下角阴影
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(shadowColor, Color.Transparent),
                center = Offset(size.width, size.height),
                radius = spread
            ),
            topLeft = Offset(size.width, size.height),
            size = androidx.compose.ui.geometry.Size(spread, spread)
        )
    }

/** 高级模糊（HAZE 档）悬浮按钮的毛玻璃修饰符；shape 需与按钮圆角一致，避免边缘白圈 */
@Composable
internal fun fabBlurModifier(
    hazeState: HazeState,
    enabled: Boolean,
    config: AdvancedBlurConfig,
    shape: Shape = SquircleShape(FabCornerRadius)
): Modifier {
    if (!enabled) return Modifier
    return advancedHazeEffect(
        hazeState = hazeState,
        config = config,
        shape = shape
    )
}
