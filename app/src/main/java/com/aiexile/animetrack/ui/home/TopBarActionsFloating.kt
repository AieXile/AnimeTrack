package com.aiexile.animetrack.ui.home

import androidx.compose.animation.Crossfade
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.LiquidGlassKind
import com.aiexile.animetrack.ui.components.LiquidGlassSpec
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.drawLiquidGlass
import com.aiexile.animetrack.ui.components.liquidGlassSpec
import com.aiexile.animetrack.ui.components.liquidglass.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** 迁移 FAB 与原顶栏按钮行同尺寸（IconButton 48dp），高度与 morph 收拢终点一致 */
internal val TopBarActionsHeight = 48.dp

/** 单按钮 FAB 宽度（morph 终点宽度之一） */
internal val TopBarActionsSingleWidth = 48.dp

/** 组合 FAB 宽度：48(搜索) + 1(分隔线) + 48(添加) */
internal val TopBarActionsCombinedWidth = 97.dp

/** morph 终点圆角（与 FAB SquircleShape 一致） */
internal val TopBarMorphCorner = 14.dp

/** morph 终点为圆形时的圆角（= 按钮半高）；「导航栏旁」加号状态下与圆形搜索 FAB 一致 */
internal val TopBarMorphCircleCorner = TopBarActionsHeight / 2

/** 悬浮搜索条最大宽度（向左展开） */
private val FloatingSearchMaxWidth = 320.dp

/**
 * 顶栏收拢（真 Morphing）修饰符：基于 [androidx.graphics.shapes.Morph] 的顶点级几何插值，
 * 顶栏整体（背景+内容）连续变形/裁剪为按钮行位置的平滑圆角方块——整个顶栏「真的
 * 缩小成 FAB」，非缩放淡出 + FAB 淡入的组合。
 *
 * 几何：起点=顶栏全宽矩形，终点=按钮行位置的 Squircle 方块（右缘距顶栏右缘
 * [endInset]，即顶栏 horizontal padding；纵向与按钮行同水平零位移）。
 * 收拢只发生在横向（左边界向右收）与顶部（statusBar 区域向下收），
 * 右侧按钮行原地保留，终点与迁移 FAB 位置/尺寸像素级重合。
 *
 * 高级模糊时顶栏背景本身即为毛玻璃（由 HomeTopBar 内 hazeEffect 提供），
 * 收拢全程保持毛玻璃，与 FAB 毛玻璃自然衔接，无需过渡。
 *
 * @param collapseProgress 收拢进度：0 = 顶栏完全展开，1 = 完全收拢为 FAB
 * @param targetWidth 收拢终点宽度（单按钮 48dp / 组合 97dp），由顶栏按钮构成决定
 * @param endInset 终点右缘距顶栏右缘的距离（与顶栏 horizontal padding 一致，默认 20dp）
 * @param targetCornerRadius 收拢终点圆角：常规为 [TopBarMorphCorner]；
 *   迁移 FAB 为圆形时传 [TopBarMorphCircleCorner]，与圆形 FAB 轮廓重合
 */
@Composable
fun Modifier.topBarCollapseMorph(
    collapseProgress: Float,
    targetWidth: Dp,
    endInset: Dp = 20.dp,
    targetCornerRadius: Dp = TopBarMorphCorner
): Modifier {
    val density = LocalDensity.current
    val morphShape = remember(density, targetWidth, endInset, targetCornerRadius) {
        with(density) {
            TopBarMorphShape(
                targetWidthPx = targetWidth.toPx(),
                targetHeightPx = TopBarActionsHeight.toPx(),
                cornerRadiusPx = targetCornerRadius.toPx(),
                endInsetPx = endInset.toPx()
            )
        }
    }
    return drawWithContent {
        if (collapseProgress <= 0f) {
            drawContent()
        } else {
            // withTransform 的 block receiver 是 DrawScope，drawContent 需显式指回外层 ContentDrawScope
            withTransform({ clipPath(morphShape.buildPath(size, collapseProgress)) }) {
                this@drawWithContent.drawContent()
            }
        }
    }
}

/**
 * 顶栏 morph 形状缓存：按顶栏实际尺寸构造一次 [Morph]（起点=全宽矩形，
 * 终点=右下角按钮行位置的 Squircle），每帧仅做 toPath(progress) 插值。
 */
