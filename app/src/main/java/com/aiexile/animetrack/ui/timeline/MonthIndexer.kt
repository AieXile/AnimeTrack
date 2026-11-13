package com.aiexile.animetrack.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.components.SquircleShape
import kotlin.math.abs
import kotlin.math.roundToInt

/** 时间线月份索引：连续位置只驱动布局，月份文字仅在原有高亮边界变化时重组。 */
@Composable
internal fun MonthIndexer(
    months: List<String>,
    currentIndex: Int,
    onIndexClick: (Int) -> Unit,
    onIndexSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var contentHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .width(32.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = SquircleShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(vertical = 8.dp)
            .onSizeChanged { contentHeightPx = it.height }
            .pointerInput(months) {
                fun indexOfY(y: Float): Int =
                    ((y / size.height) * months.size).toInt().coerceIn(0, months.size - 1)
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (months.isNotEmpty()) {
                            isSeeking = true
                            onIndexSeek(indexOfY(offset.y))
                        }
                    },
                    onDragEnd = { isSeeking = false },
                    onDragCancel = { isSeeking = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        if (months.isNotEmpty()) onIndexSeek(indexOfY(change.position.y))
                    }
                )
            }
    ) {
        if (months.isNotEmpty() && contentHeightPx > 0) {
            val density = LocalDensity.current
            val slotPx = contentHeightPx.toFloat() / months.size
            val slotDp = with(density) { slotPx.toDp() }
            val animatedIndex = animateFloatAsState(
                targetValue = currentIndex.toFloat(),
                animationSpec = tween(durationMillis = 150),
                label = "indexerIndex"
            )

            if (currentIndex >= 0) {
                val highlightPx = minOf(slotPx, with(density) { 28.dp.toPx() })
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                0,
                                (animatedIndex.value * slotPx + (slotPx - highlightPx) / 2f).roundToInt()
                            )
                        }
                        .size(with(density) { highlightPx.toDp() })
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                )
            }

            months.forEachIndexed { index, month ->
                MonthIndexLabel(
                    month = month,
                    index = index,
                    animatedIndex = animatedIndex,
                    slotPx = slotPx,
                    slotDp = slotDp,
                    onIndexClick = onIndexClick
                )
            }

            // 气泡的显示、位置和退场内容沿用原有逻辑，不跟随高亮插值重组。
            AnimatedVisibility(
                visible = isSeeking && currentIndex in months.indices,
                enter = fadeIn(tween(durationMillis = 100)),
                exit = fadeOut(tween(durationMillis = 200)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val centerY = currentIndex * slotPx + slotPx / 2f
                        IntOffset(
                            -44.dp.roundToPx(),
                            (centerY - 18.dp.roundToPx()).roundToInt()
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(elevation = 2.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Text(
                        text = months.getOrNull(currentIndex).orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xDE000000)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MonthIndexLabel(
    month: String,
    index: Int,
    animatedIndex: State<Float>,
    slotPx: Float,
    slotDp: Dp,
    onIndexClick: (Int) -> Unit
) {
    // 保留 abs < 0.5 的切换规则，避免每一帧连续位置让所有文字重新组合。
    val isCurrent by remember(index, animatedIndex) {
        derivedStateOf { abs(index - animatedIndex.value) < 0.5f }
    }
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0.5f,
        animationSpec = tween(durationMillis = 150),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(0, (index * slotPx).roundToInt()) }
            .size(width = 32.dp, height = slotDp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onIndexClick(index) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = month,
            fontSize = 10.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                // 与 Modifier.alpha 一致：仅在半透明时裁剪图层边界。
                clip = alpha != 1f
            }
        )
    }
}
