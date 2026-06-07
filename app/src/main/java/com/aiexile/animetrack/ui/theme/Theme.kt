package com.aiexile.animetrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AnimeTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePreset: ThemePreset = ThemePreset.VIBRANT_BLUE,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themePreset.paletteStyle == PaletteStyle.NEUTRAL -> remember(darkTheme) {
            if (darkTheme) monoBlackDarkScheme() else monoBlackLightScheme()
        }
        else -> remember(themePreset, darkTheme) {
            seedColorScheme(
                seedColor = themePreset.seedColor,
                isDark = darkTheme,
                style = themePreset.paletteStyle,
            )
        }
    }

    val animeColors = remember(colorScheme) {
        AnimeColors(
            starFilled = Color(0xFFF9A825),
            finished = colorScheme.tertiary,
            finishedContainer = colorScheme.tertiary.copy(alpha = 0.1f),
            dropped = colorScheme.error,
            droppedContainer = colorScheme.error.copy(alpha = 0.1f),
            watching = colorScheme.primary,
            watchingContainer = colorScheme.primary.copy(alpha = 0.1f),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAnimeColors provides animeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

internal fun monoBlackLightScheme(): ColorScheme = ColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFFBDBDBD),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color(0xFF212121),
    tertiary = Color(0xFF616161),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5F5F5),
    onTertiaryContainer = Color(0xFF212121),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF424242),
    surfaceTint = Color(0xFF1A1A1A),
    inverseSurface = Color(0xFF2C2C2C),
    inverseOnSurface = Color(0xFFF0F0F0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFAFA),
    surfaceDim = Color(0xFFEAEAEA),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private fun monoBlackDarkScheme(): ColorScheme = ColorScheme(
    primary = Color(0xFFE0E0E0),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFE0E0E0),
    inversePrimary = Color(0xFF1A1A1A),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2A2A2A),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceTint = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF1A1A1A),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFFB3261E),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF2C2C2C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C2C2C),
    surfaceDim = Color(0xFF000000),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2C2C2C),
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainerLowest = Color(0xFF1A1A1A),
)