private class TopBarMorphShape(
    private val targetWidthPx: Float,
    private val targetHeightPx: Float,
    private val cornerRadiusPx: Float,
    private val endInsetPx: Float
) {
    private var cachedWidth = Float.NaN
    private var cachedHeight = Float.NaN
    private var cachedMorph: Morph? = null

    fun buildPath(size: Size, progress: Float): Path {
        val p = progress.coerceIn(0f, 1f)
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || targetWidthPx >= w || targetHeightPx >= h) {
            return Path().apply { addRect(Rect(0f, 0f, w, h)) }
        }
        // 终点右缘 = 顶栏右缘 - endInset（与顶栏 horizontal padding 内的按钮行右缘对齐）
        val endRight = w - endInsetPx
        if (p >= 1f) {
            // 完全收拢：终点方块（与 FAB 完全重合）
            return Path().apply {
                addRect(Rect(endRight - targetWidthPx, h - targetHeightPx, endRight, h))
            }
        }
        var morph = cachedMorph
        if (morph == null || cachedWidth != w || cachedHeight != h) {
            // 起点：顶栏全宽矩形（无圆角）
            val start = RoundedPolygon.rectangle(
                width = w, height = h, centerX = w / 2f, centerY = h / 2f
            )
            // 终点：按钮行位置的 Squircle（顶栏底边即按钮行底边，纵向零位移）
            val rounding = CornerRounding(radius = cornerRadiusPx, smoothing = 0.64f)
            val end = RoundedPolygon.rectangle(
                width = targetWidthPx,
                height = targetHeightPx,
                centerX = endRight - targetWidthPx / 2f,
                centerY = h - targetHeightPx / 2f,
                perVertexRounding = List(4) { rounding }
            )
            morph = Morph(start, end)
            cachedMorph = morph
            cachedWidth = w
            cachedHeight = h
        }
        val androidPath = android.graphics.Path()
        morph.toPath(p, androidPath)
        return androidPath.asComposePath()
    }
}

/**
 * 顶栏动作迁移 FAB：「下滑隐藏顶栏」开启且顶栏收拢后，
 * 顶栏右上角的搜索/添加按钮在原位置以悬浮 FAB 形式出现（尺寸/位置与原按钮一致）。
 * 按钮构成与顶栏一致——仅搜索时单个搜索 FAB；顶栏含添加按钮时为
 * 「搜索 + 添加」组合 FAB（两动作放在一起，非独立两个 FAB）。
 * 背景跟随高级模糊/液态玻璃开关（模糊时透明底 + hazeEffect，液态玻璃时折射玻璃渲染），
 * 圆角使用 SquircleShape 平滑圆角。
 *
 * 悬浮搜索：顶栏隐藏时点击搜索按钮，FAB 左边框向左展开为悬浮搜索条
 * （右边框/垂直位置不动），关闭后收缩回 FAB。
 *
 * @param collapseProgress 顶栏收拢进度（与顶栏侧共用同一动画值，保证 morph 连续）：
 * 0 = 顶栏展开（FAB 不存在），1 = 顶栏完全收拢（FAB 完全显示）
 * @param circleFab 「导航栏旁」加号 FAB 状态下，仅搜索的单个 FAB 改用全圆
 * （与加号 FAB 同为圆形，统一风格）；组合 FAB 与展开后的搜索条保持原圆角
 * @param style 界面外壳材质档位：标准实色 / HAZE 毛玻璃 / LIQUID_GLASS 折射玻璃（需 backdrop）
 * @param backdrop 液态玻璃折射内容来源（页面内容层）
 */
