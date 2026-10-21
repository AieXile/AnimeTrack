package com.aiexile.animetrack.ui.components.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import com.aiexile.animetrack.ui.theme.LocalChromeStyle
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState

/** 顶栏行高：与 M3 TopAppBar 的 64dp 一致，保证三档下 Scaffold 内容内边距不变 */
internal val ChromeTopBarRowHeight = 64.dp

/**
 * 三档统一顶栏：
 *
 * - [ChromeStyle.STANDARD]：M3 [TopAppBar]（标题 headlineSmall/SemiBold，返回为 IconButton）；
 * - [ChromeStyle.HAZE]：整条毛玻璃（haze）+ 顶部实色渐隐 + 羽化蒙版，返回为轻量圆底；
 * - [ChromeStyle.LIQUID_GLASS]：backdrop 折射玻璃（vibrancy + blur + 底色 + 羽化蒙版），
 *   返回为液态玻璃圆钮。
 *
 * 三档结构一致（返回 + 标题 + 动作），页面只描述内容，不再写 if/else。
 *
 * @param sources 页面内容层采样源；玻璃档需要与内容根上挂的源为同一个对象
 * @param titleContent 自定义标题槽（详情页滚动淡入标题、日程双行标题等）
 * @param scrollBehavior 仅 STANDARD 生效（详情页滚动收缩）；玻璃档忽略
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)?,
    sources: ChromeSources,
    modifier: Modifier = Modifier,
    style: ChromeStyle = LocalChromeStyle.current,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val backdrop = sources.backdrop
    val hazeState = sources.hazeState
    when {
        style == ChromeStyle.LIQUID_GLASS && backdrop != null ->
            GlassTopBar(title, onBack, backdrop, actions, titleContent, modifier)

        style == ChromeStyle.HAZE && hazeState != null ->
            HazeTopBar(title, onBack, hazeState, actions, titleContent, modifier)

        else -> StandardTopBar(title, onBack, actions, titleContent, modifier, scrollBehavior)
    }
}

/** 默认标题：与原有各页 TopAppBar 标题样式保持一致（headlineSmall + SemiBold + 单行省略） */
@Composable
private fun TopBarTitle(title: String, titleContent: (@Composable () -> Unit)?) {
    if (titleContent != null) {
        titleContent()
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardTopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    titleContent: (@Composable () -> Unit)?,
    modifier: Modifier,
    scrollBehavior: TopAppBarScrollBehavior?
) {
    // 未传滚动行为时给一个 pinned 默认值：不改变高度，仅满足 TopAppBar 的必需参数
    val defaultBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // 无返回按钮的主页顶栏（设置/日程）与时间线保持一致：标题左缘 20dp（M3 默认 16dp）
    val titleStartPadding = if (onBack == null) 4.dp else 0.dp
    TopAppBar(
        title = {
            Box(modifier = Modifier.padding(start = titleStartPadding)) {
                TopBarTitle(title, titleContent)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                AppBackButton(onClick = onBack)
            }
        },
        actions = actions,
        modifier = modifier,
        scrollBehavior = scrollBehavior ?: defaultBehavior
    )
}

@Composable
private fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    backdrop: Backdrop,
    actions: @Composable RowScope.() -> Unit,
    titleContent: (@Composable () -> Unit)?,
    modifier: Modifier
) {
    // 玻璃底色跟随应用内主题（与 LiquidGlassNavBar 同源），保证明暗切换时玻璃观感一致
    val isLightTheme = !isAppDarkTheme()
    val stripTint =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)
    // 与重构前的设置页玻璃顶栏完全一致的几何参数：羽化/过渡分界 = 状态栏 + 32dp
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val featherStartPx = statusBarTopPx + with(density) { 32f.dp.toPx() }

    Box(modifier = modifier.fillMaxWidth()) {
        // 玻璃背景层：内层子节点绘制玻璃效果，外层离屏合成后再叠加羽化蒙版——
        // 蒙版与玻璃效果分层，避免 DstIn 蒙版参与玻璃 RenderEffect 自身的 layer 处理
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawTopBarFeatherMask(statusBarTopPx, density)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            vibrancy()
                            blur(20f.dp.toPx())
                        },
                        // 通栏玻璃不给高光/外阴影：矩形四边的白色亮线即来自 kyant 默认
                        // Highlight/Shadow，暗色下尤为明显
                        highlight = null,
                        shadow = null,
                        onDrawSurface = {
                            // 沿用重构前已调好的「上实下虚」渐变：顶部玻璃更实，
                            // 自顶向下（至标题文字中段）渐变回常规玻璃底色
                            val transitionFraction =
                                (featherStartPx / size.height).coerceIn(0f, 1f)
                            drawRect(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to stripTint.copy(alpha = 0.95f),
                                        transitionFraction to stripTint,
                                        1f to stripTint
                                    )
                                )
                            )
                        }
                    )
            )
        }
        TopBarRow(
            title = title,
            onBack = onBack,
            style = ChromeStyle.LIQUID_GLASS,
            backdrop = backdrop,
            actions = actions,
            titleContent = titleContent
        )
    }
}

