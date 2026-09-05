package com.aiexile.animetrack.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.aiexile.animetrack.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.home.AccountPanelDialog
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.StatusBarMode
import com.aiexile.animetrack.ui.update.UpdateDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory()),
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
    onNavigateFeedback: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val animeList by viewModel.animeList.collectAsState()
    val context = LocalContext.current
    val authManager = remember { AppContainer.getAuthManager() }
    val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
    val userAuthManager = remember { AppContainer.getUserAuthManager() }
    val bangumiLoggedIn by authManager.isLoggedIn.collectAsState(initial = false)
    val bangumiAvatar by authManager.userAvatar.collectAsState(initial = null)
    val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
    val bilibiliAvatar by bilibiliAuthManager.userAvatar.collectAsState(initial = null)
    val customAvatarUri by authManager.customAvatarUri.collectAsState(initial = null)
    val userLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)
    val userAvatarPath by userAuthManager.avatar.collectAsState(initial = null)
    // 服务器头像存储的是相对路径，需拼接为完整 URL
    val userAvatarUrl = userAvatarPath?.let { if (it.startsWith("http")) it else "https://www.aiexile.top$it" }
    val isLoggedIn = userLoggedIn || bangumiLoggedIn || bilibiliLoggedIn
    // 头像优先级：自定义头像 > 服务器头像 > Bilibili 头像 > Bangumi 头像
    val userAvatar = customAvatarUri ?: userAvatarUrl ?: bilibiliAvatar ?: bangumiAvatar
    val hideBangumiAvatar by (settingsRepository?.hideBangumiAvatar?.collectAsState(false) ?: remember { mutableStateOf(false) })
    val showUpdateBanner by (settingsRepository?.showUpdateBanner?.collectAsState(true) ?: remember { mutableStateOf(true) })
    // 「下滑隐藏顶栏」与状态栏处理方式：实心状态栏模式收起时列表顶部预留随之收紧，
    // 内容被实心状态栏条顶下去（初始值取同步缓存防闪变）
    val hideTopBarOnScroll by (settingsRepository?.hideTopBarOnScrollEnabled
        ?.collectAsState(settingsRepository.cachedHideTopBarOnScroll())
        ?: remember { mutableStateOf(false) })
    val statusBarMode by (settingsRepository?.statusBarMode
        ?.collectAsState(settingsRepository.cachedStatusBarMode())
        ?: remember { mutableStateOf(StatusBarMode.SCRIM) })
    val seriesStackEnabled by viewModel.seriesStackEnabled.collectAsState()
    val todayUpdateCount by viewModel.todayUpdateCount.collectAsState()
    val bannerDismissed by viewModel.bannerDismissed.collectAsState()
    val autoSyncState by viewModel.autoSyncState.collectAsState()

    // 反馈有新回复（显示胶囊提示，无红点）：登录状态下主页可见时检查
    var hasFeedbackReply by remember { mutableStateOf(false) }
    val feedbackScope = rememberCoroutineScope()
    LifecycleResumeEffect(isCurrentPage) {
        if (isCurrentPage && userLoggedIn) {
            feedbackScope.launch {
                hasFeedbackReply = AppContainer.getFeedbackRepository().hasNewReplies()
            }
        } else if (!userLoggedIn) {
            hasFeedbackReply = false
        }
        onPauseOrDispose { }
    }
    // customGreeting / greetingTypingEffect / showSearchButton / focusRequester
    // 已移至 MainOverlay（SharedTransitionLayout 外层）的 HomeTopBar 中维护

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            // 切页/进详情时保留本地搜索状态（由 ViewModel 持有），
            // 返回主页后搜索条/搜索结果原样恢复
            viewModel.dismissBanner()
        }
    }
    var showAccountPanel by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.showCompletedToast) {
        if (uiState.showCompletedToast) {
            Toast.makeText(context, context.getString(R.string.home_completed_celebration), Toast.LENGTH_SHORT).show()
            viewModel.dismissCompletedToast()
        }
    }

    LaunchedEffect(uiState.showDuplicateToast) {
        if (uiState.showDuplicateToast) {
            Toast.makeText(context, context.getString(R.string.home_anime_already_exists), Toast.LENGTH_SHORT).show()
            viewModel.dismissDuplicateToast()
        }
    }

    val filteredAnimeListItems by viewModel.filteredAnimeListItems.collectAsState()
    val scope = rememberCoroutineScope()

    val gridState = viewModel.gridState
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
    
    LaunchedEffect(uiState.shouldScrollToTop) {
        if (uiState.shouldScrollToTop) {
            gridState.animateScrollToItem(index = 0)
            viewModel.onScrollCompleted()
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
    
    // 更新弹窗后组合，层级高于公告弹窗：更新检查期间公告可能先弹出，
    // 若检查出新版，更新弹窗叠在其上方，确保公告不遮挡更新弹窗。
    com.aiexile.animetrack.ui.announcement.AnnouncementDialog(viewModel = viewModel.announcementViewModel)
    UpdateDialog(viewModel = viewModel.updateViewModel)
    
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
                targetValue = if (statusBarMode == StatusBarMode.SOLID && hideTopBarOnScroll && viewModel.isTopBarHidden) 0.dp else 48.dp,
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
                        seriesStackEnabled = seriesStackEnabled
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
                    onTogglePin = { anime -> viewModel.togglePin(anime) },
                    onFilterSelected = { viewModel.setFilter(it) },
                    onAvatarClick = {
                        showAccountPanel = true
                    },
                    onDismissBanner = { viewModel.dismissBanner() },
                    onBannerClick = { viewModel.highlightTodayUpdates() },
                    onFeedbackClick = onNavigateFeedback,
                    gridState = gridState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    isCapsuleNav = isCapsuleNav,
                    topContentPadding = topBarReservedTop
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
