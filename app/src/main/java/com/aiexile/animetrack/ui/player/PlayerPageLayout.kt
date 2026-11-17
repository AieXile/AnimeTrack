package com.aiexile.animetrack.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.isExpandedWidth
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/** 固定视频槽位，不在小窗与全屏间迁移 AndroidView，避免暂停切全屏时重绑 Surface 导致黑屏或画面偏移。 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerPageLayout(
    isFullscreen: Boolean,
    title: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    video: @Composable () -> Unit,
    details: @Composable () -> Unit
) {
    val expandedWidth = isExpandedWidth()

    AppScaffold(
        title = title,
        onBack = onBack,
        showTopBar = !isFullscreen,
        contentWindowInsets = if (isFullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    rememberAppIconPainter(AppIcon.SETTINGS),
                    contentDescription = stringResource(R.string.player_settings_title)
                )
            }
        }
    ) { chrome ->
        BoxWithConstraints(Modifier.fillMaxSize().then(chrome.layout).padding(chrome.content)) {
            val expanded = expandedWidth && maxWidth > maxHeight
            Layout(
                content = {
                    Box(Modifier.background(Color.Black).clipToBounds()) {
                        video()
                    }
                    if (!isFullscreen) {
                        Box(Modifier.fillMaxSize()) {
                            details()
                        }
                    }
                }
            ) { measurables, constraints ->
                val videoMeasurable = measurables[0]
                val detailsMeasurable = measurables.getOrNull(1)
                val totalWidth = constraints.maxWidth
                val totalHeight = constraints.maxHeight
                if (isFullscreen || detailsMeasurable == null) {
                    val videoPlaceable = videoMeasurable.measure(
                        Constraints.fixed(totalWidth, totalHeight)
                    )
                    layout(totalWidth, totalHeight) {
                        videoPlaceable.placeRelative(0, 0)
                    }
                } else if (expanded) {
                    val leftWidth = totalWidth / 2
                    val rightWidth = totalWidth - leftWidth
                    val videoPlaceable = videoMeasurable.measure(
                        Constraints.fixed(leftWidth, totalHeight)
                    )
                    val detailsPlaceable = detailsMeasurable.measure(
                        Constraints.fixed(rightWidth, totalHeight)
                    )
                    layout(totalWidth, totalHeight) {
                        videoPlaceable.placeRelative(0, 0)
                        detailsPlaceable.placeRelative(leftWidth, 0)
                    }
                } else {
                    val videoHeight = minOf(
                        (totalWidth * 9f / 16f).toInt(),
                        (totalHeight * 0.5f).toInt()
                    ).coerceAtLeast(0)
                    val detailsHeight = (totalHeight - videoHeight).coerceAtLeast(0)
                    val videoPlaceable = videoMeasurable.measure(
                        Constraints.fixed(totalWidth, videoHeight)
                    )
                    val detailsPlaceable = detailsMeasurable.measure(
                        Constraints.fixed(totalWidth, detailsHeight)
                    )
                    layout(totalWidth, totalHeight) {
                        videoPlaceable.placeRelative(0, 0)
                        detailsPlaceable.placeRelative(0, videoHeight)
                    }
                }
            }
        }
    }
}
