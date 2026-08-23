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
