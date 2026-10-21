package com.aiexile.animetrack.data

import androidx.annotation.StringRes
import com.aiexile.animetrack.R

enum class NavigationStyle(val displayName: String) {
    BOTTOM("传统沉底"),
    CAPSULE("悬浮胶囊"),
}

enum class FabLocation(val displayName: String) {
    BOTTOM_RIGHT("右下角悬浮"),
    TOP_BAR("顶部标题栏"),
    NAV_BAR("导航栏旁"),
}

/** 导航栏项目的图标与文字显示模式 */
enum class NavigationLabelMode(@param:StringRes val labelRes: Int) {
    ICON_ONLY(R.string.nav_custom_label_icon_only),
    ICON_AND_TEXT(R.string.nav_custom_label_icon_text),
    TEXT_ONLY(R.string.nav_custom_label_text_only),
}

/** 「下滑隐藏顶栏」开启时，顶栏收起后的状态栏处理方式 */
enum class StatusBarMode(@param:StringRes val labelRes: Int) {
    /** 全屏：不处理，列表内容滚入透明状态栏区域 */
    FULLSCREEN(R.string.statusbar_mode_fullscreen),

    /** 留白遮罩：表面色纵向渐变盖在状态栏区域内容之上 */
    SCRIM(R.string.statusbar_mode_scrim),

    /** 实心状态栏：实色状态栏条，列表顶部预留随收拢收紧，内容被顶到其下方 */
    SOLID(R.string.statusbar_mode_solid),
}

/**
 * 界面外壳（chrome）材质档位：整套界面（悬浮导航、主页顶栏/FAB、二级页顶栏）共用一档。
 *
 * - [STANDARD]：实色 / M3 TopAppBar，旧「两个开关都关」的行为
 * - [HAZE]：haze 毛玻璃（沿用 [com.aiexile.animetrack.ui.components.AdvancedBlurConfig]）
 * - [LIQUID_GLASS]：backdrop 折射玻璃（沿用 [com.aiexile.animetrack.ui.components.LiquidGlassSpec]）
 *
 * 低版本设备（不支持 RenderEffect）由 MainActivity 统一降级为 STANDARD 下发。
 */
enum class ChromeStyle(@param:StringRes val labelRes: Int) {
    STANDARD(R.string.appearance_glass_effect_standard),
    HAZE(R.string.nav_custom_advanced_blur),
    LIQUID_GLASS(R.string.nav_custom_liquid_glass),
}

/**
 * 旧数据迁移映射：液态玻璃优先于高级模糊（旧 UI 保证互斥，此处只做防御）。
 * 抽成纯函数便于单元测试。
 */
internal fun chromeStyleFromLegacy(liquidGlass: Boolean, advancedBlur: Boolean): ChromeStyle = when {
    liquidGlass -> ChromeStyle.LIQUID_GLASS
    advancedBlur -> ChromeStyle.HAZE
    else -> ChromeStyle.STANDARD
}

/** 欢迎语入场效果：关闭 / 打字机逐字 / 乱码逐渐解码 */
enum class GreetingEffectStyle(@param:StringRes val labelRes: Int) {
    NONE(R.string.greeting_effect_none),
    TYPING(R.string.greeting_effect_typing),
    SCRAMBLE(R.string.greeting_effect_scramble);

    companion object {
        fun fromName(name: String?): GreetingEffectStyle =
            entries.firstOrNull { it.name == name } ?: TYPING
    }
}
