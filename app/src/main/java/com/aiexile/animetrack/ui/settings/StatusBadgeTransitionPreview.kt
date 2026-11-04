package com.aiexile.animetrack.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.StatusBadgeTransitionMode
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import kotlin.math.max
import kotlin.math.min

/**
 * 状态角标转场示意动画（纯 Compose Canvas 绘制，无外链图片/动图）。
 *
 * 循环总时长 2.4s，时间轴（占循环比例）：
 * 0.00–0.03 淡入 → 0.03–0.13 起飞前停留 → 0.13–0.53 飞行
 * → 0.53–0.82 落位停留 → 0.82–0.92 淡出 → 0.92–1.00 空白。
 * 进度只在 draw 阶段读取，逐帧只触发重绘，不引起重组。
 *
 * - FOLLOW_COVER：封面带着右上角角标起飞，角标在 p=0..0.12 内快速淡出，
 *   飞行途中封面是"裸"的；右侧标题列的徽章在接近落位（p=0.85..1.0）才淡入。
 * - FLY_TO_TITLE：封面单独飞向详情封面，角标沿弧线单独飞向右侧标题列的徽章位置。
 */

/** 循环淡入结束。 */
private const val PreviewFadeInEnd = 0.03f

/** 飞行开始（起飞前停留的结束）。 */
private const val PreviewFlightStart = 0.13f

/** 飞行段占整个循环的比例。 */
private const val PreviewFlightDuration = 0.40f

/** 循环淡出开始（落位停留的结束）。 */
private const val PreviewFadeOutStart = 0.82f

/** 循环淡出结束。 */
private const val PreviewFadeOutEnd = 0.92f

/** 飞行比例节点：角标在此前快速淡出 / 此后标题徽章才淡入。 */
private const val StatusBadgeEarlyFadeEnd = 0.12f
private const val TitleBadgeFadeInStart = 0.85f

@Composable
internal fun StatusBadgeTransitionPreview(
    mode: StatusBadgeTransitionMode,
    progress: State<Float>,
    modifier: Modifier = Modifier
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val animeColors = LocalAnimeColors.current
    val badgeContainer = animeColors.statusBadgeContainer
    val badgeContent = animeColors.statusBadgeContent

    Canvas(modifier = modifier) {
        val t = progress.value
        val cycleAlpha = when {
            t < PreviewFadeInEnd -> t / PreviewFadeInEnd
            t < PreviewFadeOutStart -> 1f
            t < PreviewFadeOutEnd ->
                (PreviewFadeOutEnd - t) / (PreviewFadeOutEnd - PreviewFadeOutStart)
            else -> 0f
        }
        if (cycleAlpha <= 0f) return@Canvas

        val p = FastOutSlowInEasing.transform(
            ((t - PreviewFlightStart) / PreviewFlightDuration).coerceIn(0f, 1f)
        )
        drawTransitionFrame(
            mode = mode,
            p = p,
            cycleAlpha = cycleAlpha,
            surfaceVariant = surfaceVariant,
            surfaceHigh = surfaceHigh,
            outline = outline,
            onSurface = onSurface,
            badgeContainer = badgeContainer,
            badgeContent = badgeContent
        )
    }
}

