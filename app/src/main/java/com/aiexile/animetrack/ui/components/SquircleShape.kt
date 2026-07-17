package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import kotlin.math.min

/**
 * 平滑圆角（Squircle）Shape。
 *
 * 基于 [androidx.graphics:graphics-shapes] 官方库的 [RoundedPolygon] + [CornerRounding]，
 * 使用三曲线方案（中间圆弧 + 两条侧翼平滑曲线，连接处斜率匹配），
 * 相比单曲线方案过渡更平滑、无视觉断层。
 *
 * - `smoothing = 1f`：侧翼曲线最大，中间圆弧长度为零，最接近超椭圆 squircle
 * - `smoothing = 0f`：退化为纯圆弧（等同 [RoundedCornerShape]）
 *
 * API 与 [RoundedCornerShape] 完全兼容，可直接替换：
 * - [SquircleShape]`(Dp)` 统一半径
 * - [SquircleShape]`(topStart = X.dp, ...)` 四角不同
 * - [SquircleShape]`(percent: Int)` 百分比（50 = 完全圆角，胶囊形）
 *
 * 注意：真正需要正圆的场景（头像、圆点指示器）应继续使用 `CircleShape`，
 * 因为 squircle 在 50% 半径时仍带轻微弧度，不是几何正圆。
 *
 * 性能说明：[createOutline] 每次绘制都会构造 [Path]；与 [RoundedCornerShape] 一样
 * 依赖 Compose 上层（如 `Modifier.clip`）的缓存。若在长列表中频繁使用同一形状，
 * 建议将 [SquircleShape] 实例提升为顶层 `val` 复用，避免重复构造。
 *
 * @param topStart 左上角圆角大小
 * @param topEnd 右上角圆角大小
 * @param bottomEnd 右下角圆角大小
 * @param bottomStart 左下角圆角大小
 */
class SquircleShape(
    private val topStart: CornerSize,
    private val topEnd: CornerSize,
    private val bottomEnd: CornerSize,
    private val bottomStart: CornerSize,
) : Shape {

    /** 统一四角半径。 */
    constructor(all: CornerSize) : this(all, all, all, all)

    /** 统一四角半径（Dp）。 */
    constructor(size: Dp) : this(CornerSize(size))

    /**
     * 统一四角半径（百分比）。
     * @param percent 0..100，50 即为完全圆角（胶囊形）。
     */
    constructor(percent: Int) : this(CornerSize(percent))

    /**
     * 四角可分别指定半径（Dp），默认 0（直角）。
     * 与 [RoundedCornerShape] 同名参数一一对应，便于直接替换。
     */
    constructor(
        topStart: Dp = 0.dp,
        topEnd: Dp = 0.dp,
        bottomEnd: Dp = 0.dp,
        bottomStart: Dp = 0.dp,
    ) : this(
        topStart = CornerSize(topStart),
        topEnd = CornerSize(topEnd),
        bottomEnd = CornerSize(bottomEnd),
        bottomStart = CornerSize(bottomStart),
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))

        // CornerSize.toPx 只解析 dp / percent，RTL 下 start/end 语义互换需手动处理
        // （与 RoundedCornerShape 内部一致）
        val rawTs = topStart.toPx(size, density)
        val rawTe = topEnd.toPx(size, density)
        val rawBe = bottomEnd.toPx(size, density)
        val rawBs = bottomStart.toPx(size, density)
        val (ts, te, be, bs) = when (layoutDirection) {
            LayoutDirection.Ltr -> floatArrayOf(rawTs, rawTe, rawBe, rawBs)
            LayoutDirection.Rtl -> floatArrayOf(rawTe, rawTs, rawBs, rawBe)
        }

        if (ts <= 0f && te <= 0f && be <= 0f && bs <= 0f) {
            return Outline.Rectangle(Rect(0f, 0f, w, h))
        }

        // 钳制半径不超过短边的一半
        val halfMin = min(w, h) * 0.5f
        val tsA = ts.coerceIn(0f, halfMin)
        val teA = te.coerceIn(0f, halfMin)
        val beA = be.coerceIn(0f, halfMin)
        val bsA = bs.coerceIn(0f, halfMin)

        // smoothing=0.64f：中间圆弧为主 + 适度侧翼平滑，过渡自然、内凹感弱
        // RoundedPolygon.rectangle 的 perVertexRounding 顶点顺序为 BR, BL, TL, TR
        // （由 rectangle 源码中 floatArrayOf(right, bottom, left, bottom, left, top, right, top) 决定）
        val perVertexRounding = listOf(
            CornerRounding(radius = beA, smoothing = 0.64f),  // BR
            CornerRounding(radius = bsA, smoothing = 0.64f),  // BL
            CornerRounding(radius = tsA, smoothing = 0.64f),  // TL
            CornerRounding(radius = teA, smoothing = 0.64f),  // TR
        )

        val polygon = RoundedPolygon.rectangle(
            width = w,
            height = h,
            perVertexRounding = perVertexRounding,
            centerX = w / 2f,
            centerY = h / 2f
        )

        // toPath 扩展函数接受 android.graphics.Path，再用 asComposePath 转 Compose Path
        val androidPath = android.graphics.Path()
        polygon.toPath(androidPath)
        return Outline.Generic(androidPath.asComposePath())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return result
    }

    override fun toString(): String =
        "SquircleShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart)"
}
