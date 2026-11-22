package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 渐进模糊（progressive blur）公共实现。
 *
 * 优先沿竖直方向改变模糊半径，而不是靠整层透明度淡出来伪装模糊强弱。
 *
 * 两个材质后端的实现路径：
 * - Haze（[ProgressiveHazeBlur]）：SDK 33+ 由着色器渐变模糊半径；较低版本由 Haze
 *   用同一条渐变曲线给满强度模糊做透明度遮罩，避免分段裁剪产生接缝。
 * - 液态玻璃（[ProgressiveBackdropBlur]）：kyant backdrop 没有 progressive API，
 *   由本文件用 [ProgressiveBlurLayers] 层不同半径的 `drawBackdrop` 叠出来。
 *
 * 过渡带之外按端点强度继续：0 = 无模糊，1 = 满强度。因此调用方只需要描述
 * 「从哪到哪、从多少到多少」，不用关心两个后端各自的差异。
 */

/** 过渡带默认叠层数：两处过渡带都只有 20–32dp，3 层即可覆盖 1/3、2/3、满强度。 */
internal const val ProgressiveBlurLayers = 3

/**
 * 一段竖直渐进模糊过渡带。
 *
 * @param startFraction 过渡带起点（占整个模糊层高度的比例）
 * @param endFraction 过渡带终点（此后按 [endIntensity] 恒定）
 * @param startIntensity 起点的模糊强度（0..1，相对满强度半径）
 * @param endIntensity 终点的模糊强度（0..1）
 */
@Immutable
internal data class ProgressiveBlurSpan(
    val startFraction: Float,
    val endFraction: Float,
    val startIntensity: Float,
    val endIntensity: Float
) {
    init {
        require(startFraction in 0f..1f && endFraction in 0f..1f) {
            "span fractions must be in 0..1: $startFraction..$endFraction"
        }
        require(startFraction < endFraction) {
            "span start must be before end: $startFraction..$endFraction"
        }
        require(startIntensity in 0f..1f && endIntensity in 0f..1f) {
            "intensities must be in 0..1: $startIntensity..$endIntensity"
        }
    }

    /** 强度是否向下递增（决定多层遮罩的方向） */
    val increasingDownward: Boolean get() = endIntensity >= startIntensity
}

/**
 * 按 [span] 渲染 haze 渐进模糊（不含底色 / tint / 高光，调用方自行叠加）。
 *
 * 渐隐段和恒定段共用一个模糊节点，采样不会在两段交界处被裁断。
 */
@Composable
internal fun ProgressiveHazeBlur(
    hazeState: HazeState,
    maxBlurRadius: Dp,
    span: ProgressiveBlurSpan,
    modifier: Modifier = Modifier,
    noiseFactor: Float = 0f
) {
    val progressive = remember(span) {
        val start = Color.Black.copy(alpha = span.startIntensity)
        val end = Color.Black.copy(alpha = span.endIntensity)
        HazeProgressive.Brush(
            gradientBrush(
                0f to start,
                span.startFraction to start,
                span.endFraction to end,
                1f to end
            )
        )
    }
    Box(
        modifier
            .fillMaxSize()
            .hazeEffect(state = hazeState) {
                blurRadius = maxBlurRadius
                // 显式指定透明底色和空白 tint，避免继承默认样式；着色由调用方绘制。
                backgroundColor = Color.Transparent
                tints = listOf(HazeTint.Unspecified)
                this.noiseFactor = noiseFactor
                this.progressive = progressive
            }
    )
}

/**
 * 按 [span] 渲染 kyant backdrop 渐进模糊（不含 tint / 高光 / 阴影）。
 *
 * [steps] 层叠：每层半径 = 满强度半径 × 该层强度，再用竖直渐变 `DstIn` 只显示它负责的
 * 强度区间；层间用渐变重叠过渡，合成后呈现连续的半径渐变。
 *
 * **每层都铺满整个模糊节点**（而不是只铺过渡带子区域）：kyant 的 blur 需要用
 * `padding = 半径` 采样节点外的内容，若把层裁在过渡带边界，过渡带末端会丢掉下半侧
 * 的采样上下文，表现为「过渡带结束处一条硬边」。铺满整节点后，过渡带末端与恒定段
 * 由同一套多层遮罩连续覆盖；最外层再用 [clipToBounds] 只裁掉节点外的溢出，不影响内部采样。
 *
 * 当前用法都是满量程过渡（0→1 或 1→0），遮罩自然覆盖过渡带之外的恒定段；
 * 该实现不支持中间强度（例如 0→0.5 的恒定半强度），需要时请另行扩展。
 */
