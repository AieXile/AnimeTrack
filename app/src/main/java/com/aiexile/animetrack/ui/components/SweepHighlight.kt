package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** 扫光动画时长（ms），与首页欢迎语扫光一致 */
internal const val SweepHighlightDurationMs = 750

/** 光带扫过区间：从元素左下外扫到右上外 */
internal const val SweepHighlightStart = -0.4f
internal const val SweepHighlightEnd = 1.4f

/** 光带半宽占元素长边的比例（渐变两端透明，中心为高光） */
internal const val SweepHighlightBandWidthRatio = 0.22f

/**
 * 点击触发的高亮扫光修饰符：一道白色光带沿对角线（左下 → 右上）扫过元素。
 *
 * 参数与首页欢迎语 [com.aiexile.animetrack.ui.home.GreetingText] 的点击扫光保持一致：
 * 进度按宽度归一化，光带从左侧外扫到右侧外，渐变方向为左下到右上。
 * 每次 [trigger] 递增播放一次；调用方在 [trigger] 为 0 时不显示（首帧不闪）。
 *
 * 绘制发生在内容之上，需由调用方自行 `clip` 到目标形状（如头像的 CircleShape）。
 *
 * @param trigger 扫光触发器：点击头像时自增
 * @param highlightColor 光带颜色，默认白色
 * @param highlightAlpha 光带中心透明度
 * @param durationMillis 本次扫光时长，默认与首页欢迎语一致
 * @param easing 进度曲线：默认快起慢收；需要「前半段慢、后半段加速」时传 ease-in 曲线
 */
@Composable
fun Modifier.sweepHighlight(
    trigger: Int,
    highlightColor: Color = Color.White,
    highlightAlpha: Float = 0.62f,
    durationMillis: Int = SweepHighlightDurationMs,
    easing: Easing = FastOutSlowInEasing,
): Modifier {
    val progress = remember { Animatable(SweepHighlightEnd) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(trigger) {
        if (trigger > 0 && size != IntSize.Zero) {
            progress.snapTo(SweepHighlightStart)
            progress.animateTo(
                targetValue = SweepHighlightEnd,
                animationSpec = tween(durationMillis, easing = easing)
            )
        }
    }

    return this
        .onSizeChanged { size = it }
        .drawWithContent {
            drawContent()
            val sweep = progress.value
            if (size == IntSize.Zero || sweep <= SweepHighlightStart || sweep >= SweepHighlightEnd) {
                return@drawWithContent
            }
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            val centerX = width * sweep
            val halfBand = maxOf(width, height) * SweepHighlightBandWidthRatio
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.35f to Color.Transparent,
                        0.5f to highlightColor.copy(alpha = highlightAlpha),
                        0.65f to Color.Transparent
                    ),
                    start = Offset(centerX - halfBand, height),
                    end = Offset(centerX + halfBand, 0f)
                )
            )
        }
}
