package com.aiexile.animetrack.model

/**
 * 深色风格：参考系统深色模式的分级档位。
 * 增强 = 纯黑背景（OLED），标准 = 适中深灰，柔和 = 偏灰的柔和底色，
 * 高对比 = 纯黑背景 + 更亮的组件层级（参考 Windows 高对比暗色）。
 */
enum class DarkStyle(val displayName: String) {
    BOOST("增强"),
    STANDARD("标准"),
    SOFT("柔和"),
    CONTRAST("高对比")
}
