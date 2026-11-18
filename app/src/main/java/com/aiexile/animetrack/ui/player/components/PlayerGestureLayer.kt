package com.aiexile.animetrack.ui.player.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val GestureOverlayColor = Color.Black.copy(alpha = 0.7f)

/** 播放器手势四边防误触距离：避开系统侧滑返回/下拉状态栏/上滑回桌面手势区 */
private val GestureEdgeExclusion = 28.dp

private sealed class GestureFeedback {
    data class Brightness(val value: Float) : GestureFeedback()
    data class Volume(val value: Float) : GestureFeedback()
}

/** 播放器触控手势层与反馈浮层（点击/双击/长按倍速/左亮右音/横向快进）。 */
@Composable
internal fun PlayerGestureLayer(
    currentPositionMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    isLongPressSpeedActive: Boolean,
    seekDeltaMs: Long,
    onSeekDeltaChange: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStartLongPressSpeed: () -> Unit,
    onStopLongPressSpeed: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestSeekDeltaMs by rememberUpdatedState(seekDeltaMs)
    val latestOnSeekDeltaChange by rememberUpdatedState(onSeekDeltaChange)
    val latestOnToggleControls by rememberUpdatedState(onToggleControls)
    val latestOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val latestOnStartLongPressSpeed by rememberUpdatedState(onStartLongPressSpeed)
    val latestOnStopLongPressSpeed by rememberUpdatedState(onStopLongPressSpeed)
    val latestOnSeekTo by rememberUpdatedState(onSeekTo)

    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var gestureFeedbackJob by remember { mutableStateOf<Job?>(null) }

    val gestureEdgeExclusionPx = with(LocalDensity.current) { GestureEdgeExclusion.toPx() }
    var ignoreVerticalGesture by remember { mutableStateOf(false) }
    var ignoreHorizontalGesture by remember { mutableStateOf(false) }

    fun isInGestureEdgeZone(x: Float, y: Float, w: Int, h: Int): Boolean {
        val e = gestureEdgeExclusionPx
        return x < e || y < e || x > w - e || y > h - e
    }

    var currentBrightness by remember { mutableFloatStateOf(-1f) }
    var currentVolume by remember { mutableFloatStateOf(-1f) }
    var maxVolume by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            currentBrightness = window.attributes.screenBrightness.let {
                if (it < 0f) {
                    try {
                        Settings.System.getInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS
                        ) / 255f
                    } catch (_: Exception) {
                        0.5f
                    }
                } else it
            }
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = if (maxVolume > 0) {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        } else 0f
    }

    fun showGestureFeedback(feedback: GestureFeedback) {
        gestureFeedbackJob?.cancel()
        gestureFeedback = feedback
        gestureFeedbackJob = coroutineScope.launch {
            delay(800)
            gestureFeedback = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (!isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)) {
                                latestOnToggleControls()
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)) {
                                latestOnTogglePlayPause()
                            }
                        },
                        onLongPress = { offset ->
                            if (!isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)) {
                                latestOnStartLongPressSpeed()
                            }
                        }
                    )
                }
                // 长按加速时，松手恢复：只以「手指全部抬起」结束。
                // 滑动会被其他手势消费，waitForUpOrCancellation 会把消费当成取消，
                // 导致手指还没抬起长按加速就提前恢复
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) break
                        }
                        latestOnStopLongPressSpeed()
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            latestOnSeekDeltaChange(0L)
                            ignoreVerticalGesture = isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (ignoreVerticalGesture) return@detectVerticalDragGestures
                            change.consume()
                            val screenWidth = size.width
                            val isLeftHalf = change.position.x < screenWidth / 2

                            if (isLeftHalf) {
                                val delta = -dragAmount / size.height
                                currentBrightness = (currentBrightness + delta).coerceIn(0f, 1f)
                                activity?.window?.let { window ->
                                    val params = window.attributes
                                    params.screenBrightness = currentBrightness
                                    window.attributes = params
                                }
                                showGestureFeedback(GestureFeedback.Brightness(currentBrightness))
                            } else {
                                val delta = -dragAmount / size.height
                                currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
                                val audioManager =
                                    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val targetVol =
                                    (currentVolume * maxVolume).toInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    targetVol,
                                    0
                                )
                                showGestureFeedback(GestureFeedback.Volume(currentVolume))
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            latestOnSeekDeltaChange(0L)
                            ignoreHorizontalGesture = isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (ignoreHorizontalGesture) return@detectHorizontalDragGestures
                            change.consume()
                            val seekPerPx = 60_000f / size.width
                            latestOnSeekDeltaChange(latestSeekDeltaMs + (dragAmount * seekPerPx).toLong())
                        },
                        onDragEnd = {
                            if (latestSeekDeltaMs != 0L && !ignoreHorizontalGesture) {
                                val newPosition =
                                    (latestPositionMs + latestSeekDeltaMs).coerceIn(0, latestDurationMs)
                                latestOnSeekTo(newPosition)
                            }
                            latestOnSeekDeltaChange(0L)
                        },
                        onDragCancel = {
                            latestOnSeekDeltaChange(0L)
                        }
                    )
                }
        )

        if (isLongPressSpeedActive) {
            LongPressSpeedIndicator(speed = playbackSpeed)
        }

        if (seekDeltaMs != 0L) {
            val previewPosition = (currentPositionMs + seekDeltaMs).coerceIn(0, durationMs)
            SeekPreviewOverlay(
                currentPosition = currentPositionMs,
                seekPosition = previewPosition,
                duration = durationMs
            )
        }

        gestureFeedback?.let { feedback ->
            GestureFeedbackOverlay(feedback = feedback)
        }
    }
}

