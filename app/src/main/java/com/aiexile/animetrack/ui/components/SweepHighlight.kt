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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * 点击触发的一次扫光修饰符：一道柔和高光沿对角线（左下 → 右上）扫过元素。
 *
 * 光带形状与 [ShimmerText] 的循环微光共用 [shimmerBrush]，
 * 只是一次性播放：进度按宽度归一化，从左侧外扫到右侧外。
 * 每次 [trigger] 递增播放一次；[trigger] 为 0 时不显示（首帧不闪）。
 *
 * 绘制发生在内容之上，需由调用方自行 `clip` 到目标形状（如头像的 CircleShape）。
 *
 * @param trigger 扫光触发器：点击头像时自增
 * @param highlightColor 光带颜色，默认白色
 * @param highlightAlpha 光带中心透明度
 * @param durationMillis 本次扫光时长
 * @param easing 进度曲线：默认快起慢收；需要「前半段慢、后半段加速」时传 ease-in 曲线
 * @param bandWidthRatio 光带半宽占元素长边的比例，方形元素可调小让光带更窄
 */
@Composable
fun Modifier.sweepHighlight(
    trigger: Int,
    highlightColor: Color = Color.White,
    highlightAlpha: Float = 0.62f,
    durationMillis: Int = ShimmerSweepDurationMs,
    easing: Easing = FastOutSlowInEasing,
    bandWidthRatio: Float = ShimmerBandWidthRatio,
): Modifier {
    val progress = remember { Animatable(ShimmerSweepEnd) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(trigger) {
        if (trigger > 0 && size != IntSize.Zero) {
            progress.snapTo(ShimmerSweepStart)
            progress.animateTo(
                targetValue = ShimmerSweepEnd,
                animationSpec = tween(durationMillis, easing = easing)
            )
        }
    }

    return this
        .onSizeChanged { size = it }
        .drawWithContent {
            drawContent()
            val sweep = progress.value
            if (size == IntSize.Zero || sweep <= ShimmerSweepStart || sweep >= ShimmerSweepEnd) {
                return@drawWithContent
            }
            drawRect(
                brush = shimmerBrush(
                    size = size,
                    progress = sweep,
                    baseColor = Color.Transparent,
                    glowColor = highlightColor.copy(alpha = highlightAlpha),
                    bandWidthRatio = bandWidthRatio
                )
            )
        }
}
