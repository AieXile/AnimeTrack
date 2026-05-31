package com.aiexile.animetrack.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.CorePalette
import com.google.android.material.color.utilities.TonalPalette

fun seedColorScheme(
    seedColor: Color,
    isDark: Boolean,
    style: PaletteStyle = PaletteStyle.TONAL_SPOT,
): ColorScheme {
    val argb = colorToArgb(seedColor)
    val palette = CorePalette.of(argb)
    val error = TonalPalette.fromHueAndChroma(25.0, 84.0)
    return if (isDark) darkScheme(palette, error) else lightScheme(palette, error)
}

private fun colorToArgb(color: Color): Int {
    return ((color.alpha * 255 + 0.5f).toInt() shl 24) or
            ((color.red * 255 + 0.5f).toInt() shl 16) or
            ((color.green * 255 + 0.5f).toInt() shl 8) or
            ((color.blue * 255 + 0.5f).toInt())
}

private fun lightScheme(p: CorePalette, e: TonalPalette): ColorScheme = ColorScheme(
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
    error = Color(e.tone(40)),
    onError = Color(e.tone(100)),
    errorContainer = Color(e.tone(90)),
    onErrorContainer = Color(e.tone(10)),
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
)

private fun darkScheme(p: CorePalette, e: TonalPalette): ColorScheme = ColorScheme(
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
    background = Color(p.n1.tone(10)),
    onBackground = Color(p.n1.tone(90)),
    surface = Color(p.n1.tone(10)),
    onSurface = Color(p.n1.tone(90)),
    surfaceVariant = Color(p.n2.tone(30)),
    onSurfaceVariant = Color(p.n2.tone(80)),
    surfaceTint = Color(p.a1.tone(80)),
    inverseSurface = Color(p.n1.tone(90)),
    inverseOnSurface = Color(p.n1.tone(20)),
    error = Color(e.tone(80)),
    onError = Color(e.tone(20)),
    errorContainer = Color(e.tone(30)),
    onErrorContainer = Color(e.tone(90)),
    outline = Color(p.n2.tone(60)),
    outlineVariant = Color(p.n2.tone(30)),
    scrim = Color(p.n1.tone(0)),
    surfaceBright = Color(p.n1.tone(24)),
    surfaceDim = Color(p.n1.tone(6)),
    surfaceContainer = Color(p.n1.tone(12)),
    surfaceContainerHigh = Color(p.n1.tone(17)),
    surfaceContainerHighest = Color(p.n1.tone(22)),
    surfaceContainerLow = Color(p.n1.tone(10)),
    surfaceContainerLowest = Color(p.n1.tone(4)),
)
