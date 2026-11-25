package com.aiexile.animetrack.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlin.math.roundToInt

/** Highlights a real control and keeps the guide actions inside the page's safe area. */
@Composable
internal fun FeatureSpotlight(
    target: Rect,
    title: String,
    message: String,
    step: Int,
    total: Int,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var overlayOrigin by remember { mutableStateOf<Offset?>(null) }
    var safeOrigin by remember { mutableStateOf<Offset?>(null) }
    val outline = MaterialTheme.colorScheme.primary
    val bubbleColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = SquircleShape(20.dp)
    val messageScroll = rememberScrollState()
    LaunchedEffect(step) { messageScroll.scrollTo(0) }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInWindow() }) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
            val origin = overlayOrigin ?: return@Canvas
            val cutout = target.translate(-origin).inflate(4.dp.toPx())
            val radius = CornerRadius(12.dp.toPx())
            val path = Path().apply { addRoundRect(RoundRect(cutout, radius)) }
            clipPath(path, ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.68f))
            }
            drawRoundRect(
                color = outline,
                topLeft = cutout.topLeft,
                size = cutout.size,
                cornerRadius = radius,
                style = Stroke(2.dp.toPx())
            )
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Layout(
                modifier = Modifier.fillMaxSize().onGloballyPositioned { safeOrigin = it.positionInWindow() },
                content = {
                    Surface(
                        modifier = Modifier.semantics { paneTitle = title },
                        shape = shape,
                        color = bubbleColor,
                        shadowElevation = 6.dp
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.onboarding_step_progress, step + 1, total),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        painter = rememberAppIconPainter(AppIcon.CLOSE),
                                        contentDescription = stringResource(R.string.onboarding_skip)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f, fill = false).verticalScroll(messageScroll),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() }
                                )
                                Text(
                                    message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                            ) {
                                if (step > 0) {
                                    TextButton(onClick = onPrevious) {
                                        Text(stringResource(R.string.player_guide_previous))
                                    }
                                }
                                Button(onClick = onNext) {
                                    Text(stringResource(
                                        if (step + 1 == total) R.string.player_guide_done else R.string.common_next_step
                                    ))
                                }
                            }
                        }
                    }
                    Canvas(Modifier) {
                        val arrow = Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(size.width / 2f, size.height)
                            lineTo(0f, size.height / 2f)
                            close()
                        }
                        drawPath(arrow, bubbleColor)
                    }
                }
            ) { measurables, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val localTarget = safeOrigin?.let { target.translate(-it) }
                if (localTarget == null || width == 0 || height == 0) {
                    layout(width, height) {}
                } else {
                    val margin = 12.dp.roundToPx().coerceAtMost(width / 4)
                    val gap = 16.dp.roundToPx()
                    val bubbleWidth = minOf(380.dp.roundToPx(), width - margin * 2)
                    val above = localTarget.top.roundToInt().coerceIn(0, height) - gap
                    val below = height - localTarget.bottom.roundToInt().coerceIn(0, height) - gap
                    val placeBelow = below >= above
                    val available = maxOf(above, below) - margin
                    val fitsBesideTarget = available >= 155.dp.roundToPx()
                    val bubble = measurables[0].measure(
                        Constraints.fixedWidth(bubbleWidth).copy(maxHeight = if (fitsBesideTarget) available else height)
                    )
                    val x = (localTarget.center.x.roundToInt() - bubble.width / 2)
                        .coerceIn(margin, width - bubble.width - margin)
                    val y = when {
                        !fitsBesideTarget -> (height - bubble.height) / 2
                        placeBelow -> height - below
                        else -> above - bubble.height
                    }
                    val arrowSize = 16.dp.roundToPx()
                    val arrow = measurables[1].measure(Constraints.fixed(arrowSize, arrowSize))
                    layout(width, height) {
                        if (fitsBesideTarget && bubble.width > arrowSize * 4) {
                            val arrowX = (localTarget.center.x.roundToInt() - arrowSize / 2)
                                .coerceIn(x + arrowSize, x + bubble.width - arrowSize * 2)
                            val arrowY = if (placeBelow) y - arrowSize / 2 else y + bubble.height - arrowSize / 2
                            arrow.place(arrowX, arrowY)
                        }
                        bubble.place(x, y)
                    }
                }
            }
        }
    }
}
