package com.aiexile.animetrack.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.aiexile.animetrack.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.di.TopNoticeType
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.home.AccountPanelDialog
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.auth.UserAuthManager
import com.aiexile.animetrack.data.StatusBarMode
import com.aiexile.animetrack.ui.update.UpdateDialog
import com.aiexile.animetrack.ui.update.UpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 主页「回到顶部」的滚动。
 *
 * [LazyGridState.animateScrollToItem] 的动画时长由距离决定（内部是默认弹簧），距离太短会
 * 「一瞬间就上去」看不出过程；而跨越整条长列表又会逐帧组合沿途全部卡片，必然掉帧。
 * 因此先瞬移到离顶部 [RIDE_ITEMS] 项的位置，把最后这一段（约 15 行、4~6 屏）留给动画，
 * 观感是明显从下方滚上来，同时只组合这几十张卡片。
 */
internal suspend fun LazyGridState.smoothScrollToTop() {
    val rideStart = firstVisibleItemIndex - RIDE_ITEMS
    if (rideStart > 0) {
        scrollToItem(rideStart)
    }
    animateScrollToItem(index = 0)
}

/** 回到顶部的动画段长度（项数）。调大可加长上滚观感，代价是动画期间组合的卡片更多。 */
private const val RIDE_ITEMS = 30

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    updateViewModel: UpdateViewModel,
    gridState: LazyGridState = rememberLazyGridState(),
    showBottomBar: Boolean = true,
    onNavigate: (String) -> Unit = {},
    onNavigateToDetail: (Int, String?) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    settingsRepository: SettingsRepository? = null,
    isCapsuleNav: Boolean = false,
    isCurrentPage: Boolean = true,
    onNavigateBilibiliLogin: () -> Unit = {},
    onNavigateBangumiLogin: () -> Unit = {},
    onNavigateBangumiAccount: () -> Unit = {},
    onNavigateUserLogin: () -> Unit = {},
    onNavigateFeedback: () -> Unit = {},
    authManager: AuthManager,
    bilibiliAuthManager: BilibiliAuthManager,
    userAuthManager: UserAuthManager
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val animeList by viewModel.animeList.collectAsStateWithLifecycle()
    val isTopBarHidden by viewModel.isTopBarHidden.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bangumiLoggedIn by authManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val bangumiAvatar by authManager.userAvatar.collectAsStateWithLifecycle(initialValue = null)
    val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val bilibiliAvatar by bilibiliAuthManager.userAvatar.collectAsStateWithLifecycle(initialValue = null)
    val customAvatarUri by authManager.customAvatarUri.collectAsStateWithLifecycle(initialValue = null)
    val userLoggedIn by userAuthManager.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val userAvatarPath by userAuthManager.avatar.collectAsStateWithLifecycle(initialValue = null)
    // 服务器头像存储的是相对路径，需拼接为完整 URL
    val userAvatarUrl = userAvatarPath?.let { if (it.startsWith("http")) it else "https://www.aiexile.top$it" }
    val isLoggedIn = userLoggedIn || bangumiLoggedIn || bilibiliLoggedIn
    // 头像优先级：自定义头像 > 服务器头像 > Bilibili 头像 > Bangumi 头像
    val userAvatar = customAvatarUri ?: userAvatarUrl ?: bilibiliAvatar ?: bangumiAvatar
    val hideBangumiAvatar by (settingsRepository?.hideBangumiAvatar?.collectAsStateWithLifecycle(false) ?: remember { mutableStateOf(false) })
    val showUpdateBanner by (settingsRepository?.showUpdateBanner?.collectAsStateWithLifecycle(true) ?: remember { mutableStateOf(true) })
    // 「下滑隐藏顶栏」与状态栏处理方式：实心状态栏模式收起时列表顶部预留随之收紧，
    // 内容被实心状态栏条顶下去（初始值取同步缓存防闪变）
    val hideTopBarOnScroll by (settingsRepository?.hideTopBarOnScrollEnabled
        ?.collectAsStateWithLifecycle(settingsRepository.cachedHideTopBarOnScroll())
        ?: remember { mutableStateOf(false) })
    val statusBarMode by (settingsRepository?.statusBarMode
        ?.collectAsStateWithLifecycle(settingsRepository.cachedStatusBarMode())
        ?: remember { mutableStateOf(StatusBarMode.SCRIM) })
    val seriesStackEnabled by viewModel.seriesStackEnabled.collectAsStateWithLifecycle()
    val seriesStackTopIndices by viewModel.seriesStackTopIndices.collectAsStateWithLifecycle()
    val todayUpdateCount by viewModel.todayUpdateCount.collectAsStateWithLifecycle()
    val bannerDismissed by viewModel.bannerDismissed.collectAsStateWithLifecycle()
    val autoSyncState by viewModel.autoSyncState.collectAsStateWithLifecycle()

    // 反馈有新回复（显示胶囊提示，无红点）：登录状态下主页可见时检查；
    // 设置中关闭「反馈新回复提醒」后不再请求接口，胶囊隐藏（今日更新横幅可正常显示）
    val hasFeedbackReply by viewModel.hasFeedbackReply.collectAsStateWithLifecycle()
    val feedbackReplyPillEnabled by viewModel.feedbackReplyPillEnabled.collectAsStateWithLifecycle()
    LifecycleResumeEffect(isCurrentPage, feedbackReplyPillEnabled) {
        viewModel.refreshFeedbackReply(isCurrentPage, userLoggedIn)
        onPauseOrDispose { }
    }
    // SSE 管理员回复事件：主页可见期间实时刷新反馈新回复胶囊（不再依赖切页/重启）
    LaunchedEffect(isCurrentPage, userLoggedIn, feedbackReplyPillEnabled) {
        if (!isCurrentPage || !userLoggedIn || !feedbackReplyPillEnabled) return@LaunchedEffect
        viewModel.observeFeedbackReplyEvents()
    }
    // customGreeting / greetingEffectStyle / showSearchButton / focusRequester
    // 已移至 MainOverlay（SharedTransitionLayout 外层）的 HomeTopBar 中维护

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            // 切页/进详情时保留本地搜索状态（由 ViewModel 持有），
            // 返回主页后搜索条/搜索结果原样恢复
            viewModel.dismissBanner()
        }
    }
    var showAccountPanel by remember { mutableStateOf(false) }

    // 返回键先收起主页临时状态（手动排序、本地搜索、长按选中），而不是直接退出主界面；
    // 仅当前页生效（Pager 相邻页仍处于组合中，避免误拦截其他页的返回）
    BackHandler(enabled = isCurrentPage && (uiState.isSortEditMode || uiState.isLocalSearchActive || uiState.selectedAnimeId != null)) {
        when {
            uiState.isSortEditMode -> viewModel.cancelSortEditMode()
            uiState.isLocalSearchActive -> viewModel.clearLocalSearch()
            uiState.selectedAnimeId != null -> viewModel.clearSelection()
        }
    }

    LaunchedEffect(uiState.showCompletedToast) {
        if (uiState.showCompletedToast) {
            // 完结庆祝横幅：随机附一条内置庆祝语（不走网络，即时出现）
            AppContainer.showTopNotice(
                TopNoticeType.CELEBRATION,
                context.getString(R.string.home_completed_celebration),
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

    val filteredAnimeListItems by viewModel.filteredAnimeListItems.collectAsStateWithLifecycle()
    val listSortMode by viewModel.listSortMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // showScrollToTop 状态已移至 MainOverlay（SharedTransitionLayout 外层）维护，
    // 因为 HomeFloatingActions 现在在 MainOverlay 中渲染。

    // Task 5.2: 首屏渲染完成后标记 firstFrameRendered。
    // 监听 gridState 首次出现可见项（即首帧布局完成），调用 ViewModel 标记方法。
    // 注意：markFirstFrameRendered() 在 HomeViewModel 中实现，由 Task 5 完成。
    LaunchedEffect(Unit) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.isNotEmpty() }
            .first { it }
        viewModel.markFirstFrameRendered()
    }

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            newValue != androidx.compose.material3.SheetValue.PartiallyExpanded
        }
    )
    
    LaunchedEffect(bottomSheetState.currentValue) {
        if (bottomSheetState.currentValue == androidx.compose.material3.SheetValue.Hidden 
            && uiState.isBottomSheetVisible) {
            viewModel.hideBottomSheet()
        }
    }
    
    // 添加成功后请求关闭面板：播放 hide() 动画，动画完成后经上方
    // currentValue == Hidden 链路统一收尾，避免直接移除组合导致
    // SheetState 残留锚点、下次打开面板位置偏高
    LaunchedEffect(uiState.pendingSheetDismiss) {
        if (uiState.pendingSheetDismiss && uiState.isBottomSheetVisible) {
            bottomSheetState.hide()
        }
    }
    
    LaunchedEffect(uiState.shouldScrollToTop) {
        if (uiState.shouldScrollToTop) {
            gridState.smoothScrollToTop()
            viewModel.onScrollCompleted()
        }
    }

    // 回到顶部到达后的顶部拉伸回弹：系统 overscroll 只响应手势，程序化回顶不会触发，
    // 这里用同一视觉语言补一个——顶部锚定的纵向拉伸（无空隙）+ 弹簧回弹
    val topStretch = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        viewModel.topStretchRequests.collect {
            topStretch.animateTo(1f, tween(durationMillis = 90, easing = FastOutLinearInEasing))
            topStretch.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 340f))
        }
    }
    
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            if (uiState.selectedAnimeId != null) {
                viewModel.clearSelection()
            }
            if (uiState.highlightedAnimeIds.isNotEmpty()) {
                viewModel.clearHighlight()
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelection()
            viewModel.clearHighlight()
        }
    }
    
    // 公告已由 MainPagerContent 在页面内容前统一组合，更新弹窗保持更高层级。
    UpdateDialog(viewModel = updateViewModel)
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // 顶栏区域不再用容器占位（收缩动画会拖拽内容整体上移），
        // 改由 AnimeGrid 的 contentPadding 预留顶栏高度：顶栏隐藏时内容零移动，
        // 滚动时列表可滚入顶栏背后（edge-to-edge，与 MainOverlay 顶栏遮盖配合）
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = "home",
                    onNavigate = onNavigate
                )
            }
        },
        floatingActionButton = {
            // FAB 占位：实际 FAB 在 MainOverlay（SharedTransitionLayout 外层）渲染
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (uiState.selectedAnimeId != null) {
                                viewModel.clearSelection()
                            }
                        }
                    )
                }
        ) {
            // 顶栏高度预留（statusBar + 48.dp）：与 MainOverlay 顶栏高度一致，列表首屏从顶栏下方开始。
            // 实心状态栏模式 +「下滑隐藏顶栏」开启且顶栏收起时，48dp 预留随收拢动画同步收紧
            // （与 MainOverlay morph 同一 300ms 缓动），列表上移紧贴实心状态栏条下沿；
            // 全屏/留白遮罩模式保持固定预留，内容可滚入状态栏区域；滚动中途 LazyGrid 锚定可见项，内容不跳
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val topBarExtraPadding by animateDpAsState(
                targetValue = if (statusBarMode == StatusBarMode.SOLID && hideTopBarOnScroll && isTopBarHidden) 0.dp else 48.dp,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "topBarExtraPadding"
            )
            val topBarReservedTop = statusBarTop + topBarExtraPadding
            if (animeList.isEmpty()) {
                EmptyAnimePlaceholder(
                    modifier = Modifier
                        .weight(1f)
                        .statusBarsPadding()
                        .padding(top = topBarExtraPadding)
                )
            } else {
                AnimeGrid(
                    state = AnimeGridState(
                        animeListItems = filteredAnimeListItems,
                        hasAnyAnime = true,
                        newlyAddedAnimeId = uiState.newlyAddedAnimeId,
                        selectedAnimeId = uiState.selectedAnimeId,
                        highlightedAnimeIds = uiState.highlightedAnimeIds,
                        selectedFilter = uiState.selectedFilter,
                        seriesStackEnabled = seriesStackEnabled,
                        seriesStackTopIndices = seriesStackTopIndices,
                        listSortMode = listSortMode,
                        isSortEditMode = uiState.isSortEditMode,
                        sortEditList = uiState.sortEditList
                    ),
                    headerState = AnimeGridHeaderState(
                        isLoggedIn = isLoggedIn,
                        userAvatar = userAvatar,
                        hideBangumiAvatar = hideBangumiAvatar,
                        showBanner = showUpdateBanner && todayUpdateCount > 0 && !bannerDismissed,
                        todayUpdateCount = todayUpdateCount,
                        autoSyncState = autoSyncState,
                        hasFeedbackReply = hasFeedbackReply
                    ),
                    onHighlightComplete = { viewModel.onHighlightCompleted() },
                    onAnimeClick = { anime ->
                        scope.launch {
                            delay(250)
                            viewModel.dismissBanner()
                        }
                        viewModel.clearHighlight()
                        if (uiState.selectedAnimeId != null) {
                            viewModel.clearSelection()
                        } else {
                            // 保留本地搜索状态：从详情页返回后仍可继续浏览搜索结果
                            onNavigateToDetail(anime.id, anime.coverUrl)
                        }
                    },
                    onAnimeLongPress = { anime ->
                        // 长按卡片结束本地搜索栏状态
                        viewModel.clearLocalSearch()
                        viewModel.selectAnime(anime.id.toLong())
                    },
                    onStatusChange = { anime, status -> viewModel.updateAnimeStatus(anime, status) },
                    onDelete = { anime -> viewModel.deleteAnime(anime) },
                    onClearSelection = { viewModel.clearSelection() },
                    onTogglePin = { anime -> viewModel.togglePin(anime) },
                    onToggleFavorite = { anime -> viewModel.toggleFavorite(anime) },
                    onFilterSelected = { viewModel.setFilter(it) },
                    onSortModeSelected = { viewModel.setListSortMode(it) },
                    onStartSortEdit = { viewModel.startSortEditMode() },
                    onSaveSortEdit = { viewModel.saveSortEditOrder() },
                    onCancelSortEdit = { viewModel.cancelSortEditMode() },
                    onSortItemMove = { from, to -> viewModel.onSortItemMove(from, to) },
                    onAvatarClick = {
                        showAccountPanel = true
                    },
                    onDismissBanner = { viewModel.dismissBanner() },
                    onBannerClick = { viewModel.highlightTodayUpdates() },
                    onFeedbackClick = onNavigateFeedback,
                    onSeriesStackTopChange = { seriesKey, topIndex ->
                        viewModel.setSeriesStackTopIndex(seriesKey, topIndex)
                    },
                    gridState = gridState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    isCapsuleNav = isCapsuleNav,
                    topContentPadding = topBarReservedTop,
                    modifier = Modifier.graphicsLayer {
                        // 只读 Animatable，逐帧失效绘制层，不触发重组
                        scaleY = 1f + 0.025f * topStretch.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                )
            }
        }
        
        if (uiState.isBottomSheetVisible) {
            AddAnimeBottomSheet(
                sheetState = bottomSheetState,
                onDismiss = {
                    scope.launch {
                        bottomSheetState.hide()
                    }
                },
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearching = uiState.isSearching,
                searchError = uiState.searchError,
                searchSource = uiState.searchSource,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.searchAnime() },
                onSearchResultSelect = { viewModel.selectSearchResult(it) },
                onManualAdd = { viewModel.showManualAddDialog() },
                hasSearched = uiState.hasSearched,
                onSearchSourceChange = { viewModel.updateSearchSource(it) }
            )
        }

        if (uiState.showFormDialog) {
            AddAnimeFormDialog(
                formState = uiState.formState,
                formError = uiState.formError,
                onFormStateChange = { viewModel.updateFormState(it) },
                onSave = { viewModel.saveAnime() },
                onDismiss = { viewModel.hideFormDialog() }
            )
        }

        if (showAccountPanel) {
            AccountPanelDialog(
                onDismiss = { showAccountPanel = false },
                authManager = authManager,
                bilibiliAuthManager = bilibiliAuthManager,
                userAuthManager = userAuthManager,
                onNavigateUserLogin = {
                    showAccountPanel = false
                    onNavigateUserLogin()
                },
                onNavigateBilibiliLogin = {
                    showAccountPanel = false
                    onNavigateBilibiliLogin()
                },
                onNavigateBangumiLogin = {
                    showAccountPanel = false
                    onNavigateBangumiLogin()
                },
                onNavigateBangumiAccount = {
                    showAccountPanel = false
                    onNavigateBangumiAccount()
                }
            )
        }
    }
}