private fun DrawScope.drawTransitionFrame(
    mode: StatusBadgeTransitionMode,
    p: Float,
    cycleAlpha: Float,
    surfaceVariant: Color,
    surfaceHigh: Color,
    outline: Color,
    onSurface: Color,
    badgeContainer: Color,
    badgeContent: Color
) {
    val pad = 6.dp.toPx()
    val w = size.width
    val h = size.height

    // ---- 起始：左侧《番剧卡片》封面（2:3），右上角带状态角标 ----
    val startH = h * 0.62f
    val startW = startH * 2f / 3f
    val startTopLeft = Offset(pad, (h - startH) / 2f)

    // ---- 结束：右侧《详情页》信息头 ----
    // 真实布局（AnimeDetailScreen 信息头 Row）：封面 130dp + 间距 12dp + 标题列余量。
    // 按 360dp 屏、页面左右各 16dp 边距计算内容宽 328dp（130 + 12 + 186），
    // 这里等比缩放，保证封面 / 间距 / 标题列的横向比例与真实一致。
    val detailLeft = w * 0.50f
    val groupW = w - pad - detailLeft
    val detailCoverW = groupW * (130f / 328f)
    val detailGapW = groupW * (12f / 328f)
    val detailCoverH = detailCoverW * 3f / 2f
    val detailCoverTop = h * 0.12f
    val detailCoverBottom = detailCoverTop + detailCoverH
    val titleLeft = detailLeft + detailCoverW + detailGapW
    val titleW = w - pad - titleLeft

    // 标题列内部：标题占位在上，元信息居中，状态角标贴标题列底部
    // （真实布局标题列高度与封面一致，角标行是 SpaceEvenly 的最后一行）
    val titleBadgeH = 10.dp.toPx()
    val titleBadgeW = min(titleW, 26.dp.toPx())
    val titleBadgeRect = Rect(
        offset = Offset(titleLeft, detailCoverBottom - titleBadgeH),
        size = Size(titleBadgeW, titleBadgeH)
    )

    val detailReveal = cycleAlpha * smoothstep(0f, 0.8f, p)
    if (detailReveal > 0f) {
        // 标题两行
        drawRoundRect(
            color = onSurface.copy(alpha = 0.22f * detailReveal),
            topLeft = Offset(titleLeft, detailCoverTop + 1.dp.toPx()),
            size = Size(titleW * 0.95f, 2.5.dp.toPx()),
            cornerRadius = CornerRadius(1.25.dp.toPx())
        )
        drawRoundRect(
            color = onSurface.copy(alpha = 0.18f * detailReveal),
            topLeft = Offset(titleLeft, detailCoverTop + 7.5.dp.toPx()),
            size = Size(titleW * 0.72f, 2.5.dp.toPx()),
            cornerRadius = CornerRadius(1.25.dp.toPx())
        )
        // 放送日期 / 总集数等元信息
        drawRoundRect(
            color = onSurface.copy(alpha = 0.14f * detailReveal),
            topLeft = Offset(titleLeft, detailCoverTop + detailCoverH * 0.46f),
            size = Size(titleW * 0.55f, 2.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
        drawRoundRect(
            color = onSurface.copy(alpha = 0.12f * detailReveal),
            topLeft = Offset(titleLeft, detailCoverTop + detailCoverH * 0.62f),
            size = Size(titleW * 0.42f, 2.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx())
        )
    }

    // 卡片下方标题占位条：随飞行淡出
    val cardBarAlpha = cycleAlpha * (1f - p * 1.8f).coerceIn(0f, 1f)
    if (cardBarAlpha > 0f) {
        drawRoundRect(
            color = onSurface.copy(alpha = 0.18f * cardBarAlpha),
            topLeft = Offset(startTopLeft.x, startTopLeft.y + startH + 4.dp.toPx()),
            size = Size(startW, 2.5.dp.toPx()),
            cornerRadius = CornerRadius(1.25.dp.toPx())
        )
        drawRoundRect(
            color = onSurface.copy(alpha = 0.12f * cardBarAlpha),
            topLeft = Offset(startTopLeft.x, startTopLeft.y + startH + 8.5.dp.toPx()),
            size = Size(startW * 0.6f, 2.5.dp.toPx()),
            cornerRadius = CornerRadius(1.25.dp.toPx())
        )
    }

    val coverTopLeft = Offset(
        x = lerp(startTopLeft.x, detailLeft, p),
        y = lerp(startTopLeft.y, detailCoverTop, p)
    )
    val coverW = lerp(startW, detailCoverW, p)
    val coverH = lerp(startH, detailCoverH, p)
    val coverCorner = 5.dp.toPx()

    // 封面本体（两套策略同一段位移）
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                surfaceVariant.copy(alpha = cycleAlpha),
                surfaceHigh.copy(alpha = cycleAlpha)
            )
        ),
        topLeft = coverTopLeft,
        size = Size(coverW, coverH),
        cornerRadius = CornerRadius(coverCorner)
    )
    drawRoundRect(
        color = outline.copy(alpha = 0.5f * cycleAlpha),
        topLeft = coverTopLeft,
        size = Size(coverW, coverH),
        cornerRadius = CornerRadius(coverCorner),
        style = Stroke(width = 1.dp.toPx())
    )

    when (mode) {
        StatusBadgeTransitionMode.FOLLOW_COVER -> {
            // 角标紧贴飞行中的封面右上角，随封面一起位移缩放
            val badgeW = lerp(15.dp.toPx(), 17.dp.toPx(), p)
            val badgeH = lerp(8.dp.toPx(), 9.dp.toPx(), p)
            val badgeRect = Rect(
                offset = Offset(
                    x = coverTopLeft.x + coverW - 3.dp.toPx() - badgeW,
                    y = coverTopLeft.y + 3.dp.toPx()
                ),
                size = Size(badgeW, badgeH)
            )
            // 角标在起飞最开始就快速淡出（p=0..0.12），飞行途中封面是"裸"的；
            // 用连续的 smoothstep 收尾，不存在先归零再跳回来的闪烁
            val badgeAlpha = cycleAlpha * (1f - smoothstep(0f, StatusBadgeEarlyFadeEnd, p))
            if (badgeAlpha > 0f) {
                drawBadge(
                    rect = badgeRect,
                    alpha = badgeAlpha,
                    container = badgeContainer,
                    content = badgeContent
                )
            }
            // 右侧标题列自带的徽章：接近落位（p=0.85..1.0）才淡入，落位后保持
            val titleBadgeAlpha = cycleAlpha * smoothstep(TitleBadgeFadeInStart, 1f, p)
            if (titleBadgeAlpha > 0f) {
                drawBadge(
                    rect = titleBadgeRect,
                    alpha = titleBadgeAlpha,
                    container = badgeContainer,
                    content = badgeContent
                )
            }
        }

        StatusBadgeTransitionMode.FLY_TO_TITLE -> {
            // 落点不画任何占位/空心框：飞行途中右侧标题列只有标题占位条，
            // 直到角标本身飞抵该位置（p=1）才由它自己占据落点。
            // 角标从卡片右上角单独沿弧线飞向右侧标题列，尺寸从小号过渡到标题徽章
            val startBadgeCenter = Offset(
                x = startTopLeft.x + startW - 3.dp.toPx() - 7.5.dp.toPx(),
                y = startTopLeft.y + 3.dp.toPx() + 4.dp.toPx()
            )
            val endBadgeCenter = titleBadgeRect.center
            val control = Offset(
                x = (startBadgeCenter.x + endBadgeCenter.x) / 2,
                y = min(startBadgeCenter.y, endBadgeCenter.y) - h * 0.18f
            )
            val badgeCenter = quadraticBezier(startBadgeCenter, control, endBadgeCenter, p)
            val badgeW = lerp(15.dp.toPx(), titleBadgeRect.width, p)
            val badgeH = lerp(8.dp.toPx(), titleBadgeRect.height, p)
            drawBadge(
                rect = Rect(
                    offset = Offset(badgeCenter.x - badgeW / 2, badgeCenter.y - badgeH / 2),
                    size = Size(badgeW, badgeH)
                ),
                alpha = cycleAlpha,
                container = badgeContainer,
                content = badgeContent
            )
        }
    }
}

/** 状态角标示意：圆角胶囊 + 内部短横线，避免逐帧测量文字。 */
private fun DrawScope.drawBadge(
    rect: Rect,
    alpha: Float,
    container: Color,
    content: Color
) {
    drawRoundRect(
        color = container.copy(alpha = container.alpha * alpha),
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = CornerRadius(rect.height / 2)
    )
    val lineHeight = max(1.dp.toPx(), rect.height * 0.16f)
    drawRoundRect(
        color = content.copy(alpha = content.alpha * alpha),
        topLeft = Offset(
            x = rect.left + rect.width * 0.24f,
            y = rect.center.y - lineHeight / 2
        ),
        size = Size(rect.width * 0.52f, lineHeight),
        cornerRadius = CornerRadius(lineHeight / 2)
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val x = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun quadraticBezier(start: Offset, control: Offset, end: Offset, t: Float): Offset {
    val inv = 1f - t
    return Offset(
        x = inv * inv * start.x + 2f * inv * t * control.x + t * t * end.x,
        y = inv * inv * start.y + 2f * inv * t * control.y + t * t * end.y
    )
}
