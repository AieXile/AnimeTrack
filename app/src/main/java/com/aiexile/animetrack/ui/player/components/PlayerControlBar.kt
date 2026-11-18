package com.aiexile.animetrack.ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

private val ControlsBarColor = Color.Black.copy(alpha = 0.5f)

@Composable
internal fun TopControlBar(
    title: String?,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    onSelectSubtitles: () -> Unit,
    onSkipForward: () -> Unit,
    onOpenMoreMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ControlsBarColor, Color.Transparent)
                )
            )
            .padding(horizontal = 4.dp, vertical = if (isFullscreen) 8.dp else 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isFullscreen) IconButton(onClick = onBack) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = title ?: "",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            // 字幕轨选择（无字幕轨时面板显示空提示）
            if (isFullscreen) IconButton(onClick = onSelectSubtitles) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CLOSED_CAPTION),
                    contentDescription = stringResource(R.string.player_subtitles),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 快进85秒按钮
            if (isFullscreen) IconButton(onClick = onSkipForward) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.REPLAY),
                        contentDescription = stringResource(R.string.player_skip_forward),
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { scaleX = -1f }
                    )
                    Text(
                        text = "85",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // 更多选项菜单：面板由 PlayerScreen 顶层渲染（与字幕抽屉同风格），
            // 不随控制条自动隐藏而消失
            IconButton(onClick = onOpenMoreMenu) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.MORE_VERT),
                    contentDescription = stringResource(R.string.common_more),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
internal fun BottomControlBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    playbackSpeed: Float,
    isFullscreen: Boolean,
    playWhenReady: Boolean,
    hasNextEpisode: Boolean,
    onOpenSpeed: () -> Unit,
    onSeekToRatio: (Float) -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextEpisode: () -> Unit,
    onOpenQuality: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBarHeightChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onBarHeightChanged(it.height) }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ControlsBarColor)
                )
            )
            .padding(horizontal = 12.dp)
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        // 小窗口把进度条单独放一行，给时间和播放按钮留出空间。
        if (!isFullscreen) {
            PlayerSeekBar(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                onSeekToRatio = onSeekToRatio,
                onSeekDragStart = onSeekDragStart,
                onSeekDrag = onSeekDrag,
                onSeekDragEnd = onSeekDragEnd
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = if (playWhenReady) rememberAppIconPainter(AppIcon.PAUSE) else rememberAppIconPainter(AppIcon.PLAY_ARROW),
                        contentDescription = if (playWhenReady) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (hasNextEpisode) {
                    IconButton(onClick = onNextEpisode, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.SKIP_NEXT),
                            contentDescription = stringResource(R.string.player_next_episode),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 全屏时沿用横向控制栏的进度条。
            if (isFullscreen) Box(
                modifier = Modifier.weight(1f)
            ) {
                PlayerSeekBar(
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    bufferedPositionMs = bufferedPositionMs,
                    onSeekToRatio = onSeekToRatio,
                    onSeekDragStart = onSeekDragStart,
                    onSeekDrag = onSeekDrag,
                    onSeekDragEnd = onSeekDragEnd
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Time display
            Text(
                text = "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = if (isFullscreen) Modifier else Modifier.weight(1f),
                style = TextStyle(shadow = Shadow(Color.Black, Offset(1f, 1f), 2f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 小窗口的倍速与清晰度入口位于下方详情区。
            if (isFullscreen) Box {
                Text(
                    text = "${playbackSpeed}x",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onOpenSpeed)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            // 清晰度（网站来源与自适应视频轨道）
            if (isFullscreen) Box {
                Text(
                    text = stringResource(R.string.player_quality),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onOpenQuality)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Fullscreen button
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    painter = if (isFullscreen) rememberAppIconPainter(AppIcon.FULLSCREEN_EXIT)
                    else rememberAppIconPainter(AppIcon.FULLSCREEN),
                    contentDescription = if (isFullscreen) stringResource(R.string.player_exit_fullscreen) else stringResource(R.string.player_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlayerSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeekToRatio: (Float) -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary
) {
    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
    val buffered = if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs else 0f

    var sliderValue by remember(progress) { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val barHeight = 3.dp.toPx()
            val barY = (size.height - barHeight) / 2f
            val cornerRadius = barHeight / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(0f, barY),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )

            val bufferedWidth = size.width * buffered.coerceIn(0f, 1f)
            if (bufferedWidth > 0) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(0f, barY),
                    size = Size(bufferedWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            }

            val currentProgress = if (isDragging) sliderValue else progress
            val progressWidth = size.width * currentProgress.coerceIn(0f, 1f)
            if (progressWidth > 0) {
                drawRoundRect(
                    color = progressColor,
                    topLeft = Offset(0f, barY),
                    size = Size(progressWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            }
        }

        Slider(
            value = if (isDragging) sliderValue else progress,
            onValueChange = {
                sliderValue = it
                isDragging = true
                onSeekDrag(it)
            },
            onValueChangeFinished = {
                isDragging = false
                onSeekToRatio(sliderValue)
                onSeekDragEnd()
            },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(5.dp)
                        .background(Color.White, CircleShape)
                )
            }
        )
    }

    LaunchedEffect(isDragging) {
        if (isDragging) {
            onSeekDragStart()
        }
    }
}