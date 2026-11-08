package com.aiexile.animetrack.loadinglab

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.ui.components.SquircleShape
import kotlinx.coroutines.delay

/** 卡片与形变终点共用的形状，保证“线条变成卡片轮廓”时严丝合缝。 */
internal val LabCardShape = SquircleShape(12.dp)

/**
 * 方案 B：旋转换面 + 形变接续。
 *
 * 旋转换面出处：原片 2.2–2.9s / 3.5–4.4s / 6.7–7.5s 三次整幅画布旋转（每次 0.6–0.7s，约 20 帧），
 * 换面靠旋转遮掉（见 `.trae/loading-anim-analysis/burst/seq_0-3s.jpg` 第 3–4 行）。
 * 形变接续出处：`frames/f_004.jpg` 的旋转中间态——锦鲤尾鳍的长弧被“读成”人物发丝。
 *
 * 这里不照搬整幅画布旋转，而是把“翻面”落到单张卡片上：骨架绕左边缘翻出、内容绕右边缘翻入；
 * 加载态的锦鲤线条在中间被描出来，再形变（同一批采样点插值）成卡片自己的轮廓，然后淡出。
 */
@Composable
internal fun SpinRevealCard(
    revealed: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val progress = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(
            durationMillis = 620,
            delayMillis = index * 45,
            easing = FastOutSlowInEasing,
        ),
        label = "spinReveal",
    )

    Box(modifier) {
        // 骨架：绕左边缘翻走（像翻书页，而不是原地打转）
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val out = (progress.value / 0.42f).coerceIn(0f, 1f)
                    rotationY = -74f * out
                    cameraDistance = 14f * density
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    alpha = 1f - out
                }
                .clip(LabCardShape)
                .background(scheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(SquircleShape(8.dp))
                        .background(scheme.onSurface.copy(alpha = 0.10f)),
                )
                SkeletonBar(color = scheme.onSurface.copy(alpha = 0.22f), widthFraction = 0.82f)
                SkeletonBar(color = scheme.onSurface.copy(alpha = 0.14f), widthFraction = 0.55f)
            }
        }

        // 内容：绕右边缘翻入，翻到一半之后才开始显影
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val t = ((progress.value - 0.30f) / 0.70f).coerceIn(0f, 1f)
                    rotationY = 74f * (1f - t)
                    cameraDistance = 14f * density
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    alpha = t
                },
            content = { content() },
        )

        // 共用轮廓：描线 → 形变成卡片轮廓 → 淡出
        SharedContourLayer(progress = progress, modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun SkeletonBar(color: Color, widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(8.dp)
            .clip(SquircleShape(4.dp))
            .background(color),
    )
}

/**
 * 形变接续的那根线。进度只在 draw 阶段读取，不产生重组；
 * 采样点数组与 Path 全部 remember 复用，每帧只做 reset + lineTo。
 */
@Composable
private fun SharedContourLayer(
    progress: State<Float>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val shape = LabCardShape

    val koiPath = remember { Path() }
    val koiMeasure = remember { PathMeasure() }
    val silhouettePath = remember { Path() }
    val drawnPath = remember { Path() }
    val morphPath = remember { Path() }
    val koiSamples = remember { FloatArray(LabGeometry.SAMPLES * 2) }
    val silhouetteSamples = remember { FloatArray(LabGeometry.SAMPLES * 2) }
    val sizeCache = remember { SizeCache() }
    val stroke = remember(density) {
        Stroke(width = with(density) { 1.6.dp.toPx() })
    }

    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        if (!sizeCache.matches(w, h)) {
            LabGeometry.koi(Size(w, h), koiPath)
            koiMeasure.setPath(koiPath, false)
            LabGeometry.silhouette(shape, Size(w, h), this, silhouettePath)
            LabGeometry.sample(koiPath, koiSamples)
            LabGeometry.sample(silhouettePath, silhouetteSamples)
            LabGeometry.alignStart(koiSamples, silhouetteSamples)
            sizeCache.store(w, h)
        }

        val p = progress.value
        // 0.28–0.54 描线（笔触生长）；0.54–0.78 形变（形变接续）；0.78–1 淡出。
        val drawFraction = ((p - 0.28f) / 0.26f).coerceIn(0f, 1f)
        val morphFraction = ((p - 0.54f) / 0.24f).coerceIn(0f, 1f)
        val fade = 1f - ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
        if (drawFraction <= 0f || fade <= 0f) return@Canvas

        val path = if (morphFraction > 0f) {
            LabGeometry.morph(koiSamples, silhouetteSamples, morphFraction, morphPath)
            morphPath
        } else {
            val length = koiMeasure.length
            if (length <= 0f) return@Canvas
            drawnPath.reset()
            koiMeasure.getSegment(0f, length * drawFraction, drawnPath, true)
            drawnPath
        }

        // 同一条线左右异色，呼应原片的“反色对页”，但只出现在过渡层，不改变卡片本身配色。
        clipRect(left = 0f, top = 0f, right = w / 2f, bottom = h) {
            drawPath(path, scheme.primary.copy(alpha = fade), style = stroke)
        }
        clipRect(left = w / 2f, top = 0f, right = w, bottom = h) {
            drawPath(path, scheme.onSurfaceVariant.copy(alpha = fade), style = stroke)
        }
    }
}

/**
 * 一次性交接演示：两行卡片，每 4 秒在“骨架 / 内容”之间来回播一次，
 * 用来观察旋转方向、错峰节奏与线条形变是否顺。
 */
@Composable
internal fun SpinRevealStage(
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    var revealed by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            revealed = false
            delay(1400)
            revealed = true
        }
    }

    val gap = 12.dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(2) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(columns) { columnIndex ->
                    val index = rowIndex * columns + columnIndex
                    SpinRevealCard(
                        revealed = revealed,
                        index = index,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.72f),
                    ) {
                        LabCardContent(index)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabCardContent(index: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(LabCardShape)
            .background(scheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 内容态的封面用实色 primary（骨架态是低透明度灰块），保证两种状态一眼能分清；
            // 深浅色主题下都成立，也不引入装饰性渐变。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(SquircleShape(8.dp))
                    .background(scheme.primary),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 2 == 0) 0.82f else 0.66f)
                            .height(8.dp)
                            .clip(SquircleShape(4.dp))
                            .background(scheme.onPrimary.copy(alpha = 0.86f)),
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.44f)
                            .height(6.dp)
                            .clip(SquircleShape(3.dp))
                            .background(scheme.onPrimary.copy(alpha = 0.42f)),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .height(6.dp)
                    .clip(SquircleShape(3.dp))
                    .background(scheme.onSurface.copy(alpha = 0.32f)),
            )
        }
    }
}
