package com.aiexile.animetrack.ui.player

import android.app.Activity
import android.content.res.Configuration

import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalConfiguration
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
    initialLocalUri: String? = null,
    initialLocalTitle: String? = null,
    initialLocalMediaId: Long? = null,
    initialWebPlay: Boolean = false,
    onBack: () -> Unit,
    onBrowseWebDAV: () -> Unit,
    onSelectLocalFile: () -> Unit = {},
    navController: NavController,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val webParseRequest by viewModel.webParseRequest.collectAsStateWithLifecycle()
    var webAssistExpanded by remember(webParseRequest) { mutableStateOf(false) }
    var webAssistShowWeb by remember(webParseRequest) { mutableStateOf(false) }
    val currentPlaybackState by rememberUpdatedState(uiState)
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // 路由已带视频时，首帧等待 LaunchedEffect 交接请求，不能先展示选源界面。
    var initialRequestHandled by remember(
        initialLocalUri, initialLocalTitle, initialLocalMediaId, initialWebPlay
    ) { mutableStateOf(false) }

    LaunchedEffect(initialLocalUri, initialLocalTitle, initialLocalMediaId, initialWebPlay) {
        when {
            initialWebPlay -> com.aiexile.animetrack.data.player.PendingWebPlayback.consume()?.let {
                if (it.resolvePage) viewModel.playWebPage(it) else viewModel.playWeb(
                    it.url, it.mimeType, it.title, it.pageUrl, it.headers, it.userAgent, it.referer, it.queue
                )
            }
            initialLocalMediaId != null -> viewModel.playLocalMedia(initialLocalMediaId)
            initialLocalUri != null -> viewModel.playLocalUri(Uri.parse(initialLocalUri), initialLocalTitle)
        }
        initialRequestHandled = true
    }

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
    // WebDAV 返回时请求还在 savedStateHandle 中，订阅处理前也属于已选源。
    val hasPendingWebDavRequest = navBackStackEntry.value?.savedStateHandle
        ?.get<String>("webdav_file_path") != null
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
    LaunchedEffect(animeId, initialLocalUri, initialLocalMediaId, initialWebPlay) {
        if (initialLocalUri == null && initialLocalMediaId == null && !initialWebPlay &&
            animeId > 0 && !autoBrowseTriggered
        ) {
            autoBrowseTriggered = true
            onBrowseWebDAV()
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var controlsHideJob by remember { mutableStateOf<Job?>(null) }

    // 右侧抽屉层级栈：空 = 关闭；[Menu] = 一级「更多」；[Menu, 二级] = 从一级进入的二级。
    // 从控制条直接打开的倍速/字幕只有一层，点外部直接关闭；
    // 从「更多」进入的二级共用一个抽屉框，点外部只回退层级。
    var drawerStack by remember { mutableStateOf<List<PlayerDrawerLevel>>(emptyList()) }
    val drawerLevel = drawerStack.lastOrNull()
    var detailsSheetType by remember { mutableStateOf<DetailsSheetType?>(null) }

    val detailsGridState = rememberLazyGridState()
    val hasChildDrawer = drawerStack.size > 1
    // 半屏面板（字幕/音轨选择、更多菜单、选集）是否打开：用于联动隐藏上下控制条
    val anyPanelOpen = drawerLevel != null || detailsSheetType != null

    LaunchedEffect(uiState.notice) {
        uiState.notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearNotice()
        }
    }

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
        }
    }

    // Fullscreen mode - hide/show system bars & change orientation
    val insetsController = activity?.let {
        WindowCompat.getInsetsController(it.window, it.window.decorView)
    }
    val configuration = LocalConfiguration.current

    // 退出全屏时等待窗口先转回竖屏，再切回小窗布局。
    // 进入/退出的时序与 Animeko 一致：进入是「先切布局、再改窗口」，
    // 退出是「先改窗口、再切布局」；两个方向都要避免窗口还在过渡时反复重排视频槽位。
    var exitFullscreenPending by remember { mutableStateOf(false) }

    fun requestFullscreenToggle() {
        if (!uiState.isFullscreen) {
            // 立即切到全屏布局：视频按自身比例在竖屏窗口内留黑边，随后系统旋转，
            // 不会像「等方向到了再切」那样把横屏下的详情分栏露出来。
            if (exitFullscreenPending) return
            viewModel.setFullscreen(true)
            return
        }
        if (exitFullscreenPending) return
        exitFullscreenPending = true
        insetsController?.show(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    LaunchedEffect(exitFullscreenPending, configuration.orientation) {
        if (!exitFullscreenPending) return@LaunchedEffect
        if (configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return@LaunchedEffect
        exitFullscreenPending = false
        viewModel.setFullscreen(false)
    }

    // 分屏/自由窗口下方向请求可能被系统忽略，超时后仍退出全屏，避免按钮失效
    LaunchedEffect(exitFullscreenPending) {
        if (!exitFullscreenPending) return@LaunchedEffect
        delay(1200)
        if (exitFullscreenPending) {
            exitFullscreenPending = false
            viewModel.setFullscreen(false)
        }
    }

    val handleBack: () -> Unit = {
        when {
            // 退出全屏旋转中忽略返回，避免半途退出把布局留在过渡态
            exitFullscreenPending -> Unit
            uiState.isFullscreen -> requestFullscreenToggle()
            else -> onBack()
        }
    }

    androidx.activity.compose.BackHandler(uiState.isFullscreen && !anyPanelOpen && !webAssistExpanded) {
        requestFullscreenToggle()
    }
    androidx.activity.compose.BackHandler(anyPanelOpen) {
        drawerStack = if (hasChildDrawer) drawerStack.dropLast(1) else emptyList()
        detailsSheetType = null
    }

    // Fullscreen mode - hide/show system bars & change orientation。
    // 手动全屏按钮永远旋转到横向；「自动横屏」开关只控制播放横屏视频时
    // 是否自动进入全屏（见 PlayerViewModel.onVideoSizeChanged）
    LaunchedEffect(uiState.isFullscreen) {
        showControls = true
        resetControlsTimer()
        if (uiState.isFullscreen) {
            // 全屏下用户下滑唤出的系统栏只做临时显示，超时后自动收起；
            // 默认行为会把系统栏一直留在屏幕上。
            insetsController?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = if (uiState.fullscreenLandscape)
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            insetsController?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Restore system bars and orientation when leaving
    DisposableEffect(Unit) {
        onDispose {
            insetsController?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
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

    val pageTitle = uiState.seriesTitle?.takeIf { it.isNotBlank() }
        ?: uiState.mediaTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_playback)

    Box(Modifier.fillMaxSize()) {
        PlayerPageLayout(
            isFullscreen = uiState.isFullscreen,
            title = pageTitle,
            onBack = onBack,
            onOpenSettings = { detailsSheetType = DetailsSheetType.MORE },
            video = {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    // Error state
                    if (uiState.error != null) {
                        ErrorOverlay(
                            error = uiState.error!!,
                            onRetry = {
                                if (!viewModel.retryLast()) handleBack()
                            },
                            onBack = handleBack,
                            compact = !uiState.isFullscreen
                        )
                    } else if (!uiState.isControllerReady || !initialRequestHandled ||
                        hasPendingWebDavRequest || uiState.isPreparingMedia
                    ) {
                        PlayerLoadingOverlay(
                            message = stringResource(when {
                                uiState.isResolvingMedia -> R.string.web_episode_resolving
                                !uiState.isControllerReady -> R.string.player_connecting
                                else -> R.string.player_preparing
                            }),
                            onBack = if (uiState.isFullscreen) handleBack else null,
                            onAssist = if (webParseRequest != null) {
                                {
                                    webAssistShowWeb = true
                                    webAssistExpanded = true
                                }
                            } else null
                        )
                    } else if (!uiState.hasPlaybackRequest) {
                        // 只有从未选择视频时才显示选源，时长未知或暂停不会回退到这里。
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.player_select_source), color = Color.White)
                        }
                    } else {
                        // Video surface
                        PlayerViewContainer(
                            viewModel = viewModel,
                            videoScale = uiState.videoScale,
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
                                // 长按加速时，松手恢复：只以「手指全部抬起」结束。
                                // 滑动会被其他手势消费，waitForUpOrCancellation 会把消费当成取消，
                                // 导致手指还没抬起长按加速就提前恢复
                                .pointerInput(viewModel) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        while (true) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            if (event.changes.none { it.pressed }) break
                                        }
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
                                                    (currentPlaybackState.currentPositionMs + seekDeltaMs)
                                                        .coerceIn(0, currentPlaybackState.durationMs)
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

                        if (uiState.isBuffering && uiState.playWhenReady && !isSeekDragging) {
                            PlayerLoadingOverlay(message = stringResource(R.string.player_buffering))
                        }

                        // 只有用户/服务实际暂停才显示暂停，缓冲时 isPlaying 也会为 false。
                        if (!uiState.playWhenReady) {
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
                            visible = showControls && uiState.isFullscreen,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Top bar
                                TopControlBar(
                                    title = uiState.mediaTitle,
                                    isFullscreen = uiState.isFullscreen,
                                    onBack = handleBack,
                                    onSkipForward = {
                                        viewModel.seekTo(uiState.currentPositionMs + 85_000L)
                                        resetControlsTimer()
                                    },
                                    onOpenMoreMenu = { drawerStack = listOf(PlayerDrawerLevel.Menu) },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                )

                                // Center play/pause indicator (moved to bottom bar)

                                // Bottom bar
                                BottomControlBar(
                                    currentPositionMs = if (isSeekDragging) seekDragPositionMs else uiState.currentPositionMs,
                                    durationMs = uiState.durationMs,
                                    bufferedPositionMs = uiState.bufferedPositionMs,
                                    playbackSpeed = uiState.playbackSpeed,
                                    isFullscreen = uiState.isFullscreen,
                                    playWhenReady = uiState.playWhenReady,
                                    hasNextEpisode = viewModel.hasNextEpisode(),
                                    onOpenSpeed = { drawerStack = listOf(PlayerDrawerLevel.Speed) },
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
                                        drawerStack = listOf(PlayerDrawerLevel.Tracks(C.TRACK_TYPE_TEXT))
                                        resetControlsTimer()
                                    },
                                    onToggleFullscreen = {
                                        requestFullscreenToggle()
                                        resetControlsTimer()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                )
                            }
                        }
                    }

                    if (!uiState.isFullscreen && uiState.hasPlaybackRequest && uiState.error == null) {
                        EmbeddedPlayerControls(
                            state = uiState,
                                visible = showControls,
                            positionMs = if (isSeekDragging) seekDragPositionMs else uiState.currentPositionMs,
                            isSeeking = isSeekDragging || seekDeltaMs != 0L,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onToggleFullscreen = { requestFullscreenToggle() },
                            onSeekToRatio = viewModel::seekToPositionRatio,
                            onSeekDragStart = {
                                isSeekDragging = true
                                seekDragPositionMs = uiState.currentPositionMs
                            },
                            onSeekDrag = { ratio -> seekDragPositionMs = (uiState.durationMs * ratio).toLong() },
                            onSeekDragEnd = { isSeekDragging = false }
                        )
                    }

                    // 解析或播放失败后仍允许切换线路与选集。
                    if (uiState.error != null && uiState.hasPlaybackRequest && !anyPanelOpen) {
                        IconButton(onClick = { detailsSheetType = DetailsSheetType.MORE },
                            modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(rememberAppIconPainter(AppIcon.SETTINGS),
                                contentDescription = stringResource(R.string.player_settings_title), tint = Color.White)
                        }
                    }

                }
            },
            details = {
                if (initialRequestHandled && uiState.isControllerReady &&
                    !hasPendingWebDavRequest && !uiState.hasPlaybackRequest) {
                    EmptyMediaState(
                        onBrowseWebDAV = onBrowseWebDAV,
                        onSelectLocalFile = { localFileLauncher.launch(arrayOf("video/*")) }
                    )
                } else {
                    PlayerDetailsContent(
                        state = uiState,
                        gridState = detailsGridState,
                        onSelectEpisode = viewModel::selectEpisode,
                        onSelectChannel = viewModel::selectWebChannel,
                        onOpenQuality = { detailsSheetType = DetailsSheetType.QUALITY },
                        onOpenSpeed = { detailsSheetType = DetailsSheetType.SPEED },
                        onOpenSubtitles = { detailsSheetType = DetailsSheetType.SUBTITLES },
                        onOpenSettings = { detailsSheetType = DetailsSheetType.MORE }
                    )
                }
            }
        )

        // 播放器抽屉：一级「更多」与字幕/音轨、线路、选集、清晰度、倍速、画面比例等
        // 二级界面共用同一个抽屉框与遮罩。二级状态下点外部/返回键只回退到一级，
        // 一级状态下才关闭抽屉；层级切换只替换框内内容，一级面板不会被卸载重建。
        drawerLevel?.let { level ->
            PlayerDrawerHost(
                level = level,
                onDismissed = { drawerStack = emptyList() },
                onStepBack = if (hasChildDrawer) {
                    { drawerStack = drawerStack.dropLast(1) }
                } else null
            ) { target, requestClose ->
                when (target) {
                    // 横屏全屏时抽屉高度有限，一级菜单内容随高度滚动
                    PlayerDrawerLevel.Menu -> Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                    ) {
                        PlayerMenuSectionTitle(stringResource(R.string.player_menu_common))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerMenuGridItem(
                                icon = rememberAppIconPainter(AppIcon.CLOSED_CAPTION),
                                label = stringResource(R.string.player_subtitles),
                                onClick = { drawerStack = drawerStack + PlayerDrawerLevel.Tracks(C.TRACK_TYPE_TEXT) },
                                modifier = Modifier.weight(1f)
                            )
                            PlayerMenuGridItem(
                                icon = rememberAppIconPainter(AppIcon.MUSIC_NOTE),
                                label = stringResource(R.string.player_audio_track),
                                onClick = { drawerStack = drawerStack + PlayerDrawerLevel.Tracks(C.TRACK_TYPE_AUDIO) },
                                modifier = Modifier.weight(1f)
                            )
                            PlayerMenuGridItem(
                                icon = rememberAppIconPainter(AppIcon.SPEED),
                                label = stringResource(R.string.player_playback_speed),
                                onClick = { drawerStack = drawerStack + PlayerDrawerLevel.Speed },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerMenuGridItem(
                                icon = rememberAppIconPainter(AppIcon.FULLSCREEN),
                                label = stringResource(R.string.player_video_scale),
                                onClick = { drawerStack = drawerStack + PlayerDrawerLevel.Scale },
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.channels.size > 1) {
                                PlayerMenuGridItem(
                                    icon = rememberAppIconPainter(AppIcon.SYNC),
                                    label = stringResource(R.string.player_channels),
                                    onClick = { drawerStack = drawerStack + PlayerDrawerLevel.Channels },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(12.dp))
                        PlayerMenuSectionTitle(stringResource(R.string.player_menu_more))
                        PlayerMenuItemToggle(
                            icon = rememberAppIconPainter(AppIcon.SCREEN_ROTATION),
                            label = stringResource(R.string.player_auto_landscape),
                            checked = uiState.autoLandscape,
                            onCheckedChange = { viewModel.setAutoLandscape(it) }
                        )
                        PlayerMenuItemToggle(rememberAppIconPainter(AppIcon.SKIP_NEXT),
                            stringResource(R.string.player_settings_auto_play_next), uiState.autoPlayNext,
                            viewModel::setAutoPlayNext)
                        PlayerMenuItemToggle(rememberAppIconPainter(AppIcon.HISTORY),
                            stringResource(R.string.player_settings_remember_position), uiState.rememberPosition,
                            viewModel::setRememberPosition)
                        PlayerMenuItem(rememberAppIconPainter(AppIcon.REPLAY),
                            stringResource(if (uiState.isWebPlayback) R.string.web_playback_reparse else R.string.common_retry), {
                                requestClose()
                                viewModel.retryLast()
                            })
                    }

                    is PlayerDrawerLevel.Tracks -> {
                        if (uiState.isControllerReady) {
                            TrackSelectionContent(
                                trackType = target.trackType,
                                player = viewModel.player,
                                requestClose = requestClose
                            )
                        }
                    }

                    PlayerDrawerLevel.Episodes -> EpisodeSelectionContent(
                        episodes = uiState.episodes,
                        selectedIndex = uiState.currentEpisodeIndex,
                        progress = uiState.episodeProgress,
                        onSelect = { index ->
                            viewModel.selectEpisode(index)
                            resetControlsTimer()
                        }
                    )

                    PlayerDrawerLevel.Channels -> PlayerSelectionContent(
                        labels = uiState.channels.mapIndexed { index, name ->
                            name ?: stringResource(R.string.web_source_channel, index + 1)
                        },
                        selectedIndex = uiState.currentChannelIndex,
                        onSelect = { index ->
                            viewModel.selectWebChannel(index)
                            requestClose()
                        }
                    )

                    PlayerDrawerLevel.Quality -> {
                        if (uiState.isControllerReady) {
                            PlayerQualityContent(
                                player = viewModel.player,
                                sources = uiState.webSources,
                                selectedSource = uiState.selectedWebSource,
                                onSelectSource = viewModel::selectWebSource,
                                requestClose = requestClose
                            )
                        }
                    }

                    PlayerDrawerLevel.Speed -> {
                        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                        PlayerSelectionContent(
                            labels = speeds.map { "${it}×" },
                            selectedIndex = speeds.indexOf(uiState.playbackSpeed),
                            onSelect = { index ->
                                viewModel.setPlaybackSpeed(speeds[index])
                                requestClose()
                            }
                        )
                    }

                    PlayerDrawerLevel.Scale -> PlayerSelectionContent(
                        labels = listOf(
                            stringResource(R.string.player_scale_fit),
                            stringResource(R.string.player_scale_fill),
                            stringResource(R.string.player_scale_crop)
                        ),
                        selectedIndex = uiState.videoScale,
                        onSelect = { index ->
                            viewModel.setVideoScale(index)
                            requestClose()
                        }
                    )
                }
            }
        }
        detailsSheetType?.let { type ->
            PlayerDetailsSheet(
                type = type,
                state = uiState,
                player = viewModel.player,
                onDismiss = { detailsSheetType = null },
                onSelectSource = { index ->
                    viewModel.selectWebSource(index)
                    detailsSheetType = null
                },
                onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                onSetScale = viewModel::setVideoScale,
                onSetAutoNext = viewModel::setAutoPlayNext,
                onSetRememberPosition = viewModel::setRememberPosition,
                onSetAutoLandscape = viewModel::setAutoLandscape,
                onRetry = { viewModel.retryLast() }
            )
        }
    }

    webParseRequest?.let { request ->
        key(request) {
            WebMediaSnifferOverlay(
                pageUrl = request.pageUrl ?: request.url,
                userAgent = request.userAgent,
                referer = request.referer,
                expanded = webAssistExpanded,
                showWeb = webAssistShowWeb,
                onExpandChange = { webAssistExpanded = it },
                onShowWebChange = { webAssistShowWeb = it },
                onDismiss = viewModel::cancelWebResolution,
                onSourcesAvailable = { viewModel.updateWebSources(request, it) },
                onPlay = { target, _, headers, actualUserAgent, mimeType ->
                    viewModel.completeWebPage(request, target, mimeType, headers, actualUserAgent)
                }
            )
        }
    }
}

/** 起播覆盖层和播放中缓冲共用，缓冲时保留下面的视频画面与控制条。 */
@Composable
private fun PlayerLoadingOverlay(
    message: String,
    onBack: (() -> Unit)? = null,
    onAssist: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(color = Color.White)
            Text(message, color = Color.White, fontSize = 14.sp)
            if (onAssist != null) {
                OutlinedButton(
                    onClick = onAssist,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(stringResource(R.string.web_episode_assist), fontSize = 12.sp)
                }
            }
        }
        if (onBack != null) {
            IconButton(onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)) {
                Icon(painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                    contentDescription = stringResource(R.string.common_back), tint = Color.White)
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun PlayerViewContainer(
    viewModel: PlayerViewModel,
    videoScale: Int,
    modifier: Modifier = Modifier
) {
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx)
                .inflate(R.layout.player_view, null, false) as PlayerView).apply {
                // API 34 上 SurfaceView 在 Compose AndroidView 内可能丢失尺寸事务（画面拉伸/裁切），
                // media3 提供了官方 workaround（更高版本系统已修复，仅 API 34 生效）
                setEnableComposeSurfaceSyncWorkaround(true)
                player = viewModel.player
            }.also { playerView = it }
        },
        update = { view ->
            if (view.player !== viewModel.player) view.player = viewModel.player
            view.resizeMode = when (videoScale) {
                1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )

    // 特效字幕（ass-media 调用 setVideoEffects）会让 media3 停止上报 VideoSize，
    // PlayerView 的内容框比例保持为 0，画面被拉伸填满任意容器（全屏与旋转时尤其明显）。
    // 这里退回当前视频轨的 Format 计算显示比例，比例正确后全屏/竖屏过渡按原比例留黑边。
    val player = viewModel.player
    val view = playerView
    if (view != null) {
        DisposableEffect(view, player) {
            val listener = object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) =
                    applyVideoAspectFallback(view, player)

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) =
                    applyVideoAspectFallback(view, player)
            }
            player.addListener(listener)
            applyVideoAspectFallback(view, player)
            onDispose { player.removeListener(listener) }
        }
    }

    // 暂停时固定 SurfaceView 缓冲区：特效字幕管线暂停不重绘，
    // 布局变化（进/出全屏）若让缓冲区跟随尺寸，会裁切/偏移最后一帧
    val surfaceView = playerView?.videoSurfaceView as? android.view.SurfaceView
    if (surfaceView != null) {
        DisposableEffect(surfaceView, player, videoScale) {
            val keeper = VideoSurfaceBuffer(surfaceView, player) { videoScale == 0 }
            keeper.refresh()
            onDispose { keeper.release() }
        }
    }
}

