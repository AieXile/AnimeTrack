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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 指示器滑动过程中的最小缩放比例（缩小到 60%） */
private const val INDICATOR_MIN_SCALE = 0.6f

@Composable
fun CapsuleNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    visiblePages: List<String> = listOf("home", "favorites", "timeline", "settings"),
    pagerState: PagerState? = null,
    jumpTarget: Int? = null,
    modifier: Modifier = Modifier
) {
    val visibleItems = bottomNavItems.filter { it.route in visiblePages }
    val selectedIndex = visibleItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val itemCount = visibleItems.size
    val scope = rememberCoroutineScope()

    // 点击跳转时：指示器直线动画到目标，不经过中间项
    val jumpAnimatedIndex by animateFloatAsState(
        targetValue = jumpTarget?.toFloat() ?: selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "jumpIndicator"
    )

    val effectiveIndex = when {
        // 点击 Tab 跳转中：指示器直线动画到目标
        jumpTarget != null && itemCount > 0 ->
            jumpAnimatedIndex.coerceIn(0f, (itemCount - 1).toFloat())
        // 手势滑动：跟随 Pager 实际位置
        pagerState != null && itemCount > 0 ->
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (itemCount - 1).toFloat())
        else -> selectedIndex.toFloat()
    }

    // 滑动时整体缩小，到位恢复满尺寸
    val indicatorScale = if (pagerState != null) {
        1f - (1f - INDICATOR_MIN_SCALE) * abs(pagerState.currentPageOffsetFraction)
    } else 1f

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
                            scope.launch {
                                pagerState.scrollBy(-dragAmount / pageWidthPx)
                            }
                        }
                    }
                )
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = SquircleShape(100.dp)
                )
                .clip(SquircleShape(100.dp)),
            color = MaterialTheme.colorScheme.surfaceContainer,
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
                if (itemCount > 0) {
                    val paddingPx = with(density) { 4.dp.toPx() } * 2
                    val innerWidthPx = (rowWidthPx - paddingPx).coerceAtLeast(0f)
                    val itemWidthPx = if (innerWidthPx > 0f) innerWidthPx / itemCount else 0f
                    val indicatorOffsetPx = effectiveIndex * itemWidthPx
                    val indicatorOffsetDp = with(density) { indicatorOffsetPx.toDp() }
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
                            .offset(x = indicatorOffsetDp)
                            .graphicsLayer {
                                scaleX = indicatorScale
                                scaleY = indicatorScale
                                alpha = indicatorAlpha
                            }
                            .width(itemWidthDp)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                    )
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
                                ) { onNavigate(item.route) },
                            contentAlignment = Alignment.Center
                        ) {
                            CapsuleNavItem(
                                item = item,
                                selected = selected,
                                proximity = proximity
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleNavItem(
    item: BottomNavItem,
    selected: Boolean,
    proximity: Float
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
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = stringResource(item.titleRes),
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = stringResource(item.titleRes),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1
        )
    }
}
