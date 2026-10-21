package com.aiexile.animetrack.ui.home

import androidx.compose.animation.AnimatedVisibility

import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.LiquidGlassKind
import com.aiexile.animetrack.ui.components.LiquidGlassSpec
import com.aiexile.animetrack.ui.components.drawLiquidGlass
import com.aiexile.animetrack.ui.components.liquidGlassSpec
import com.aiexile.animetrack.ui.components.liquidglass.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 主页浮动按钮组（独立 Composable，用于在 SharedTransitionLayout 外层渲染，
 * 避免转场期间被共享元素 Overlay 遮盖）。
 */
@Composable
fun HomeFloatingActions(
    fabLocation: FabLocation,
    isCapsuleNav: Boolean,
    showScrollToTop: Boolean,
    onScrollToTop: () -> Unit,
    onAddClick: () -> Unit,
    hazeState: HazeState,
    style: ChromeStyle,
    blurConfig: AdvancedBlurConfig = AdvancedBlurConfig.DEFAULT,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {

    val colorScheme = MaterialTheme.colorScheme

    // 「导航栏旁」且为胶囊导航：加号/回到顶部按钮槽由 MainOverlay 胶囊分支
    // 渲染在胶囊右侧（见 BesideNavFabSlot），此处不再渲染；
    // 传统沉底导航下 NAV_BAR 回退为右下角悬浮行为
    if (fabLocation == FabLocation.NAV_BAR && isCapsuleNav) return

    val advancedBlurEnabled = style == ChromeStyle.HAZE
    // 液态玻璃模式优先生效（与悬浮胶囊/顶栏 FAB 一致）
    val liquidBackdrop = backdrop.takeIf { style == ChromeStyle.LIQUID_GLASS }
    // 常规悬浮按钮的玻璃规格：保持轻模糊的通透感（「导航栏旁」按钮槽另用 CapsuleCompanion）
    val fabGlassSpec = liquidGlassSpec(LiquidGlassKind.Fab)
    val fabContainerColor = if (advancedBlurEnabled) Color.Transparent else colorScheme.surfaceContainer
    val fabBlur = fabBlurModifier(hazeState, advancedBlurEnabled && liquidBackdrop == null, blurConfig)
    val fabElevation = if (advancedBlurEnabled) {
        androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    } else {
        androidx.compose.material3.FloatingActionButtonDefaults.elevation()
    }

    val fabOffsetY = if (isCapsuleNav) (-56).dp else 0.dp
    val fabEndPadding = 24.dp
    val fabBottomPadding = if (isCapsuleNav) 36.dp else 84.dp

    if (fabLocation == FabLocation.BOTTOM_RIGHT) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .offset(y = fabOffsetY)
                .navigationBarsPadding()
                .padding(end = fabEndPadding, bottom = fabBottomPadding)
        ) {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = scaleIn(
                    initialScale = 0.3f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = fadeOut(tween(100, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        targetScale = 0.8f,
                        animationSpec = tween(100, easing = FastOutSlowInEasing)
                    )
            ) {
                if (liquidBackdrop != null) {
                    LiquidGlassFab(
                        icon = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                        contentDescription = stringResource(R.string.home_scroll_to_top),
                        backdrop = liquidBackdrop,
                        spec = fabGlassSpec,
                        onClick = onScrollToTop
                    )
                } else {
                    ScrollToTopFab(onClick = onScrollToTop, containerColor = fabContainerColor, blurModifier = fabBlur, elevation = fabElevation)
                }
            }
            if (liquidBackdrop != null) {
                LiquidGlassFab(
                    icon = rememberAppIconPainter(AppIcon.ADD),
                    contentDescription = stringResource(R.string.home_add_anime),
                    backdrop = liquidBackdrop,
                    spec = fabGlassSpec,
                    onClick = onAddClick
                )
            } else {
                androidx.compose.material3.FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = fabContainerColor,
                    contentColor = colorScheme.primary,
                    shape = SquircleShape(16.dp),
                    elevation = fabElevation,
                    modifier = Modifier
                        .directionalFabShadow(shape = SquircleShape(16.dp))
                        .then(fabBlur)
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.ADD),
                        contentDescription = stringResource(R.string.home_add_anime),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    } else {
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = scaleIn(
                initialScale = 0.3f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(tween(100, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(100, easing = FastOutSlowInEasing)
                ),
            modifier = modifier
                .offset(y = fabOffsetY)
                .navigationBarsPadding()
                .padding(end = fabEndPadding, bottom = fabBottomPadding)
        ) {
            if (liquidBackdrop != null) {
                LiquidGlassFab(
                    icon = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                    contentDescription = stringResource(R.string.home_scroll_to_top),
                    backdrop = liquidBackdrop,
                    spec = fabGlassSpec,
                    onClick = onScrollToTop
                )
            } else {
                ScrollToTopFab(onClick = onScrollToTop, containerColor = fabContainerColor, blurModifier = fabBlur, elevation = fabElevation)
            }
        }
    }
}

/**
 * 液态玻璃 FAB（默认 56dp 方形，与 M3 FAB 同尺寸）：折射玻璃渲染 + LiquidButton 同款
 * 交互物理（按压放大、拖动跟随形变、指尖高光）。lens 仅支持 CornerBasedShape，
 * 故用 16dp 普通圆角（视觉与 Squircle 差异可忽略）。
 * size/shape/spec 可覆写：「导航栏旁」按钮槽用 54dp 全圆与胶囊对齐，并传入
 * CapsuleCompanion 规格（与胶囊同白度）；其余入口沿用 [LiquidGlassKind.Fab] 的通透感。
 */
@Composable
private fun LiquidGlassFab(
    icon: Painter,
    contentDescription: String,
    backdrop: Backdrop,
    spec: LiquidGlassSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fabSize: Dp = 56.dp,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }
    val contentColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(fabSize)
            .drawLiquidGlass(
                backdrop = backdrop,
                shape = { shape },
                spec = spec,
                layerBlock = {
                    // 与顶栏玻璃 FAB 同款交互物理（Kyant0 LiquidButton）
                    val width = size.width
                    val height = size.height
                    val progress = interactiveHighlight.pressProgress
                    val scale = 1f + 4.dp.toPx() / height * progress
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
            // 高光层绘制在玻璃之上、图标之下；手势仅观察不消费，不影响点击
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

internal fun Modifier.directionalFabShadow(
    shape: Shape,
    elevation: Dp = 2.dp,
    shadowSpread: Dp = 8.dp,
    enabled: Boolean = true
): Modifier = this
    .then(if (enabled) Modifier.shadow(elevation = elevation, shape = shape) else Modifier)
    .drawBehind {
        if (!enabled) return@drawBehind
        val spread = shadowSpread.toPx()
        val shadowColor = Color.Black.copy(alpha = 0.18f)

        // 底部阴影
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(shadowColor, Color.Transparent),
                startY = size.height,
                endY = size.height + spread
            ),
            topLeft = Offset(0f, size.height),
            size = androidx.compose.ui.geometry.Size(size.width, spread)
        )

        // 右侧阴影
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(shadowColor, Color.Transparent),
                startX = size.width,
                endX = size.width + spread
            ),
            topLeft = Offset(size.width, 0f),
            size = androidx.compose.ui.geometry.Size(spread, size.height)
        )

        // 右下角阴影
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(shadowColor, Color.Transparent),
                center = Offset(size.width, size.height),
                radius = spread
            ),
            topLeft = Offset(size.width, size.height),
            size = androidx.compose.ui.geometry.Size(spread, spread)
        )
    }

