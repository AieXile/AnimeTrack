package com.aiexile.animetrack.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 主题预设：中性底座 + 彩色点缀（duotone 模型，参考 Apple HIG）。
 *
 * 彩色主题的背景/表面为近纯中性（不带 seed 色调），主题色只作用于
 * primary/secondary/tertiary 等强调角色，与「黑白简洁」观感一致。
 * seed 色为柔和中间调（chroma 38~56），避免高饱和荧光感，保证优雅观感。
 *
 * [MONET] 为特例：seed 由系统壁纸动态配色（Monet）提供，因此 [seedColor] 为 null，
 * 取色失败或系统不支持时由 [AnimeTrackTheme] 回退「黑白简洁」。
 */
enum class ThemePreset(val displayName: String, val seedColor: Color?) {
    BLUE("晨雾蓝", Color(0xFF4D7FE8)),
    CYAN("湖水青", Color(0xFF2C9BB5)),
    GREEN("苔原绿", Color(0xFF45A374)),
    ORANGE("落日橘", Color(0xFFE07E45)),
    PURPLE("雾霭紫", Color(0xFF8066C9)),
    PINK("蔷薇粉", Color(0xFFD96A85)),
    MONO_BLACK("黑白简洁", Color(0xFF000000)),

    /** 莫奈取色：色相来自系统壁纸，需 Android 12+（动态配色） */
    MONET("莫奈取色", null),
}

/** 当前设备可用的主题预设：莫奈取色依赖 Android 12+ 的系统动态配色，低版本直接不展示。 */
val availableThemePresets: List<ThemePreset>
    get() = ThemePreset.entries.filter { it.seedColor != null || isMonetColorSupported() }

data class AnimeColors(
    val starFilled: Color,
    val finished: Color,
    val finishedContainer: Color,
    val dropped: Color,
    val droppedContainer: Color,
    val watching: Color,
    val watchingContainer: Color,
    val chipSelected: Color,
    val chipSelectedContainer: Color,
    val chipSelectedContent: Color,
    val chipUnselected: Color,
    val chipUnselectedContainer: Color,
    val chipUnselectedContent: Color,
    /** 封面状态角标底色：黑白简洁暗色下为半透明黑 scrim，其余主题跟随 primary */
    val statusBadgeContainer: Color,
    /** 封面状态角标文字色：与 statusBadgeContainer 配对 */
    val statusBadgeContent: Color,
)

val LocalAnimeColors = staticCompositionLocalOf {
    AnimeColors(
        starFilled = Color.Unspecified,
        finished = Color.Unspecified,
        finishedContainer = Color.Unspecified,
        dropped = Color.Unspecified,
        droppedContainer = Color.Unspecified,
        watching = Color.Unspecified,
        watchingContainer = Color.Unspecified,
        chipSelected = Color.Unspecified,
        chipSelectedContainer = Color.Unspecified,
        chipSelectedContent = Color.Unspecified,
        chipUnselected = Color.Unspecified,
        chipUnselectedContainer = Color.Unspecified,
        chipUnselectedContent = Color.Unspecified,
        statusBadgeContainer = Color.Unspecified,
        statusBadgeContent = Color.Unspecified,
    )
}
