package com.aiexile.animetrack.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * 主界面卡片封面圆角风格：true 时封面四角均为 16dp 圆角（底部跟随卡片轮廓），
 * false 时保持历史样式（仅顶部两角圆角、底部直角）。
 *
 * 由 MainActivity 根组合下发，设置变更后所有卡片与共享元素转场一起刷新。
 * 用 compositionLocalOf 而非 staticCompositionLocalOf：读取者只有卡片/详情封面，
 * 改设置时无需让整棵 App 树失效。
 */
val LocalCardCoverFullyRounded = compositionLocalOf { false }
