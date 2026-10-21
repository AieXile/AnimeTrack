package com.aiexile.animetrack.ui.components.settings

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * 选项弹层的纯定位算法（无 Compose 依赖，可单元测试）。
 *
 * 坐标契约（规范 §5.1）：
 * - [anchorInWindow] 与 [hostOrigin] 都是**窗口像素**坐标；
 * - [menuSize] / [containerSize] / 入参 safe 边界是 **Host 局部像素**；
 * - 返回值为菜单左上角在 Host 局部坐标系中的偏移。
 *
 * 首选「盖住锚点、右端对齐、向下展开」（FlClash 的 overAnchorEnd）；下方放不下时
 * 依次回退到锚点上方、锚点下方，最后压到安全区内居中，再由调用方限制高度 + 内部滚动。
 */
internal fun placeOptionMenu(
    anchorInWindow: IntRect,
    hostOrigin: IntOffset,
    containerSize: IntSize,
    menuSize: IntSize,
    safeTop: Int,
    safeBottom: Int,
    rtl: Boolean,
    margin: Int,
    overlap: Int,
    gap: Int,
): IntOffset {
    val anchor = IntRect(
        left = anchorInWindow.left - hostOrigin.x,
        top = anchorInWindow.top - hostOrigin.y,
        right = anchorInWindow.right - hostOrigin.x,
        bottom = anchorInWindow.bottom - hostOrigin.y
    )

    // 水平：LTR 右端对齐锚点，RTL 镜像为左端对齐
    val preferredX = if (rtl) anchor.left else anchor.right - menuSize.width
    val maxX = (containerSize.width - margin - menuSize.width).coerceAtLeast(margin)
    val x = preferredX.coerceIn(margin, maxX)

    // 垂直：优先盖住锚点向下展开
    val preferredY = anchor.top - overlap
    val y = when {
        preferredY + menuSize.height <= safeBottom -> preferredY
        anchor.top - gap - menuSize.height >= safeTop -> anchor.top - gap - menuSize.height
        anchor.bottom + gap + menuSize.height <= safeBottom -> anchor.bottom + gap
        else -> safeTop + (safeBottom - safeTop - menuSize.height) / 2
    }
    val maxY = (safeBottom - menuSize.height).coerceAtLeast(safeTop)

    return IntOffset(x, y.coerceIn(safeTop, maxY))
}
