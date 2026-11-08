package com.aiexile.animetrack.loadinglab

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density

/**
 * LoadingLab 几何层：只负责“画什么形状”，不涉及动画、配色和布局。
 *
 * 锦鲤 S 形出处：原片 0–2.2s 的鱼身主弧线 + 尾鳍回勾
 * （`.trae/loading-anim-analysis/frames/f_001.jpg` 的双联锦鲤）。
 * 坐标全部归一化到 0..1，按目标尺寸缩放后再描边，因此同一份路径数据可以同时给 32dp / 56dp / 80dp 用。
 */
internal object LabGeometry {

    /**
     * 形变采样点数。闭合轮廓在 300–400px 周长上约 2–3px 一个点，
     * 圆角不会被切出明显折角；点数再多只是白白增加每帧 lineTo 次数。
     */
    const val SAMPLES = 128

    /** 四段三次贝塞尔，首尾相接构成一条连续的锦鲤身体轮廓。 */
    private val KOI_CURVES: Array<FloatArray> = arrayOf(
        // 鱼头 → 身体中段
        floatArrayOf(0.44f, 0.06f, 0.22f, 0.18f, 0.28f, 0.36f, 0.41f, 0.44f),
        // 身体中段 → 尾柄收窄
        floatArrayOf(0.41f, 0.44f, 0.52f, 0.50f, 0.55f, 0.62f, 0.50f, 0.70f),
        // 尾柄 → 尾鳍外弧
        floatArrayOf(0.50f, 0.70f, 0.45f, 0.78f, 0.47f, 0.90f, 0.62f, 0.92f),
        // 尾鳍回勾（原片那一笔往上兜的尾巴）
        floatArrayOf(0.62f, 0.92f, 0.75f, 0.90f, 0.74f, 0.78f, 0.61f, 0.74f),
    )

    /** 把锦鲤轮廓写进 [into]（复用传入的 Path，不新建对象）。 */
    fun koi(size: Size, into: Path): Path {
        into.reset()
        KOI_CURVES.forEachIndexed { index, curve ->
            val x0 = curve[0] * size.width
            val y0 = curve[1] * size.height
            if (index == 0) into.moveTo(x0, y0)
            into.cubicTo(
                curve[2] * size.width, curve[3] * size.height,
                curve[4] * size.width, curve[5] * size.height,
                curve[6] * size.width, curve[7] * size.height,
            )
        }
        return into
    }

    /**
     * 形变终点：与卡片同一枚 [shape] 的轮廓。
     * 用 Shape 而不是手写 RoundRect，是为了让形变终点和真实卡片边缘完全重合——
     * “同一条线换个读法”要成立，终点必须严丝合缝。
     */
    fun silhouette(shape: Shape, size: Size, density: Density, into: Path): Path {
        into.reset()
        when (val outline = shape.createOutline(size, androidx.compose.ui.unit.LayoutDirection.Ltr, density)) {
            is Outline.Rectangle -> into.addRect(outline.rect)
            is Outline.Rounded -> into.addRoundRect(outline.roundRect)
            is Outline.Generic -> into.addPath(outline.path)
        }
        return into
    }

    /** 按弧长等距采样到 [out]，长度必须是 SAMPLES * 2。只在尺寸变化时调用。 */
    fun sample(path: Path, out: FloatArray) {
        val measure = PathMeasure()
        measure.setPath(path, false)
        val length = measure.length
        if (length <= 0f) return
        for (i in 0 until SAMPLES) {
            val distance = (length * i / (SAMPLES - 1)).coerceAtMost(length)
            val position = measure.getPosition(distance)
            out[i * 2] = position.x
            out[i * 2 + 1] = position.y
        }
    }

    /** 两组采样点线性插值成一条折线。每帧只 reset + lineTo，不产生新对象。 */
    fun morph(from: FloatArray, to: FloatArray, fraction: Float, into: Path) {
        into.reset()
        for (i in 0 until SAMPLES) {
            val x = from[i * 2] + (to[i * 2] - from[i * 2]) * fraction
            val y = from[i * 2 + 1] + (to[i * 2 + 1] - from[i * 2 + 1]) * fraction
            if (i == 0) into.moveTo(x, y) else into.lineTo(x, y)
        }
    }

    /**
     * 把 [samples] 循环移位，让第一个点尽量靠近 [reference] 的第一个点。
     *
     * 不做这件事的话，锦鲤曲线（从顶部中间起笔）和圆角轮廓（从左上角起笔）会按索引硬插值，
     * 中间态整圈会“打转”，看起来像线在乱飞，而不是“同一条线被折成矩形”。
     * 只在尺寸变化时执行一次，不在每帧路径上。
     */
    fun alignStart(reference: FloatArray, samples: FloatArray) {
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until SAMPLES) {
            val dx = samples[i * 2] - reference[0]
            val dy = samples[i * 2 + 1] - reference[1]
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i
            }
        }
        if (bestIndex == 0) return
        val copy = samples.copyOf()
        for (i in 0 until SAMPLES) {
            val source = (bestIndex + i) % SAMPLES
            samples[i * 2] = copy[source * 2]
            samples[i * 2 + 1] = copy[source * 2 + 1]
        }
    }
}

/** draw 阶段做尺寸缓存用的可变盒子，避免每帧重复构建路径。 */
internal class SizeCache {
    var width: Float = -1f
    var height: Float = -1f

    fun matches(w: Float, h: Float): Boolean = w == width && h == height

    fun store(w: Float, h: Float) {
        width = w
        height = h
    }
}
