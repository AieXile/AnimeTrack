package com.aiexile.animetrack.ui.home

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.util.resolveCoverModel
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.ui.components.AddAnimeForm
import com.aiexile.animetrack.ui.components.AddAnimeFormState
import com.aiexile.animetrack.ui.components.AnimeCard
import com.aiexile.animetrack.ui.components.AnimeCardStack
import com.aiexile.animetrack.ui.components.animateEnter
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.home.AccountPanelDialog
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.update.UpdateDialog
import kotlinx.coroutines.delay
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
    fabLocation: FabLocation = FabLocation.BOTTOM_RIGHT,
    isCapsuleNav: Boolean = false,
    isCurrentPage: Boolean = true,
    onNavigateBilibiliLogin: () -> Unit = {},
    onNavigateBangumiLogin: () -> Unit = {},
    onNavigateUserLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val animeList by viewModel.animeList.collectAsState()
    val customGreeting by (settingsRepository?.customGreeting?.collectAsState("") ?: mutableStateOf(""))
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
    val hideBangumiAvatar by (settingsRepository?.hideBangumiAvatar?.collectAsState(false) ?: mutableStateOf(false))
    val greetingTypingEffect by (settingsRepository?.greetingTypingEffect?.collectAsState(true) ?: mutableStateOf(true))
    val showUpdateBanner by (settingsRepository?.showUpdateBanner?.collectAsState(true) ?: mutableStateOf(true))
    val showSearchButton by (settingsRepository?.showSearchButton?.collectAsState(true) ?: mutableStateOf(true))
    val seriesStackEnabled by (settingsRepository?.seriesStackEnabled?.collectAsState(true) ?: mutableStateOf(true))
    val todayUpdateCount by viewModel.todayUpdateCount.collectAsState()
    val bannerDismissed by viewModel.bannerDismissed.collectAsState()
    val autoSyncState by viewModel.autoSyncState.collectAsState()

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            // 切换导航栏离开当前页时结束本地搜索栏状态
            viewModel.clearLocalSearch()
            viewModel.dismissBanner()
        }
    }
    var showAccountPanel by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.showCompletedToast) {
        if (uiState.showCompletedToast) {
            Toast.makeText(context, "完结撒花！", Toast.LENGTH_SHORT).show()
            viewModel.dismissCompletedToast()
        }
    }

    LaunchedEffect(uiState.showDuplicateToast) {
        if (uiState.showDuplicateToast) {
            Toast.makeText(context, "番剧已存在", Toast.LENGTH_SHORT).show()
            viewModel.dismissDuplicateToast()
        }
    }

    val filteredAnimeList = remember(animeList, uiState.selectedFilter, uiState.localSearchQuery, uiState.todayUpdatePinnedIds) {
        viewModel.getFilteredAnimeList(animeList, uiState.selectedFilter, uiState.localSearchQuery, uiState.todayUpdatePinnedIds)
    }
    val filteredAnimeListItems = remember(filteredAnimeList) {
        SeriesMatcher.groupAnimeList(filteredAnimeList)
    }
    val scope = rememberCoroutineScope()
    
    val gridState = viewModel.gridState
    var showScrollToTop by remember { mutableStateOf(false) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { index ->
                if (index > 2 && !showScrollToTop) showScrollToTop = true
                else if (index <= 1 && showScrollToTop) showScrollToTop = false
            }
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
            delay(100)
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
    
    UpdateDialog(viewModel = viewModel.updateViewModel)
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                if (uiState.isLocalSearchActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        BasicTextField(
                            value = TextFieldValue(
                                text = uiState.localSearchQuery,
                                selection = TextRange(uiState.localSearchQuery.length)
                            ),
                            onValueChange = { newValue ->
                                viewModel.updateLocalSearchQuery(newValue.text)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (uiState.localSearchQuery.isEmpty()) {
                                        Text(
                                            text = "搜索番剧",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (uiState.localSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateLocalSearchQuery("") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.clearLocalSearch()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "关闭搜索",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    LaunchedEffect(uiState.isLocalSearchActive, isCurrentPage) {
                        if (uiState.isLocalSearchActive && isCurrentPage) {
                            focusRequester.requestFocus()
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val greetingText = viewModel.resolveGreetingText(customGreeting)
                        TypingGreeting(
                            greetingText = greetingText,
                            shouldAnimate = greetingTypingEffect && viewModel.shouldAnimateGreeting(greetingText),
                            onAnimated = { viewModel.onGreetingAnimated(it) }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                            val showSearchIcon = showSearchButton && animeList.isNotEmpty() && filteredAnimeList.isNotEmpty()
                            if (showSearchIcon) {
                                IconButton(onClick = { viewModel.startLocalSearch() }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "搜索",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            if (fabLocation == FabLocation.TOP_BAR) {
                                IconButton(onClick = { viewModel.showBottomSheet() }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "添加番剧",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = "home",
                    onNavigate = onNavigate
                )
            }
        },
        floatingActionButton = {
            val fabOffsetY = if (isCapsuleNav) (-80).dp else 0.dp

            if (fabLocation == FabLocation.BOTTOM_RIGHT) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .offset(y = fabOffsetY)
                        .padding(end = 8.dp, bottom = 8.dp)
                ) {
                    AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = scaleIn(
                            initialScale = 0.3f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        exit = scaleOut(
                            targetScale = 0.3f,
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                        )
                    ) {
                        ScrollToTopFab(onClick = { scope.launch { gridState.animateScrollToItem(0) } })
                    }
                    androidx.compose.material3.FloatingActionButton(
                        onClick = { viewModel.showBottomSheet() },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.directionalFabShadow(
                            shape = RoundedCornerShape(16.dp)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加番剧",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = scaleIn(
                        initialScale = 0.3f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    exit = scaleOut(
                        targetScale = 0.3f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
                ) {
                    ScrollToTopFab(
                        onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                        modifier = Modifier
                            .offset(y = fabOffsetY)
                            .padding(end = 8.dp, bottom = 8.dp)
                    )
                }
            }
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
            if (animeList.isEmpty()) {
                EmptyAnimePlaceholder(
                    modifier = Modifier.weight(1f)
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
                        autoSyncState = autoSyncState
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
                            // 进入详情页前结束本地搜索栏状态
                            viewModel.clearLocalSearch()
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
                    onFilterSelected = { viewModel.setFilter(it) },
                    onAvatarClick = {
                        showAccountPanel = true
                    },
                    onDismissBanner = { viewModel.dismissBanner() },
                    onBannerClick = { viewModel.highlightTodayUpdates() },
                    gridState = gridState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    isCapsuleNav = isCapsuleNav
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
                }
            )
        }
    }
}

private fun Modifier.directionalFabShadow(
    shape: RoundedCornerShape,
    elevation: Dp = 2.dp,
    shadowSpread: Dp = 8.dp
): Modifier = this
    .shadow(elevation = elevation, shape = shape)
    .drawBehind {
        val spread = shadowSpread.toPx()
        val shadowColor = Color.Black.copy(alpha = 0.18f)

        // 底部阴影
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(shadowColor, Color.Transparent),
                startY = size.height,
                endY = size.height + spread
            ),
            topLeft = Offset(0f, size.height),
            size = androidx.compose.ui.geometry.Size(size.width, spread)
        )

        // 右侧阴影
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(shadowColor, Color.Transparent),
                startX = size.width,
                endX = size.width + spread
            ),
            topLeft = Offset(size.width, 0f),
            size = androidx.compose.ui.geometry.Size(spread, size.height)
        )

        // 右下角阴影
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(shadowColor, Color.Transparent),
                center = Offset(size.width, size.height),
                radius = spread
            ),
            topLeft = Offset(size.width, size.height),
            size = androidx.compose.ui.geometry.Size(spread, spread)
        )
    }

@Composable
private fun ScrollToTopFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.directionalFabShadow(
            shape = RoundedCornerShape(16.dp)
        )
    ) {
        Icon(
            imageVector = Icons.Filled.VerticalAlignTop,
            contentDescription = "返回顶部",
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun TypingGreeting(
    greetingText: String,
    shouldAnimate: Boolean,
    onAnimated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var visibleCharCount by remember { mutableIntStateOf(if (shouldAnimate) 0 else greetingText.length) }

    LaunchedEffect(greetingText, shouldAnimate) {
        if (shouldAnimate) {
            delay(500)
            for (i in 1..greetingText.length) {
                visibleCharCount = i
                delay(100)
            }
            onAnimated(greetingText)
        } else {
            visibleCharCount = greetingText.length
        }
    }

    Text(
        text = greetingText.substring(0, visibleCharCount.coerceAtMost(greetingText.length)),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAnimeBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    searchQuery: String = "",
    searchResults: List<SearchResult> = emptyList(),
    isSearching: Boolean = false,
    searchError: String? = null,
    searchSource: SearchSource = SearchSource.BANGUMI,
    onSearchQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSearchResultSelect: (SearchResult) -> Unit = {},
    onManualAdd: () -> Unit = {},
    hasSearched: Boolean = false,
    onSearchSourceChange: (SearchSource) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp)
            ) {
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    width = 32.dp,
                    height = 4.dp,
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current

        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Text(
                text = "添加新番剧",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                placeholder = {
                    Text(
                        "搜索番剧...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    SearchSourceDropdown(
                        selectedSource = searchSource,
                        onSourceChange = onSearchSourceChange,
                        onSearch = {
                            keyboardController?.hide()
                            onSearch()
                        },
                        onClear = { onSearchQueryChange("") },
                        hasQuery = searchQuery.isNotBlank()
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    onSearch()
                })
            )

            if (searchError != null) {
                Text(
                    text = searchError,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                val lazyListState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                val flingBehavior = ScrollableDefaults.flingBehavior()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            val velocityTracker = VelocityTracker()
                            detectVerticalDragGestures(
                                onDragStart = {
                                    velocityTracker.resetTracking()
                                },
                                onDragEnd = {
                                    val velocity = velocityTracker.calculateVelocity().y
                                    coroutineScope.launch {
                                        lazyListState.scroll {
                                            with(flingBehavior) {
                                                performFling(-velocity)
                                            }
                                        }
                                    }
                                    velocityTracker.resetTracking()
                                },
                                onDragCancel = {
                                    velocityTracker.resetTracking()
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    coroutineScope.launch {
                                        lazyListState.scroll { scrollBy(-dragAmount) }
                                    }
                                }
                            )
                        }
                ) {
                    LazyColumn(
                        state = lazyListState,
                        userScrollEnabled = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(
                            count = searchResults.size,
                            key = { "${searchResults[it].source}_${searchResults[it].sourceId}" },
                            contentType = { "search_result" }
                        ) { index ->
                            SearchResultItem(
                                result = searchResults[index],
                                onClick = { onSearchResultSelect(searchResults[index]) }
                            )
                        }
                    }
                }
            } else if (hasSearched && !isSearching && searchResults.isEmpty() && searchError == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到相关番剧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }

            TextButton(
                onClick = onManualAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "手动添加番剧",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchSourceDropdown(
    selectedSource: SearchSource,
    onSourceChange: (SearchSource) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    hasQuery: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (hasQuery) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onSearch() },
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "清除",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClear() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = when (selectedSource) {
                        SearchSource.BANGUMI -> "Bangumi"
                        SearchSource.TMDB -> "TMDB"
                        SearchSource.ALL -> "全部"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(12.dp)
            ) {
                SearchSource.entries.forEach { source ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when (source) {
                                    SearchSource.BANGUMI -> "Bangumi"
                                    SearchSource.TMDB -> "TMDB"
                                    SearchSource.ALL -> "全部"
                                },
                                fontSize = 14.sp,
                                fontWeight = if (source == selectedSource) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (source == selectedSource) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSourceChange(source)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolveCoverModel(result.coverUrl))
                .bitmapConfig(Bitmap.Config.HARDWARE)
                .build(),
            contentDescription = result.title,
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    shape = RoundedCornerShape(8.dp)
                    clip = true
                },
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val score = result.rating
                if (score != null && score > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = LocalAnimeColors.current.starFilled,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = String.format("%.1f", score),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LocalAnimeColors.current.starFilled
                        )
                    }
                }
            }

            if (!result.summary.isNullOrBlank()) {
                Text(
                    text = result.summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!result.airDate.isNullOrBlank()) {
                    val formattedDate = try {
                        val parts = result.airDate.split("-")
                        if (parts.size >= 2) "${parts[0]}年${parts[1].toInt()}月" else result.airDate
                    } catch (_: Exception) { result.airDate }
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Text(
                    text = result.episodeCountText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // 来源标识
                Text(
                    text = "·",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = when (result.source) {
                        SearchSource.BANGUMI -> "Bangumi"
                        SearchSource.TMDB -> "TMDB"
                        SearchSource.ALL -> "全部"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun AddAnimeFormDialog(
    formState: AddAnimeFormState,
    formError: String?,
    onFormStateChange: (AddAnimeFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val watchedEpisodesError = if (formState.watchedEpisodes > formState.totalEpisodes) {
        "已看集数不能超过总集数"
    } else {
        null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                FormDialogHeader(formState = formState)

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .imePadding(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    item {
                        AddAnimeForm(
                            formState = formState,
                            onFormStateChange = onFormStateChange,
                            watchedEpisodesError = watchedEpisodesError
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = formError == null && formState.title.isNotBlank()
                    ) {
                        Text(
                            text = "保存",
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
private fun FormDialogHeader(
    formState: AddAnimeFormState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (formState.coverUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolveCoverModel(formState.coverUrl))
                    .bitmapConfig(Bitmap.Config.HARDWARE)
                    .build(),
                contentDescription = formState.title,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(2f / 3f)
                    .graphicsLayer {
                        shape = RoundedCornerShape(10.dp)
                        clip = true
                    },
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formState.title.ifBlank { "新番剧" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            val episodeText = if (formState.totalEpisodes > 0) {
                "共 ${formState.totalEpisodes} 集"
            } else if (formState.currentEpisodes > 0) {
                "连载中 (更新至 ${formState.currentEpisodes} 集)"
            } else {
                "连载中"
            }

            Text(
                text = episodeText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (formState.airDate != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formState.airDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterMenu(
    selectedFilter: AnimeFilter,
    onFilterSelected: (AnimeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "筛选",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        ) {
            AnimeFilter.entries.forEach { filter ->
                val isSelected = filter == selectedFilter

                DropdownMenuItem(
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = filter.displayName,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onFilterSelected(filter)
                        expanded = false
                    },
                    colors = androidx.compose.material3.MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyAnimePlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "还没有添加任何番剧",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            Text(
                text = "点击右下角的 + 按钮添加",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp
            )
        }
    }
}

private data class AnimeGridState(
    val animeListItems: List<AnimeListItem>,
    val hasAnyAnime: Boolean = false,
    val newlyAddedAnimeId: Long?,
    val selectedAnimeId: Long?,
    val highlightedAnimeIds: Set<Long> = emptySet(),
    val selectedFilter: AnimeFilter,
    val seriesStackEnabled: Boolean = true
)

private data class AnimeGridHeaderState(
    val isLoggedIn: Boolean = false,
    val userAvatar: String? = null,
    val hideBangumiAvatar: Boolean = false,
    val showBanner: Boolean = false,
    val todayUpdateCount: Int = 0,
    val autoSyncState: AutoSyncState = AutoSyncState.Idle
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnimeGrid(
    state: AnimeGridState,
    headerState: AnimeGridHeaderState,
    onHighlightComplete: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongPress: (Anime) -> Unit,
    onStatusChange: (Anime, com.aiexile.animetrack.model.AnimeStatus) -> Unit,
    onDelete: (Anime) -> Unit,
    onFilterSelected: (AnimeFilter) -> Unit,
    onAvatarClick: () -> Unit = {},
    onDismissBanner: () -> Unit = {},
    onBannerClick: () -> Unit = {},
    gridState: LazyGridState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isCapsuleNav: Boolean = false,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val horizontalPadding = 24.dp
    val cardMinWidth = 140.dp
    val spacing = 12.dp
    
    val availableWidth = screenWidthDp - horizontalPadding
    val calculatedColumns = ((availableWidth + spacing) / (cardMinWidth + spacing)).toInt()
    val columns = minOf(calculatedColumns.coerceAtLeast(3), 4)

    // 已展开系列 key 集合：支持多系列同时独立展开。
    // 使用 stateSaver + listSaver 让 Set 可在配置变更/进程恢复后保留。
    var expandedSeriesKeys by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet()) }

    // 堆叠卡在窗口中的位置（key=series.stableKey），作为展开卡片的平移起点
    val seriesPositionMap = remember { mutableStateMapOf<String, Offset>() }

    // 已播放展开动画的系列 key 集合：避免 LazyGrid 回收 item 后滚回时重播动画，
    // 以及进入详情页再返回时重播动画。
    // 使用 rememberSaveable + listSaver 让 Set 可在导航切换/配置变更后保留。
    var animatedSeriesKeys by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet()) }

    // 基于 expandedSeriesKeys 构建实际渲染列表：展开时将 Series 拆分为多个 ExpandedSeriesCard，
    // 每季占据一个独立网格格子，后续卡片自动顺延（真正网格回流）。
    // seriesStackEnabled=false 时，所有 Series 强制拆分为 Single（不堆叠）。
    val displayList = remember(state.animeListItems, expandedSeriesKeys, state.seriesStackEnabled) {
        if (!state.seriesStackEnabled) {
            // 堆叠开关关闭：所有 Series 拆分为 Single
            buildList {
                for (item in state.animeListItems) {
                    if (item is AnimeListItem.Series) {
                        item.animeList.forEach { add(AnimeListItem.Single(it)) }
                    } else {
                        add(item)
                    }
                }
            }
        } else if (expandedSeriesKeys.isEmpty()) {
            state.animeListItems
        } else {
            buildList {
                for (item in state.animeListItems) {
                    if (item is AnimeListItem.Series && item.stableKey in expandedSeriesKeys) {
                        item.animeList.forEachIndexed { index, anime ->
                            add(
                                AnimeListItem.ExpandedSeriesCard(
                                    anime = anime,
                                    baseTitle = item.baseTitle,
                                    seasonIndex = index + 1,
                                    totalSeasons = item.animeList.size,
                                    seriesStableKey = item.stableKey
                                )
                            )
                        }
                    } else {
                        add(item)
                    }
                }
            }
        }
    }

    // 首次加载动画标记：初始为 true，延迟后切换为 false
    // 切换后滚入的卡片只播轻量动效（alpha=1），避免快速滑动白屏
    var isInitialLoad by remember { mutableStateOf(true) }
    LaunchedEffect(state.animeListItems) {
        if (state.animeListItems.isNotEmpty()) {
            // 等待首屏波浪动画完成（maxStaggerMs + 弹簧动画时间）
            delay(700)
            isInitialLoad = false
        }
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState,
        contentPadding = PaddingValues(bottom = if (isCapsuleNav) 96.dp else 16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!headerState.hideBangumiAvatar) {
                    UserAvatarButton(
                        isLoggedIn = headerState.isLoggedIn,
                        avatarUrl = headerState.userAvatar,
                        onClick = onAvatarClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }

                SyncBannerArea(
                    autoSyncState = headerState.autoSyncState,
                    showTodayBanner = headerState.showBanner,
                    todayUpdateCount = headerState.todayUpdateCount,
                    onBannerClick = onBannerClick,
                    modifier = Modifier.align(Alignment.Center)
                )

                FilterMenu(
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = onFilterSelected,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        items(
            count = displayList.size,
            key = { index -> displayList[index].stableKey },
            contentType = { index -> when (displayList[index]) {
                is AnimeListItem.Single -> "anime_card"
                is AnimeListItem.Series -> "anime_series"
                is AnimeListItem.ExpandedSeriesCard -> "anime_card_expanded"
            }}
        ) { index ->
            val item = displayList[index]
            when (item) {
                is AnimeListItem.Single -> {
                    Box(
                        modifier = Modifier.animateEnter(
                            index = index,
                            key = Unit,
                            isInitialLoad = isInitialLoad,
                            animationEnabled = true
                        )
                    ) {
                        val anime = item.anime
                        val isNew = anime.id.toLong() == state.newlyAddedAnimeId
                        val isSelected = anime.id.toLong() == state.selectedAnimeId
                        val isHighlighted = anime.id.toLong() in state.highlightedAnimeIds

                        if (isNew) {
                            NewAnimeCardWrapper(
                                anime = anime,
                                onHighlightComplete = onHighlightComplete,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = { onAnimeLongPress(anime) },
                                isSelected = isSelected,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        } else {
                            AnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = { onAnimeLongPress(anime) },
                                isSelected = isSelected,
                                isHighlighted = isHighlighted,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                }
                is AnimeListItem.Series -> {
                    Box(
                        modifier = Modifier
                            .animateEnter(
                                index = index,
                                key = Unit,
                                isInitialLoad = isInitialLoad,
                                animationEnabled = true
                            )
                            .onGloballyPositioned { coords ->
                                // 记录堆叠卡在窗口中的位置，作为展开卡片的平移起点
                                seriesPositionMap[item.stableKey] = coords.positionInWindow()
                            }
                    ) {
                        AnimeCardStack(
                            baseTitle = item.baseTitle,
                            animeList = item.animeList,
                            onClick = { onAnimeClick(item.animeList.first()) },
                            onLongPress = { expandedSeriesKeys = expandedSeriesKeys + item.stableKey }
                        )
                    }
                }
                is AnimeListItem.ExpandedSeriesCard -> {
                    val anime = item.anime
                    val isNew = anime.id.toLong() == state.newlyAddedAnimeId
                    val isHighlighted = anime.id.toLong() in state.highlightedAnimeIds

                    // 从堆叠位置平移动效：
                    // 1. 通过 onGloballyPositioned 获取自身目标位置，计算与堆叠卡位置的偏移
                    // 2. enterProgress 0→1 驱动 translationX/Y 从 initialOffset 平移到 0
                    // 3. 多季按 seasonIndex 微调起点，营造从堆叠深处抽出的层次感
                    //
                    // 动画完成状态基于 seriesStableKey 持久化到 LazyGrid 外部，
                    // 避免滚动回收 item 后滚回时重播动画。
                    val seriesKey = item.seriesStableKey
                    val hasAnimated = seriesKey in animatedSeriesKeys
                    var enterStarted by remember(seriesKey) { mutableStateOf(hasAnimated) }
                    LaunchedEffect(seriesKey) {
                        if (!enterStarted) {
                            enterStarted = true
                            animatedSeriesKeys = animatedSeriesKeys + seriesKey
                        }
                    }

                    // 记录展开卡片目标位置（首次定位后）
                    var targetPosition by remember { mutableStateOf(Offset.Zero) }
                    val seriesPosition = seriesPositionMap[item.seriesStableKey] ?: Offset.Zero
                    // 初始偏移：从堆叠位置出发到目标位置的位移（展开卡片需平移的距离）
                    val initialOffsetX = seriesPosition.x - targetPosition.x
                    val initialOffsetY = seriesPosition.y - targetPosition.y
                    // 多季按 seasonIndex 微调起点（底层季从更深处抽出），偏移量小
                    val seasonDepthPx = with(LocalDensity.current) { (4.dp * item.seasonIndex).toPx() }

                    val enterProgress by animateFloatAsState(
                        targetValue = if (enterStarted) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "expandEnter"
                    )

                    Box(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = tween(200),
                                placementSpec = spring(
                                    dampingRatio = 0.7f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .onGloballyPositioned { coords ->
                                targetPosition = coords.positionInWindow()
                            }
                            .graphicsLayer {
                                val scale = 0.9f + 0.1f * enterProgress
                                scaleX = scale
                                scaleY = scale
                                // 从堆叠位置平移到目标位置（initialOffset → 0），叠加多季深度偏移
                                translationX = (initialOffsetX - seasonDepthPx) * (1f - enterProgress)
                                translationY = (initialOffsetY - seasonDepthPx) * (1f - enterProgress)
                                alpha = enterProgress
                            }
                    ) {
                        if (isNew) {
                            NewAnimeCardWrapper(
                                anime = anime,
                                onHighlightComplete = onHighlightComplete,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = {
                                    expandedSeriesKeys = expandedSeriesKeys - item.seriesStableKey
                                    animatedSeriesKeys = animatedSeriesKeys - item.seriesStableKey
                                },
                                isSelected = false,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        } else {
                            AnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = {
                                    expandedSeriesKeys = expandedSeriesKeys - item.seriesStableKey
                                    animatedSeriesKeys = animatedSeriesKeys - item.seriesStableKey
                                },
                                isHighlighted = isHighlighted,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                        // 季数角标：左上角，避免与右上角 StatusBadge 重叠
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 6.dp, y = 6.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "第${item.seasonIndex}季",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (state.hasAnyAnime && state.animeListItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "该分类下暂无番剧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBannerArea(
    autoSyncState: AutoSyncState,
    showTodayBanner: Boolean,
    todayUpdateCount: Int,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 自动同步状态优先：非 Idle 时显示同步 Banner（含退出动画）
    // Idle 且有今日更新时显示今日更新 Banner
    val isAutoSyncVisible = autoSyncState !is AutoSyncState.Idle

    AnimatedVisibility(
        visible = isAutoSyncVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        modifier = modifier
    ) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(50)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                when (autoSyncState) {
                    is AutoSyncState.Syncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "正在同步追番数据...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AutoSyncState.Completed -> {
                        Text(
                            text = "已同步 ${autoSyncState.count} 部番剧更新",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AutoSyncState.Failed -> {
                        Text(
                            text = "同步失败",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is AutoSyncState.Idle -> {}
                }
            }
        }
    }

    // 今日更新 Banner：仅在自动同步完全隐藏时显示
    if (!isAutoSyncVisible && showTodayBanner) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(50),
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .clickable { onBannerClick() }
        ) {
            Text(
                text = "今日有 $todayUpdateCount 部番剧更新",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NewAnimeCardWrapper(
    anime: Anime,
    onHighlightComplete: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    isSelected: Boolean,
    onStatusChange: (com.aiexile.animetrack.model.AnimeStatus) -> Unit,
    onDelete: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    var hasAnimated by remember { mutableStateOf(false) }
    
    val highlightAlpha by animateFloatAsState(
        targetValue = if (hasAnimated) 0f else 1f,
        animationSpec = tween(durationMillis = 1500),
        finishedListener = {
            onHighlightComplete()
        },
        label = "highlight"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (hasAnimated) 1f else 1.02f,
        animationSpec = tween(durationMillis = 300),
        label = "scale"
    )
    
    LaunchedEffect(Unit) {
        delay(100)
        hasAnimated = true
    }
    
    Box(
        modifier = modifier
    ) {
        AnimeCard(
            anime = anime,
            onClick = onClick,
            onLongPress = onLongPress,
            isSelected = isSelected,
            onStatusChange = onStatusChange,
            onDelete = onDelete,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
        
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha * 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}

@Composable
private fun UserAvatarButton(
    isLoggedIn: Boolean,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                color = if (isLoggedIn) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoggedIn && avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .bitmapConfig(Bitmap.Config.HARDWARE)
                    .build(),
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "登录",
                modifier = Modifier.size(18.dp),
                tint = if (isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
