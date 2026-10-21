package com.aiexile.animetrack.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.aiexile.animetrack.data.ChromeStyle

/**
 * 当前生效的界面外壳（chrome）材质档位：由 MainActivity 根组合下发。
 *
 * - 页面/组件只读它决定材质与结构，不再各自 collect 两个互斥布尔；
 * - 已包含低版本能力降级（不支持实时模糊时恒为 [ChromeStyle.STANDARD]）；
 * - 默认 STANDARD，保证 @Preview / 单测未显式提供时渲染与默认主题一致。
 *
 * 档位切换是全局事件（整树换观感），用 staticCompositionLocalOf 整树失效更高效。
 */
val LocalChromeStyle = staticCompositionLocalOf { ChromeStyle.STANDARD }
