package com.aiexile.animetrack.ui.favorites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape

@Composable
internal fun FavoriteFolderPinchGuide(
    visible: Boolean,
    ready: Boolean,
    pinchProgress: State<Float?>,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible, modifier = modifier) {
        val requester = remember { BringIntoViewRequester() }
        val demo = remember { Animatable(0f) }
        LaunchedEffect(ready, visible) {
            if (ready && visible) {
                requester.bringIntoView()
                repeat(2) {
                    demo.animateTo(1f, tween(durationMillis = 680, delayMillis = 260))
                    demo.animateTo(0f, tween(durationMillis = 340, delayMillis = 220))
                }
            }
        }

        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester).padding(bottom = 6.dp),
            shape = SquircleShape(12.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(width = 42.dp, height = 30.dp)) {
                    val progress = pinchProgress.value
                    val fraction = progress ?: demo.value
                    val separation = (16.dp - 10.dp * fraction).toPx()
                    val color = if (progress == null) colors.onPrimaryContainer else colors.primary
                    drawCircle(color, radius = 4.dp.toPx(), center = center - Offset(separation, 0f))
                    drawCircle(color, radius = 4.dp.toPx(), center = center + Offset(separation, 0f))
                }
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(
                        text = stringResource(R.string.favorite_folder_pinch_guide_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.favorite_folder_pinch_guide_message),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = onSkip, enabled = ready && visible) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
    }
}
