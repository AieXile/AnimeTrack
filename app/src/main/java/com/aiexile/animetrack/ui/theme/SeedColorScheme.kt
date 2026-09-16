@file:Suppress(
    "RestrictedApi", // Cam16/TonalPalette 为 Material Color Utilities 的内部 API，
                     // 用于从 seed 色生成自定义 Material 3 配色；官方无完全等价的公开 API，
                     // 为保证颜色与既有 UI 完全一致，此处有意直接使用内部类并抑制该 lint。
)

package com.aiexile.animetrack.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.aiexile.animetrack.model.DarkStyle
import com.google.android.material.color.utilities.Cam16
import com.google.android.material.color.utilities.TonalPalette

/** 各角色色板：a1/a2/a3 从 seed 派生彩色，n1/n2 为近纯中性（背景不带 seed 色调）。 */
private class SeedPalettes(
    val a1: TonalPalette,
    val a2: TonalPalette,
    val a3: TonalPalette,
    val n1: TonalPalette,
    val n2: TonalPalette,
    val error: TonalPalette,
)

/**
 * 深色分层 tone 表（按风格档位切换，各档独立校准）。
 *
 * 设计依据（参考主流系统/应用的暗色分层量化标准）：
 * - 暗色下阴影几乎不可见，层级靠表面明度阶梯传达，需 4~5 级；
 * - 灰底上人眼对明度差的分辨能力更弱，背景越灰（标准/柔和），
 *   相邻层级的明度差应比增强档更大，而非简单平移：
 *   组件-bg 差：增强 17 → 标准 19 → 柔和 21；级差：5 → 6 → 7；
 * - 高对比（参考 Windows）：纯黑背景 + 更亮组件（差 24），满足强对比需求；
 * - 文本对比：onSurface = tone 90 on bg（各档 ≥ 15:1，远超 WCAG AA 4.5:1），
 *   规避纯白文本在纯黑背景上的光晕效应。
 *
 * 组件间分层：Lowest 凹陷 < Low 次级卡片 < Container 主卡片 < High 输入区 < Highest 悬浮层。
 */
internal class DarkTones(
    val bg: Double,
    val lowest: Double,
    val low: Double,
    val container: Double,
    val high: Double,
    val highest: Double,
    val variant: Double,
    val bright: Double,
    val dim: Double,
)

internal fun darkTones(style: DarkStyle): DarkTones = when (style) {
    DarkStyle.BOOST -> DarkTones(
        bg = 0.0, lowest = 5.0, low = 12.0, container = 17.0,
        high = 22.0, highest = 27.0, variant = 29.0, bright = 30.0, dim = 0.0
    )
    DarkStyle.STANDARD -> DarkTones(
        bg = 6.0, lowest = 4.0, low = 19.0, container = 25.0,
        high = 31.0, highest = 36.0, variant = 35.0, bright = 37.0, dim = 4.0
    )
    DarkStyle.SOFT -> DarkTones(
        bg = 10.0, lowest = 8.0, low = 24.0, container = 31.0,
        high = 38.0, highest = 45.0, variant = 39.0, bright = 46.0, dim = 8.0
    )
    DarkStyle.CONTRAST -> DarkTones(
        bg = 0.0, lowest = 6.0, low = 16.0, container = 24.0,
        high = 31.0, highest = 38.0, variant = 29.0, bright = 39.0, dim = 0.0
    )
}

/** 纯灰阶（chroma 0），供黑白简洁主题共享同套深色分层。 */
private val NeutralGray = TonalPalette.fromHueAndChroma(0.0, 0.0)

internal fun neutralToneColor(tone: Double): Color = Color(NeutralGray.tone(tone.toInt()))

/**
 * 从 seed 色生成「中性底座 + 彩色点缀」的 Material 3 配色（duotone 模型）。
 *
 * 有别于 CorePalette.of 默认的中性色板（chroma 4/8，背景被 seed 色染色），
 * 此处 n1/n2 chroma 压至 1/3，背景/表面观感与「黑白简洁」一致；
 * 彩色只出现在 primary/secondary/tertiary 及对应 container 等强调角色。
 */