@Composable
fun TopBarActionsFloating(
    collapseProgress: Float,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    showSearch: Boolean,
    showAdd: Boolean,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    hazeState: HazeState,
    style: ChromeStyle,
    blurConfig: AdvancedBlurConfig = AdvancedBlurConfig.DEFAULT,
    backdrop: Backdrop? = null,
    circleFab: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 顶栏本身无按钮时无需迁移（搜索激活时除外——搜索条需常驻可编辑）；顶栏展开时 FAB 不存在
    if ((!showSearch && !showAdd && !isSearchActive) || collapseProgress <= 0f) return

    val colorScheme = MaterialTheme.colorScheme
    val advancedBlurEnabled = style == ChromeStyle.HAZE
    // 液态玻璃模式优先生效（与悬浮胶囊导航栏一致）
    val liquidGlass = style == ChromeStyle.LIQUID_GLASS && backdrop != null
    // 仅「只有搜索」的单个 FAB 用圆形；组合 FAB 仍是按钮行，保持原圆角
    val singleCircleFab = circleFab && !showAdd
    // 玻璃规格统一出处：顶栏 FAB/搜索条属于常规悬浮按钮，保持轻模糊的通透感
    //（只有「导航栏旁」按钮槽用 CapsuleCompanion 与胶囊对齐白度）
    val fabGlassSpec = liquidGlassSpec(LiquidGlassKind.Fab)
    val fabContainerColor = when {
        liquidGlass -> fabGlassSpec.containerColor
        advancedBlurEnabled -> Color.Transparent
        else -> colorScheme.surfaceContainer
    }
    // lens 折射只支持 RoundedRectangularShape / CornerBasedShape，
    // SquircleShape（自定义平滑圆角）会直接抛异常，玻璃模式换普通圆角（视觉差异可忽略）
    val fabShape = if (liquidGlass) {
        RoundedCornerShape(TopBarMorphCorner)
    } else {
        SquircleShape(TopBarMorphCorner)
    }
    // 「导航栏旁」加号 FAB 状态：单个搜索 FAB 与加号同为 48dp 全圆（保留顶栏按钮行尺寸，
    // 与 topBarCollapseMorph 的圆形终点重合）；玻璃模式用 CornerBasedShape 供 lens 采样
    val circleFabShape = if (liquidGlass) {
        RoundedCornerShape(TopBarActionsHeight / 2)
    } else {
        SquircleShape(100.dp)
    }
    val collapsedFabShape = if (singleCircleFab) circleFabShape else fabShape
    // 模糊 shape 与 FAB 圆角一致（14dp / 全圆），避免 haze 层与背景层圆角不匹配产生的边缘白圈
    val fabBlur = fabBlurModifier(hazeState, !liquidGlass && advancedBlurEnabled, blurConfig, shape = fabShape)
    val collapsedFabBlur = if (singleCircleFab) {
        fabBlurModifier(hazeState, !liquidGlass && advancedBlurEnabled, blurConfig, shape = collapsedFabShape)
    } else {
        fabBlur
    }
    val shadowElevation = if (liquidGlass || advancedBlurEnabled) 0.dp else 6.dp

    // morph 出现：顶栏收拢后期（0.75→1）原位淡入，位置/尺寸与顶栏收拢终点完全重合
    Box(
        modifier = modifier.graphicsLayer {
            alpha = ((collapseProgress - 0.75f) / 0.25f).coerceIn(0f, 1f)
        }
    ) {
        // FAB ⇄ 悬浮搜索条：宽度驱动容器（容器本身锚定 TopEnd，右边框全程恒定不动，
        // 展开时仅左边框向左伸展，收回时仅左边框收回），内容随容器宽度即时重排
        BoxWithConstraints {
            val collapsedWidth = if (showSearch && showAdd) TopBarActionsCombinedWidth else TopBarActionsSingleWidth
            val expandedWidth = minOf(maxWidth, FloatingSearchMaxWidth)
            val targetWidth = if (isSearchActive) expandedWidth else collapsedWidth
            val animatedWidth by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "floatingActionsWidth"
            )

            Crossfade(
                targetState = isSearchActive,
                animationSpec = tween(180, easing = FastOutSlowInEasing),
                modifier = Modifier
                    .width(animatedWidth)
                    .height(TopBarActionsHeight)
            ) { active ->
                if (active) {
                    FloatingSearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = onCloseSearch,
                        containerColor = fabContainerColor,
                        shape = fabShape,
                        blurModifier = fabBlur,
                        shadowElevation = shadowElevation,
                        advancedBlurEnabled = advancedBlurEnabled,
                        liquidGlassEnabled = liquidGlass,
                        glassBackdrop = backdrop,
                        glassSpec = fabGlassSpec,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CombinedActionsFab(
                            showSearch = showSearch,
                            showAdd = showAdd,
                            onSearchClick = onSearchClick,
                            onAddClick = onAddClick,
                            containerColor = fabContainerColor,
                            shape = collapsedFabShape,
                            blurModifier = collapsedFabBlur,
                            shadowElevation = shadowElevation,
                            advancedBlurEnabled = advancedBlurEnabled,
                            liquidGlassEnabled = liquidGlass,
                            glassBackdrop = backdrop,
                            glassSpec = fabGlassSpec,
                            // 收缩过渡期间容器比 FAB 宽，FAB 始终贴右缘（右边框不动）
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 悬浮搜索条：FAB 向左展开而来（高度 48dp 不变，右边框对齐 FAB 右缘），
 * 宽度由外层宽度驱动容器动画。两端图标均 24dp、视觉边距均 12dp（严格对称）。
 * 液态玻璃模式：整条为折射玻璃渲染（vibrancy + blur + lens）。
 */
@Composable
private fun FloatingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    containerColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    blurModifier: Modifier,
    shadowElevation: Dp,
    advancedBlurEnabled: Boolean,
    liquidGlassEnabled: Boolean = false,
    glassBackdrop: Backdrop? = null,
    glassSpec: LiquidGlassSpec,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    // 展开后自动聚焦输入框
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.then(
            if (liquidGlassEnabled && glassBackdrop != null) {
                // 液态玻璃：折射玻璃渲染（规格见 ui/components/LiquidGlassSpec.kt）
                Modifier.drawLiquidGlass(
                    backdrop = glassBackdrop,
                    shape = { shape },
                    spec = glassSpec
                )
            } else {
                Modifier
                    // 阴影必须在 clip 外层：放内侧会被裁进形状内，渐变/环境阴影显示为内部阴影
                    .then(
                        if (advancedBlurEnabled) Modifier
                        else Modifier.shadow(elevation = shadowElevation, shape = shape)
                    )
                    .clip(shape)
                    .background(containerColor)
                    .then(blurModifier)
            }
        )
    ) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.SEARCH),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(24.dp)
        )
        BasicTextField(
            value = TextFieldValue(
                text = query,
                selection = TextRange(query.length)
            ),
            onValueChange = { onQueryChange(it.text) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_search_anime),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CLOSE),
                    contentDescription = stringResource(R.string.common_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                contentDescription = stringResource(R.string.home_close_search),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 「搜索 + 添加」组合 FAB：两动作合并为一个悬浮单元（非独立两个 FAB）。
 * 尺寸与原顶栏按钮行严格一致（高 48dp，IconButton 48dp，图标 22/26dp），
 * 单动作时为 48dp 方块，两者齐备时为宽 FAB（搜索 + 分隔线 + 添加）。
 * 液态玻璃模式：整块为折射玻璃渲染（vibrancy + blur + lens）。
 */
@Composable
private fun CombinedActionsFab(
    showSearch: Boolean,
    showAdd: Boolean,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    containerColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    blurModifier: Modifier,
    shadowElevation: Dp = 6.dp,
    advancedBlurEnabled: Boolean,
    liquidGlassEnabled: Boolean = false,
    glassBackdrop: Backdrop? = null,
    glassSpec: LiquidGlassSpec,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.primary
    // 交互物理反馈（Kyant0 LiquidButton 同款）：按压整体放大、拖动朝手指方向
    // 平移并沿角度拉伸（tanh 限幅）、指尖跟随高光
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(TopBarActionsHeight)
            .then(
                if (liquidGlassEnabled && glassBackdrop != null) {
                    Modifier
                        .drawLiquidGlass(
                            backdrop = glassBackdrop,
                            shape = { shape },
                            spec = glassSpec,
                            layerBlock = {
                                val width = size.width
                                val height = size.height
                                val progress = interactiveHighlight.pressProgress
                                val scale = lerp(1f, 1f + 4.dp.toPx() / height, progress)
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
                        // 高光层绘制在玻璃之上、图标之下；手势仅观察不消费，不影响内部按钮点击
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                        // 阴影必须在 clip 外层：放内侧会被裁进形状内，渐变/环境阴影显示为内部阴影
                        .then(
                            if (advancedBlurEnabled) Modifier
                            else Modifier.shadow(elevation = shadowElevation, shape = shape)
                        )
                        .clip(shape)
                        .background(containerColor)
                        .then(blurModifier)
                }
            )
    ) {
        if (showSearch) {
            // 无涟漪点击（FAB 玻璃底上不叠加涟漪遮罩），触控区域与原 IconButton 一致（48dp）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSearchClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.SEARCH),
                    contentDescription = stringResource(R.string.common_search),
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (showAdd) {
                // 分隔线：区分两个动作区域
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        if (showAdd) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAddClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ADD),
                    contentDescription = stringResource(R.string.home_add_anime),
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