@Composable
internal fun ProgressiveBackdropBlur(
    backdrop: Backdrop,
    maxBlurRadius: Dp,
    span: ProgressiveBlurSpan,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    steps: Int = ProgressiveBlurLayers,
    effects: BackdropEffectScope.(Dp) -> Unit = { blurRadius -> blur(blurRadius.toPx()) }
) {
    require(span.startIntensity == 0f || span.startIntensity == 1f) {
        "ProgressiveBackdropBlur only supports 0/1 endpoint intensities: $span"
    }
    require(span.endIntensity == 0f || span.endIntensity == 1f) {
        "ProgressiveBackdropBlur only supports 0/1 endpoint intensities: $span"
    }
    require(span.startIntensity != span.endIntensity) {
        "ProgressiveBackdropBlur needs a non-uniform span: $span"
    }

    Box(modifier.clipToBounds()) {
        val layerCount = steps.coerceAtLeast(2)
        repeat(layerCount) { index ->
            val layer = index + 1
            val intensity = layerIntensity(span, layer, layerCount)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithCache {
                        val mask = bandMaskBrush(span, layer, layerCount, endY = size.height)
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = mask, blendMode = BlendMode.DstIn)
                        }
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = { effects(maxBlurRadius * intensity) },
                            highlight = null,
                            shadow = null
                        )
                )
            }
        }
    }
}

/**
 * 过渡带内第 [layer] 层（1..[layers]）对应的强度。
 * 向下递增：从 startIntensity 升到 endIntensity；向下递减：半径排序仍保持「弱→强」，
 * 这样叠绘时越靠后（越强）的层负责越靠上的位置。
 */
private fun layerIntensity(span: ProgressiveBlurSpan, layer: Int, layers: Int): Float {
    val fraction = layer / layers.toFloat()
    return if (span.increasingDownward) {
        span.startIntensity + (span.endIntensity - span.startIntensity) * fraction
    } else {
        span.endIntensity + (span.startIntensity - span.endIntensity) * fraction
    }
}

/**
 * 过渡带内第 [layer] 层的 `DstIn` 遮罩（坐标按整个模糊节点高度）。
 *
 * - 向下递增：第 layer 层在过渡带内的对应区间由透明渐变到不透明，之后保持不透明到节点底
 *   （因此过渡带以下的恒定满强度段也由这层连续覆盖，不再另开一个节点）。
 * - 向下递减：镜像到上方，越强的层负责越靠上的区间，过渡带以上保持不透明。
 */
private fun bandMaskBrush(
    span: ProgressiveBlurSpan,
    layer: Int,
    layers: Int,
    endY: Float
): Brush {
    val n = layers.toFloat()
    val start = span.startFraction
    val length = span.endFraction - span.startFraction
    return if (span.increasingDownward) {
        val fadeStart = start + (layer - 1) / n * length
        val fadeEnd = start + layer / n * length
        gradientBrush(
            0f to Color.Transparent,
            fadeStart to Color.Transparent,
            fadeEnd to Color.Black,
            1f to Color.Black,
            endY = endY
        )
    } else {
        val opaqueEnd = start + (1f - layer / n) * length
        val transparentStart = start + (1f - (layer - 1) / n) * length
        gradientBrush(
            0f to Color.Black,
            opaqueEnd to Color.Black,
            transparentStart to Color.Transparent,
            1f to Color.Transparent,
            endY = endY
        )
    }
}

/** 竖直渐变（同位置停靠点去重，避免 Compose 对非法 colorStops 抛错） */
internal fun gradientBrush(
    vararg stops: Pair<Float, Color>,
    endY: Float = Float.POSITIVE_INFINITY
): Brush {
    val merged = mutableListOf<Pair<Float, Color>>()
    stops.sortedBy { it.first }.forEach { stop ->
        val position = stop.first.coerceIn(0f, 1f)
        if (merged.isNotEmpty() && merged.last().first == position) {
            merged[merged.lastIndex] = position to stop.second
        } else {
            merged += position to stop.second
        }
    }
    return Brush.verticalGradient(
        *merged.toTypedArray(),
        startY = 0f,
        endY = endY
    )
}

/**
 * 纯色竖直渐隐刷：[color] 在 [fadeEndFraction] 之前由透明过渡到自身 alpha，
 * 之后保持。用于导航栏磨砂层的淡底色（与旧 DstIn 遮罩的透明度曲线一致）。
 *
 * 起点用 [color] 的 0 alpha 而不是 [Color.Transparent]：后者是「透明黑」，
 * Compose 渐变按非预乘通道插值，会在过渡带里混入灰色（视觉上像一条淡边/脏色）。
 */
internal fun verticalFadeTintBrush(color: Color, fadeEndFraction: Float): Brush =
    gradientBrush(
        0f to color.copy(alpha = 0f),
        fadeEndFraction.coerceIn(0f, 1f) to color,
        1f to color
    )
