package com.aiexile.animetrack.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.aiexile.animetrack.data.CardInfoField

/** 主界面卡片信息栏左右两个槽位各自显示的字段。 */
@Immutable
data class CardInfoFields(
    val left: CardInfoField = CardInfoField.PROGRESS,
    val right: CardInfoField = CardInfoField.RATING
)

/**
 * 卡片信息栏配置：由 MainActivity 根组合下发，设置变更后所有卡片一起刷新。
 *
 * 默认值与历史表现一致（左进度 + 右评分），@Preview / 单测未显式提供时同样成立。
 * 用 compositionLocalOf 而非 staticCompositionLocalOf：读取者只有卡片本身，改设置时无需让整棵 App 树失效。
 */
val LocalCardInfoFields = compositionLocalOf { CardInfoFields() }
