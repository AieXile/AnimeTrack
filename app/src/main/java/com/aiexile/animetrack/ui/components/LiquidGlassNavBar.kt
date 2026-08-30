package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.ui.components.liquidglass.DampedDragAnimation
import com.aiexile.animetrack.ui.components.liquidglass.InteractiveHighlight
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 液态玻璃悬浮胶囊导航栏（悬浮胶囊的液态玻璃形态）。
 *
 * 渲染与交互完全参照 Kyant0/AndroidLiquidGlass 示例 LiquidBottomTabs 的三层结构：
 * 1. 胶囊容器：液态模糊（vibrancy + blur + lens 折射）+ 可见 Tab 内容，可点击切换；
 * 2. 镜像内容层（不可见）：捕获 Tab 内容到 [tabsBackdrop]，供浮块折射出强调色图标，
 *    按压时内容随浮块放大 1.2 倍（折射放大效果）；
 * 3. 玻璃浮块（选中指示器）：拖拽移动、松手弹性吸附最近 Tab，按压时放大 +
 *    高光/阴影/内阴影渐显 + 色差折射；拖拽时整条胶囊有轻微弹性位移（panelOffset）。
 *
 * 尺寸与 [CapsuleNavigationBar] 普通模式一致：胶囊高 54dp、内部 4dp 边距、
 * 浮块高 46dp；水平 32dp / 底部 24dp 边距由 [CapsuleNavigationBar] 统一应用。
 */
@Composable
internal fun LiquidGlassNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    visibleItems: List<BottomNavItem>,
    pagerState: PagerState?,
    labelMode: NavigationLabelMode,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    // 跟随应用内主题选择（而非系统暗色），保证 app 切暗色时玻璃底色同步切换
    val isLightTheme = !isAppDarkTheme()
    // 折射强调色跟随应用主题（与普通胶囊选中色同源），不写死示例的蓝色，
    // 保证浮块中选项颜色与用户所选主题一致
    val accentColor = MaterialTheme.colorScheme.primary
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val selectedIndex = visibleItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val itemCount = visibleItems.size

    val tabsBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        // 胶囊总高 54dp，左右各留 4dp 边距 → 浮块/Tab 宽 = (总宽 - 8dp) / Tab 数
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / itemCount.coerceAtLeast(1)
        }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        // 注意 remember 不带 key：currentRoute 变化统一由下方 route sync effect
        // 处理（更新索引 + 浮块动画），避免重置绕过动画导致浮块不动
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }

        // key 包含 tabWidth/itemCount/isLtr：布局或可见 Tab 变化时重建，
        // 避免拖拽回调闭包捕获过期的尺寸与索引范围（旋转/改设置后拖拽失准）
        val dampedDragAnimation = remember(animationScope, tabWidth, itemCount, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(itemCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                // 46dp 浮块按压放大 22dp（与示例 56→78dp 的放大增量一致）
                pressedScale = 68f / 46f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    if (targetIndex in visibleItems.indices) {
                        onNavigate(visibleItems[targetIndex].route)
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount, _ ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (itemCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        // Pager 滑动联动：targetPage（将要停的页）驱动浮块平滑跟随（回弹自然弹回）。
        // 注意用 updateValue 而非 animateToValue：程序驱动不触发按压（press），
        // 否则滑动时浮块高光/放大持续渐显，胶囊中间会出现一条移动亮线
        LaunchedEffect(pagerState, visibleItems, dampedDragAnimation) {
            if (pagerState == null) return@LaunchedEffect
            snapshotFlow { pagerState.targetPage }.collectLatest { page ->
                if (page in visibleItems.indices) {
                    currentIndex = page
                    dampedDragAnimation.updateValue(page.toFloat())
                }
            }
        }
        // 路由兜底：非 Pager 驱动的选中变化（如可见页集合变动后索引移位），
        // 仅同步显示状态、不回调 onNavigate——回调会与 Pager 滚动形成回环竞态：
        // 滚动途中 currentPage 变化 → onNavigate(中间页) → 又触发
        // animateScrollToPage(中间页) 取消原滚动，表现为点击后页面卡住不动
        LaunchedEffect(currentRoute, dampedDragAnimation, visibleItems) {
            val idx = visibleItems.indexOfFirst { it.route == currentRoute }
            if (idx >= 0 && idx != currentIndex) {
                currentIndex = idx
                dampedDragAnimation.updateValue(idx.toFloat())
            }
        }

        // key 包含 dampedDragAnimation：指示器动画对象重建后高亮跟随新实例
        val interactiveHighlight = remember(dampedDragAnimation, tabWidth, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // ===== 1. 胶囊容器（54dp 液态玻璃 + 可见可点击的 Tab）=====
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        // 按压浮块时整条胶囊轻微放大（与浮块放大联动）
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(54f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleItems.forEachIndexed { index, item ->
                GlassNavTab(
                    item = item,
                    selected = index == currentIndex,
                    labelMode = labelMode,
                    onClick = {
                        if (index != currentIndex) {
                            currentIndex = index
                            dampedDragAnimation.animateToValue(index.toFloat())
                            onNavigate(item.route)
                        }
                    }
                )
            }
        }

        // ===== 2. 镜像内容层（隐形：捕获 Tab 内容供浮块折射出强调色图标）=====
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(
                            24f.dp.toPx() * progress,
                            24f.dp.toPx() * progress
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(46f.dp)
                .fillMaxWidth()
                .padding(horizontal = 4f.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            visibleItems.forEachIndexed { index, item ->
                // 折射内容按压时放大 1.2 倍，与浮块放大同步（示例 LocalLiquidBottomTabScale 语义）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val scale = lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CapsuleNavItem(
                        item = item,
                        selected = index == currentIndex,
                        proximity = if (index == currentIndex) 1f else 0f,
                        labelMode = labelMode
                    )
                }
            }
        }

        // ===== 3. 可拖拽玻璃浮块（选中指示器）=====
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        // 拖拽速度带来的果冻拉伸形变
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(46f.dp)
                .fillMaxWidth(1f / itemCount)
        )
    }
}

/** 液态玻璃胶囊内的可见 Tab：点击切换，选中态与普通胶囊一致 */
@Composable
private fun RowScope.GlassNavTab(
    item: BottomNavItem,
    selected: Boolean,
    labelMode: NavigationLabelMode,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        CapsuleNavItem(
            item = item,
            selected = selected,
            proximity = if (selected) 1f else 0f,
            labelMode = labelMode
        )
    }
}
