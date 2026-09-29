package com.aiexile.animetrack.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.ui.components.liquidglass.DampedDragAnimation
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

@Composable
fun CapsuleNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    visiblePages: List<String> = listOf("home", "favorites", "timeline", "settings"),
    pagerState: PagerState? = null,
    jumpTarget: Int? = null,
    labelMode: NavigationLabelMode = NavigationLabelMode.ICON_AND_TEXT,
    hazeState: HazeState,
    advancedBlurEnabled: Boolean = false,
    blurConfig: AdvancedBlurConfig = AdvancedBlurConfig.DEFAULT,
    liquidGlassEnabled: Boolean = false,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {
    val visibleItems = bottomNavItems.filter { it.route in visiblePages }

    // 液态玻璃模式：与普通模式共用外部布局（水平 32dp / 底部 24dp 边距 + 全宽），
    // 内部由 LiquidGlassNavBar 按液态玻璃渲染（胶囊高 54dp，浮块交互）
    if (liquidGlassEnabled && backdrop != null && visibleItems.isNotEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .padding(bottom = 24.dp)
        ) {
            LiquidGlassNavBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                visibleItems = visibleItems,
                pagerState = pagerState,
                labelMode = labelMode,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    val selectedIndex = visibleItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val itemCount = visibleItems.size
    val scope = rememberCoroutineScope()

    // 指示器目标位置：点击跳转为静态目标（动画由 dragAnim 的 spring 完成），
    // 手势滑动跟随 Pager 实际位置
    val effectiveIndex = when {
        jumpTarget != null && itemCount > 0 ->
            jumpTarget.toFloat().coerceIn(0f, (itemCount - 1).toFloat())
        pagerState != null && itemCount > 0 ->
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (itemCount - 1).toFloat())
        else -> selectedIndex.toFloat()
    }

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    // Pager 单页宽度（屏幕宽度），用于把导航栏拖拽像素换算为页面数
    val pageWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp)
            .pointerInput(pagerState) {
                if (pagerState == null) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (pageWidthPx > 0f) {
                            AppLogManager.i("CapsuleNav", "bar drag scrollBy ${-dragAmount / pageWidthPx}")
                            scope.launch {
                                pagerState.scrollBy(-dragAmount / pageWidthPx)
                            }
                        }
                    }
                )
            }
    ) {
        val colorScheme = MaterialTheme.colorScheme
        val capsuleColor = if (advancedBlurEnabled) {
            Color.Transparent
        } else {
            colorScheme.surfaceContainer
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = SquircleShape(100.dp)
                )
                .clip(SquircleShape(100.dp))
                .then(
                    if (advancedBlurEnabled) {
                        advancedHazeEffect(
                            hazeState = hazeState,
                            config = blurConfig,
                            shape = null
                        )
                    } else {
                        Modifier
                    }
                ),
            color = capsuleColor,
            shape = SquircleShape(100.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowWidthPx = it.width.toFloat() }
                    .padding(4.dp)
            ) {
                // ===== 指示器拖拽动画（与液态玻璃浮块同一套物理交互）=====
                // 拖动色块移动，松手弹性吸附最近 Tab 并滚动 Pager 过去；
                // 按压放大 + 拖拽速度带来的果冻拉伸形变
                val touchSlopPx = LocalViewConfiguration.current.touchSlop
                val dragAnim = remember(scope, itemCount, density, touchSlopPx) {
                    DampedDragAnimation(
                        animationScope = scope,
                        initialValue = selectedIndex.toFloat(),
                        valueRange = 0f..(itemCount - 1).coerceAtLeast(0).toFloat(),
                        visibilityThreshold = 0.001f,
                        initialScale = 1f,
                        pressedScale = 1.15f,
                        onDragStarted = {},
                        onDragStopped = { change ->
                            val target = targetValue.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
                            animateToValue(target.toFloat())
                            if (draggedDistance > touchSlopPx) {
                                // 仅真实拖拽（累计位移超过 touchSlop）才按指示器吸附
                                // 目标滚动 Pager：若点按也按指示器位置滚动，飞行途中
                                // 会以中途四舍五入的索引重启动画，取消进行中的跳转
                                // 导致误跳相邻页
                                AppLogManager.i("CapsuleNav", "indicator DRAGGED -> animateScrollToPage($target) draggedDistance=$draggedDistance value=$value")
                                pagerState?.let { pager ->
                                    scope.launch { pager.animateScrollToPage(target) }
                                }
                            } else if (change != null) {
                                // 纯点按：指示器是渲染在 Tab 行之上的兄弟节点，命中
                                // 测试独占其 [v, v+1) 跨度，飞行途中会盖住相邻 Tab
                                //（如设置），此时点按事件到不了下层 Tab 的 clickable
                                //（此前直接吞掉，导致滑动途中点「设置」无反应、Pager
                                // 按自身惯性停在看板）。按点按落点换算 Tab 并导航，
                                // 与手指所指的 Tab 永远一致
                                val paddingPx = with(density) { 4.dp.toPx() } * 2
                                val innerWidthPx = (rowWidthPx - paddingPx).coerceAtLeast(0f)
                                val itemW = if (innerWidthPx > 0f) innerWidthPx / itemCount else 0f
                                if (itemW > 0f) {
                                    val tapIndex = (value + change.position.x / itemW)
                                        .toInt().coerceIn(0, itemCount - 1)
                                    AppLogManager.i("CapsuleNav", "indicator TAP -> navigate tapIndex=$tapIndex value=$value")
                                    if (tapIndex < visibleItems.size) {
                                        onNavigate(visibleItems[tapIndex].route)
                                    }
                                }
                            }
                        },
                        // 手势被取消（事件被其他手势消费/系统打断）：仅视觉回弹，不滚动
                        onDragCancelled = {
                            val target = targetValue.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
                            animateToValue(target.toFloat())
                        },
                        onDrag = { _, dragAmount, change ->
                            // 仅在真实拖拽（累计位移超过 touchSlop）时才消费事件，
                            // 避免与整栏水平拖拽手势双重滚动。点按时不消费——
                            // 此前无条件 consume 会把点按也吞掉，导致滑动途中
                            // 指示器停在目标 Tab 上方时，下层 Tab 的 clickable
                            // 收不到 down（detectTapAndPress 要求 down 未被消费），
                            // onNavigate 不触发，跳转落在相邻页（如看板）而非设置
                            if (draggedDistance > touchSlopPx) {
                                change.consume()
                            }
                            val paddingPx = with(density) { 4.dp.toPx() } * 2
                            val innerWidthPx = (rowWidthPx - paddingPx).coerceAtLeast(0f)
                            val itemW = if (innerWidthPx > 0f) innerWidthPx / itemCount else 0f
                            if (itemW > 0f) {
                                updateValue(
                                    (targetValue + dragAmount.x / itemW)
                                        .fastCoerceIn(0f, (itemCount - 1).coerceAtLeast(0).toFloat())
                                )
                            }
                        }
                    )
                }
                // 指示器跟随：必须直接读取 Pager 的快照状态（逐帧重发），
                // 滑动中的分数位置与最终落点才能持续驱动弹簧。
                // 不能监听 effectiveIndex——它是组合期计算的普通值，
                // snapshotFlow 只发射一次，滑动结束后指示器会停在两个 Tab 中间。
                // 拖动指示器本身时 Pager 静止、流不发射，手指位置不会被覆盖；
                // 松手吸附期间按压未消退则跳过，避免 Pager 补间把指示器拉回中途
                if (pagerState != null) {
                    LaunchedEffect(dragAnim, pagerState) {
                        snapshotFlow {
                            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, (itemCount - 1).toFloat())
                        }.collect { idx ->
                            if (dragAnim.pressProgress < 0.01f && abs(dragAnim.value - idx) > 0.001f) {
                                dragAnim.updateValue(idx)
                            }
                        }
                    }
                } else {
                    LaunchedEffect(dragAnim, currentRoute) {
                        val idx = selectedIndex.toFloat()
                        if (abs(dragAnim.value - idx) > 0.001f) {
                            dragAnim.updateValue(idx)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleItems.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val proximity = if (itemCount > 1) {
                            1f - abs(effectiveIndex - index) / (itemCount - 1).toFloat()
                        } else if (selected) 1f else 0f

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    AppLogManager.i("CapsuleNav", "tab CLICK route=${item.route}")
                                    onNavigate(item.route)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            CapsuleNavItem(
                                item = item,
                                selected = selected,
                                proximity = proximity,
                                labelMode = labelMode
                            )
                        }
                    }
                }

                // ===== 可拖拽指示器（渲染在 Tab 行之上以接收拖拽手势）=====
                // 静止时恰盖当前 Tab（点击本就无操作）；飞行途中会盖住相邻 Tab，
                // 其上的纯点按由 onDragStopped 的点按分支按落点代为导航，
                // 不损失可点击性
                if (itemCount > 0) {
                    val paddingPx = with(density) { 4.dp.toPx() } * 2
                    val innerWidthPx = (rowWidthPx - paddingPx).coerceAtLeast(0f)
                    val itemWidthPx = if (innerWidthPx > 0f) innerWidthPx / itemCount else 0f
                    val itemWidthDp = with(density) { itemWidthPx.toDp() }

                    // 指示器淡入：避免 MainOverlay 重组时 rowWidthPx 从 0 变为非零，
                    // 指示器突然出现造成闪烁
                    val indicatorAlpha by animateFloatAsState(
                        targetValue = if (rowWidthPx > 0f) 1f else 0f,
                        animationSpec = tween(150),
                        label = "indicatorAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = dragAnim.value * itemWidthPx
                                scaleX = dragAnim.scaleX
                                scaleY = dragAnim.scaleY
                                // 拖拽速度带来的果冻拉伸形变（与液态玻璃浮块一致）
                                val velocity = dragAnim.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                alpha = indicatorAlpha
                            }
                            .then(dragAnim.modifier)
                            .width(itemWidthDp)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
internal fun CapsuleNavItem(
    item: BottomNavItem,
    selected: Boolean,
    proximity: Float,
    labelMode: NavigationLabelMode
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f + 0.65f * proximity),
        animationSpec = spring(stiffness = 600f),
        label = "iconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f + 0.65f * proximity),
        animationSpec = spring(stiffness = 600f),
        label = "textColor"
    )
    val scale = 0.88f + 0.12f * proximity

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        if (labelMode != NavigationLabelMode.TEXT_ONLY) {
            Icon(
                painter = rememberAppIconPainter(if (selected) item.selectedIcon else item.icon),
                contentDescription = stringResource(item.titleRes),
                tint = iconColor,
                modifier = Modifier.size(if (labelMode == NavigationLabelMode.ICON_ONLY) 24.dp else 20.dp)
            )
        }
        if (labelMode != NavigationLabelMode.ICON_ONLY) {
            Text(
                text = stringResource(item.titleRes),
                fontSize = if (labelMode == NavigationLabelMode.TEXT_ONLY) 14.sp else 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
