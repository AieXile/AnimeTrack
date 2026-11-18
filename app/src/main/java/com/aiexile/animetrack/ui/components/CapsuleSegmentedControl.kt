package com.aiexile.animetrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.components.liquidglass.DampedDragAnimation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 胶囊分段选择（可滑动）：手感与主界面底部导航栏的选中胶囊同源。
 *
 * - 整条胶囊可横向拖动，高亮色块连续跟随手指，松手弹簧吸附最近选项并回调选中；
 * - 拖拽速度带来与导航栏一致的水平/垂直「果冻」拉伸，按下时轻微放大；
 * - 点击选项仍可直接选中（拖拽结束手速/位移不足时按点按处理，不重复回调）。
 * - 未拖拽时的外部选中变化（点击、页面状态自动回写）同样走弹簧动画。
 * - 手势归本控件独占：横向拖拽期间消费全部指针事件，避免外层竖向滚动容器
 *   在半路回收手势、把累计位移一次性补给页面（见 [detectSegmentDrag]）。
 *
 * 选项较多（> 4 个）时等分宽度会让文字挤在一起，调用方应改用可横向滚动的胶囊行。
 */
@Composable
fun <T> CapsuleSegmentedButton(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    val itemCount = options.size
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    // 拖拽回调里读取最新选中态/回调，避免 pointerInput 闭包捕获过期值
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnOptionSelected by rememberUpdatedState(onOptionSelected)

    // 与导航栏浮块同一套物理：value 为连续选项位置，松手吸附最近整数位
    val dragAnim = remember(scope, itemCount) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(itemCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.08f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _, _ -> }
        )
    }

    // 点击选择 / 页面状态自动回写时，胶囊弹簧滑到新选项。
    // 拖拽期间 selectedIndex 不变，本 effect 不会与手指位置抢动画
    LaunchedEffect(selectedIndex, dragAnim) {
        if (abs(dragAnim.value - selectedIndex) > 0.001f) {
            dragAnim.updateValue(selectedIndex.toFloat())
        }
    }

    // 文字选中态跟随色块连续位置：按整数位量化，仅在跨过选项边界时重组
    val activeIndex by remember(dragAnim) {
        derivedStateOf { dragAnim.value.roundToInt().coerceIn(0, itemCount - 1) }
    }

    val itemGap = 4.dp
    val pillColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SquircleShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 与 Row 的 weight(1f) + spacedBy(itemGap) 等价：各选项等宽、间隔固定
            val itemWidth = (
                (maxWidth - 4.dp * 2 - itemGap * (itemCount - 1).toFloat()) / itemCount
                ).coerceAtLeast(0.dp)
            val stepPx = with(density) { (itemWidth + itemGap).toPx() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(stepPx, itemCount, isLtr) {
                        if (stepPx <= 0f) return@pointerInput
                        detectSegmentDrag(
                            onDragStart = { dragAnim.press() },
                            onHorizontalDrag = { dragAmount ->
                                val delta = dragAmount / stepPx * if (isLtr) 1f else -1f
                                dragAnim.updateValue(
                                    (dragAnim.targetValue + delta)
                                        .coerceIn(0f, (itemCount - 1).toFloat())
                                )
                            },
                            onDragEnd = {
                                val target = dragAnim.targetValue
                                    .roundToInt().coerceIn(0, itemCount - 1)
                                dragAnim.animateToValue(target.toFloat())
                                if (target != latestSelectedIndex) {
                                    latestOnOptionSelected(options[target])
                                }
                            },
                            onDragCancel = {
                                dragAnim.animateToValue(
                                    dragAnim.value.roundToInt()
                                        .coerceIn(0, itemCount - 1).toFloat()
                                )
                            }
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .drawBehind {
                        // 色块绘制在文字下层：位置/果冻拉伸逐帧读取拖拽动画，仅重绘不重组
                        val pillWidth = itemWidth.toPx()
                        val step = (itemWidth + itemGap).toPx()
                        val left = if (isLtr) dragAnim.value * step
                        else (itemCount - 1 - dragAnim.value) * step
                        val velocityScale = (dragAnim.velocity / 10f).coerceIn(-0.2f, 0.2f)
                        // 实心色块（不像导航栏浮块是半透明）拉伸过度会压住相邻文字，
                        // 故保留同款方向的手感、幅度收小到不越过选项间隔
                        val scaleX = dragAnim.scaleX / (1f - velocityScale * 0.3f)
                        val scaleY = dragAnim.scaleY * (1f - velocityScale * 0.1f)
                        scale(
                            scaleX = scaleX,
                            scaleY = scaleY,
                            pivot = Offset(left + pillWidth / 2f, size.height / 2f)
                        ) {
                            drawRoundRect(
                                color = pillColor,
                                topLeft = Offset(left, 0f),
                                size = Size(pillWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(itemGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == activeIndex
                    val shape = SquircleShape(100.dp)
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = 600f),
                        label = "segmentedTextColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label(option),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分段控件专用的横向拖拽检测：判定为横向拖拽后，**消费之后的所有指针事件**（含纯竖向位移）。
 *
 * 为什么不用 `detectHorizontalDragGestures`：它内部的 `horizontalDrag` 只把横向位移非 0 的
 * 事件交给回调，纯竖向位移的事件既不回调也不消费；而 `scrollable`（Compose 1.11 的
 * `Draggable` 节点）在失去手势后会进入 AwaitGesturePickup 状态，只要在 Final 通道看到一个
 * 未被消费的事件，就以「按下点」为起点重新做 slop 判定——手指此时已横向拖开几十到上百 px，
 * 瞬间超过阈值，父级滚动容器在一帧内补上整段累计位移，表现为「按住指示器不动，过一会儿
 * 整页跳一下」。拖拽期间全部消费即可堵住这条回收路径。
 *
 * 判定前不消费：点击仍由各选项的 `clickable` 正常处理；竖向位移先过 slop 时直接放弃，
 * 把页面滚动完整让给父级（此时父级仍在正常 slop 阶段，滚动 1:1 跟手，不会跳）。
 */
private suspend fun PointerInputScope.detectSegmentDrag(
    onDragStart: () -> Unit,
    onHorizontalDrag: (dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val touchSlop = viewConfiguration.touchSlop
        var dragging = false
        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (dragging) onDragCancel()
                return@awaitEachGesture
            }
            if (change.changedToUpIgnoreConsumed()) {
                if (dragging) onDragEnd()
                return@awaitEachGesture
            }
            val delta = change.positionChange()
            if (dragging) {
                // 拖拽中：全部消费，父级滚动容器无法通过 AwaitGesturePickup 抢回手势
                change.consume()
                onHorizontalDrag(delta.x)
            } else {
                if (change.isConsumed) {
                    // 父级滚动已接管本次手势，直接放弃
                    return@awaitEachGesture
                }
                totalX += delta.x
                totalY += delta.y
                when {
                    abs(totalX) > touchSlop && abs(totalX) >= abs(totalY) -> {
                        dragging = true
                        change.consume()
                        onDragStart()
                        // 与 detectHorizontalDragGestures 一致：越过 slop 的部分立即生效
                        onHorizontalDrag(
                            totalX - if (totalX > 0f) touchSlop else -touchSlop
                        )
                    }
                    abs(totalY) > touchSlop -> return@awaitEachGesture
                }
            }
        }
    }
}