@Composable
private fun SeekPreviewOverlay(
    currentPosition: Long,
    seekPosition: Long,
    duration: Long,
    modifier: Modifier = Modifier
) {
    val delta = seekPosition - currentPosition
    val sign = if (delta >= 0) "+" else "-"
    val deltaText = formatDuration(abs(delta))

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = SquircleShape(8.dp),
            color = GestureOverlayColor,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$sign$deltaText",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatDuration(seekPosition)} / ${formatDuration(duration)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GestureFeedbackOverlay(
    feedback: GestureFeedback,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = SquircleShape(8.dp),
            color = GestureOverlayColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (feedback) {
                    is GestureFeedback.Brightness -> {
                        Icon(
                            painter = if (feedback.value < 0.3f) rememberAppIconPainter(AppIcon.BRIGHTNESS_LOW)
                            else rememberAppIconPainter(AppIcon.BRIGHTNESS_HIGH),
                            contentDescription = stringResource(R.string.player_brightness),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${(feedback.value * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    is GestureFeedback.Volume -> {
                        Icon(
                            painter = when {
                                feedback.value <= 0f -> rememberAppIconPainter(AppIcon.VOLUME_OFF)
                                feedback.value < 0.5f -> rememberAppIconPainter(AppIcon.VOLUME_DOWN)
                                else -> rememberAppIconPainter(AppIcon.VOLUME_UP)
                            },
                            contentDescription = stringResource(R.string.player_volume),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${(feedback.value * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LongPressSpeedIndicator(
    speed: Float,
    modifier: Modifier = Modifier
) {
    var raised by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_500)
        raised = true
    }
    val topPadding by animateDpAsState(
        targetValue = if (raised) 16.dp else 35.dp,
        animationSpec = tween(320), label = "speedHintTop"
    )
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (raised) 0f else 0.8f,
        animationSpec = tween(320), label = "speedHintBackground"
    )
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = SquircleShape(8.dp),
            color = Color.Black.copy(alpha = backgroundAlpha),
            contentColor = Color.White,
            modifier = Modifier.padding(top = topPadding)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.SPEED),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${speed}x",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 4f))
                )
            }
        }
    }
}