/**
 * 特效字幕管线下 PlayerView 收不到 VideoSize，内容框比例会停在 0（画面填满容器被拉伸）。
 * 用当前选中视频轨的 Format 计算显示宽高比补上；media3 正常上报尺寸时不做处理。
 */
@androidx.media3.common.util.UnstableApi
private fun applyVideoAspectFallback(view: PlayerView, player: Player) {
    if (player.videoSize.width > 0 && player.videoSize.height > 0) return
    val group = player.currentTracks.groups.firstOrNull {
        it.type == C.TRACK_TYPE_VIDEO && it.isSelected
    } ?: return
    val index = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
    val format = group.getTrackFormat(index)
    if (format.width <= 0 || format.height <= 0) return
    val quarterTurn = format.rotationDegrees % 180 != 0
    val displayWidth = if (quarterTurn) format.height else format.width
    val displayHeight = if (quarterTurn) format.width else format.height
    val pixelRatio = format.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
    val contentFrame = view.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(
        androidx.media3.ui.R.id.exo_content_frame
    ) ?: return
    val aspect = displayWidth * pixelRatio / displayHeight
    contentFrame.setAspectRatio(aspect)
}

@Composable
private fun TopControlBar(
    title: String?,
    isFullscreen: Boolean,
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
private fun BottomControlBar(
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
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = if (playWhenReady) rememberAppIconPainter(AppIcon.PAUSE) else rememberAppIconPainter(AppIcon.PLAY_ARROW),
                        contentDescription = if (playWhenReady) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Next episode button (only if available)
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

            // 小窗口的倍速与字幕入口位于下方详情区。
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

            // 字幕轨选择（无字幕轨时面板显示空提示）
            if (isFullscreen) IconButton(onClick = onSelectSubtitles) {
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
    val primaryColor = progressColor

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
private fun ErrorOverlay(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(if (compact) 12.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.ERROR),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(if (compact) 24.dp else 48.dp)
        )

        Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))

        Text(
            text = error,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(if (compact) 8.dp else 24.dp))

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
    // 长按开始直接是紧凑提示，不再有放大版；持续一会儿后上移并淡掉底色，
    // 只留图标和倍速文字，减少对画面的遮挡
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
