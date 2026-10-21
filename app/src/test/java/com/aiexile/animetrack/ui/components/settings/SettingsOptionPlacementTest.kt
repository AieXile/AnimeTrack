package com.aiexile.animetrack.ui.components.settings

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 选项弹层定位算法：`placeOptionMenu` 的边界行为。
 * 坐标口径见 SettingsOptionPlacement.kt 的 KDoc。
 */
class SettingsOptionPlacementTest {

    private val hostOrigin = IntOffset.Zero
    private val container = IntSize(width = 1080, height = 2000)
    private val safeTop = 200
    private val safeBottom = 1900
    private val margin = 40
    private val overlap = 24
    private val gap = 24
    private val menuSize = IntSize(width = 600, height = 400)

    private fun place(
        anchor: IntRect,
        menu: IntSize = menuSize,
        rtl: Boolean = false,
    ): IntOffset = placeOptionMenu(
        anchorInWindow = anchor,
        hostOrigin = hostOrigin,
        containerSize = container,
        menuSize = menu,
        safeTop = safeTop,
        safeBottom = safeBottom,
        rtl = rtl,
        margin = margin,
        overlap = overlap,
        gap = gap
    )

    @Test
    fun `下方空间充足时盖住锚点并从右端对齐`() {
        val anchor = IntRect(left = 300, top = 800, right = 600, bottom = 900)
        // preferredX = 600 - 600 = 0 → 夹到左边距 40；preferredY = 800 - 24
        assertEquals(IntOffset(40, 776), place(anchor))
    }

    @Test
    fun `锚点靠近底部时翻到锚点上方`() {
        val anchor = IntRect(left = 300, top = 1700, right = 600, bottom = 1800)
        // 下方放不下（1676 + 400 > 1900），上方 1700 - 24 - 400 仍 ≥ safeTop
        assertEquals(IntOffset(40, 1276), place(anchor))
    }

    @Test
    fun `菜单比安全区还高时夹到安全区内居中`() {
        val anchor = IntRect(left = 300, top = 800, right = 600, bottom = 900)
        val tallMenu = IntSize(width = 600, height = 1400)
        // 上下都放不下 → safeTop + (1700 - 1400) / 2 = 350，再夹到 maxY = 500 以内
        assertEquals(IntOffset(40, 350), place(anchor, menu = tallMenu))
    }

    @Test
    fun `锚点贴近右边缘时水平夹取而不是出界`() {
        val anchor = IntRect(left = 900, top = 800, right = 1080, bottom = 900)
        // preferredX = 1080 - 600 = 480 → 夹到 1080 - 40 - 600 = 440
        assertEquals(IntOffset(440, 776), place(anchor))
    }

    @Test
    fun `RTL 下镜像为左端对齐锚点`() {
        val anchor = IntRect(left = 100, top = 800, right = 400, bottom = 900)
        assertEquals(IntOffset(100, 776), place(anchor, rtl = true))
    }
}
