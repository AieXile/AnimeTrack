package com.aiexile.animetrack.ui.icons

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 应用图标包：同一 AppIcon 在不同包下对应不同视觉风格的 drawable。
 *
 * - MATERIAL_SYMBOLS：填充式 Material Symbols Rounded（sym_*），默认包
 * - LUCIDE：描边式 Lucide（lucide_*）
 *
 * Lucide 无实心变体，导航选中态以 primary 颜色区分。
 */
enum class IconPack {
    MATERIAL_SYMBOLS,
    LUCIDE;

    /** 解析图标在当前包下对应的 drawable 资源 ID */
    fun resolve(icon: AppIcon): Int = when (this) {
        MATERIAL_SYMBOLS -> icon.materialRes
        LUCIDE -> icon.lucideRes
    }
}

/**
 * 当前生效的图标包：由 MainActivity 根组合提供。
 * 默认 Material Symbols，保证未显式提供时（如 @Preview）渲染与默认主题一致。
 * pack 切换是全局事件，staticCompositionLocalOf 整树失效更高效。
 */
val LocalIconPack = staticCompositionLocalOf { IconPack.MATERIAL_SYMBOLS }