@Composable
private fun HazeTopBar(
    title: String,
    onBack: (() -> Unit)?,
    hazeState: HazeState,
    actions: @Composable RowScope.() -> Unit,
    titleContent: (@Composable () -> Unit)?,
    modifier: Modifier
) {
    val config = LocalAdvancedBlurConfig.current
    // 与液态玻璃顶栏共用同一套已调好的几何参数（上实下虚）
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val featherStartPx = statusBarTopPx + with(density) { 32f.dp.toPx() }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawTopBarFeatherMask(statusBarTopPx, density)
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(advancedHazeEffect(hazeState = hazeState, config = config))
            )
            // 顶部实色渐隐（与液态玻璃同结构，只换材质）：状态栏区域更实，
            // 自标题中段向下由蒙版渐隐到透明
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val transitionFraction =
                            (featherStartPx / size.height).coerceIn(0f, 1f)
                        drawRect(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to surfaceColor.copy(alpha = 0.95f),
                                    transitionFraction to Color.Transparent,
                                    1f to Color.Transparent
                                )
                            )
                        )
                    }
            )
        }
        TopBarRow(
            title = title,
            onBack = onBack,
            style = ChromeStyle.HAZE,
            backdrop = null,
            actions = actions,
            titleContent = titleContent
        )
    }
}

/** 玻璃档共用行布局：返回 + 标题（占满剩余宽度）+ 动作 */
@Composable
private fun TopBarRow(
    title: String,
    onBack: (() -> Unit)?,
    style: ChromeStyle,
    backdrop: Backdrop?,
    actions: @Composable RowScope.() -> Unit,
    titleContent: (@Composable () -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(ChromeTopBarRowHeight)
            // 无返回按钮的主页顶栏（设置/日程）标题左缘 20dp，与时间线一致
            .padding(start = if (onBack == null) 20.dp else 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            AppBackButton(
                onClick = onBack,
                style = style,
                backdrop = backdrop
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            TopBarTitle(title, titleContent)
        }
        actions()
    }
}

/**
 * 顶栏羽化蒙版（DstIn）：玻璃自标题文字中段（状态栏 + 半行高）向下
 * alpha 先缓后快渐隐，模糊效果平滑减弱到底部完全消失，隐化硬边界；
 * 末段提前归零，避免底边残留一条未蒙干净的亮线。
 *
 * 与重构前的设置页玻璃顶栏参数完全一致（已调好，勿再改方向）。
 * 必须工作在离屏缓冲里（调用方的父节点已设 [CompositingStrategy.Offscreen]）。
 */
private fun DrawScope.drawTopBarFeatherMask(
    statusBarTopPx: Int,
    density: Density
) {
    val featherStart = statusBarTopPx + with(density) { 32f.dp.toPx() }
    val startFraction = (featherStart / size.height).coerceIn(0f, 0.9f)
    drawRect(
        Brush.verticalGradient(
            colorStops = arrayOf(
                startFraction to Color.Black,
                startFraction + (1f - startFraction) * 0.55f to
                    Color.Black.copy(alpha = 0.35f),
                0.995f to Color.Transparent,
                1f to Color.Transparent
            ),
            startY = 0f,
            endY = size.height
        ),
        blendMode = BlendMode.DstIn
    )
}
