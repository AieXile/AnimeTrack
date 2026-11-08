package com.aiexile.animetrack.loadinglab

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.ui.components.SquircleShape
import kotlinx.coroutines.delay

/**
 * 方案 A：反色对页 + 笔触生长。
 *
 * 造型出处：原片 0–2.2s 的双联锦鲤（左页黄底蓝线 / 右页蓝底黄线），
 * 以及 0–1.5s 段线条从小点逐步长成完整鱼身的“笔触生长”
 * （见 `.trae/loading-anim-analysis/frames/f_001.jpg`、`f_004.jpg`）。
 *
 * 一页是 `primary` 底 + `onPrimary` 墨，另一页反过来，中间硬分界；
 * 同一条 S 形路径跨过分界，于是左右两半的墨色互换——这就是把“图底反转”缩到 56dp 的做法。
 *
 * 一个循环 1.74s：描线 0.90s（起笔快、收笔慢）→ 停留 0.30s → 收笔 0.42s → 空档 0.12s。
 * 收笔是“线段往回缩”，不是整体淡出，这样循环接得上，也不会出现“墨没了、只剩半块黑”的空窗。
 */
private const val DRAW_MS = 900
private const val HOLD_MS = 300
private const val RETRACT_MS = 420
private const val GAP_MS = 120

@Composable
internal fun DuotoneStrokeLoader(
    modifier: Modifier = Modifier,
    loaderSize: Dp = 56.dp,
    strokeWidth: Dp = 2.4.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = remember { SquircleShape(percent = 24) }
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current

    // 动画值以 State<Float> 交给 draw 阶段读取，不进重组。
    val drawProgress = progress.asState()

    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DRAW_MS,
                    easing = CubicBezierEasing(0.18f, 0.72f, 0.28f, 1f),
                ),
            )
            delay(HOLD_MS.toLong())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = RETRACT_MS, easing = LinearEasing),
            )
            delay(GAP_MS.toLong())
        }
    }

    val sourcePath = remember { Path() }
    val drawnPath = remember { Path() }
    val borderPath = remember { Path() }
    val measure = remember { PathMeasure() }
    val sizeCache = remember { SizeCache() }
    val stroke = remember(density, strokeWidth) {
        Stroke(
            width = with(density) { strokeWidth.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
    }
    val hairline = remember(density) {
        Stroke(width = with(density) { 0.8.dp.toPx() })
    }

    Canvas(modifier = modifier.size(loaderSize).clip(shape)) {
        val w = this.size.width
        val h = this.size.height
        if (!sizeCache.matches(w, h)) {
            LabGeometry.koi(Size(w, h), sourcePath)
            measure.setPath(sourcePath, false)
            LabGeometry.silhouette(shape, Size(w, h), this, borderPath)
            sizeCache.store(w, h)
        }

        // 反色对页：左页 primary 底 + onPrimary 墨，右页 surfaceContainerHigh 底 + primary 墨。
        drawRect(color = scheme.primary, size = Size(w / 2f, h))
        drawRect(
            color = scheme.surfaceContainerHigh,
            topLeft = Offset(w / 2f, 0f),
            size = Size(w / 2f, h),
        )
        // 右页与页面底色接近时，靠这一圈 hairline 保住徽标的边界。
        drawPath(borderPath, scheme.onSurface.copy(alpha = 0.14f), style = hairline)

        val progressValue = drawProgress.value
        if (progressValue <= 0f) return@Canvas

        val length = measure.length
        if (length <= 0f) return@Canvas
        drawnPath.reset()
        measure.getSegment(0f, length * progressValue, drawnPath, true)

        clipRect(left = 0f, top = 0f, right = w / 2f, bottom = h) {
            drawPath(drawnPath, scheme.onPrimary, style = stroke)
        }
        clipRect(left = w / 2f, top = 0f, right = w, bottom = h) {
            drawPath(drawnPath, scheme.primary, style = stroke)
        }
    }
}
