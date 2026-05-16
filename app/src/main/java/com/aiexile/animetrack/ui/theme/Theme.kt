package com.aiexile.animetrack.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
