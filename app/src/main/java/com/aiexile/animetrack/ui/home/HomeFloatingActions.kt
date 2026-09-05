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
import com.aiexile.animetrack.ui.components.liquidglass.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
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
    advancedBlurEnabled: Boolean = false,
    blurConfig: AdvancedBlurConfig = AdvancedBlurConfig.DEFAULT,
    liquidGlassEnabled: Boolean = false,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {

    val colorScheme = MaterialTheme.colorScheme
    // 液态玻璃模式优先生效（与悬浮胶囊/顶栏 FAB 一致）
    val liquidGlass = liquidGlassEnabled && backdrop != null
    val fabContainerColor = if (advancedBlurEnabled) Color.Transparent else colorScheme.surfaceContainer
    val fabBlur = fabBlurModifier(hazeState, !liquidGlass && advancedBlurEnabled, blurConfig)
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
                if (liquidGlass) {
                    LiquidGlassFab(
                        icon = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                        contentDescription = stringResource(R.string.home_scroll_to_top),
                        backdrop = backdrop!!,
                        onClick = onScrollToTop
                    )
                } else {
                    ScrollToTopFab(onClick = onScrollToTop, containerColor = fabContainerColor, blurModifier = fabBlur, elevation = fabElevation)
                }
            }
            if (liquidGlass) {
                LiquidGlassFab(
                    icon = rememberAppIconPainter(AppIcon.ADD),
                    contentDescription = stringResource(R.string.home_add_anime),
                    backdrop = backdrop!!,
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
            if (liquidGlass) {
                LiquidGlassFab(
                    icon = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                    contentDescription = stringResource(R.string.home_scroll_to_top),
                    backdrop = backdrop!!,
                    onClick = onScrollToTop
                )
            } else {
                ScrollToTopFab(onClick = onScrollToTop, containerColor = fabContainerColor, blurModifier = fabBlur, elevation = fabElevation)
            }
        }
    }
}

/**
 * 液态玻璃 FAB（56dp 方形，与 M3 FAB 同尺寸）：折射玻璃渲染 + LiquidButton 同款
 * 交互物理（按压放大、拖动跟随形变、指尖高光）。lens 仅支持 CornerBasedShape，
 * 故用 16dp 普通圆角（视觉与 Squircle 差异可忽略）。
 */
@Composable
private fun LiquidGlassFab(
    icon: Painter,
    contentDescription: String,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope)
    }
    val containerColor =
        if (isAppDarkTheme()) Color(0xFF121212).copy(0.4f)
        else Color(0xFFFAFAFA).copy(0.4f)
    val contentColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
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
                },
                onDrawSurface = { drawRect(containerColor) }
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