@Composable
internal fun fabBlurModifier(
    hazeState: HazeState,
    enabled: Boolean,
    config: AdvancedBlurConfig,
    shape: Shape = SquircleShape(16.dp)
): Modifier {
    if (!enabled) return Modifier
    return advancedHazeEffect(
        hazeState = hazeState,
        config = config,
        shape = shape
    )
}

@Composable
private fun ScrollToTopFab(
    onClick: () -> Unit,
    containerColor: Color,
    blurModifier: Modifier,
    elevation: androidx.compose.material3.FloatingActionButtonElevation,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = SquircleShape(16.dp),
        elevation = elevation,
        modifier = modifier
            .directionalFabShadow(shape = SquircleShape(16.dp))
            .then(blurModifier)
    ) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
            contentDescription = stringResource(R.string.home_scroll_to_top),
            modifier = Modifier.size(24.dp)
        )
    }
}

/** 「导航栏旁」加号按钮尺寸：52dp 略小于胶囊高（54dp），视觉与导航栏统一 */
internal val BesideNavFabSize = 52.dp

/** 「导航栏旁」按钮与胶囊的水平间隔 */
internal val BesideNavFabGap = 12.dp

/** 「导航栏旁」按钮槽预留宽度：按钮 + 间隔（胶囊收窄总宽） */
internal val BesideNavFabSlotWidth = BesideNavFabSize + BesideNavFabGap

/**
 * 「导航栏旁」按钮槽滑入滑出的「挤压」形变：与 navFabReserve 同一动画源驱动，
 * 越贴近屏幕右缘（进度越小）形变越大——水平压扁、纵向微鼓（体积守恒感），
 * 归位进度=1 时恢复原状。滑入=从边缘挤回来，滑出=被挤出去，
 * 形变与胶囊让位逐帧锁定，无独立时序。
 */
