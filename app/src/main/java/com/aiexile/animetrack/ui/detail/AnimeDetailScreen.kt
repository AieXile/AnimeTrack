package com.aiexile.animetrack.ui.detail

import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.isExpandedWidth
import com.aiexile.animetrack.ui.components.rememberSharedCoverClipShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.net.Uri
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.di.TopNoticeType
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayRequest
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayResult
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayerId
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayerLauncher
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.ui.components.AirDateEditor
import com.aiexile.animetrack.ui.components.EmptyCoverPlaceholder
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.liquidglass.DampedDragAnimation
import com.aiexile.animetrack.ui.components.dialogBlurBehind
import com.aiexile.animetrack.ui.components.rememberShrinkOnScrollTopAppBarBehavior
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.util.coverMemoryCacheKey
import com.aiexile.animetrack.util.formatAirDateDisplay
import com.aiexile.animetrack.util.isUnaired
import com.aiexile.animetrack.util.resolveCoverModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CoverAspectRatio = 2f / 3f
private val CardCornerRadius = 16.dp

@Composable
private fun cardContainerColor() = MaterialTheme.colorScheme.surfaceContainerLowest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimeDetailScreen(
    animeId: Int,
    coverUrl: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit = {},
    isInPane: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: AnimeDetailViewModel = viewModel(
        key = "anime_detail_$animeId",
        factory = AnimeDetailViewModel.Factory(animeId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editState = uiState.editState
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val settingsRepository = remember { com.aiexile.animetrack.di.AppContainer.getSettingsRepository() }
    val shareButtonEnabled by settingsRepository.shareButtonEnabled.collectAsState(initial = false)
    // 外部播放器 Animeko 跳转按钮可见性（开发者选项控制，默认隐藏）
    val externalPlayerEntryVisible by settingsRepository.externalPlayerEntryVisible.collectAsState(initial = false)

    // 浏览下方内容时顶栏收缩变窄（64dp → 32dp），内容滚回顶部后才展开
    val topBarScrollBehavior = rememberShrinkOnScrollTopAppBarBehavior(collapsedHeight = 32.dp)

    // 标题跟随滚动：跟踪内容标题底边与顶栏底边（窗口坐标 px），
    // 标题滚出顶栏下沿后，顶栏标题淡入接管显示；滚回时反向消失
    val topBarBottomPx = remember { mutableFloatStateOf(0f) }
    val contentTitleBottomPx = remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val isContentTitleVisible by remember {
        derivedStateOf { contentTitleBottomPx.floatValue > topBarBottomPx.floatValue }
    }

    val showMatchDialog by viewModel.showMatchDialog.collectAsState()
    val matchSearchQuery by viewModel.matchSearchQuery.collectAsState()
    val matchSearchState by viewModel.matchSearchState.collectAsState()
    val showBangumiHint by viewModel.showBangumiHint.collectAsState()

    // 匹配按钮在 window 中的位置（供引导遮罩挖洞定位），按钮组合完成后回调
    var matchButtonBounds by remember { mutableStateOf<Rect?>(null) }

    // 引导遮罩展示时优先拦截返回键：先关遮罩（并标记已提示），不直接退出详情页
    BackHandler(enabled = showBangumiHint) {
        viewModel.dismissBangumiHint()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateEditLocalCoverUri(it.toString()) }
    }

    LaunchedEffect(uiState.showCompletedToast) {
        if (uiState.showCompletedToast) {
            // 完结庆祝横幅：随机附一条内置庆祝语（不走网络，即时出现）
            AppContainer.showTopNotice(
                TopNoticeType.CELEBRATION,
                context.getString(R.string.detail_completed_celebration),
                context.resources.getStringArray(R.array.completed_celebration_lines).random()
            )
            viewModel.dismissCompletedToast()
        }
    }

    LaunchedEffect(uiState.showDuplicateToast) {
        if (uiState.showDuplicateToast) {
            Toast.makeText(context, context.getString(R.string.home_anime_already_exists), Toast.LENGTH_SHORT).show()
            viewModel.dismissDuplicateToast()
        }
    }

    BackHandler(enabled = uiState.coverSearch.isVisible || editState.isEditing) {
        when {
            uiState.coverSearch.isVisible -> viewModel.hideCoverSearch()
            editState.isEditingTitle -> viewModel.setEditingTitle(false)
            editState.isEditing -> {
                if (viewModel.hasUnsavedChanges()) {
                    showDiscardDialog = true
                } else {
                    viewModel.exitEditMode()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val detailTopBarTitle: @Composable () -> Unit = {
            // 内容标题滚出可视区后，顶栏标题淡入上移出现；过长单行省略。
            // 标题常驻布局（透明度 0 时不可见），仅用 graphicsLayer 做动画，
            // 并裁剪在自身边界内——动画过程中不会画到返回按钮区域
            val topBarTitle = uiState.anime?.let { anime ->
                if (editState.isEditing) editState.title else anime.title
            }
            val titleVisible = !isContentTitleVisible && topBarTitle != null
            val titleAlpha by animateFloatAsState(
                targetValue = if (titleVisible) 1f else 0f,
                animationSpec = tween(200),
                label = "top_bar_title_alpha"
            )
            val density = LocalDensity.current
            val titleSlidePx by animateFloatAsState(
                targetValue = if (titleVisible) 0f else with(density) { 4.dp.toPx() },
                animationSpec = tween(200),
                label = "top_bar_title_slide"
            )
            Text(
                text = topBarTitle.orEmpty(),
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    // 常驻布局下 title 槽左端从导航图标右缘起算且带默认
                    // start padding；用 layout 平移收紧（视觉内缩，不越界）
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(x = -8.dp.roundToPx(), y = 0)
                        }
                    }
                    .clipToBounds()
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = titleSlidePx
                    }
            )
        }
        val detailTopBarActions: @Composable RowScope.() -> Unit = {
            if (editState.isEditing) {
                TextButton(onClick = {
                    viewModel.saveEditChanges()
                    focusManager.clearFocus()
                }) {
                    Text(stringResource(R.string.common_save), color = MaterialTheme.colorScheme.primary)
                }
            } else if (uiState.anime != null) {
                val anime = uiState.anime!!
                // 播放按钮暂时隐藏，以后复用
                // IconButton(onClick = { onNavigateToPlayer(animeId) }) {
                //     Icon(
                //         painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
                //         contentDescription = stringResource(R.string.detail_play),
                //         tint = MaterialTheme.colorScheme.primary
                //     )
                // }
                // bangumiId<=0（历史脏数据）与 null 同样视为未关联，保证引导遮罩触发时按钮必然可见
                val missingBangumi = anime.bangumiId == null || anime.bangumiId <= 0
                val missingTmdb = anime.tmdbId == null
                if (missingBangumi || missingTmdb) {
                    IconButton(
                        onClick = { viewModel.showMatchDialog() },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            matchButtonBounds = coordinates.boundsInWindow()
                        }
                    ) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.LINK),
                            contentDescription = stringResource(R.string.detail_match_source),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (externalPlayerEntryVisible) {
                    IconButton(onClick = {
                        // totalEpisodes<=0 视为未知(null)：continueWatching 语义为“恒给下一集”，
                        // 直接传 0 会被误判为“已看完”而回退详情页级跳转
                        val request = ExternalPlayRequest.continueWatching(
                            animeId = anime.id,
                            bangumiId = anime.bangumiId,
                            title = anime.title,
                            watchedEpisodes = anime.watchedEpisodes,
                            totalEpisodes = anime.totalEpisodes.takeIf { it > 0 }
                        )
                        val result = ExternalPlayerLauncher.launch(
                            context, request, ExternalPlayerId.ANIMEKO
                        )
                        // 未安装/唤起失败已由便捷入口自动 Toast+降级；未关联需调用方提示
                        if (result is ExternalPlayResult.MissingBangumiLink) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.external_player_missing_link),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
                            contentDescription = stringResource(R.string.external_player_watch_in_animeko),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (shareButtonEnabled) {
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.SHARE),
                            contentDescription = stringResource(R.string.common_share),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.DELETE),
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = { viewModel.enterEditMode() }) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.EDIT),
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        AppScaffold(
            title = "",
            onBack = {
                if (editState.isEditing) {
                    if (viewModel.hasUnsavedChanges()) {
                        showDiscardDialog = true
                    } else {
                        viewModel.exitEditMode()
                    }
                } else {
                    onNavigateBack()
                }
            },
            titleContent = detailTopBarTitle,
            actions = detailTopBarActions,
            topBarModifier = Modifier.onGloballyPositioned { coordinates ->
                topBarBottomPx.floatValue = coordinates.boundsInWindow().bottom
            },
            scrollBehavior = topBarScrollBehavior
        ) { chrome ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(chrome.layout)
                    .then(
                        // 玻璃档顶栏不收缩：不挂 scrollBehavior 的 nestedScroll，
                        // 否则内容滚动被顶栏行为消费却没有对应视觉位移
                        if (chrome.style == ChromeStyle.STANDARD) {
                            Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
                        } else {
                            Modifier
                        }
                    )
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    uiState.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.error ?: stringResource(R.string.detail_unknown_error),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    uiState.anime != null -> {
                        AnimeDetailContent(
                            anime = uiState.anime!!,
                            isFetchingDetail = uiState.isFetchingDetail,
                            detailFetchError = uiState.detailFetchError,
                            notesText = uiState.notesText,
                            isEditingNotes = uiState.isEditingNotes,
                            airStatusText = uiState.airStatusText,
                            onNotesChange = { viewModel.updateNotes(it) },
                            onSaveNotes = { viewModel.saveNotes() },
                            onEditNotes = { viewModel.setEditingNotes(true) },
                            onUpdateWatchedEpisodes = { viewModel.updateWatchedEpisodes(it) },
                            onAdjustWatchedEpisodes = { viewModel.adjustWatchedEpisodes(it) },
                            onStatusChange = { viewModel.updateStatus(it) },
                            onFinishDateChange = { viewModel.updateFinishDate(it) },
                            editState = editState,
                            onEditCoverSearch = { viewModel.showCoverSearch() },
                            onEditCoverUpload = { imagePickerLauncher.launch("image/*") },
                            onEditCoverSave = {
                                val coverUrl = editState.localCoverUri ?: editState.coverUrl ?: uiState.anime?.coverUrl
                                if (coverUrl != null) {
                                    scope.launch { saveCoverToGallery(context, coverUrl) }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.detail_no_cover_to_save), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEditTitleChange = { viewModel.updateEditTitle(it) },
                            onEditTitleStart = { viewModel.setEditingTitle(true) },
                            onEditTitleDone = { viewModel.setEditingTitle(false) },
                            onTitlePositionChanged = { contentTitleBottomPx.floatValue = it },
                            onEditAirWeekdayChange = { viewModel.updateEditAirWeekday(it) },
                            onEditAirDateChange = { viewModel.updateEditAirDate(it) },
                            onUpdateEditTotalEpisodes = { viewModel.updateEditTotalEpisodes(it) },
                            onAdjustEditTotalEpisodes = { viewModel.adjustEditTotalEpisodes(it) },
                            onEditSummaryChange = { viewModel.updateEditSummary(it) },
                            onEditAiringStatusOverrideChange = { viewModel.updateEditAiringStatusOverride(it) },
                            isInPane = isInPane,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            // 玻璃档内容视口全高，顶/底内边距由滚动内容自垫（标准档为零）
                            chromeContentPadding = chrome.content
                        )
                    }
                }
            }
        }

        if (uiState.coverSearch.isVisible) {
            CoverSearchOverlay(
                query = uiState.coverSearch.query,
                results = uiState.coverSearch.results,
                isSearching = uiState.coverSearch.isSearching,
                error = uiState.coverSearch.error,
                searchSource = uiState.coverSearch.source,
                onQueryChange = { viewModel.updateCoverSearchQuery(it) },
                onSearch = { viewModel.searchCover() },
                onSelectResult = { viewModel.selectCoverResult(it) },
                onSourceChange = { viewModel.updateCoverSearchSource(it) },
                onDismiss = { viewModel.hideCoverSearch() }
            )
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                modifier = Modifier.dialogBlurBehind(),
                shape = SquircleShape(24.dp),
                title = { Text(stringResource(R.string.detail_discard_changes)) },
                text = { Text(stringResource(R.string.detail_discard_changes_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        viewModel.exitEditMode()
                    }) { Text(stringResource(R.string.detail_discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                modifier = Modifier.dialogBlurBehind(),
                shape = SquircleShape(24.dp),
                title = { Text(stringResource(R.string.detail_delete_anime)) },
                text = { Text(stringResource(R.string.detail_delete_confirm_format, uiState.anime?.title ?: "")) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAnime()
                        onNavigateBack()
                    }) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }

        if (showShareDialog && uiState.anime != null) {
            ShareNotesDialog(
                initialNotes = uiState.anime!!.notes,
                onConfirm = { notes ->
                    showShareDialog = false
                    viewModel.shareAnime(context, notes)
                },
                onDismiss = { showShareDialog = false }
            )
        }

        if (showMatchDialog) {
            val missingSource = viewModel.missingSearchSource
            MatchDialog(
                searchQuery = matchSearchQuery,
                searchState = matchSearchState,
                searchSource = missingSource,
                onQueryChange = { viewModel.updateMatchSearchQuery(it) },
                onSearch = { viewModel.searchForMatch() },
                onSelectResult = { viewModel.selectMatchResult(it) },
                onDismiss = { viewModel.hideMatchDialog() }
            )
        }

        // 「Bangumi 未关联」引导遮罩：bangumiId 为空/为 0 且未提示过时展示（仅一次）
        BangumiMatchHintOverlay(
            visible = showBangumiHint,
            targetBounds = matchButtonBounds,
            onDismiss = { viewModel.dismissBangumiHint() },
            onMatchClick = {
                viewModel.dismissBangumiHint()
                viewModel.showMatchDialog()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSearchOverlay(
    query: String,
    results: List<SearchResult>,
    isSearching: Boolean,
    error: String?,
    searchSource: SearchSource,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (SearchResult) -> Unit,
    onSourceChange: (SearchSource) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    val allText = stringResource(R.string.common_all)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.detail_search_anime_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    shape = SquircleShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    leadingIcon = {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.SEARCH),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier
                                    .clip(SquircleShape(12.dp))
                                    .clickable { sourceExpanded = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = when (searchSource) {
                                        SearchSource.BANGUMI -> "Bangumi"
                                        SearchSource.TMDB -> "TMDB"
                                        SearchSource.ALL -> allText
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    painter = rememberAppIconPainter(AppIcon.ARROW_DROP_DOWN),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = sourceExpanded,
                                onDismissRequest = { sourceExpanded = false },
                                shape = SquircleShape(12.dp)
                            ) {
                                SearchSource.entries.forEach { source ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (source) {
                                                    SearchSource.BANGUMI -> "Bangumi"
                                                    SearchSource.TMDB -> "TMDB"
                                                    SearchSource.ALL -> allText
                                                },
                                                fontSize = 14.sp,
                                                fontWeight = if (source == searchSource) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (source == searchSource) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onSourceChange(source)
                                            sourceExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                IconButton(onClick = onSearch) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.SEARCH),
                        contentDescription = stringResource(R.string.common_search),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.CLOSE),
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.5.dp)
                        .clip(SquircleShape(1.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { "${it.source}_${it.sourceId}" }) { result ->
                        CoverSearchResultItem(
                            result = result,
                            onClick = { onSelectResult(result) }
                        )
                    }
                }
            } else if (!isSearching && query.isNotBlank() && error == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.detail_click_to_search_cover),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverSearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SquircleShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resolveCoverModel(result.coverUrl),
                contentDescription = result.title,
                modifier = Modifier
                    .size(52.dp, 70.dp)
                    .clip(SquircleShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.episodeCountText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val rating = result.rating
                    if (rating != null && rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.STAR_SHINE),
                                contentDescription = null,
                                tint = LocalAnimeColors.current.starFilled,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                fontSize = 12.sp,
                                color = LocalAnimeColors.current.starFilled
                            )
                        }
                    }
                    Text(
                        text = when (result.source) {
                            SearchSource.BANGUMI -> "Bangumi"
                            SearchSource.TMDB -> "TMDB"
                            SearchSource.ALL -> stringResource(R.string.common_all)
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnimeDetailContent(
    anime: Anime,
    isFetchingDetail: Boolean,
    detailFetchError: String? = null,
    notesText: String,
    isEditingNotes: Boolean,
    airStatusText: String?,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onEditNotes: () -> Unit,
    onUpdateWatchedEpisodes: (Int) -> Unit,
    onAdjustWatchedEpisodes: (Int) -> Unit,
    onStatusChange: (AnimeStatus) -> Unit,
    onFinishDateChange: (Long?) -> Unit,
    editState: EditState = EditState(),
    onEditCoverSearch: () -> Unit = {},
    onEditCoverUpload: () -> Unit = {},
    onEditCoverSave: () -> Unit = {},
    onEditTitleChange: (String) -> Unit = {},
    onEditTitleStart: () -> Unit = {},
    onEditTitleDone: () -> Unit = {},
    // 标题底边（窗口坐标 px）变化时回调，供顶栏判断标题是否滚出可视区
    onTitlePositionChanged: (Float) -> Unit = {},
    onEditAirWeekdayChange: (Int?) -> Unit = {},
    onEditAirDateChange: (String?) -> Unit = {},
    onUpdateEditTotalEpisodes: (Int) -> Unit = {},
    onAdjustEditTotalEpisodes: (Int) -> Unit = {},
    onEditSummaryChange: (String) -> Unit = {},
    onEditAiringStatusOverrideChange: (Boolean?) -> Unit = {},
    isInPane: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** 玻璃档需由滚动内容自垫的顶/底内边距；标准档为零 */
    chromeContentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    // 大屏适配：Expanded 宽度（>=840dp）且非 pane 模式采用双栏布局——左栏封面信息头 + 简介，
    // 右栏进度 / 笔记 / 状态，两栏独立滚动。pane 模式（详情在右侧窄 pane 打开）强制单列，
    // 避免 pane 内再拆两列导致显示不全。分支不依赖编辑状态，
    // 避免编辑模式切换时子树结构重建与 SharedTransition 动画冲突。
    val isTwoPane = isExpandedWidth() && !isInPane

    // 左栏内容：封面信息头 + 简介（单栏/双栏共用）
    val headerSection: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = SquircleShape(CardCornerRadius),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val enableSharedCover = sharedTransitionScope != null && animatedVisibilityScope != null
                // 转场期间圆角随进度连续插值（与卡片侧同一形状曲线），
                // 避免底部圆角在两侧静态 clip 切换时 0↔16dp 硬跳变
                val sharedCoverClipShape = if (enableSharedCover) {
                    rememberSharedCoverClipShape(
                        animatedVisibilityScope = animatedVisibilityScope!!,
                        cornerRadius = CardCornerRadius,
                        detailSide = true
                    )
                } else {
                    SquircleShape(CardCornerRadius)
                }
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(2f / 3f)
                        .then(
                            if (enableSharedCover) {
                                with(sharedTransitionScope!!) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "cover_${anime.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope!!
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                        .clip(sharedCoverClipShape)
                ) {
                    val displayCoverUrl = if (editState.isEditing) editState.localCoverUri ?: editState.coverUrl else anime.coverUrl

                    val coverClipShape = SquircleShape(CardCornerRadius)
                    val context = LocalContext.current

                    if (editState.isEditing) {
                        Crossfade(
                            targetState = displayCoverUrl,
                            label = "cover_crossfade"
                        ) { url ->
                            if (url != null) {
                                val request = remember(url) {
                                    ImageRequest.Builder(context)
                                        .data(resolveCoverModel(url))
                                        .crossfade(false)
                                        .build()
                                }
                                AsyncImage(
                                    model = request,
                                    contentDescription = anime.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                EmptyCoverPlaceholder(
                                    shape = coverClipShape
                                )
                            }
                        }
                    } else {
                        if (displayCoverUrl != null) {
                            val request = remember(displayCoverUrl) {
                                ImageRequest.Builder(context)
                                    .data(resolveCoverModel(displayCoverUrl))
                                    .memoryCacheKey(coverMemoryCacheKey(displayCoverUrl))
                                    .placeholderMemoryCacheKey(coverMemoryCacheKey(displayCoverUrl))
                                    .crossfade(false)
                                    .build()
                            }
                            AsyncImage(
                                model = request,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            EmptyCoverPlaceholder(
                                shape = coverClipShape
                            )
                        }
                    }

                    if (editState.isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(coverClipShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = onEditCoverSave,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    painter = rememberAppIconPainter(AppIcon.SAVE),
                                    contentDescription = stringResource(R.string.detail_save_cover),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = onEditCoverSearch,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    painter = rememberAppIconPainter(AppIcon.SEARCH),
                                    contentDescription = stringResource(R.string.detail_search_cover),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = onEditCoverUpload,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    painter = rememberAppIconPainter(AppIcon.PHOTO_CAMERA),
                                    contentDescription = stringResource(R.string.detail_upload_cover),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = 0.dp, bottom = 0.dp)
                        .padding(end = 4.dp)
                ) {
                    var titleLineCount by remember(anime.id) { mutableIntStateOf(1) }
                    if (editState.isEditing) {
                        if (editState.isEditingTitle) {
                            val focusRequester = remember { FocusRequester() }
                            val keyboardController = LocalSoftwareKeyboardController.current

                            BasicTextField(
                                value = editState.title,
                                onValueChange = onEditTitleChange,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = false,
                                maxLines = 3,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        onEditTitleDone()
                                        keyboardController?.hide()
                                    }
                                )
                            )

                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = editState.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    painter = rememberAppIconPainter(AppIcon.EDIT),
                                    contentDescription = stringResource(R.string.detail_edit_title),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onEditTitleStart() },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Text(
                            text = anime.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { titleLineCount = it.lineCount },
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                onTitlePositionChanged(coordinates.boundsInWindow().bottom)
                            }
                        )
                    }

                    if (anime.rating != null && titleLineCount <= 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        RatingBadge(rating = anime.rating)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (editState.isEditing) {
                            AirDateEditor(
                                airDate = editState.airDate,
                                onAirDateChange = onEditAirDateChange
                            )
                        } else {
                            val airDateDisplay = formatAirDateDisplay(anime.airDate)
                            val airDateText = if (airDateDisplay != null) {
                                stringResource(R.string.detail_air_date_format, airDateDisplay)
                            } else {
                                stringResource(R.string.detail_air_date_undetermined)
                            }
                            Text(
                                text = airDateText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (anime.totalEpisodes > 0) {
                            Text(
                                text = stringResource(R.string.detail_total_episodes_format, anime.totalEpisodes),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (anime.currentEpisodes > 0) {
                            Text(
                                text = stringResource(R.string.detail_ongoing_with_eps_format, anime.currentEpisodes),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.detail_ongoing),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 编辑模式下使用 override 计算有效完结状态，确保选择「连载中」后立即显示每周更新
                        val effectiveIsFinished = when (editState.airingStatusOverride) {
                            true -> false
                            false -> true
                            null -> anime.isFinished
                        }
                        if (editState.isEditing) {
                            AiringStatusSelector(
                                override = editState.airingStatusOverride,
                                onOverrideChange = onEditAiringStatusOverrideChange
                            )
                            if (!effectiveIsFinished && anime.status != AnimeStatus.COMPLETED) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.detail_every_week),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    WeekdayChips(
                                        selected = editState.airWeekday,
                                        onSelect = onEditAirWeekdayChange
                                    )
                                }
                            }
                        } else if (!airStatusText.isNullOrBlank()) {
                            Text(
                                text = airStatusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (airStatusText.contains("已完结")) {
                                    LocalAnimeColors.current.finished
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    // Row 节点保持稳定，避免编辑模式切换时整体子树移除/添加
                    // 触发 SharedTransition 动画窗口内的 LayoutNode.onChildRemoved NPE。
                    // 编辑模式下仅清空 Row 内部内容，Row 本身始终存在。
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!editState.isEditing) {
                            val unaired = isUnaired(anime.airDate)
                            StatusBadge(status = anime.status, unaired = unaired)

                            if (anime.rating != null && titleLineCount >= 2) {
                                Spacer(modifier = Modifier.width(8.dp))
                                RatingBadge(rating = anime.rating)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SummaryCard(
            summary = if (editState.isEditing) editState.summary else anime.summary,
            isFetchingDetail = isFetchingDetail,
            // 数据源按绑定 id 判断：Bangumi 优先，其次 TMDB
            syncSource = if (anime.bangumiId != null) "Bangumi" else "TMDB",
            fetchError = detailFetchError,
            isEditing = editState.isEditing,
            onSummaryChange = onEditSummaryChange
        )
    }

    // 右栏内容：进度 / 笔记 / 状态（单栏/双栏共用）
    val detailSection: @Composable () -> Unit = {
        // 已看完/已弃番时收起「观看进度」卡片；编辑模式下该卡是「总集数」编辑器，仍需保留
        val isProgressHidden = !editState.isEditing &&
            (anime.status == AnimeStatus.COMPLETED || anime.status == AnimeStatus.DROPPED)

        AnimatedVisibility(
            visible = !isProgressHidden,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                ProgressCard(
                    anime = anime,
                    onUpdateWatchedEpisodes = onUpdateWatchedEpisodes,
                    onAdjustWatchedEpisodes = onAdjustWatchedEpisodes,
                    isEditMode = editState.isEditing,
                    editTotalEpisodes = editState.totalEpisodes,
                    onUpdateEditTotalEpisodes = onUpdateEditTotalEpisodes,
                    onAdjustEditTotalEpisodes = onAdjustEditTotalEpisodes
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        NotesCard(
            notes = anime.notes,
            notesText = notesText,
            isEditingNotes = isEditingNotes,
            onNotesChange = onNotesChange,
            onSaveNotes = onSaveNotes,
            onEditNotes = onEditNotes
        )

        // 未放送时隐藏「状态」组件；到达放送日期后自动显示
        val currentAirDate = if (editState.isEditing) editState.airDate else anime.airDate
        if (!isUnaired(currentAirDate)) {
            Spacer(modifier = Modifier.height(12.dp))

            StatusCard(
                currentStatus = anime.status,
                finishDate = anime.finishDate,
                onStatusChange = onStatusChange,
                onFinishDateChange = onFinishDateChange
            )
        }
    }

    if (isTwoPane) {
        // 大屏双栏布局：左栏（封面 + 简介）与右栏（进度 / 笔记 / 状态）各自独立滚动
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(chromeContentPadding)
                    .padding(bottom = 24.dp)
            ) {
                headerSection()
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(chromeContentPadding)
                    .padding(bottom = 24.dp)
            ) {
                detailSection()
            }
        }
    } else {
        // 手机单栏布局：与原结构保持一致
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(chromeContentPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            headerSection()
            Spacer(modifier = Modifier.height(12.dp))
            detailSection()
        }
    }
}

@Composable
private fun RatingBadge(
    rating: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = SquircleShape(8.dp),
        color = LocalAnimeColors.current.finished.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.STAR_SHINE),
                contentDescription = null,
                tint = LocalAnimeColors.current.starFilled,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = String.format("%.1f", rating),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LocalAnimeColors.current.starFilled
            )
        }
    }
}

@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SquircleShape(CardCornerRadius),
        color = cardContainerColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SummaryCard(
    summary: String?,
    isFetchingDetail: Boolean,
    syncSource: String? = null,
    fetchError: String? = null,
    isEditing: Boolean = false,
    onSummaryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    DetailCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.detail_summary),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isEditing) {
            OutlinedTextField(
                value = summary ?: "",
                onValueChange = onSummaryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.detail_add_summary_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                shape = SquircleShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        } else if (!summary.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (hasOverflow) isExpanded = !isExpanded
                    }
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!isExpanded) {
                            hasOverflow = result.hasVisualOverflow
                        }
                    }
                )

                if (hasOverflow && !isExpanded) {
                    Text(
                        text = stringResource(R.string.detail_click_to_expand),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (isFetchingDetail) {
            // 简介为空且正在同步：灰色小字提示数据源
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.detail_syncing_summary, syncSource ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else if (fetchError != null) {
            // 同步失败：标注失败原因，下次进入详情页可重试
            Text(
                text = stringResource(R.string.detail_sync_failed, fetchError),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        } else {
            Text(
                text = stringResource(R.string.detail_no_summary),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressCard(
    anime: Anime,
    onUpdateWatchedEpisodes: (Int) -> Unit,
    onAdjustWatchedEpisodes: (Int) -> Unit,
    isEditMode: Boolean = false,
    editTotalEpisodes: Int = 0,
    onUpdateEditTotalEpisodes: (Int) -> Unit = {},
    onAdjustEditTotalEpisodes: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(TextFieldValue()) }
    val focusReq = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 编辑模式下使用 editTotalEpisodes，否则使用 watchedEpisodes
    val currentDisplayValue = if (isEditMode) editTotalEpisodes else anime.watchedEpisodes
    val currentMaxValue = if (isEditMode) 9999 else anime.effectiveMaxEpisodes

    var sliderValue by remember(currentDisplayValue) {
        mutableFloatStateOf(currentDisplayValue.toFloat())
    }
    var isDragging by remember { mutableStateOf(false) }

    // 非编辑模式始终用 sliderValue：松手时保持松手值，DB 回流后 remember 重置为相同值，避免数字跳动
    val displayValue = if (isEditMode) currentDisplayValue else sliderValue.toInt()

    DetailCard(modifier = modifier) {
        // 标题行
        Text(
            text = if (isEditMode) stringResource(R.string.detail_total_episodes) else stringResource(R.string.detail_watch_progress),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 集数行（进度条上方，居中）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditing) {
                BasicTextField(
                    value = editValue,
                    onValueChange = { input ->
                        val filtered = input.copy(text = input.text.filter { it.isDigit() })
                        editValue = filtered
                        val num = filtered.text.toIntOrNull()
                        if (num != null && num >= 0) {
                            if (isEditMode) {
                                onUpdateEditTotalEpisodes(num)
                            } else {
                                onUpdateWatchedEpisodes(num.coerceAtMost(currentMaxValue))
                            }
                        }
                    },
                    modifier = Modifier
                        .widthIn(min = 60.dp, max = 120.dp)
                        .focusRequester(focusReq),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            isEditing = false
                            keyboardController?.hide()
                        }
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            } else {
                if (isEditMode) {
                    Text(
                        text = if (editTotalEpisodes > 0) stringResource(R.string.detail_total_episodes_format, editTotalEpisodes) else stringResource(R.string.detail_unknown_episodes),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable {
                            isEditing = true
                            val text = editTotalEpisodes.toString()
                            editValue = TextFieldValue(
                                text = text,
                                selection = TextRange(text.length)
                            )
                        }
                    )
                } else {
                    val maxEps = anime.effectiveMaxEpisodes
                    val displayMax = if (anime.totalEpisodes > 0) anime.totalEpisodes else anime.currentEpisodes
                    Text(
                        text = if (maxEps > 0) stringResource(R.string.detail_watched_progress_format, displayValue, displayMax) else stringResource(R.string.detail_watched_only_format, displayValue),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable {
                            isEditing = true
                            val text = anime.watchedEpisodes.toString()
                            editValue = TextFieldValue(
                                text = text,
                                selection = TextRange(text.length)
                            )
                        }
                    )
                }
            }
        }

        LaunchedEffect(isEditing) {
            if (isEditing) {
                val text = currentDisplayValue.toString()
                editValue = TextFieldValue(
                    text = text,
                    selection = TextRange(text.length)
                )
                focusReq.requestFocus()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 控制行：减按钮 + 进度条 + 加按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AcceleratedButton(
                text = "-",
                enabled = currentDisplayValue > 0,
                hapticFeedback = hapticFeedback,
                onTap = { if (isEditMode) onAdjustEditTotalEpisodes(-1) else onAdjustWatchedEpisodes(-1) },
                onAdjust = { step -> if (isEditMode) onAdjustEditTotalEpisodes(-step) else onAdjustWatchedEpisodes(-step) }
            )

            // 编辑模式下隐藏进度条，用占位保持两按钮分立两端
            if (!isEditMode) {
                val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val progressColor = MaterialTheme.colorScheme.primary
                val thumbBaseColor = MaterialTheme.colorScheme.surface
                val tickColor = MaterialTheme.colorScheme.surface
                val maxEps = anime.effectiveMaxEpisodes
                val valueRange = if (maxEps > 0) maxEps else 100

                val thumbSize by animateFloatAsState(
                    targetValue = if (isDragging) 20f else 16f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
                    label = "thumb_size"
                )

                // 手势位置 → 集数（与绘制的 thumb 几何一致：中心活动区间 [thumbPx/2, W-thumbPx/2]）
                fun positionToValue(x: Float, width: Float, thumbPx: Float): Float {
                    val usable = (width - thumbPx).coerceAtLeast(1f)
                    val fraction = ((x - thumbPx / 2f) / usable).coerceIn(0f, 1f)
                    return fraction * valueRange
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .pointerInput(valueRange) {
                            detectTapGestures(
                                // 点按也要 thumb 放大反馈：按下时抬起球，松开回落
                                onPress = {
                                    isDragging = true
                                    tryAwaitRelease()
                                    isDragging = false
                                },
                                onTap = { offset ->
                                    val thumbPx = 16.dp.toPx()
                                    sliderValue = positionToValue(offset.x, size.width.toFloat(), thumbPx)
                                        .roundToInt().toFloat()
                                    onUpdateWatchedEpisodes(sliderValue.toInt())
                                }
                            )
                        }
                        .pointerInput(valueRange) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val thumbPx = 16.dp.toPx()
                                    sliderValue = positionToValue(offset.x, size.width.toFloat(), thumbPx)
                                        .roundToInt().toFloat()
                                },
                                onDragEnd = {
                                    isDragging = false
                                    onUpdateWatchedEpisodes(sliderValue.toInt())
                                },
                                onDragCancel = { isDragging = false }
                            ) { change, _ ->
                                val thumbPx = 16.dp.toPx()
                                sliderValue = positionToValue(change.position.x, size.width.toFloat(), thumbPx)
                                    .roundToInt().toFloat()
                                change.consume()
                            }
                        }
                ) {
                    val barHeight = 6.dp.toPx()
                    val barY = (size.height - barHeight) / 2f
                    val cornerRadius = barHeight / 2f
                    val progress = (sliderValue / valueRange).coerceIn(0f, 1f)
                    val thumbPx = thumbSize.dp.toPx()
                    // thumb 中心与填充末端共用同一坐标：填充永远止于球心，进度色与球零间隙
                    val thumbCenter = thumbPx / 2f + (size.width - thumbPx) * progress

                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, barY),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(cornerRadius)
                    )

                    if (thumbCenter > 0f) {
                        drawRoundRect(
                            color = progressColor,
                            topLeft = Offset(0f, barY),
                            size = Size(thumbCenter, barHeight),
                            cornerRadius = CornerRadius(cornerRadius)
                        )
                    }

                    // 集数刻度：轨道中线上的小圆点（章节节点风格），
                    // 位置与球心共用同一映射（[thumbPx/2, W-thumbPx/2]）——球到第 i 集时球心与第 i 个圆点重合；
                    // 集数超过 20 时按步长抽稀，圆点始终落在真实集数比例位置上
                    if (maxEps > 1) {
                        val step = if (maxEps > 20) (maxEps + 19) / 20 else 1
                        val baseThumbPx = 16.dp.toPx()
                        val usable = size.width - baseThumbPx
                        val dotCenterY = barY + barHeight / 2f
                        var ep = step
                        while (ep < maxEps) {
                            val x = baseThumbPx / 2f + usable * (ep.toFloat() / maxEps)
                            drawCircle(
                                color = tickColor,
                                radius = 1.dp.toPx(),
                                center = Offset(x, dotCenterY)
                            )
                            ep += step
                        }
                    }

                    // 进度球：surface 底圆 + primary 内圆（双层样式与原 thumb 一致）
                    val centerY = size.height / 2f
                    drawCircle(
                        color = thumbBaseColor,
                        radius = thumbPx / 2f,
                        center = Offset(thumbCenter, centerY)
                    )
                    drawCircle(
                        color = progressColor,
                        radius = thumbPx / 2f - 2.dp.toPx(),
                        center = Offset(thumbCenter, centerY)
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            AcceleratedButton(
                text = "+",
                enabled = isEditMode || anime.effectiveMaxEpisodes == 0 || anime.watchedEpisodes < anime.effectiveMaxEpisodes,
                hapticFeedback = hapticFeedback,
                onTap = { if (isEditMode) onAdjustEditTotalEpisodes(1) else onAdjustWatchedEpisodes(1) },
                onAdjust = { step -> if (isEditMode) onAdjustEditTotalEpisodes(step) else onAdjustWatchedEpisodes(step) }
            )
        }
    }
}

@Composable
private fun AcceleratedButton(
    text: String,
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onTap: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(1) }
    var currentDelay by remember { mutableLongStateOf(200L) }
    val pressStartTime = remember { mutableLongStateOf(0L) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "press_scale"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled) Modifier.pointerInput(enabled) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown()
                            isPressed = true
                            pressStartTime.longValue = System.currentTimeMillis()
                            currentStep = 1
                            currentDelay = 200L
                            onTap()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            try {
                                waitForUpOrCancellation()
                            } finally {
                                isPressed = false
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            while (isPressed) {
                delay(currentDelay)
                if (!isPressed) break

                val elapsed = System.currentTimeMillis() - pressStartTime.longValue
                if (elapsed > 1000L) {
                    currentStep = minOf(10, ((elapsed - 1000L) / 500L + 1).toInt().coerceIn(1, 10))
                    currentDelay = maxOf(100L, 200L - (elapsed - 1000L) / 20L)
                }

                onAdjust(currentStep)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

@Composable
private fun NotesCard(
    notes: String,
    notesText: String,
    isEditingNotes: Boolean,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onEditNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    DetailCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.detail_notes),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))

            // 无备注时点击卡片即可进入编辑，无需按钮；有内容后才显示编辑入口
            if (!isEditingNotes && notes.isNotBlank()) {
                IconButton(
                    onClick = onEditNotes,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.EDIT),
                        contentDescription = stringResource(R.string.detail_notes),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isEditingNotes) {
            OutlinedTextField(
                value = notesText,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    // 短内容保持原高度，长内容自动增高至上限，超出部分在输入框内滚动
                    .heightIn(min = 100.dp, max = 240.dp),
                placeholder = { Text(stringResource(R.string.detail_add_notes_hint)) },
                shape = SquircleShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onNotesChange(notes)
                        onSaveNotes()
                    },
                    shape = SquircleShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.common_cancel))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSaveNotes,
                    shape = SquircleShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        } else if (notes.isNotBlank()) {
            // 与简介一致的展开/收起交互：默认最多 5 行，点击切换展开
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (hasOverflow) isExpanded = !isExpanded
                    }
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                Text(
                    text = notes,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!isExpanded) {
                            hasOverflow = result.hasVisualOverflow
                        }
                    }
                )

                if (hasOverflow && !isExpanded) {
                    Text(
                        text = stringResource(R.string.detail_click_to_expand),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = SquircleShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                onClick = onEditNotes
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.detail_click_to_add_notes),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusCard(
    currentStatus: AnimeStatus,
    finishDate: Long?,
    onStatusChange: (AnimeStatus) -> Unit,
    onFinishDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }

    DetailCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.detail_status),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        CapsuleSegmentedButton(
            options = AnimeStatus.entries,
            selectedOption = currentStatus,
            onOptionSelected = onStatusChange,
            label = { it.displayName }
        )

        AnimatedVisibility(
            visible = currentStatus == AnimeStatus.COMPLETED,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SquircleShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleShape(24.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.detail_finish_date),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (finishDate != null) {
                                com.aiexile.animetrack.util.formatDate(finishDate)
                            } else {
                                stringResource(R.string.detail_select_date)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (finishDate != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            modifier = Modifier.dialogBlurBehind(),
            confirmButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                if (finishDate != null) {
                    TextButton(
                        onClick = {
                            onFinishDateChange(null)
                            showDatePicker = false
                        }
                    ) {
                        Text(stringResource(R.string.common_clear))
                    }
                }
            }
        ) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = finishDate ?: System.currentTimeMillis()
            )
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
            LaunchedEffect(datePickerState.selectedDateMillis) {
                datePickerState.selectedDateMillis?.let {
                    onFinishDateChange(it)
                }
            }
        }
    }
}

/**
 * 胶囊分段选择（可滑动）：手感与主界面底部导航栏的选中胶囊同源。
 *
 * - 整条胶囊可横向拖动，高亮色块连续跟随手指，松手弹簧吸附最近选项并回调选中；
 * - 拖拽速度带来与导航栏一致的水平/垂直「果冻」拉伸，按下时轻微放大；
 * - 点击选项仍可直接选中（拖拽结束手速/位移不足时按点按处理，不重复回调）。
 * - 未拖拽时的外部选中变化（点击、详情页状态自动回写）同样走弹簧动画。
 * - 手势归本控件独占：横向拖拽期间消费全部指针事件，避免详情页的竖向滚动容器
 *   在半路回收手势、把累计位移一次性补给页面（见 [detectSegmentDrag]）。
 */
@Composable
private fun <T> CapsuleSegmentedButton(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    val itemCount = options.size
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    // 拖拽回调里读取最新选中态/回调，避免 pointerInput 闭包捕获过期值
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    val latestOnOptionSelected by rememberUpdatedState(onOptionSelected)

    // 与导航栏浮块同一套物理：value 为连续选项位置，松手吸附最近整数位
    val dragAnim = remember(scope, itemCount) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(itemCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.08f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _, _ -> }
        )
    }

    // 点击选择 / 详情页状态自动回写时，胶囊弹簧滑到新选项。
    // 拖拽期间 selectedIndex 不变，本 effect 不会与手指位置抢动画
    LaunchedEffect(selectedIndex, dragAnim) {
        if (abs(dragAnim.value - selectedIndex) > 0.001f) {
            dragAnim.updateValue(selectedIndex.toFloat())
        }
    }

    // 文字选中态跟随色块连续位置：按整数位量化，仅在跨过选项边界时重组
    val activeIndex by remember(dragAnim) {
        derivedStateOf { dragAnim.value.roundToInt().coerceIn(0, itemCount - 1) }
    }

    val itemGap = 4.dp
    val pillColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SquircleShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 与 Row 的 weight(1f) + spacedBy(itemGap) 等价：各选项等宽、间隔固定
            val itemWidth = (
                (maxWidth - 4.dp * 2 - itemGap * (itemCount - 1).toFloat()) / itemCount
                ).coerceAtLeast(0.dp)
            val stepPx = with(density) { (itemWidth + itemGap).toPx() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(stepPx, itemCount, isLtr) {
                        if (stepPx <= 0f) return@pointerInput
                        detectSegmentDrag(
                            onDragStart = { dragAnim.press() },
                            onHorizontalDrag = { dragAmount ->
                                val delta = dragAmount / stepPx * if (isLtr) 1f else -1f
                                dragAnim.updateValue(
                                    (dragAnim.targetValue + delta)
                                        .coerceIn(0f, (itemCount - 1).toFloat())
                                )
                            },
                            onDragEnd = {
                                val target = dragAnim.targetValue
                                    .roundToInt().coerceIn(0, itemCount - 1)
                                dragAnim.animateToValue(target.toFloat())
                                if (target != latestSelectedIndex) {
                                    latestOnOptionSelected(options[target])
                                }
                            },
                            onDragCancel = {
                                dragAnim.animateToValue(
                                    dragAnim.value.roundToInt()
                                        .coerceIn(0, itemCount - 1).toFloat()
                                )
                            }
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .drawBehind {
                        // 色块绘制在文字下层：位置/果冻拉伸逐帧读取拖拽动画，仅重绘不重组
                        val pillWidth = itemWidth.toPx()
                        val step = (itemWidth + itemGap).toPx()
                        val left = if (isLtr) dragAnim.value * step
                        else (itemCount - 1 - dragAnim.value) * step
                        val velocityScale = (dragAnim.velocity / 10f).coerceIn(-0.2f, 0.2f)
                        // 实心色块（不像导航栏浮块是半透明）拉伸过度会压住相邻文字，
                        // 故保留同款方向的手感、幅度收小到不越过选项间隔
                        val scaleX = dragAnim.scaleX / (1f - velocityScale * 0.3f)
                        val scaleY = dragAnim.scaleY * (1f - velocityScale * 0.1f)
                        scale(
                            scaleX = scaleX,
                            scaleY = scaleY,
                            pivot = Offset(left + pillWidth / 2f, size.height / 2f)
                        ) {
                            drawRoundRect(
                                color = pillColor,
                                topLeft = Offset(left, 0f),
                                size = Size(pillWidth, size.height),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(itemGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == activeIndex
                    val shape = SquircleShape(100.dp)
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = 600f),
                        label = "segmentedTextColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label(option),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分段控件专用的横向拖拽检测：判定为横向拖拽后，**消费之后的所有指针事件**（含纯竖向位移）。
 *
 * 为什么不用 `detectHorizontalDragGestures`：它内部的 `horizontalDrag` 只把横向位移非 0 的
 * 事件交给回调，纯竖向位移的事件既不回调也不消费；而 `scrollable`（Compose 1.11 的
 * `Draggable` 节点）在失去手势后会进入 AwaitGesturePickup 状态，只要在 Final 通道看到一个
 * 未被消费的事件，就以「按下点」为起点重新做 slop 判定——手指此时已横向拖开几十到上百 px，
 * 瞬间超过阈值，父级滚动容器在一帧内补上整段累计位移，表现为「按住指示器不动，过一会儿
 * 整页跳一下」。拖拽期间全部消费即可堵住这条回收路径。
 *
 * 判定前不消费：点击仍由各选项的 `clickable` 正常处理；竖向位移先过 slop 时直接放弃，
 * 把页面滚动完整让给父级（此时父级仍在正常 slop 阶段，滚动 1:1 跟手，不会跳）。
 */
private suspend fun PointerInputScope.detectSegmentDrag(
    onDragStart: () -> Unit,
    onHorizontalDrag: (dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val touchSlop = viewConfiguration.touchSlop
        var dragging = false
        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (dragging) onDragCancel()
                return@awaitEachGesture
            }
            if (change.changedToUpIgnoreConsumed()) {
                if (dragging) onDragEnd()
                return@awaitEachGesture
            }
            val delta = change.positionChange()
            if (dragging) {
                // 拖拽中：全部消费，父级滚动容器无法通过 AwaitGesturePickup 抢回手势
                change.consume()
                onHorizontalDrag(delta.x)
            } else {
                if (change.isConsumed) {
                    // 父级滚动已接管本次手势，直接放弃
                    return@awaitEachGesture
                }
                totalX += delta.x
                totalY += delta.y
                when {
                    abs(totalX) > touchSlop && abs(totalX) >= abs(totalY) -> {
                        dragging = true
                        change.consume()
                        onDragStart()
                        // 与 detectHorizontalDragGestures 一致：越过 slop 的部分立即生效
                        onHorizontalDrag(
                            totalX - if (totalX > 0f) touchSlop else -touchSlop
                        )
                    }
                    abs(totalY) > touchSlop -> return@awaitEachGesture
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: AnimeStatus,
    modifier: Modifier = Modifier,
    unaired: Boolean = false
) {
    // 黑白简洁暗色下为半透明黑 scrim + 浅字（与主界面角标一致，见 Theme.kt 中 AnimeColors 计算）
    val containerColor = LocalAnimeColors.current.statusBadgeContainer
    val contentColor = LocalAnimeColors.current.statusBadgeContent
    val text = if (unaired) {
        stringResource(R.string.status_unaired)
    } else {
        status.displayName
    }
    Surface(
        modifier = modifier,
        shape = SquircleShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WeekdayChips(
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    val animeColors = LocalAnimeColors.current
    val weekdays = listOf(
        1 to stringResource(R.string.detail_weekday_short_1),
        2 to stringResource(R.string.detail_weekday_short_2),
        3 to stringResource(R.string.detail_weekday_short_3),
        4 to stringResource(R.string.detail_weekday_short_4),
        5 to stringResource(R.string.detail_weekday_short_5),
        6 to stringResource(R.string.detail_weekday_short_6),
        7 to stringResource(R.string.detail_weekday_short_7)
    )

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekdays.forEach { (day, label) ->
            val isSelected = selected == day
            Surface(
                onClick = { onSelect(day) },
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = if (isSelected) animeColors.chipSelectedContainer
                    else animeColors.chipUnselectedContainer,
                contentColor = if (isSelected) animeColors.chipSelectedContent
                    else animeColors.chipUnselectedContent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 连载状态选择器（编辑模式）：紧凑行式按钮，点击弹出 Dialog 选择三选一。
 *
 * - null（自动）：按 computeIsFinished 自动判定
 * - true（连载中）：强制 isFinished=false
 * - false（已完结）：强制 isFinished=true
 *
 * 改为按钮 + Dialog 形式避免三个按钮挤压；chip 配色使用 [LocalAnimeColors]
 * 的 chipSelected/chipUnselected 系列，与 AirDateModeChip 风格一致，
 * 在浅色/深色/黑白主题下均有清晰对比。
 */
@Composable
private fun AiringStatusSelector(
    override: Boolean?,
    onOverrideChange: (Boolean?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val currentLabel = when (override) {
        null -> stringResource(R.string.detail_airing_status_auto)
        true -> stringResource(R.string.detail_airing_status_airing)
        false -> stringResource(R.string.detail_airing_status_finished)
    }

    Row(
        modifier = Modifier
            .clip(SquircleShape(8.dp))
            .clickable { showDialog = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.detail_airing_status),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AiringStatusChip(
            text = currentLabel,
            selected = true,
            onClick = { showDialog = true }
        )
    }

    if (showDialog) {
        AiringStatusPickerDialog(
            currentOverride = override,
            onSelect = onOverrideChange,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 连载状态单选 Dialog：三个 chip 横向排列，点选即应用并保持打开，
 * 底部「关闭」按钮关闭 Dialog。配色与 AirDateModeChip 一致。
 */
@Composable
private fun AiringStatusPickerDialog(
    currentOverride: Boolean?,
    onSelect: (Boolean?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        null to stringResource(R.string.detail_airing_status_auto),
        true to stringResource(R.string.detail_airing_status_airing),
        false to stringResource(R.string.detail_airing_status_finished)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(360.dp).dialogBlurBehind(),
            shape = SquircleShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.detail_airing_status),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (value, label) ->
                        AiringStatusChip(
                            text = label,
                            selected = currentOverride == value,
                            onClick = { onSelect(value) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

/**
 * 连载状态 chip：选中用 chipSelectedContainer，未选中用 chipUnselectedContainer。
 * 与 [AirDateModeChip] 配色风格一致，无 border，靠 container color 区分选中态。
 */
@Composable
private fun AiringStatusChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animeColors = LocalAnimeColors.current
    Surface(
        onClick = onClick,
        shape = SquircleShape(8.dp),
        color = if (selected) animeColors.chipSelectedContainer
            else animeColors.chipUnselectedContainer,
        contentColor = if (selected) animeColors.chipSelectedContent
            else animeColors.chipUnselectedContent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ShareNotesDialog(
    initialNotes: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogBlurBehind(),
        shape = SquircleShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.detail_share_anime),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.detail_share_notes_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.detail_write_something_hint)) },
                    maxLines = 3,
                    shape = SquircleShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(notes) },
                shape = SquircleShape(12.dp)
            ) {
                Text(stringResource(R.string.common_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDialog(
    searchQuery: String,
    searchState: MatchSearchState,
    searchSource: SearchSource?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val dataSourceText = stringResource(R.string.detail_data_source)
    val sourceName = when (searchSource) {
        SearchSource.BANGUMI -> "Bangumi"
        SearchSource.TMDB -> "TMDB"
        SearchSource.ALL -> dataSourceText
        null -> dataSourceText
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogBlurBehind(),
        shape = SquircleShape(24.dp),
        title = {
            Text(
                text = stringResource(R.string.detail_match_source_format, sourceName),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.detail_search_keyword_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    shape = SquircleShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    leadingIcon = {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.SEARCH),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (searchState) {
                    is MatchSearchState.Idle -> {
                        Text(
                            text = stringResource(R.string.detail_input_keyword_hint),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    is MatchSearchState.Searching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is MatchSearchState.Results -> {
                        if (searchState.results.isEmpty()) {
                            Text(
                                text = stringResource(R.string.detail_no_match_found),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchState.results, key = { "${it.source}_${it.sourceId}" }) { result ->
                                    MatchResultItem(
                                        result = result,
                                        onClick = { onSelectResult(result) }
                                    )
                                }
                            }
                        }
                    }
                    is MatchSearchState.Failed -> {
                        Text(
                            text = searchState.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSearch) {
                Text(stringResource(R.string.common_search))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun MatchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SquircleShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resolveCoverModel(result.coverUrl),
                contentDescription = result.title,
                modifier = Modifier
                    .size(52.dp, 70.dp)
                    .clip(SquircleShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.episodeCountText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val rating = result.rating
                    if (rating != null && rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.STAR_SHINE),
                                contentDescription = null,
                                tint = LocalAnimeColors.current.starFilled,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                fontSize = 12.sp,
                                color = LocalAnimeColors.current.starFilled
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveCoverToGallery(context: Context, coverUrl: String) = withContext(Dispatchers.IO) {
    try {
        val imageLoader = Coil.imageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)

        if (result !is SuccessResult) {
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.detail_load_cover_failed), Toast.LENGTH_SHORT).show() }
            return@withContext
        }

        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.detail_get_image_failed), Toast.LENGTH_SHORT).show() }
            return@withContext
        }

        val filename = "AnimeTrack_Cover_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AnimeTrack")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: run {
            withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.detail_save_failed), Toast.LENGTH_SHORT).show() }
            return@withContext
        }

        resolver.openOutputStream(uri)?.use { stream: java.io.OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        withContext(Dispatchers.Main) {
            AppContainer.showTopNotice(TopNoticeType.SUCCESS, context.getString(R.string.detail_cover_saved_to_gallery))
        }
    } catch (e: Exception) {
        AppLogManager.e("AnimeDetailScreen", "saveCoverToGallery failed", e)
        withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.detail_save_failed_with_reason_format, e.message ?: ""), Toast.LENGTH_SHORT).show() }
    }
}
