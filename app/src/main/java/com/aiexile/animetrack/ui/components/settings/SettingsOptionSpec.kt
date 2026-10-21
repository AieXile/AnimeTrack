package com.aiexile.animetrack.ui.components.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.components.SquircleShape

/**
 * 设置项选项弹层的视觉令牌（唯一出处）。
 *
 * 数值口径来自 `.trae/documents/ui-settings-option-menu-spec.md` §4.2；
 * 形状实例提升为顶层 val，复用 [SquircleShape] 内部的 size 级缓存，
 * 避免在 Composable 函数体里反复构造。
 */
internal object SettingsOptionSpec {

    // ---- 卡片 ----
    val MenuCorner = 24.dp
    val MenuInset = 8.dp
    val MenuMinWidth = 160.dp
    val MenuMaxWidth = 280.dp
    val ShadowElevation = 16.dp

    /**
     * 副文案参与菜单宽度的份额。
     *
     * 菜单宽度主要以选项文案为准；副文案若按整行宽度参与，长描述会把只有两个短标签的菜单
     * 撑到 280dp（如代理协议）。这里按「约两行可容纳」折算（0.55 ≈ 1/1.8），
     * 让描述自然换行到两行，菜单保持紧凑。
     */
    const val SupportingWidthShare = 0.55f

    /** 玻璃档菜单阴影：比实色更轻，且由框架图层绘制，随入场动画一起渐显 */
    val GlassShadowElevation = 10.dp

    // ---- Morph 打开动画（外框从触发行边界展开到菜单边界）----
    /** 菜单内容开始淡入的进度：外框先展开，内容随后出现，掩盖拉伸形变 */
    const val ContentRevealStart = 0.30f

    /** 菜单内容完全显示的进度 */
    const val ContentRevealEnd = 0.85f

    /** 最多露出 7 行，超出后菜单内部滚动 */
    const val MaxVisibleItems = 7

    // ---- 菜单行 ----
    val ItemCorner = 16.dp
    val MenuItemMinHeight = 48.dp
    val MenuItemHPadding = 12.dp
    val MenuItemVPadding = 12.dp
    val MenuItemCheck = 16.dp
    val MenuItemGap = 8.dp
    val ItemSpacing = 2.dp
    // ---- 定位 ----
    val AnchorOverlap = 8.dp
    val ScreenMargin = 16.dp
    val MenuGap = 8.dp

    // ---- 设置行 ----
    val RowMinHeight = 48.dp
    val LeadingIcon = 22.dp
    val LeadingGap = 16.dp
    val ValueMaxWidth = 160.dp
    val ValueChevron = 18.dp
    val ValueGap = 4.dp

    /** 菜单高度上限：7 行 + 行间距 + 上下内衬（实际行更高时按测量结果内部滚动） */
    val MenuMaxHeight = MenuItemMinHeight * MaxVisibleItems +
        ItemSpacing * (MaxVisibleItems - 1) +
        MenuInset * 2
}

/** 菜单外卡片形状（与设置分组的 16dp 形成「卡片里嵌行」的双层圆角） */
internal val MenuCardShape = SquircleShape(SettingsOptionSpec.MenuCorner)

/** 菜单行按压 / 焦点底色形状 */
internal val MenuItemShape = SquircleShape(SettingsOptionSpec.ItemCorner)

/** 菜单选项文案：与设置行标题同字号 */
@Composable
internal fun optionLabelStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Medium
)

/** 菜单选项副文案 */
@Composable
internal fun optionSupportingStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontSize = 12.sp,
    lineHeight = 16.sp
)

/** 设置行右侧当前值 */
@Composable
internal fun optionValueStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontSize = 13.sp,
    lineHeight = 18.sp
)

/** 设置行标题 */
@Composable
internal fun optionRowTitleStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    fontSize = 15.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Medium
)

/** 设置行副标题 */
@Composable
internal fun optionRowSubtitleStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontSize = 12.sp,
    lineHeight = 16.sp
)
