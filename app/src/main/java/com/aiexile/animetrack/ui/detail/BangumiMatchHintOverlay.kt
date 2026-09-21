package com.aiexile.animetrack.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 聚焦收敛缓动：起步快、收尾慢，模拟镜头对焦的落定感 */
private val FocusEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * 「Bangumi 未关联」详情页引导遮罩。
 *
 * 聚光灯式聚焦动画：遮罩并非直接出现，而是延迟片刻（待详情页转场结束）后，
 * 圆形洞半径从「覆盖全屏」向「匹配数据源」按钮收敛、遮罩透明度同步加深，
 * 模拟镜头对焦；提示卡片跟随聚光灯下缘从屏幕外升起，聚焦后段渐显落位。
 *
 * 交互：
 * - 点击洞内（按钮区域）→ [onMatchClick]（打开匹配对话框）
 * - 点击遮罩空白处 / 「知道了」→ [onDismiss]
 *
 * @param targetBounds 匹配按钮在 window 坐标系中的位置（由按钮 onGloballyPositioned 捕获）
 */
@Composable
fun BangumiMatchHintOverlay(
    visible: Boolean,
    targetBounds: Rect?,
    onDismiss: () -> Unit,
    onMatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && targetBounds != null,
        enter = fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 250)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        val bounds = targetBounds ?: return@AnimatedVisibility
        val density = LocalDensity.current

        // 圆形洞：按钮中心 + 半径（贴按钮边缘，不额外外扩，聚光灯更聚焦）
        val buttonCenter = Offset(
            x = (bounds.left + bounds.right) / 2f,
            y = (bounds.top + bounds.bottom) / 2f
        )
        val holeRadius = maxOf(bounds.width, bounds.height) / 2f

        // 遮罩自身在 window 中的偏移（edge-to-edge 下通常为 (0,0)，显式换算避免坐标假设）
        var overlayOffset by remember { mutableStateOf(Offset.Zero) }
        var overlaySize by remember { mutableStateOf(IntSize.Zero) }

        // 聚焦进度 0→1：驱动洞半径收敛与遮罩加深
        val focusProgress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            focusProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 500,
                    delayMillis = 250,
                    easing = FocusEasing
                )
            )
        }

        // 聚焦起始半径：洞中心到遮罩最远角落的距离，保证动画起点全屏透亮（无遮罩）
        fun startRadiusFor(width: Float, height: Float): Float {
            if (width <= 0f || height <= 0f) return holeRadius
            val local = buttonCenter - overlayOffset
            return maxOf(
                hypot(local.x, local.y),
                hypot(width - local.x, local.y),
                hypot(local.x, height - local.y),
                hypot(width - local.x, height - local.y)
            )
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    overlayOffset = it.boundsInWindow().topLeft
                    overlaySize = it.size
                }
                // drawBehind 画在 children（提示卡片）之下：遮罩盖住 Scaffold，卡片浮在遮罩上
                .drawBehind {
                    val progress = focusProgress.value
                    if (progress <= 0f) return@drawBehind
                    val radius = lerp(
                        startRadiusFor(size.width, size.height),
                        holeRadius,
                        progress
                    )
                    // 离屏 layer 内绘制：挖洞只擦除遮罩自身，不破坏底层 Scaffold 内容
                    val layerBounds = Rect(Offset.Zero, size)
                    drawContext.canvas.saveLayer(layerBounds, Paint())
                    drawRect(Color.Black.copy(alpha = 0.72f * progress))
                    drawCircle(
                        color = Color.Black,
                        radius = radius,
                        center = buttonCenter - overlayOffset,
                        blendMode = BlendMode.Clear
                    )
                    drawContext.canvas.restore()
                }
                // 点击分发：洞内 → 匹配；空白处 → 关闭（按最终半径判定，聚焦过程中同样生效）
                .pointerInput(buttonCenter, holeRadius, overlayOffset) {
                    detectTapGestures { position ->
                        val windowPos = position + overlayOffset
                        val inHole = (windowPos - buttonCenter).getDistance() <= holeRadius
                        if (inHole) onMatchClick() else onDismiss()
                    }
                }
        ) {
            // 提示卡片：跟随聚光灯下缘从屏幕外升起，聚焦后段渐显落位
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset {
                        val radius = lerp(
                            startRadiusFor(
                                overlaySize.width.toFloat(),
                                overlaySize.height.toFloat()
                            ),
                            holeRadius,
                            focusProgress.value
                        )
                        // 卡片顶部 = 洞底部（圆心 y + 半径）+ 20dp（局部坐标换算）
                        val holeBottom = buttonCenter.y + radius - overlayOffset.y
                        IntOffset(0, (holeBottom + with(density) { 20.dp.toPx() }).roundToInt())
                    }
                    .graphicsLayer {
                        val cardProgress =
                            ((focusProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        alpha = cardProgress
                        translationY = (1f - cardProgress) * 14.dp.toPx()
                    }
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = SquircleShape(12.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.detail_bangumi_hint_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.detail_bangumi_hint_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                // 纯 Text 替代 TextButton：后者强制 40dp minHeight + 48dp 触控目标，上下留白过大；
                // 遮罩本身洞外任意点击即关闭，此处无需独立触控目标
                Text(
                    text = stringResource(R.string.detail_bangumi_hint_button),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onDismiss)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}
