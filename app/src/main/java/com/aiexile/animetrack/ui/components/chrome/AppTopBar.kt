package com.aiexile.animetrack.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.ProgressiveBackdropBlur
import com.aiexile.animetrack.ui.components.ProgressiveBlurSpan
import com.aiexile.animetrack.ui.components.ProgressiveHazeBlur
import com.aiexile.animetrack.ui.theme.LocalChromeStyle
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState

/** 顶栏行高：与 M3 TopAppBar 的 64dp 一致，保证三档下 Scaffold 内容内边距不变 */
internal val ChromeTopBarRowHeight = 64.dp

/**
 * 三档统一顶栏：
 *
 * - [ChromeStyle.STANDARD]：M3 [TopAppBar]（标题 headlineSmall/SemiBold，返回为 IconButton）；
 * - [ChromeStyle.HAZE]：整条毛玻璃（haze，羽化区间内用 progressive 做模糊强度渐变）+ 顶部实色渐隐 + 羽化底色，返回为轻量圆底；
 * - [ChromeStyle.LIQUID_GLASS]：backdrop 折射玻璃（vibrancy + 多层 progressive blur + 底色 + 羽化底色），
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
    // 与重构前完全一致的几何参数：羽化/过渡分界 = 状态栏 + 32dp
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val featherStartFraction = topBarFeatherStartFraction(statusBarTopPx, density)

    Box(modifier = modifier.fillMaxWidth()) {
        // 模糊：羽化区间内由 20dp 渐变到 0dp（真正的强度渐变），区间上方保持满强度
        ProgressiveBackdropBlur(
            backdrop = backdrop,
            maxBlurRadius = GlassTopBarBlurRadius,
            span = ProgressiveBlurSpan(
                startFraction = featherStartFraction,
                endFraction = 1f,
                startIntensity = 1f,
                endIntensity = 0f
            ),
            modifier = Modifier.matchParentSize(),
            // 保持原有 vibrancy → blur 的渲染链顺序（与全项目其它液态玻璃一致）
            effects = { blurRadius ->
                vibrancy()
                blur(blurRadius.toPx())
            }
        )
        // 底色：原「上实下虚」渐变 × 原羽化关键点，合成为一个直接 alpha 渐变。
        // 不再用 DstIn——模糊强度渐变已由 ProgressiveBackdropBlur 承担，
        // 再叠一层 alpha 遮罩会把过渡带削得过淡。
        Box(
            Modifier
                .matchParentSize()
                .background(
                    topBarFeatheredBrush(
                        topColor = stripTint.copy(alpha = 0.95f),
                        bodyColor = stripTint,
                        startFraction = featherStartFraction
                    )
                )
        )
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
    val featherStartFraction = topBarFeatherStartFraction(statusBarTopPx, density)
    val backgroundAlpha = config.backgroundColorAlpha.coerceIn(0f, 1f)
    val tintAlpha = config.tintAlpha.coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxWidth()) {
        // 底色：与原先 hazeEffect 的 backgroundColor 等值，绘制在模糊之下
        Box(
            Modifier
                .matchParentSize()
                .background(
                    topBarFeatheredBrush(
                        topColor = surfaceColor.copy(alpha = backgroundAlpha),
                        bodyColor = surfaceColor.copy(alpha = backgroundAlpha),
                        startFraction = featherStartFraction
                    )
                )
        )
        // 模糊：羽化区间内由满强度渐变到 0，区间上方保持满强度（Haze progressive）
        ProgressiveHazeBlur(
            hazeState = hazeState,
            maxBlurRadius = config.blurRadius,
            span = ProgressiveBlurSpan(
                startFraction = featherStartFraction,
                endFraction = 1f,
                startIntensity = 1f,
                endIntensity = 0f
            ),
            noiseFactor = config.noiseFactor,
            modifier = Modifier.matchParentSize()
        )
        // 着色：与原先 haze tints 等值（0.4），绘制在模糊之上
        Box(
            Modifier
                .matchParentSize()
                .background(
                    topBarFeatheredBrush(
                        topColor = surfaceColor.copy(alpha = tintAlpha),
                        bodyColor = surfaceColor.copy(alpha = tintAlpha),
                        startFraction = featherStartFraction
                    )
                )
        )
        // 顶部实色渐隐（原结构）：状态栏区域更实，到羽化起点已归零
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to surfaceColor.copy(alpha = 0.95f),
                        featherStartFraction to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
        )
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

/** 顶栏玻璃模糊半径（沿用重构前参数） */
private val GlassTopBarBlurRadius = 20.dp

/** 羽化/过渡分界：状态栏 + 32dp（沿用重构前已调好的参数） */
private val TopBarFeatherStartOffset = 32.dp

/** 羽化中段保留的 alpha 比例与位置占比（与旧 DstIn 蒙版关键点一致） */
private const val TopBarFeatherMidScale = 0.35f
private const val TopBarFeatherMidFraction = 0.55f

/** 羽化末段提前归零，避免底边残留一条未蒙干净的亮线 */
private const val TopBarFeatherZeroFraction = 0.995f

/** 羽化起点占整个顶栏（状态栏 + 64dp 行）高度的比例 */
private fun topBarFeatherStartFraction(statusBarTopPx: Int, density: Density): Float {
    val featherStart = statusBarTopPx + with(density) { TopBarFeatherStartOffset.toPx() }
    val totalHeight = statusBarTopPx + with(density) { ChromeTopBarRowHeight.toPx() }
    return (featherStart / totalHeight)
        .coerceIn(0f, TopBarFeatherZeroFraction - 0.01f)
}

/**
 * 顶栏底色/着色的羽化 alpha 渐变：保留原 DstIn 蒙版的关键点
 * （羽化起点 alpha 1 → 中段 0.35 → 0.995 处归零），
 * 但用直接渐变代替离屏 + DstIn，避免遮罩与模糊的 RenderEffect 相互干扰。
 *
 * 原结构是「底色/cover 渐变 × 羽化蒙版」；这里把两层 alpha 直接合成到一条渐变里，
 * 关键位置与旧参数完全一致。
 */
private fun topBarFeatheredBrush(
    topColor: Color,
    bodyColor: Color,
    startFraction: Float
): Brush {
    val start = startFraction.coerceIn(0f, TopBarFeatherZeroFraction - 0.01f)
    val mid = (start + (1f - start) * TopBarFeatherMidFraction)
        .coerceIn(start, TopBarFeatherZeroFraction - 0.001f)
    // 末段用 bodyColor 的 0 alpha，避免「透明黑」参与通道插值产生灰色淡边
    return Brush.verticalGradient(
        0f to topColor,
        start to bodyColor,
        mid to bodyColor.copy(alpha = bodyColor.alpha * TopBarFeatherMidScale),
        TopBarFeatherZeroFraction to bodyColor.copy(alpha = 0f),
        1f to bodyColor.copy(alpha = 0f)
    )
}
