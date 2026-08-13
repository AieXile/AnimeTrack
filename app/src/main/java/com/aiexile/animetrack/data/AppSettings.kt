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
enum class NavigationLabelMode(@StringRes val labelRes: Int) {
    ICON_ONLY(R.string.nav_custom_label_icon_only),
    ICON_AND_TEXT(R.string.nav_custom_label_icon_text),
    TEXT_ONLY(R.string.nav_custom_label_text_only),
}
