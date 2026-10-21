package com.aiexile.animetrack.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 旧「高级模糊 / 液态玻璃」双布尔 → ChromeStyle 的一次性迁移映射 */
class ChromeStyleMigrationTest {

    @Test
    fun `液态玻璃优先（防御旧的异常双开数据）`() {
        assertEquals(
            ChromeStyle.LIQUID_GLASS,
            chromeStyleFromLegacy(liquidGlass = true, advancedBlur = true)
        )
        assertEquals(
            ChromeStyle.LIQUID_GLASS,
            chromeStyleFromLegacy(liquidGlass = true, advancedBlur = false)
        )
    }

    @Test
    fun `仅高级模糊时迁移为 HAZE`() {
        assertEquals(
            ChromeStyle.HAZE,
            chromeStyleFromLegacy(liquidGlass = false, advancedBlur = true)
        )
    }

    @Test
    fun `两个旧开关都关闭时迁移为 STANDARD`() {
        assertEquals(
            ChromeStyle.STANDARD,
            chromeStyleFromLegacy(liquidGlass = false, advancedBlur = false)
        )
    }
}