fun seedColorScheme(seedColor: Color, isDark: Boolean, darkStyle: DarkStyle = DarkStyle.BOOST): ColorScheme {
    val argb = colorToArgb(seedColor)
    val cam = Cam16.fromInt(argb)
    val palettes = SeedPalettes(
        a1 = TonalPalette.fromHueAndChroma(cam.hue, maxOf(44.0, cam.chroma)),
        a2 = TonalPalette.fromHueAndChroma(cam.hue, 12.0),
        a3 = TonalPalette.fromHueAndChroma(cam.hue, 24.0),
        n1 = TonalPalette.fromHueAndChroma(cam.hue, 1.0),
        n2 = TonalPalette.fromHueAndChroma(cam.hue, 3.0),
        error = TonalPalette.fromHueAndChroma(25.0, 84.0),
    )
    return if (isDark) darkScheme(palettes, darkStyle) else lightScheme(palettes)
}

private fun colorToArgb(color: Color): Int {
    return ((color.alpha * 255 + 0.5f).toInt() shl 24) or
            ((color.red * 255 + 0.5f).toInt() shl 16) or
            ((color.green * 255 + 0.5f).toInt() shl 8) or
            ((color.blue * 255 + 0.5f).toInt())
}

private fun lightScheme(p: SeedPalettes): ColorScheme = ColorScheme(
    primary = Color(p.a1.tone(40)),
    onPrimary = Color(p.a1.tone(100)),
    primaryContainer = Color(p.a1.tone(90)),
    onPrimaryContainer = Color(p.a1.tone(10)),
    inversePrimary = Color(p.a1.tone(80)),
    secondary = Color(p.a2.tone(40)),
    onSecondary = Color(p.a2.tone(100)),
    secondaryContainer = Color(p.a2.tone(90)),
    onSecondaryContainer = Color(p.a2.tone(10)),
    tertiary = Color(p.a3.tone(40)),
    onTertiary = Color(p.a3.tone(100)),
    tertiaryContainer = Color(p.a3.tone(90)),
    onTertiaryContainer = Color(p.a3.tone(10)),
    background = Color(p.n1.tone(99)),
    onBackground = Color(p.n1.tone(10)),
    surface = Color(p.n1.tone(99)),
    onSurface = Color(p.n1.tone(10)),
    surfaceVariant = Color(p.n2.tone(90)),
    onSurfaceVariant = Color(p.n2.tone(30)),
    surfaceTint = Color(p.a1.tone(40)),
    inverseSurface = Color(p.n1.tone(20)),
    inverseOnSurface = Color(p.n1.tone(95)),
    error = Color(p.error.tone(40)),
    onError = Color(p.error.tone(100)),
    errorContainer = Color(p.error.tone(90)),
    onErrorContainer = Color(p.error.tone(10)),
    outline = Color(p.n2.tone(50)),
    outlineVariant = Color(p.n2.tone(80)),
    scrim = Color(p.n1.tone(0)),
    surfaceBright = Color(p.n1.tone(98)),
    surfaceDim = Color(p.n1.tone(87)),
    surfaceContainer = Color(p.n1.tone(94)),
    surfaceContainerHigh = Color(p.n1.tone(92)),
    surfaceContainerHighest = Color(p.n1.tone(90)),
    surfaceContainerLow = Color(p.n1.tone(96)),
    surfaceContainerLowest = Color(p.n1.tone(100)),
    primaryFixed = Color(p.a1.tone(90)),
    primaryFixedDim = Color(p.a1.tone(80)),
    onPrimaryFixed = Color(p.a1.tone(10)),
    onPrimaryFixedVariant = Color(p.a1.tone(30)),
    secondaryFixed = Color(p.a2.tone(90)),
    secondaryFixedDim = Color(p.a2.tone(80)),
    onSecondaryFixed = Color(p.a2.tone(10)),
    onSecondaryFixedVariant = Color(p.a2.tone(30)),
    tertiaryFixed = Color(p.a3.tone(90)),
    tertiaryFixedDim = Color(p.a3.tone(80)),
    onTertiaryFixed = Color(p.a3.tone(10)),
    onTertiaryFixedVariant = Color(p.a3.tone(30)),
)

