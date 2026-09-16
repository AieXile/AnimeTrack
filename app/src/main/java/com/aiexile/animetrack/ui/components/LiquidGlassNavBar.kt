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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
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
 * 2. 镜像内容层（不可见）：捕获 Tab 内容到 [tabsBackdrop]，供浮块折射出强调色图标；
 * 3. 玻璃浮块（选中指示器）：拖拽移动、松手弹性吸附最近 Tab，按压时放大 +
 *    高光/阴影/内阴影渐显 + 色差折射；拖拽时整条胶囊有轻微弹性位移（panelOffset）。
 *    按压/拖拽/飞行期间，图标选中态与接近度实时跟随浮块连续位置（Apple
 *    液态玻璃 TabBar 行为）：浮块底下是哪个图标，哪个就渲染选中态，
 *    相邻图标按接近度渐变亮起。
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
        val touchSlopPx = LocalViewConfiguration.current.touchSlop

        // 注意 remember 不带 key：currentRoute 变化统一由下方 route sync effect
        // 处理（更新索引 + 浮块动画），避免重置绕过动画导致浮块不动
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }

        // key 包含 tabWidth/itemCount/isLtr：布局或可见 Tab 变化时重建，
        // 避免拖拽回调闭包捕获过期的尺寸与索引范围（旋转/改设置后拖拽失准）
        val dampedDragAnimation = remember(animationScope, tabWidth, itemCount, isLtr, touchSlopPx) {
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
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                    // 仅真实拖拽（累计位移超过 touchSlop）才导航：浮块上的纯点按
                    // 由下层 Tab 的 clickable 处理，此处若也导航，飞行途中的补点/
                    // 双击会二次触发 onNavigate，取消进行中的 animateScrollToPage
                    // 导致误跳相邻页（概率性无法复现的根因）
                    if (draggedDistance > touchSlopPx && targetIndex in visibleItems.indices) {
                        onNavigate(visibleItems[targetIndex].route)
                    }
                },
                // 手势被取消（事件被其他手势消费/系统打断）：仅视觉回弹，不导航
                onDragCancelled = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
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

        // 图标选中态实时跟随浮块连续位置（Apple 液态玻璃 TabBar 行为）：
        // round 量化后仅在浮块跨过图标边界时重组一次，颜色/缩放过渡由
        // CapsuleNavItem 内的 spring 动画平滑，不逐帧重组。
        // 逻辑（导航回调）仍用 currentIndex，视觉用 activeIndex，二者解耦
        val activeIndex by remember(dampedDragAnimation, itemCount) {
            derivedStateOf {
                dampedDragAnimation.value.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
            }
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
                    selected = index == activeIndex,
                    proximity = rememberTabProximity(dampedDragAnimation, index),
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
        // 整层 alpha=0 不可见。按压时按 demo（LiquidBottomTabs）结构叠加玻璃，
        // 使浮块折射采样到"磨砂内容 + 锐利图标"——模糊只作用于内容，图标绘制
        // 在其后保持清晰。
        // 与 demo 的差异：demo 镜像玻璃为 vibrancy+blur+lens 完整链，实测在本项目
        // 设备上与浮块 drawBackdrop 触发 GPU 伪影（按压时胶囊上出现水平细线，
        // 且与是否采样该输出无关），故仅保留 blur 规避；Decal 边缘使模糊裁剪
        // 干净（demo 靠 vibrancy 先行扩出的 padding 达到同样效果）。
        val mirrorGlassActive by remember(dampedDragAnimation) {
            derivedStateOf { dampedDragAnimation.pressProgress > 0.01f }
        }
        val mirrorGlassModifier = if (mirrorGlassActive) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    blur(8f.dp.toPx(), TileMode.Decal)
                },
                onDrawSurface = { drawRect(containerColor) }
            )
        } else {
            Modifier
        }
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .then(mirrorGlassModifier)
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
                        selected = index == activeIndex,
                        proximity = rememberTabProximity(dampedDragAnimation, index),
                        labelMode = labelMode
                    )
                }
            }
        }

        // ===== 3. 可拖拽玻璃浮块（选中指示器）=====
        // 未按压时浮块视觉 = 胶囊形状底色（10% 黑/白）+ 果冻拉伸（pager 联动也有速度形变），
        // 跳过 combined backdrop 采样与 lens/highlight/shadow/innerShadow 全部 shader
        // （未按压时这些效果全为 0/alpha=0），消除静态帧的多 pass 成本。
        // derivedStateOf 仅在跨过阈值时重组一次；progress/scale/velocity 在 lambda 内
        // 按绘制期读取，动画期间逐帧只刷新绘制，不触发重组。
        val knobGlassActive by remember(dampedDragAnimation) {
            derivedStateOf { dampedDragAnimation.pressProgress > 0.01f }
        }
        val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
        val knobShape = remember { Capsule() }
        val knobBaseColor =
            if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f)
        val knobGlassModifier = if (knobGlassActive) {
            Modifier.drawBackdrop(
                backdrop = combinedBackdrop,
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
                    Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                },
                shadow = {
                    Shadow(alpha = dampedDragAnimation.pressProgress)
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8f.dp * dampedDragAnimation.pressProgress,
                        alpha = dampedDragAnimation.pressProgress
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
        } else {
            Modifier
                .graphicsLayer {
                    // 果冻拉伸：pager 联动移动时未按压也有速度形变，需保留
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                }
                .drawBehind {
                    // 胶囊形状底色：progress=0 时 onDrawSurface 仅剩此底色
                    //（drawBackdrop 的 shape 裁剪由 capsule outline 等价替代）
                    drawOutline(
                        knobShape.createOutline(size, layoutDirection, this),
                        knobBaseColor
                    )
                }
        }
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
                .then(knobGlassModifier)
                .height(46f.dp)
                .fillMaxWidth(1f / itemCount)
        )
    }
}

/** 液态玻璃胶囊内的可见 Tab：点击切换，选中态实时跟随浮块位置 */
@Composable
private fun RowScope.GlassNavTab(
    item: BottomNavItem,
    selected: Boolean,
    proximity: Float,
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
            proximity = proximity,
            labelMode = labelMode
        )
    }
}

/**
 * Tab 图标与浮块连续位置的接近度（0..1），按 0.25 步长量化：
 * 拖拽/飞行期间仅跨步长时重组（每次拖拽约 4×Tab数 次），平滑渐变
 * 交给 CapsuleNavItem 内已有的 spring 动画，保持"逐帧只刷新绘制"的约定
 */
@Composable
private fun rememberTabProximity(
    dampedDragAnimation: DampedDragAnimation,
    index: Int
): Float = remember(dampedDragAnimation, index) {
    derivedStateOf {
        val raw = 1f - abs(dampedDragAnimation.value - index)
        (raw.fastCoerceIn(0f, 1f) * 4f).fastRoundToInt() / 4f
    }
}.value
