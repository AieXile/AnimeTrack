package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 自定义开关：关闭时 thumb 为左侧扁平胶囊，开启时滑到右侧并逐渐展开为正圆。
 *
 * 抗锯齿方案：track 和 thumb 均用 [Modifier.background] + [SquircleShape] 绘制，
 * 底层走 Skia drawPath，原生像素覆盖率抗锯齿，不依赖 clip 裁剪。
 * clip 仅用于限制 ripple 点击波纹范围。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val animatable = remember { Animatable(if (checked) 1f else 0f) }
    var isFirstFrame by remember { mutableStateOf(true) }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (isFirstFrame) {
            isFirstFrame = false
            animatable.snapTo(target)
        } else {
            animatable.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    val progress = animatable.value
    val colorProgress = progress.coerceIn(0f, 1f)

    val uncheckedTrackColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val checkedTrackColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primary
    }
    val trackColor = lerp(uncheckedTrackColor, checkedTrackColor, colorProgress)

    val thumbColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        colorProgress > 0.5f -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.outline
    }

    // thumb 尺寸插值：关闭扁平胶囊(14×8) → 开启正圆(18×18)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val thumbWUnchecked = 14.dp
    val thumbHUnchecked = 8.dp
    val thumbDiameter = 18.dp
    val thumbW = thumbWUnchecked + (thumbDiameter - thumbWUnchecked) * clampedProgress
    val thumbH = thumbHUnchecked + (thumbDiameter - thumbHUnchecked) * clampedProgress

    // thumb 位置：左 → 右，四周留 6dp 呼吸间距
    val padding = 6.dp
    val trackW = 48.dp
    val trackH = 26.dp
    val thumbX = padding + (trackW - thumbW - padding * 2) * clampedProgress
    val thumbY = (trackH - thumbH) / 2f

    Box(
        modifier = modifier
            .size(width = trackW, height = trackH)
            .background(color = trackColor, shape = SquircleShape(13.dp))
            .clip(SquircleShape(13.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        thumbX.roundToPx(),
                        thumbY.roundToPx()
                    )
                }
                .size(width = thumbW, height = thumbH)
                .background(color = thumbColor, shape = SquircleShape(50))
        )
    }
}
