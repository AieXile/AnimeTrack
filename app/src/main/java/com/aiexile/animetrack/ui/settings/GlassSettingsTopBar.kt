package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.liquidglass.InteractiveHighlight
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** 顶栏高度（TopAppBar 标准值，玻璃模式下与标准模式保持一致） */
private val GlassTopBarRowHeight = 64.dp

/**
 * 当前是否启用设置二级页液态玻璃顶栏（跟随胶囊导航「液态玻璃」效果开关）。
 *
 * 页面同时用它决定：内容滚动容器是否挂 layerBackdrop 采样源、
 * 内容是否改为滚动到顶栏下方（背景模糊才可见）。
 */
@Composable
fun rememberSettingsLiquidGlassTopBarEnabled(): State<Boolean> {
    val settingsRepository = remember { AppContainer.getSettingsRepository() }
    return settingsRepository.capsuleLiquidGlassEnabled
        .collectAsState(settingsRepository.cachedCapsuleLiquidGlass())
}

/**
 * 液态玻璃顶栏模式下 LazyColumn/scroll 定位的补偿偏移（px，负值）：
 * 内容视口变为全屏高后，scrollToItem 会把目标项对齐到视口顶部（藏在顶栏后方），
 * 用负偏移把目标项滚到顶栏下缘之下；未启用玻璃时为 0（行为与旧版一致）。
 */
@Composable
fun rememberGlassTopBarScrollCompensation(liquidGlassEnabled: Boolean): Int {
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    return if (liquidGlassEnabled) {
        remember(density, statusBarTop) {
            -(statusBarTop + with(density) { GlassTopBarRowHeight.roundToPx() })
        }
    } else {
        0
    }
}

/**
 * 设置二级页顶栏。
 *
 * - 液态玻璃关闭：渲染与原先完全一致的标准 TopAppBar（返回按钮 + 标题）。
 * - 液态玻璃开启：整条顶栏（含状态栏区域）对页面内容做 vibrancy + blur 背景
 *   模糊（内容滚入顶栏下方后呈现毛玻璃透视），返回按钮为圆形液态玻璃按钮
 *   （参照 [com.aiexile.animetrack.ui.components.LiquidGlassNavBar] 的玻璃参数）。
 *
 * @param backdrop 页面内容层的 layerBackdrop 采样源；null 时退回标准顶栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSettingsTopBar(
    title: String,
    onBack: () -> Unit,
    liquidGlassEnabled: Boolean,
    backdrop: Backdrop?
) {
    if (!liquidGlassEnabled || backdrop == null) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            }
        )
        return
    }

    // 玻璃底色跟随应用内主题（与 LiquidGlassNavBar 同源），保证明暗切换时玻璃观感一致
    val isLightTheme = !isAppDarkTheme()
    val stripTint =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    // 羽化/过渡分界：标题文字中段（状态栏 + 半行高）
    val featherStartPx = statusBarTopPx + with(density) { 32f.dp.toPx() }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 玻璃背景层：内层子节点绘制玻璃效果，外层离屏合成后再叠加羽化蒙版——
        // 蒙版与玻璃效果分层，避免 DstIn 蒙版参与玻璃 RenderEffect 自身的 layer 处理
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // 羽化蒙版（DstIn）：玻璃自标题文字中段（状态栏 + 半行高）向下
                    // alpha 先缓后快渐隐，模糊效果平滑减弱到底部完全消失，隐化硬边界；
                    // 末段提前归零，避免底边残留一条未蒙干净的亮线
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
                    onDrawSurface = {
                        // 顶部玻璃更「实」：玻璃模式下内容视口全高，顶栏上方采样区是
                        // contentPadding 空白，模糊采到透明像素、纯 tint 显得透明；
                        // 自顶向下（至标题文字中段）渐变回常规玻璃底色，形成「上实下虚」
                        val transitionFraction = (featherStartPx / size.height).coerceIn(0f, 1f)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(GlassTopBarRowHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircleBackButton(
                onClick = onBack,
                backdrop = backdrop,
                isLightTheme = isLightTheme
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 圆形液态玻璃返回按钮：48dp 触控区内 40dp 玻璃圆（vibrancy + blur + lens 折射）。
 * 交互物理反馈与主界面顶部搜索/添加玻璃 FAB（TopBarActionsFloating.CombinedActionsFab）
 * 一致（Kyant0 LiquidButton 同款）：按压整体放大、拖动朝手指方向平移并沿角度
 * 拉伸（tanh 限幅）、指尖跟随高光；手势仅观察不消费，不影响点击返回。
 */
@Composable
private fun GlassCircleBackButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    isLightTheme: Boolean
) {
    val baseColor =
        if (isLightTheme) Color.Black.copy(alpha = 0.1f)
        else Color.White.copy(alpha = 0.1f)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(12f.dp.toPx(), 16f.dp.toPx())
                    },
                    layerBlock = {
                        // 交互物理反馈：按压放大 + 拖拽朝手指方向平移（tanh 限幅）
                        // + 沿拖拽角度果冻拉伸
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
                    },
                    onDrawSurface = { drawRect(baseColor) }
                )
                // 高光层绘制在玻璃之上、图标之下；手势仅观察不消费，不影响点击
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
