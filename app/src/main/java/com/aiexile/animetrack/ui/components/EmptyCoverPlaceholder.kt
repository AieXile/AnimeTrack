package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 无封面时的渐变占位组件
 */
@Composable
fun EmptyCoverPlaceholder(
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(8.dp)
) {
    val gradientBackground = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
        )
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(gradientBackground)
    )
}
