package com.aiexile.animetrack.ui.components

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 大屏适配基础设施。
 *
 * WindowSizeClass 由 MainActivity 通过 calculateWindowSizeClass() 计算并写入，
 * 全局下发供各界面读取宽度档位（Compact / Medium / Expanded）。
 * null 表示未提供（如 Preview），调用方应回退到手机（Compact）布局。
 */
val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass?> { null }

/** 当前窗口是否为 Compact 宽度（< 600dp，手机竖屏）。null 回退按 Compact 处理。 */
@Composable
fun isCompactWidth(): Boolean {
    val windowSizeClass = LocalWindowSizeClass.current
    return windowSizeClass?.widthSizeClass == null ||
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
}

/** 当前窗口是否为 Medium 宽度（600-840dp，折叠屏展开 / 小平板）。 */
@Composable
fun isMediumWidth(): Boolean {
    return LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Medium
}

/** 当前窗口是否为 Expanded 宽度（>= 840dp，平板 / 大屏）。 */
@Composable
fun isExpandedWidth(): Boolean {
    return LocalWindowSizeClass.current?.widthSizeClass == WindowWidthSizeClass.Expanded
}

/**
 * 大屏适配：根据实际可用宽度动态计算网格列数。
 *
 * 列数 = 可用宽度 / (卡片最小宽度 + 间距)，并按屏幕方向限制上限，
 * 保证手机端列数下限、平板端不至于过密。
 * 可用宽度由调用方通过 BoxWithConstraints 实测传入（而非窗口宽度），
 * 这样大屏 pane 打开压缩主界面后列数会自适应减少。
 *
 * @param availableWidth 网格实际可用宽度（BoxWithConstraints.maxWidth）
 * @param cardMinWidth 单张卡片期望的最小宽度
 * @param spacing 卡片横向间距
 * @param horizontalPadding 网格左右两侧 padding 总和
 * @param minColumns 最小列数（保证手机端不会过疏）
 * @param maxColumnsPortrait 竖屏最大列数
 * @param maxColumnsLandscape 横屏最大列数
 */
@Composable
fun rememberAdaptiveGridColumns(
    availableWidth: Dp,
    cardMinWidth: Dp,
    spacing: Dp,
    horizontalPadding: Dp,
    minColumns: Int,
    maxColumnsPortrait: Int,
    maxColumnsLandscape: Int
): Int {
    val configuration = LocalConfiguration.current
    val effectiveWidth = availableWidth - horizontalPadding
    val calculatedColumns = ((effectiveWidth + spacing) / (cardMinWidth + spacing)).toInt()
    // 按屏幕方向限制列数上限（竖屏最多 maxColumnsPortrait 列，横屏最多 maxColumnsLandscape 列）
    val maxColumns = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        maxColumnsLandscape
    } else {
        maxColumnsPortrait
    }
    return calculatedColumns.coerceIn(minColumns, maxColumns)
}
