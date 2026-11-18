package com.aiexile.animetrack.ui.player.components

import com.aiexile.animetrack.ui.player.PlayerUiState

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/** 小窗口专用控制层：中央播放键、贴底单行进度与放大入口。 */
@Composable
internal fun EmbeddedPlayerControls(
    state: PlayerUiState,
    visible: Boolean,
    positionMs: Long,
    isSeeking: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSeekToRatio: (Float) -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    /** 控制条实际高度（含顶部内边距），供页面的「已暂停」提示避让 */
    onBarHeightChanged: (Int) -> Unit = {}
) {
    // 与全屏控制条一致：显隐走淡入淡出。从隐藏态起步再切到目标态，
    // 保证「退出全屏时首次挂载」也播放淡入（初始即 visible 的 AnimatedVisibility 不播动画）
    val appearance = remember { MutableTransitionState(false) }
    LaunchedEffect(visible, state.isPreparingMedia) {
        appearance.targetState = visible && !state.isPreparingMedia
    }
    AnimatedVisibility(
        visibleState = appearance,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150))
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .onSizeChanged { onBarHeightChanged(it.height) }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .padding(start = 12.dp, end = 2.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 播放/暂停在进度条之前，窄屏上不会被进度条挤到时长后面
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(32.dp)) {
                    Icon(rememberAppIconPainter(if (state.playWhenReady) AppIcon.PAUSE else AppIcon.PLAY_ARROW),
                        contentDescription = stringResource(if (state.playWhenReady)
                            R.string.player_pause else R.string.player_play),
                        tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Text(formatDuration(positionMs), color = Color.White, fontSize = 10.sp, maxLines = 1)
                PlayerSeekBar(
                    currentPositionMs = positionMs, durationMs = state.durationMs,
                    bufferedPositionMs = state.bufferedPositionMs,
                    onSeekToRatio = onSeekToRatio, onSeekDragStart = onSeekDragStart,
                    onSeekDrag = onSeekDrag, onSeekDragEnd = onSeekDragEnd,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    progressColor = Color.White
                )
                Text(formatDuration(state.durationMs), color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp, maxLines = 1)
                IconButton(onClick = onToggleFullscreen) {
                    Icon(rememberAppIconPainter(AppIcon.FULLSCREEN),
                        contentDescription = stringResource(R.string.player_fullscreen),
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