internal fun Modifier.besideNavFabSquish(
    reserve: Dp,
    slotWidth: Dp = BesideNavFabSlotWidth
): Modifier = graphicsLayer {
    val progress = (reserve / slotWidth).coerceIn(0f, 1f)
    val squash = (1f - progress) * 0.35f
    scaleX = 1f - squash
    scaleY = 1f + squash * 0.5f
}

/**
 * 「导航栏旁」按钮槽：渲染在胶囊导航右侧预留位（MainOverlay 胶囊分支内），
 * 加号在下方（52dp，略小于胶囊高 54dp），通过 MainOverlay 侧 bottom
 * padding 与胶囊中心对齐，回到顶部小按钮竖排于加号上方。
 * 普通/高级模糊/液态玻璃三态与右下角 FAB 一致；按钮全圆角 Squircle（视觉即圆形）。
 *
 * 基准约定：胶囊导航自身不消费 navigationBars inset（只留 24dp 底边距），
 * 因此本槽不得再叠 `.navigationBarsPadding()`——否则加号会按机型上报的
 * 手势提示条高度上浮（浅色机型 0～24dp，三键导航约 48dp），与胶囊错开。
 */
@Composable
fun BesideNavFabSlot(
    showScrollToTop: Boolean,
    onScrollToTop: () -> Unit,
    onAddClick: () -> Unit,
    hazeState: HazeState,
    style: ChromeStyle,
    blurConfig: AdvancedBlurConfig = AdvancedBlurConfig.DEFAULT,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val advancedBlurEnabled = style == ChromeStyle.HAZE
    val liquidBackdrop = backdrop.takeIf { style == ChromeStyle.LIQUID_GLASS }
    // 与胶囊同一白度：共用 CapsuleCompanion 规格（同底色 + 同 8dp 模糊），
    // 折射按 52dp 小面积收敛到 12dp；其余位置的按钮仍为 LiquidGlassKind.Fab 的通透感
    val slotGlassSpec = liquidGlassSpec(LiquidGlassKind.CapsuleCompanion)
    val containerColor = if (advancedBlurEnabled) Color.Transparent else colorScheme.surfaceContainer
    val fabBlur = fabBlurModifier(hazeState, advancedBlurEnabled && liquidBackdrop == null, blurConfig, SquircleShape(100.dp))
    val fabElevation = if (advancedBlurEnabled) {
        androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    } else {
        androidx.compose.material3.FloatingActionButtonDefaults.elevation()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = scaleIn(
                initialScale = 0.3f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(tween(100, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(100, easing = FastOutSlowInEasing)
                )
        ) {
            if (liquidBackdrop != null) {
                LiquidGlassFab(
                    icon = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                    contentDescription = stringResource(R.string.home_scroll_to_top),
                    backdrop = liquidBackdrop,
                    spec = slotGlassSpec,
                    onClick = onScrollToTop,
                    fabSize = BesideNavFabSize,
                    shape = RoundedCornerShape(BesideNavFabSize / 2)
                )
            } else {
                BesideNavCircleFab(
                    icon = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                    contentDescription = stringResource(R.string.home_scroll_to_top),
                    onClick = onScrollToTop,
                    containerColor = containerColor,
                    blurModifier = fabBlur,
                    elevation = fabElevation
                )
            }
        }
        if (liquidBackdrop != null) {
            LiquidGlassFab(
                icon = rememberAppIconPainter(AppIcon.ADD),
                contentDescription = stringResource(R.string.home_add_anime),
                backdrop = liquidBackdrop,
                spec = slotGlassSpec,
                onClick = onAddClick,
                fabSize = BesideNavFabSize,
                shape = RoundedCornerShape(BesideNavFabSize / 2)
            )
        } else {
            BesideNavCircleFab(
                icon = rememberAppIconPainter(AppIcon.ADD),
                contentDescription = stringResource(R.string.home_add_anime),
                onClick = onAddClick,
                containerColor = containerColor,
                blurModifier = fabBlur,
                elevation = fabElevation
            )
        }
    }
}

/** 「导航栏旁」按钮槽的单个圆形按钮（52dp 全圆角 Squircle，普通/高级模糊态共用） */
@Composable
private fun BesideNavCircleFab(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    containerColor: Color,
    blurModifier: Modifier,
    elevation: androidx.compose.material3.FloatingActionButtonElevation,
    modifier: Modifier = Modifier
) {
    val shape = SquircleShape(100.dp)
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = shape,
        elevation = elevation,
        modifier = modifier
            .size(BesideNavFabSize)
            .directionalFabShadow(shape = shape)
            .then(blurModifier)
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp)
        )
    }
}