private fun darkScheme(p: SeedPalettes, style: DarkStyle): ColorScheme {
    val t = darkTones(style)
    return ColorScheme(
    primary = Color(p.a1.tone(80)),
    onPrimary = Color(p.a1.tone(20)),
    primaryContainer = Color(p.a1.tone(30)),
    onPrimaryContainer = Color(p.a1.tone(90)),
    inversePrimary = Color(p.a1.tone(40)),
    secondary = Color(p.a2.tone(80)),
    onSecondary = Color(p.a2.tone(20)),
    secondaryContainer = Color(p.a2.tone(30)),
    onSecondaryContainer = Color(p.a2.tone(90)),
    tertiary = Color(p.a3.tone(80)),
    onTertiary = Color(p.a3.tone(20)),
    tertiaryContainer = Color(p.a3.tone(30)),
    onTertiaryContainer = Color(p.a3.tone(90)),
    // 深色分层随风格档位切换（增强=纯黑 / 标准=适中 / 柔和=偏灰），
    // 组件层级拉开边界感：Lowest 凹陷 < Low 次级卡片 < Container 主卡片 < High 输入区 < Highest 悬浮层
    background = Color(p.n1.tone(t.bg.toInt())),
    onBackground = Color(p.n1.tone(90)),
    surface = Color(p.n1.tone(t.bg.toInt())),
    onSurface = Color(p.n1.tone(90)),
    surfaceVariant = Color(p.n2.tone(t.variant.toInt())),
    onSurfaceVariant = Color(p.n2.tone(80)),
    surfaceTint = Color(p.a1.tone(80)),
    inverseSurface = Color(p.n1.tone(90)),
    inverseOnSurface = Color(p.n1.tone(20)),
    error = Color(p.error.tone(80)),
    onError = Color(p.error.tone(20)),
    errorContainer = Color(p.error.tone(30)),
    onErrorContainer = Color(p.error.tone(90)),
    outline = Color(p.n2.tone(60)),
    outlineVariant = Color(p.n2.tone(30)),
    scrim = Color(p.n1.tone(0)),
    surfaceBright = Color(p.n1.tone(t.bright.toInt())),
    surfaceDim = Color(p.n1.tone(t.dim.toInt())),
    surfaceContainer = Color(p.n1.tone(t.container.toInt())),
    surfaceContainerHigh = Color(p.n1.tone(t.high.toInt())),
    surfaceContainerHighest = Color(p.n1.tone(t.highest.toInt())),
    surfaceContainerLow = Color(p.n1.tone(t.low.toInt())),
    surfaceContainerLowest = Color(p.n1.tone(t.lowest.toInt())),
    // fixed 角色不随明暗模式变化，与浅色方案保持同一组 tone 值
    primaryFixed = Color(p.a1.tone(90)),
    primaryFixedDim = Color(p.a1.tone(80)),
    onPrimaryFixed = Color(p.a1.tone(10)),
    onPrimaryFixedVariant = Color(p.a1.tone(30)),
    secondaryFixed = Color(p.a2.tone(90)),
    secondaryFixedDim = Color(p.a2.tone(80)),
    onSecondaryFixed = Color(p.a2.tone(10)),
    onSecondaryFixedVariant = Color(p.a2.tone(30)),
    tertiaryFixed = Color(p.a3.tone(90)),
    tertiaryFixedDim = Color(p.a3.tone(80)),
    onTertiaryFixed = Color(p.a3.tone(10)),
    onTertiaryFixedVariant = Color(p.a3.tone(30)),
    )
}
