package com.aiexile.animetrack.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.player.web.PendingWebPlayback
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.player.components.BottomControlBar
import com.aiexile.animetrack.ui.player.components.DetailsSheetType
import com.aiexile.animetrack.ui.player.components.EmbeddedPlayerControls
import com.aiexile.animetrack.ui.player.components.EmptyMediaState
import com.aiexile.animetrack.ui.player.components.EpisodeSelectionContent
import com.aiexile.animetrack.ui.player.components.ErrorOverlay
import com.aiexile.animetrack.ui.player.components.PlayerDetailsContent
import com.aiexile.animetrack.ui.player.components.PlayerDrawerHost
import com.aiexile.animetrack.ui.player.components.PlayerDrawerLevel
import com.aiexile.animetrack.ui.player.components.PlayerGestureLayer
import com.aiexile.animetrack.ui.player.components.PlayerLoadingOverlay
import com.aiexile.animetrack.ui.player.components.PlayerMenuGridItem
import com.aiexile.animetrack.ui.player.components.PlayerMenuItem
import com.aiexile.animetrack.ui.player.components.PlayerMenuItemToggle
import com.aiexile.animetrack.ui.player.components.PlayerMenuSectionTitle
import com.aiexile.animetrack.ui.player.components.PlayerOptionSheet
import com.aiexile.animetrack.ui.player.components.PlayerPageLayout
import com.aiexile.animetrack.ui.player.components.PlayerQualityContent
import com.aiexile.animetrack.ui.player.components.PlayerSelectionContent
import com.aiexile.animetrack.ui.player.components.PlayerVideoPlaceholder
import com.aiexile.animetrack.ui.player.components.PlayerViewContainer
import com.aiexile.animetrack.ui.player.components.playerQualityLabel
import com.aiexile.animetrack.ui.player.components.TopControlBar
import com.aiexile.animetrack.ui.player.components.TrackSelectionContent
import com.aiexile.animetrack.ui.player.components.VideoCornerRadius
import com.aiexile.animetrack.ui.player.web.WebMediaSnifferOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerScreen(
    animeId: Int,
    initialLocalUri: String? = null,
    initialLocalTitle: String? = null,
    initialLocalMediaId: Long? = null,
    initialWebPlay: Boolean = false,
    /** 共享元素命名空间：与详情页封面 key 保持一致，小窗视频卡按同一 key 做 Morph。
     *  null 表示不是从详情页进入，不做共享元素 */
    sharedKeyPrefix: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    onBrowseWebDAV: () -> Unit,
    onSelectLocalFile: () -> Unit = {},
    /** 身份卡点击回到番剧详情页；animeId 为 0（本地文件/全局网页）时不会触发 */
    onOpenAnimeDetail: () -> Unit = {},
    navController: NavController,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory(animeId))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trackedAnime by viewModel.trackedAnime.collectAsStateWithLifecycle()
    val webParseRequest by viewModel.webParseRequest.collectAsStateWithLifecycle()
    var webAssistExpanded by remember(webParseRequest) { mutableStateOf(false) }
    var webAssistShowWeb by remember(webParseRequest) { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // 路由已带视频时，首帧等待 LaunchedEffect 交接请求，不能先展示选源界面。
    var initialRequestHandled by remember(
        initialLocalUri, initialLocalTitle, initialLocalMediaId, initialWebPlay
    ) { mutableStateOf(false) }

    LaunchedEffect(initialLocalUri, initialLocalTitle, initialLocalMediaId, initialWebPlay) {
        when {
            initialWebPlay -> PendingWebPlayback.consume()?.let {
                if (it.resolvePage) viewModel.playWebPage(it) else viewModel.playWeb(
                    it.url, it.mimeType, it.title, it.pageUrl, it.headers, it.userAgent, it.referer,
                    it.queue, it.animeId, it.animeTitle
                )
            }
            initialLocalMediaId != null -> viewModel.playLocalMedia(initialLocalMediaId)
            initialLocalUri != null -> viewModel.playLocalUri(Uri.parse(initialLocalUri), initialLocalTitle)
        }
        initialRequestHandled = true
    }

    val localFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.playLocalUri(it) }
    }

    // 用 StateFlow 单订阅：LiveData.observe 会随 LaunchedEffect 重启叠加多个 observer
    val navBackStackEntry = navController.currentBackStackEntryAsState()
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
    var controlsBarHeightPx by remember { mutableIntStateOf(0) }
    val controlsBarHeight = with(LocalDensity.current) { controlsBarHeightPx.toDp() }

    var drawerStack by remember { mutableStateOf<List<PlayerDrawerLevel>>(emptyList()) }
    val drawerLevel = drawerStack.lastOrNull()
    var detailsSheetType by remember { mutableStateOf<DetailsSheetType?>(null) }
    val detailsGridState = rememberLazyGridState()
    val hasChildDrawer = drawerStack.size > 1
    val anyPanelOpen = drawerLevel != null || detailsSheetType != null

    val openOptionsMenu: () -> Unit = {
        if (uiState.isFullscreen) drawerStack = listOf(PlayerDrawerLevel.Menu)
        else detailsSheetType = DetailsSheetType.MORE
    }
    val openQuality: () -> Unit = {
        if (uiState.isFullscreen) drawerStack = listOf(PlayerDrawerLevel.Quality)
        else detailsSheetType = DetailsSheetType.QUALITY
    }
    val openSpeed: () -> Unit = {
        if (uiState.isFullscreen) drawerStack = listOf(PlayerDrawerLevel.Speed)
        else detailsSheetType = DetailsSheetType.SPEED
    }
    val openSubtitles: () -> Unit = {
        if (uiState.isFullscreen) drawerStack = listOf(PlayerDrawerLevel.Tracks(C.TRACK_TYPE_TEXT))
        else detailsSheetType = DetailsSheetType.SUBTITLES
    }

    LaunchedEffect(uiState.notice) {
        uiState.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearNotice()
        }
    }

    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var isSeekDragging by remember { mutableStateOf(false) }
    var seekDragPositionMs by remember { mutableLongStateOf(0L) }

    fun resetControlsTimer() {
        controlsHideJob?.cancel()
        controlsHideJob = coroutineScope.launch {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(uiState.isPlaying) {
        if (anyPanelOpen) return@LaunchedEffect
        showControls = true
        resetControlsTimer()
    }

    LaunchedEffect(anyPanelOpen) {
        if (anyPanelOpen) {
            controlsHideJob?.cancel()
            showControls = false
        }
    }

    val insetsController = activity?.let {
        WindowCompat.getInsetsController(it.window, it.window.decorView)
    }
    val configuration = LocalConfiguration.current
    var exitFullscreenPending by remember { mutableStateOf(false) }

    fun requestFullscreenToggle() {
        if (!uiState.isFullscreen) {
            if (exitFullscreenPending) return
            viewModel.setFullscreen(true)
            return
        }
        if (exitFullscreenPending) return
        exitFullscreenPending = true
        insetsController?.show(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    LaunchedEffect(exitFullscreenPending, configuration.orientation) {
        if (!exitFullscreenPending) return@LaunchedEffect
        if (configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return@LaunchedEffect
        exitFullscreenPending = false
        viewModel.setFullscreen(false)
    }

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
            exitFullscreenPending -> Unit
            uiState.isFullscreen -> requestFullscreenToggle()
            else -> onBack()
        }
    }

    BackHandler(uiState.isFullscreen && !anyPanelOpen && !webAssistExpanded) {
        requestFullscreenToggle()
    }
    BackHandler(anyPanelOpen) {
        drawerStack = if (hasChildDrawer) drawerStack.dropLast(1) else emptyList()
        detailsSheetType = null
    }

    LaunchedEffect(uiState.isFullscreen) {
        showControls = true
        resetControlsTimer()
        if (uiState.isFullscreen) {
            detailsSheetType = null
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = if (uiState.fullscreenLandscape)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val videoCornerShape = remember { SquircleShape(VideoCornerRadius) }
    val pageTitle = uiState.seriesTitle?.takeIf { it.isNotBlank() }
        ?: uiState.mediaTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_playback)

    Box(Modifier.fillMaxSize()) {        PlayerPageLayout(
            isFullscreen = uiState.isFullscreen,
            title = pageTitle,
            animeId = animeId,
            sharedKeyPrefix = sharedKeyPrefix,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onBack = onBack,
            video = { isCoverMorphing ->
                val cardShape = if (uiState.isFullscreen) RectangleShape else videoCornerShape
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(color = Color.Black, shape = cardShape)
                        .clip(cardShape)
                ) {
                    PlayerVideoPlaceholder(
                        coverUrl = trackedAnime?.coverUrl,
                        visible = uiState.error == null && (
                            !uiState.isControllerReady || !initialRequestHandled ||
                                hasPendingWebDavRequest || uiState.isPreparingMedia ||
                                isCoverMorphing
                            ),
                        shape = cardShape,
                        instantEnter = isCoverMorphing
                    )

                    val failure = uiState.error
                    if (failure != null) {
                        ErrorOverlay(
                            error = failure.httpStatus?.let { stringResource(failure.kind.messageRes, it) }
                                ?: stringResource(failure.kind.messageRes),
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.player_select_source), color = Color.White)
                        }
                    } else {
                        PlayerViewContainer(
                            player = viewModel.player,
                            videoScale = uiState.videoScale,
                            hideSurface = isCoverMorphing || (animatedVisibilityScope?.transition?.let {
                                it.currentState != it.targetState
                            } == true),
                            modifier = Modifier.fillMaxSize()
                        )

                        PlayerGestureLayer(
                            currentPositionMs = uiState.currentPositionMs,
                            durationMs = uiState.durationMs,
                            playbackSpeed = uiState.playbackSpeed,
                            isLongPressSpeedActive = uiState.isLongPressSpeedActive,
                            seekDeltaMs = seekDeltaMs,
                            onSeekDeltaChange = { seekDeltaMs = it },
                            onToggleControls = {
                                showControls = !showControls
                                if (showControls) resetControlsTimer()
                            },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onStartLongPressSpeed = viewModel::startLongPressSpeed,
                            onStopLongPressSpeed = viewModel::stopLongPressSpeed,
                            onSeekTo = viewModel::seekTo
                        )

                        if (uiState.isBuffering && uiState.playWhenReady && !isSeekDragging) {
                            PlayerLoadingOverlay(message = stringResource(R.string.player_buffering))
                        }

                        if (!uiState.playWhenReady && !uiState.isPreparingMedia &&
                            !uiState.isBuffering && uiState.error == null
                        ) {
                            val pausedIndicatorBottom by animateDpAsState(
                                targetValue = when {
                                    !showControls -> 12.dp
                                    controlsBarHeightPx == 0 -> 40.dp
                                    else -> controlsBarHeight - 16.dp
                                },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "pausedIndicatorBottomPadding"
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 12.dp, bottom = pausedIndicatorBottom)
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
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showControls && uiState.isFullscreen,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                TopControlBar(
                                    title = uiState.mediaTitle,
                                    isFullscreen = uiState.isFullscreen,
                                    onBack = handleBack,
                                    onSelectSubtitles = {
                                        openSubtitles()
                                        resetControlsTimer()
                                    },
                                    onSkipForward = {
                                        viewModel.seekTo(uiState.currentPositionMs + 85_000L)
                                        resetControlsTimer()
                                    },
                                    onOpenMoreMenu = { drawerStack = listOf(PlayerDrawerLevel.Menu) },
                                    modifier = Modifier.align(Alignment.TopStart)
                                )

                                BottomControlBar(
                                    currentPositionMs = if (isSeekDragging) seekDragPositionMs else uiState.currentPositionMs,
                                    durationMs = uiState.durationMs,
                                    bufferedPositionMs = uiState.bufferedPositionMs,
                                    playbackSpeed = uiState.playbackSpeed,
                                    qualityLabel = playerQualityLabel(viewModel.player),
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
                                        controlsHideJob?.cancel()
                                        seekDragPositionMs = uiState.currentPositionMs
                                    },
                                    onSeekDrag = { ratio ->
                                        seekDragPositionMs = (uiState.durationMs * ratio).toLong()
                                    },
                                    onSeekDragEnd = { isSeekDragging = false },
                                    onTogglePlayPause = {
                                        viewModel.togglePlayPause()
                                        resetControlsTimer()
                                    },
                                    onNextEpisode = {
                                        viewModel.seekToNext()
                                        resetControlsTimer()
                                    },
                                    onOpenQuality = {
                                        openQuality()
                                        resetControlsTimer()
                                    },
                                    onToggleFullscreen = {
                                        requestFullscreenToggle()
                                        resetControlsTimer()
                                    },
                                    onBarHeightChanged = { controlsBarHeightPx = it },
                                    modifier = Modifier.align(Alignment.BottomStart)
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
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onToggleFullscreen = { requestFullscreenToggle() },
                            onSeekToRatio = viewModel::seekToPositionRatio,
                            onSeekDragStart = {
                                isSeekDragging = true
                                controlsHideJob?.cancel()
                                seekDragPositionMs = uiState.currentPositionMs
                            },
                            onSeekDrag = { ratio -> seekDragPositionMs = (uiState.durationMs * ratio).toLong() },
                            onSeekDragEnd = { isSeekDragging = false },
                            onBarHeightChanged = { controlsBarHeightPx = it }
                        )
                    }
                }
            },
            details = {
                if (initialRequestHandled && uiState.isControllerReady &&
                    !hasPendingWebDavRequest && !uiState.hasPlaybackRequest
                ) {
                    EmptyMediaState(
                        onBrowseWebDAV = onBrowseWebDAV,
                        onSelectLocalFile = { localFileLauncher.launch(arrayOf("video/*")) }
                    )
                } else {
                    PlayerDetailsContent(
                        state = uiState,
                        anime = trackedAnime,
                        gridState = detailsGridState,
                        onSelectEpisode = viewModel::selectEpisode,
                        onSelectChannel = viewModel::selectWebChannel,
                        onOpenQuality = openQuality,
                        onOpenSpeed = openSpeed,
                        onOpenSubtitles = openSubtitles,
                        onOpenSettings = openOptionsMenu,
                        onOpenAnimeDetail = if (animeId > 0) onOpenAnimeDetail else null
                    )
                }
            }
        )

        drawerLevel?.let { level ->
            PlayerDrawerHost(
                level = level,
                onDismissed = { drawerStack = emptyList() },
                onStepBack = if (hasChildDrawer) {
                    { drawerStack = drawerStack.dropLast(1) }
                } else null
            ) { target, requestClose ->
                when (target) {
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
                        PlayerMenuItemToggle(
                            rememberAppIconPainter(AppIcon.SKIP_NEXT),
                            stringResource(R.string.player_settings_auto_play_next),
                            uiState.autoPlayNext,
                            viewModel::setAutoPlayNext
                        )
                        PlayerMenuItemToggle(
                            rememberAppIconPainter(AppIcon.HISTORY),
                            stringResource(R.string.player_settings_remember_position),
                            uiState.rememberPosition,
                            viewModel::setRememberPosition
                        )
                        PlayerMenuItem(
                            rememberAppIconPainter(AppIcon.REPLAY),
                            stringResource(if (uiState.isWebPlayback) R.string.web_playback_reparse else R.string.common_retry),
                            {
                                requestClose()
                                viewModel.retryLast()
                            }
                        )
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

        if (detailsSheetType != null && !uiState.isFullscreen) {
            PlayerOptionSheet(
                type = detailsSheetType!!,
                state = uiState,
                player = if (uiState.isControllerReady) viewModel.player else null,
                onDismiss = { detailsSheetType = null },
                onSelectSource = viewModel::selectWebSource,
                onSetSpeed = viewModel::setPlaybackSpeed,
                onSetScale = { viewModel.setVideoScale((uiState.videoScale + 1) % 3) },
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