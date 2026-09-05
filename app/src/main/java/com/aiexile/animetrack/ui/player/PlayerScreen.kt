package com.aiexile.animetrack.ui.player

import android.app.Activity
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.filterNotNull
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.aiexile.animetrack.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ControlsBarColor = Color.Black.copy(alpha = 0.5f)
private val GestureOverlayColor = Color.Black.copy(alpha = 0.7f)

/** 播放器手势四边防误触距离：避开系统侧滑返回/下拉状态栏/上滑回桌面手势区 */
private val GestureEdgeExclusion = 28.dp

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
    // 用 StateFlow 单订阅：LiveData.observe 会随 LaunchedEffect 重启叠加多个 observer，
    // 且 LiveData 值分发是广播式的——曾导致 playWebDavUrl 被重复触发多次
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    navBackStackEntry.value?.let { entry ->
        LaunchedEffect(entry) {
            entry.savedStateHandle.getStateFlow<String?>("webdav_file_path", null)
                .filterNotNull()
                .collect { path ->
                    val fileName = entry.savedStateHandle.get<String?>("webdav_file_name")
                    viewModel.playWebDavUrl(path, fileName)
                    entry.savedStateHandle["webdav_file_path"] = null
                }
        }
    }

    // 从番剧详情页进入（animeId > 0）时自动打开 WebDAV 浏览，让用户选择对应视频文件；
    // rememberSaveable 防止从浏览页返回后重复触发
    var autoBrowseTriggered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(animeId) {
        if (animeId > 0 && !autoBrowseTriggered) {
            autoBrowseTriggered = true
            onBrowseWebDAV()
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var controlsHideJob by remember { mutableStateOf<Job?>(null) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // 字幕/音轨选择弹窗：null = 关闭；值为 C.TRACK_TYPE_TEXT / C.TRACK_TYPE_AUDIO
    // 自建 Compose 弹窗替代 media3 TrackSelectionDialogBuilder（后者要求 FragmentActivity，本项目为 ComponentActivity 会闪退）
    var trackDialogType by remember { mutableIntStateOf(-1) }

    // 右上角「更多」菜单：面板渲染在顶层，与控制条自动隐藏互不影响
    var moreMenuExpanded by remember { mutableStateOf(false) }

    // 半屏面板（字幕/音轨选择、更多菜单）是否打开：用于联动隐藏上下控制条
    val anyPanelOpen = trackDialogType >= 0 || moreMenuExpanded

    // Gesture feedback state
    var gestureFeedback by remember { mutableStateOf<GestureFeedback?>(null) }
    var gestureFeedbackJob by remember { mutableStateOf<Job?>(null) }

    // Seek gesture state
    var seekDeltaMs by remember { mutableLongStateOf(0L) }

    // 四边防误触：给系统手势留空间（左侧滑返回/右侧滑返回、顶部下拉状态栏、底部上滑回桌面），
    // 起点落在边缘区内时忽略播放器的拖动手势与点按
    val gestureEdgeExclusionPx = with(LocalDensity.current) { GestureEdgeExclusion.toPx() }
    var ignoreVerticalGesture by remember { mutableStateOf(false) }
    var ignoreHorizontalGesture by remember { mutableStateOf(false) }

    fun isInGestureEdgeZone(x: Float, y: Float, w: Int, h: Int): Boolean {
        val e = gestureEdgeExclusionPx
        return x < e || y < e || x > w - e || y > h - e
    }

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

    // Keep controls visible when paused（半屏面板打开期间保持隐藏，由面板关闭逻辑恢复）
    LaunchedEffect(uiState.isPlaying) {
        if (!uiState.isPlaying) {
            controlsHideJob?.cancel()
            if (!anyPanelOpen) showControls = true
        } else {
            resetControlsTimer()
        }
    }

    // 半屏面板打开期间强制隐藏上下控制条；面板完全退出（含退场动画）后恢复显示并重新计时
    LaunchedEffect(anyPanelOpen) {
        if (anyPanelOpen) {
            controlsHideJob?.cancel()
            showControls = false
        } else {
            showControls = true
            resetControlsTimer()
        }
    }

    // Fullscreen mode - hide/show system bars & change orientation
    val insetsController = activity?.let {
        WindowCompat.getInsetsController(it.window, it.window.decorView)
    }
    // Fullscreen mode - hide/show system bars & change orientation。
    // 手动全屏按钮永远旋转到横向；「自动横屏」开关只控制播放横屏视频时
    // 是否自动进入全屏（见 PlayerViewModel.onVideoSizeChanged）
    LaunchedEffect(uiState.isFullscreen) {
        if (uiState.isFullscreen) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Restore system bars and orientation when leaving
    DisposableEffect(Unit) {
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
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
                onRetry = {
                    if (!viewModel.retryLast()) onBack()
                },
                onBack = onBack
            )
        } else if (!uiState.isControllerReady) {
            // 后台播放服务连接中（通常不到一瞬，感知不到）
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
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
                            onTap = { offset ->
                                // 边缘防误触：不响应点按，避免与系统手势冲突
                                if (!isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)) {
                                    showControls = !showControls
                                    if (showControls) resetControlsTimer()
                                }
                            },
                            onDoubleTap = { offset ->
                                if (!isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)) {
                                    viewModel.togglePlayPause()
                                }
                            },
                            onLongPress = { offset ->
                                if (!isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)) {
                                    viewModel.startLongPressSpeed()
                                }
                            }
                        )
                    }
                    // 长按加速时，松手恢复：常驻层从每次按下跟踪到全部抬起，
                    // 抬起瞬间同步调用恢复（未激活时为 no-op），不依赖重组与状态置位，无延迟
                    .pointerInput(viewModel) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                            } while (event.changes.any { it.pressed })
                            viewModel.stopLongPressSpeed()
                        }
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                seekDeltaMs = 0L
                                // 起点在四边防误触区内则整段手势忽略且不消费事件，
                                // 让系统侧滑返回/下拉状态栏/上滑回桌面正常接管
                                ignoreVerticalGesture = isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (ignoreVerticalGesture) return@detectVerticalDragGestures
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
                            onDragStart = { offset ->
                                seekDeltaMs = 0L
                                ignoreHorizontalGesture = isInGestureEdgeZone(offset.x, offset.y, size.width, size.height)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (ignoreHorizontalGesture) return@detectHorizontalDragGestures
                                change.consume()
                                // ~60 seconds per screen width
                                val seekPerPx = 60_000f / size.width
                                seekDeltaMs += (dragAmount * seekPerPx).toLong()
                            },
                            onDragEnd = {
                                if (seekDeltaMs != 0L && !ignoreHorizontalGesture) {
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
                            painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
                            contentDescription = stringResource(R.string.player_paused),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.player_paused),
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
                        onOpenMoreMenu = { moreMenuExpanded = true },
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
                        onSelectSubtitles = {
                            trackDialogType = C.TRACK_TYPE_TEXT
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

        // 字幕/音轨选择面板（右侧半透明抽屉，实现见 PlayerPanels.kt）
        if (trackDialogType >= 0) {
            TrackSelectionPanel(
                trackType = trackDialogType,
                player = viewModel.player,
                onDismiss = { trackDialogType = -1 }
            )
        }

        // 右上角「更多」菜单：与字幕抽屉同一套视觉语言（1/2 屏宽右侧抽屉），
        // 渲染在控制条之外，打开期间控制条由 anyPanelOpen 联动隐藏
        if (moreMenuExpanded) {
            PlayerMenuPanel(
                onDismiss = { moreMenuExpanded = false }
            ) { requestClose ->
                PlayerMenuItem(
                    icon = rememberAppIconPainter(AppIcon.MUSIC_NOTE),
                    label = stringResource(R.string.player_audio_track),
                    onClick = {
                        requestClose()
                        trackDialogType = C.TRACK_TYPE_AUDIO
                    }
                )
                PlayerMenuItemToggle(
                    icon = rememberAppIconPainter(AppIcon.SCREEN_ROTATION),
                    label = stringResource(R.string.player_auto_landscape),
                    checked = uiState.autoLandscape,
                    onCheckedChange = { viewModel.setAutoLandscape(it) }
                )
            }
        }    }
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
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
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

            // 快进85秒按钮
            IconButton(onClick = onSkipForward) {
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
    onSelectSubtitles: () -> Unit,
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
                painter = if (isPlaying) rememberAppIconPainter(AppIcon.PAUSE) else rememberAppIconPainter(AppIcon.PLAY_ARROW),
                contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onTogglePlayPause() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Next episode button (only if available)
            if (hasNextEpisode) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.SKIP_NEXT),
                    contentDescription = stringResource(R.string.player_next_episode),
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

            // 字幕轨选择（无字幕轨时面板显示空提示）
            IconButton(onClick = onSelectSubtitles) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CLOSED_CAPTION),
                    contentDescription = stringResource(R.string.player_subtitles),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
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
            shape = SquircleShape(8.dp)
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
            painter = rememberAppIconPainter(AppIcon.ERROR),
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
                shape = SquircleShape(8.dp)
            ) {
                Text(stringResource(R.string.common_back))
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = SquircleShape(8.dp)
            ) {
                Text(stringResource(R.string.common_retry))
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
            text = stringResource(R.string.player_select_source),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.player_select_source_hint),
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
            shape = SquircleShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.CLOUD),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.player_browse_webdav))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSelectLocalFile,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color.White
            ),
            shape = SquircleShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.INSERT_DRIVE_FILE),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.player_select_local_file))
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
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = SquircleShape(8.dp),
            color = GestureOverlayColor,
            modifier = Modifier.padding(top = 80.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.SPEED),
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
                    text = stringResource(R.string.player_long_press_speed),
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
