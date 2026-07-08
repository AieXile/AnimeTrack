package com.aiexile.animetrack.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ControlsBarColor = Color.Black.copy(alpha = 0.5f)
private val GestureOverlayColor = Color.Black.copy(alpha = 0.7f)

@Composable
fun PlayerScreen(
    animeId: Int,
    onBack: () -> Unit,
    onBrowseWebDAV: () -> Unit,
    onSelectLocalFile: () -> Unit = {},
    navController: NavController,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // Local file picker
    val localFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.playLocalUri(it) }
    }

    // Observe WebDAV file path from savedStateHandle (set by WebDAVBrowseScreen)
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry.value) {
        navBackStackEntry.value?.savedStateHandle
            ?.getLiveData<String?>("webdav_file_path")
            ?.observe(navBackStackEntry.value!!) { path: String? ->
                if (path != null) {
                    val fileName = navBackStackEntry.value?.savedStateHandle
                        ?.getLiveData<String?>("webdav_file_name")?.value
                    viewModel.playWebDavUrl(path, fileName)
                    navBackStackEntry.value?.savedStateHandle?.set("webdav_file_path", null)
                }
            }
    }

    var showControls by remember { mutableStateOf(true) }
    var controlsHideJob by remember { mutableStateOf<Job?>(null) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Gesture feedback state
    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var gestureFeedbackJob by remember { mutableStateOf<Job?>(null) }

    // Seek gesture state
    var seekDeltaMs by remember { mutableLongStateOf(0L) }

    // Brightness & volume state
    var currentBrightness by remember { mutableFloatStateOf(-1f) }
    var currentVolume by remember { mutableFloatStateOf(-1f) }
    var maxVolume by remember { mutableIntStateOf(0) }

    // Slider dragging state
    var isSeekDragging by remember { mutableStateOf(false) }
    var seekDragPositionMs by remember { mutableLongStateOf(0L) }

    // Auto-hide controls
    fun resetControlsTimer() {
        controlsHideJob?.cancel()
        if (uiState.isPlaying) {
            controlsHideJob = coroutineScope.launch {
                delay(3000)
                showControls = false
            }
        }
    }

    // Keep controls visible when paused
    LaunchedEffect(uiState.isPlaying) {
        if (!uiState.isPlaying) {
            controlsHideJob?.cancel()
            showControls = true
        } else {
            resetControlsTimer()
        }
    }

    // Fullscreen mode - hide/show system bars & change orientation
    val insetsController = activity?.let {
        WindowCompat.getInsetsController(it.window, it.window.decorView)
    }
    LaunchedEffect(uiState.isFullscreen) {
        if (uiState.isFullscreen) {
            insetsController?.hide(android.view.WindowInsets.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            insetsController?.show(android.view.WindowInsets.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Restore system bars and orientation when leaving
    DisposableEffect(Unit) {
        onDispose {
            insetsController?.show(android.view.WindowInsets.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Initialize brightness/volume
    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            currentBrightness = window.attributes.screenBrightness.let {
                if (it < 0f) {
                    try {
                        android.provider.Settings.System.getInt(
                            context.contentResolver,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS
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

    // Gesture feedback auto-hide
    fun showGestureFeedback(feedback: GestureFeedback) {
        gestureFeedbackJob?.cancel()
        gestureFeedback = feedback
        gestureFeedbackJob = coroutineScope.launch {
            delay(800)
            gestureFeedback = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Error state
        if (uiState.error != null) {
            ErrorOverlay(
                error = uiState.error!!,
                onRetry = { viewModel.playWebDavUrl("", uiState.mediaTitle) },
                onBack = onBack
            )
        } else if (uiState.durationMs <= 0 && !uiState.isPlaying) {
            // Empty state - no media loaded
            EmptyMediaState(
                onBrowseWebDAV = onBrowseWebDAV,
                onSelectLocalFile = {
                    localFileLauncher.launch(arrayOf("video/*"))
                }
            )
        } else {
            // Video surface
            PlayerViewContainer(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Gesture overlay area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                if (showControls) resetControlsTimer()
                            },
                            onDoubleTap = {
                                viewModel.togglePlayPause()
                            },
                            onLongPress = {
                                viewModel.startLongPressSpeed()
                            }
                        )
                    }
                    // 长按加速时，松手恢复
                    .pointerInput(uiState.isLongPressSpeedActive) {
                        if (uiState.isLongPressSpeedActive) {
                            awaitPointerEventScope {
                                var released = false
                                while (!released) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Release) {
                                        released = true
                                    }
                                }
                            }
                            viewModel.stopLongPressSpeed()
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                seekDeltaMs = 0L
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val screenWidth = size.width
                                val isLeftHalf = change.position.x < screenWidth / 2

                                if (isLeftHalf) {
                                    // Brightness adjustment
                                    val delta = -dragAmount / size.height
                                    currentBrightness =
                                        (currentBrightness + delta).coerceIn(0f, 1f)
                                    activity?.window?.let { window ->
                                        val params = window.attributes
                                        params.screenBrightness = currentBrightness
                                        window.attributes = params
                                    }
                                    showGestureFeedback(
                                        GestureFeedback.Brightness(currentBrightness)
                                    )
                                } else {
                                    // Volume adjustment
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
                                    showGestureFeedback(
                                        GestureFeedback.Volume(currentVolume)
                                    )
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                seekDeltaMs = 0L
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                // ~60 seconds per screen width
                                val seekPerPx = 60_000f / size.width
                                seekDeltaMs += (dragAmount * seekPerPx).toLong()
                            },
                            onDragEnd = {
                                if (seekDeltaMs != 0L) {
                                    val newPosition =
                                        (uiState.currentPositionMs + seekDeltaMs)
                                            .coerceIn(0, uiState.durationMs)
                                    viewModel.seekTo(newPosition)
                                }
                                seekDeltaMs = 0L
                            },
                            onDragCancel = {
                                seekDeltaMs = 0L
                            }
                        )
                    }
            )

            // Long press speed boost indicator
            if (uiState.isLongPressSpeedActive) {
                LongPressSpeedIndicator(speed = uiState.playbackSpeed)
            }

            // Seek preview during horizontal drag
            if (seekDeltaMs != 0L) {
                val previewPosition =
                    (uiState.currentPositionMs + seekDeltaMs).coerceIn(0, uiState.durationMs)
                SeekPreviewOverlay(
                    currentPosition = uiState.currentPositionMs,
                    seekPosition = previewPosition,
                    duration = uiState.durationMs
                )
            }

            // Gesture feedback overlay (brightness/volume)
            gestureFeedback?.let { feedback ->
                GestureFeedbackOverlay(feedback = feedback)
            }

            // Pause indicator (always visible when paused, outside controls)
            if (!uiState.isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp)
                        .then(
                            if (uiState.isFullscreen) Modifier.padding(bottom = 56.dp)
                            else Modifier.padding(bottom = 48.dp)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "已暂停",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "已暂停",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Controls overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top bar
                    TopControlBar(
                        title = uiState.mediaTitle,
                        onBack = onBack,
                        onSkipForward = {
                            viewModel.seekTo(uiState.currentPositionMs + 85_000L)
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .then(
                                if (!uiState.isFullscreen) Modifier.statusBarsPadding()
                                else Modifier
                            )
                    )

                    // Center play/pause indicator (moved to bottom bar)

                    // Bottom bar
                    BottomControlBar(
                        currentPositionMs = if (isSeekDragging) seekDragPositionMs else uiState.currentPositionMs,
                        durationMs = uiState.durationMs,
                        bufferedPositionMs = uiState.bufferedPositionMs,
                        playbackSpeed = uiState.playbackSpeed,
                        isFullscreen = uiState.isFullscreen,
                        isPlaying = uiState.isPlaying,
                        hasNextEpisode = viewModel.hasNextEpisode(),
                        showSpeedMenu = showSpeedMenu,
                        onSpeedMenuChange = { showSpeedMenu = it },
                        onSpeedSelected = { speed ->
                            viewModel.setPlaybackSpeed(speed)
                        },
                        onSeekToRatio = { ratio ->
                            viewModel.seekToPositionRatio(ratio)
                            resetControlsTimer()
                        },
                        onSeekDragStart = {
                            isSeekDragging = true
                            seekDragPositionMs = uiState.currentPositionMs
                        },
                        onSeekDrag = { ratio ->
                            seekDragPositionMs = (uiState.durationMs * ratio).toLong()
                        },
                        onSeekDragEnd = {
                            isSeekDragging = false
                        },
                        onTogglePlayPause = {
                            viewModel.togglePlayPause()
                            resetControlsTimer()
                        },
                        onNextEpisode = {
                            viewModel.seekToNext()
                            resetControlsTimer()
                        },
                        onToggleFullscreen = {
                            viewModel.toggleFullscreen()
                            resetControlsTimer()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .then(
                                if (!uiState.isFullscreen) Modifier.statusBarsPadding()
                                else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerViewContainer(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = viewModel.player
                useController = false
            }
        },
        modifier = modifier
    )
}

@Composable
private fun TopControlBar(
    title: String?,
    onBack: () -> Unit,
    onSkipForward: () -> Unit,
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
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
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

            // 快进85秒按钮
            IconButton(onClick = onSkipForward) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "快进85秒",
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
        }
    }
}

@Composable
private fun BottomControlBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    playbackSpeed: Float,
    isFullscreen: Boolean,
    isPlaying: Boolean,
    hasNextEpisode: Boolean,
    showSpeedMenu: Boolean,
    onSpeedMenuChange: (Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onSeekToRatio: (Float) -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextEpisode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, ControlsBarColor)
                )
            )
            .padding(horizontal = 12.dp)
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        // Main control row: [Play/Pause] [Next] [SeekBar] [Speed] [Fullscreen]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause button
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onTogglePlayPause() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Next episode button (only if available)
            if (hasNextEpisode) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一集",
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onNextEpisode() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Seek bar
            Box(
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
                style = TextStyle(shadow = Shadow(Color.Black, Offset(1f, 1f), 2f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Speed text (no icon)
            Box {
                Text(
                    text = "${playbackSpeed}x",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onSpeedMenuChange(true) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                SpeedDropdownMenu(
                    expanded = showSpeedMenu,
                    currentSpeed = playbackSpeed,
                    onDismiss = { onSpeedMenuChange(false) },
                    onSpeedSelected = { speed ->
                        onSpeedMenuChange(false)
                        onSpeedSelected(speed)
                    }
                )
            }

            // Fullscreen button
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit
                    else Icons.Rounded.Fullscreen,
                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeekToRatio: (Float) -> Unit,
    onSeekDragStart: () -> Unit,
    onSeekDrag: (Float) -> Unit,
    onSeekDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f
    val buffered = if (durationMs > 0) bufferedPositionMs.toFloat() / durationMs else 0f

    var sliderValue by remember(progress) { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxWidth()) {
        // Custom track with buffered indicator
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        ) {
            val barHeight = 3.dp.toPx()
            val barY = (size.height - barHeight) / 2f
            val cornerRadius = barHeight / 2f

            // Background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                topLeft = Offset(0f, barY),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(cornerRadius)
            )

            // Buffered track
            val bufferedWidth = size.width * buffered.coerceIn(0f, 1f)
            if (bufferedWidth > 0) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(0f, barY),
                    size = Size(bufferedWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            }

            // Progress track
            val currentProgress = if (isDragging) sliderValue else progress
            val progressWidth = size.width * currentProgress.coerceIn(0f, 1f)
            if (progressWidth > 0) {
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(0f, barY),
                    size = Size(progressWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            }
        }

        // Slider overlay
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

@Composable
private fun SpeedDropdownMenu(
    expanded: Boolean,
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.background(
            color = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(8.dp)
        )
    ) {
        speeds.forEach { speed ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = "${speed}x",
                        color = if (speed == currentSpeed) MaterialTheme.colorScheme.primary
                        else Color.White,
                        fontWeight = if (speed == currentSpeed) FontWeight.SemiBold
                        else FontWeight.Normal
                    )
                },
                onClick = { onSpeedSelected(speed) },
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
            )
        }
    }
}

@Composable
private fun ErrorOverlay(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = error,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF333333),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("返回")
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun EmptyMediaState(
    onBrowseWebDAV: () -> Unit,
    onSelectLocalFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "选择视频源",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "从 WebDAV 或本地文件中选择要播放的视频",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onBrowseWebDAV,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("浏览 WebDAV")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSelectLocalFile,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("选择本地文件")
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
            shape = RoundedCornerShape(8.dp),
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
            shape = RoundedCornerShape(8.dp),
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
                            imageVector = if (feedback.value < 0.3f) Icons.Rounded.BrightnessLow
                            else Icons.Rounded.BrightnessHigh,
                            contentDescription = "亮度",
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
                            imageVector = when {
                                feedback.value <= 0f -> Icons.Rounded.VolumeOff
                                feedback.value < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                                else -> Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = "音量",
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
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = GestureOverlayColor,
            modifier = Modifier.padding(top = 80.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${speed}x",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "长按加速中",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private sealed class GestureFeedback {
    data class Brightness(val value: Float) : GestureFeedback()
    data class Volume(val value: Float) : GestureFeedback()
}

fun formatDuration(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
