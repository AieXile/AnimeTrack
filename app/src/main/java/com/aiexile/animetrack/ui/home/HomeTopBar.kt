package com.aiexile.animetrack.ui.home

import androidx.compose.foundation.background

import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.GreetingEffectStyle
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.LiquidGlassKind
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import com.aiexile.animetrack.ui.components.drawLiquidGlass
import com.aiexile.animetrack.ui.components.liquidGlassSpec
import com.aiexile.animetrack.ui.theme.LocalChromeStyle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.highlight.Highlight
import dev.chrisbanes.haze.HazeState

/** 顶栏可见时的底部两角圆角：顶栏作为悬浮面板，下沿以圆角收尾 */
private val TopBarBottomShape = SquircleShape(bottomStart = 32.dp, bottomEnd = 32.dp)

/**
 * 液态玻璃顶栏外形：几何与 [TopBarBottomShape] 相同（底部两角 Squircle 32dp），
 * 但顶部边缘整体上移到组件之外。
 *
 * drawBackdrop 的边缘高光（Highlight.Default）沿整个轮廓描边，通栏顶栏的顶边
 * 正好落在屏幕最上方，会画出一条本不该有的横向亮线。顶边上移后该段描边落到绘制
 * 图层外被裁掉，只保留底边/底角与左右两侧的玻璃高光。
 */
private val TopBarGlassShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val extend = size.height
        val outline = TopBarBottomShape.createOutline(
            Size(size.width, size.height + extend),
            layoutDirection,
            density
        )
        val path = (outline as? Outline.Generic)?.path ?: return outline
        return Outline.Generic(
            Path().apply {
                addPath(path)
                translate(Offset(0f, -extend))
            }
        )
    }
}

/** 关闭圆角时的玻璃外形：顶边同样上移，保证高光不会在屏幕顶部画出横向亮线 */
private val TopBarGlassSquareShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Rectangle(Rect(0f, -size.height, size.width, size.height))
}

/**
 * 主页顶部栏（独立 Composable，用于在 SharedTransitionLayout 外层渲染，
 * 避免转场期间被共享元素 Overlay 遮盖）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    viewModel: HomeViewModel,
    customGreeting: String,
    greetingEffectStyle: GreetingEffectStyle,
    showSearchButton: Boolean,
    fabLocation: FabLocation,
    isCurrentPage: Boolean,
    hasAnime: Boolean,
    hasFilteredItems: Boolean,
    onAddClick: () -> Unit,
    alwaysShowAddButton: Boolean = false,
    morphCollapse: Float = 0f,
    morphTargetColor: Color? = null,
    /** 界面外壳材质档位：标准实色 / HAZE 毛玻璃 / LIQUID_GLASS 折射玻璃 */
    style: ChromeStyle = LocalChromeStyle.current,
    hazeState: HazeState? = null,
    /** 液态玻璃折射采样源（页面内容层），仅 LIQUID_GLASS 档需要 */
    backdrop: Backdrop? = null,
    /** 底部两角是否圆角（设置项，默认开启） */
    rounded: Boolean = true,
    /** 是否跟随界面外壳材质档位使用毛玻璃/液态玻璃（设置项，默认开启） */
    glassThemeEnabled: Boolean = true,
    blurConfig: AdvancedBlurConfig = AdvancedBlurConfig.DEFAULT,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isLocalSearchActive, isCurrentPage) {
        if (uiState.isLocalSearchActive && isCurrentPage) {
            focusRequester.requestFocus()
        }
    }

    // 玻璃档顶栏背景由 hazeEffect / 折射玻璃提供（底色透明），收拢全程与迁移 FAB 同材质；
    // 标准档在 morph 收拢时向 FAB 容器色插值（收拢终点与 FAB 重合，避免色块断层）
    val cornerShape = if (rounded) TopBarBottomShape else RectangleShape
    val glassShape = if (rounded) TopBarGlassShape else TopBarGlassSquareShape
    val glassBackdrop = backdrop.takeIf { style == ChromeStyle.LIQUID_GLASS && glassThemeEnabled }
    val hazeSource = hazeState.takeIf { style == ChromeStyle.HAZE && glassThemeEnabled }
    val containerColor = if (glassBackdrop != null || hazeSource != null) {
        Color.Transparent
    } else if (morphTargetColor != null && morphCollapse > 0f) {
        lerp(MaterialTheme.colorScheme.surface, morphTargetColor, morphCollapse)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 顶栏可见（未收拢）时底部两角圆角：顶栏作为悬浮面板收尾，
            // 与列表内容在顶栏下缘形成圆角接缝；完全收拢后顶栏移除，圆角随之消失
            .clip(cornerShape)
            .then(
                when {
                    glassBackdrop != null -> Modifier.drawLiquidGlass(
                        backdrop = glassBackdrop,
                        // 玻璃外形顶边上移：高光的顶部描边落在绘制图层外，屏幕顶部不出亮线
                        shape = { glassShape },
                        spec = liquidGlassSpec(LiquidGlassKind.Bar),
                        // 边缘高光与悬浮胶囊导航栏同款（Highlight.Default）：
                        // 底边/底角内圈一圈高光；顶部描边随外形落在图层外，屏幕顶部不出亮线
                        highlight = { Highlight.Default },
                        shadow = null,
                        // 通栏顶栏贴屏幕边缘：先模糊后提亮，玻璃缓冲区与组件等大，
                        // 最上沿不会因缓冲区外的透明像素被平均进来而「看起来没模糊」
                        blurFirst = true
                    )

                    // 毛玻璃参数与 FAB 一致；收拢时被外层 morph 裁剪，与 FAB 无缝衔接
                    hazeSource != null -> advancedHazeEffect(hazeSource, blurConfig)

                    else -> Modifier.background(containerColor)
                }
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        if (uiState.isLocalSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.SEARCH),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                BasicTextField(
                    value = TextFieldValue(
                        text = uiState.localSearchQuery,
                        selection = TextRange(uiState.localSearchQuery.length)
                    ),
                    onValueChange = { newValue ->
                        viewModel.updateLocalSearchQuery(newValue.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (uiState.localSearchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.home_search_anime),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (uiState.localSearchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.updateLocalSearchQuery("") },
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
                IconButton(
                    onClick = {
                        viewModel.clearLocalSearch()
                    }
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                        contentDescription = stringResource(R.string.home_close_search),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val greetingText = viewModel.resolveGreetingText(customGreeting)
                GreetingText(
                    greetingText = greetingText,
                    style = greetingEffectStyle,
                    shouldAnimate = greetingEffectStyle != GreetingEffectStyle.NONE &&
                        viewModel.shouldAnimateGreeting(greetingText),
                    onAnimated = { viewModel.onGreetingAnimated(it) },
                    // morph 收拢早期（0→0.35）问候语先行淡出，右侧按钮保留到终点与 FAB 图标交接
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - (morphCollapse / 0.35f).coerceIn(0f, 1f)
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val showSearchIcon = showSearchButton && hasAnime && hasFilteredItems
                    if (showSearchIcon) {
                        IconButton(onClick = { viewModel.startLocalSearch() }) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.SEARCH),
                                contentDescription = stringResource(R.string.common_search),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // 大屏（alwaysShowAddButton）无 FAB，顶栏始终提供添加入口。
                    // 与迁移 FAB 布局严格一致（48+1+48，无间距），morph 收拢时按钮位置零偏移
                    if (fabLocation == FabLocation.TOP_BAR || alwaysShowAddButton) {
                        if (showSearchIcon) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        IconButton(onClick = onAddClick) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.ADD),
                                contentDescription = stringResource(R.string.home_add_anime),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
