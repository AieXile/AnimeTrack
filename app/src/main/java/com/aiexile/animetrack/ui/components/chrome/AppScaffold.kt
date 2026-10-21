package com.aiexile.animetrack.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.theme.LocalChromeStyle

/**
 * 页面骨架交给内容层的一组结果：
 *
 * - [layout]：挂在内容根上的修饰符。标准档 = `Modifier.padding(inner)`；
 *   玻璃档 = 采样源（hazeSource / layerBackdrop）+ 页面背景色（背景必须画在采样源
 *   之内，采样层才不透明、玻璃才有可模糊的底），内容视口全高；
 * - [content]：玻璃档需要由滚动容器自行垫出的顶/底内边距（LazyColumn 的
 *   `contentPadding`、Column+verticalScroll 的尾部 `padding`、非滚动内容的 `padding`）；
 *   标准档为零（内边距已由 [layout] 施加）。带 bottomBar 的页面在玻璃档把它的 bottom
 *   作为内容根 padding，避免内容从底栏下透出；
 * - [scrollCompensation]：玻璃档 `animateScrollToItem` 的补偿偏移（负值），标准档 0；
 * - [sources]：当前档位的采样源。供页面内需要「同层玻璃浮层」的组件（如设置项选项菜单）
 *   显式消费，浮层自身不能挂采样源；
 * - [topInset] / [bottomInset]：顶栏 + 状态栏 / 底栏 + 导航栏的**真实**内边距，
 *   两档都非零（与 [content] 不同，后者只在玻璃档给滚动容器垫空间）。
 *   不挂 [layout] 的同层浮层用它计算安全区，避免菜单压到顶栏或底栏下面。
 */
@Stable
class AppScaffoldPadding(
    val layout: Modifier,
    val content: PaddingValues,
    val scrollCompensation: Int,
    val style: ChromeStyle,
    val sources: ChromeSources,
    val topInset: Dp,
    val bottomInset: Dp
)

/**
 * 三档统一的页面骨架：内部决定顶栏材质、内容视口是否全高、是否挂采样源与滚动补偿。
 *
 * 页面只描述标题、返回、动作与内容，不再出现 `rememberLayerBackdrop` /
 * 条件 `padding` / 条件 `contentPadding` / 各自手写滚动补偿的样板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    topBarModifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable (AppScaffoldPadding) -> Unit
) {
    val style = LocalChromeStyle.current
    val sources = rememberChromeSources(style)
    Scaffold(
        modifier = modifier,
        contentWindowInsets = contentWindowInsets,
        topBar = {
            AppTopBar(
                title = title,
                onBack = onBack,
                sources = sources,
                style = style,
                actions = actions,
                titleContent = titleContent,
                scrollBehavior = scrollBehavior,
                modifier = topBarModifier
            )
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost
    ) { inner ->
        val glass = style != ChromeStyle.STANDARD
        val contentPadding = if (glass) {
            PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = inner.calculateBottomPadding()
            )
        } else {
            PaddingValues(0.dp)
        }
        val layoutModifier = if (glass) {
            Modifier
                .chromeSource(style, sources)
                // 页面背景必须画在采样源**之内**（chromeSource 之后）：LayerBackdrop 只录制
                // 其后方的绘制内容，Scaffold 自身的 containerColor 不在录制子树里，采样层
                // 的背景区域是透明像素——玻璃浮层（顶栏/选项菜单）在这些区域画不出模糊，
                // 真实内容会未模糊地直接透过卡片。补一层同色背景让采样层不透明，
                // 玻璃才有可模糊的底；颜色与 Scaffold 底色相同，页面自身视觉不变。
                .background(MaterialTheme.colorScheme.background)
        } else {
            Modifier.padding(inner)
        }
        val density = LocalDensity.current
        val scrollCompensation = if (glass) {
            -with(density) { inner.calculateTopPadding().roundToPx() }
        } else {
            0
        }
        content(
            AppScaffoldPadding(
                layout = layoutModifier,
                content = contentPadding,
                scrollCompensation = scrollCompensation,
                style = style,
                sources = sources,
                topInset = inner.calculateTopPadding(),
                bottomInset = inner.calculateBottomPadding()
            )
        )
    }
}
