package com.aiexile.animetrack.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class PaletteStyle {
    TONAL_SPOT,
    VIBRANT,
    CONTENT,
    NEUTRAL,
}

enum class ThemePreset(val displayName: String, val seedColor: Color, val paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT) {
    VIBRANT_BLUE("清透蓝", Color(0xFF4285F4), PaletteStyle.TONAL_SPOT),
    OCEAN_CYAN("海洋青", Color(0xFF00ACC1), PaletteStyle.TONAL_SPOT),
    COOL_MINT("薄荷绿", Color(0xFF26A69A), PaletteStyle.CONTENT),
    SLATE_INDIGO("石板靛", Color(0xFF5C7CFA), PaletteStyle.VIBRANT),
    MONO_BLACK("黑白简洁", Color(0xFF000000), PaletteStyle.NEUTRAL),
}

data class AnimeColors(
    val starFilled: Color,
    val finished: Color,
    val finishedContainer: Color,
    val dropped: Color,
    val droppedContainer: Color,
    val watching: Color,
    val watchingContainer: Color,
)

val LocalAnimeColors = staticCompositionLocalOf {
    AnimeColors(
        starFilled = Color.Unspecified,
        finished = Color.Unspecified,
        finishedContainer = Color.Unspecified,
        dropped = Color.Unspecified,
        droppedContainer = Color.Unspecified,
        watching = Color.Unspecified,
        watchingContainer = Color.Unspecified,
    )
}
