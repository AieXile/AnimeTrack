package com.aiexile.animetrack.ui.navigation

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerticalAlignTop
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationLabelMode
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.StatusBarMode
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.components.bottomNavBarHeight
import com.aiexile.animetrack.ui.components.CapsuleNavigationBar
import com.aiexile.animetrack.ui.components.SideNavigationRail
import com.aiexile.animetrack.ui.components.isCompactWidth
import com.aiexile.animetrack.ui.home.HomeFloatingActions
import com.aiexile.animetrack.ui.home.HomeScreen
import com.aiexile.animetrack.ui.home.HomeTopBar
import com.aiexile.animetrack.ui.home.HomeViewModel
import com.aiexile.animetrack.ui.home.TopBarActionsCombinedWidth
import com.aiexile.animetrack.ui.home.TopBarActionsFloating
import com.aiexile.animetrack.ui.home.TopBarActionsSingleWidth
import com.aiexile.animetrack.ui.home.topBarCollapseMorph
import com.aiexile.animetrack.ui.onboarding.OnboardingScreen
import com.aiexile.animetrack.ui.schedule.ScheduleScreen
import com.aiexile.animetrack.ui.settings.SettingsScreen
import com.aiexile.animetrack.ui.player.PlayerScreen
import com.aiexile.animetrack.ui.player.WebDAVBrowseScreen
import com.aiexile.animetrack.ui.timeline.TimelineScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimeTrackApp(
    settingsRepository: SettingsRepository,
    isDataLoaded: java.util.concurrent.atomic.AtomicBoolean
) {
    val showFavorites by settingsRepository.showFavorites.collectAsState(false)
    val showTimeline by settingsRepository.showTimeline.collectAsState(true)
    val showSchedule by settingsRepository.showSchedule.collectAsState(true)
    val isPagerScrollEnabled = remember { mutableStateOf(true) }
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.BOTTOM)
    // 大屏适配：Medium/Expanded 宽度使用左侧导航栏（覆盖导航样式设置），Compact 保持原底部导航/胶囊导航
    val useSideNavigation = !isCompactWidth()
    val fabLocation by settingsRepository.fabLocation.collectAsState(FabLocation.BOTTOM_RIGHT)
    val isFirstLaunch by settingsRepository.isFirstLaunch.collectAsState(null)

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory())
    val hazeState = rememberHazeState()

    val mainPages = remember(showFavorites, showTimeline, showSchedule) {
        buildMainPages(showFavorites, showTimeline, showSchedule)
    }

    val navController = rememberNavController()

    // ===== 大屏 List-Detail pane 状态（实现见 AdaptivePaneNav.kt） =====
    // Expanded 宽度下详情页/设置子页在右侧 pane 打开，主界面保持可见。
    val paneState = rememberPaneNavState(
        useSideNavigation = useSideNavigation,
        fullscreenNavController = navController
    )
    val onNavigateToScreen: (String) -> Unit = paneState.navigateTo

    // 决定初始路由
    var startRoute by remember { mutableStateOf<String?>(null) }
    var isInitialRouteSet by remember { mutableStateOf(false) }

    LaunchedEffect(isFirstLaunch) {
        if (!isInitialRouteSet && isFirstLaunch != null) {
            startRoute = if (isFirstLaunch!!) Routes.ONBOARDING else Routes.MAIN
            isInitialRouteSet = true
            isDataLoaded.set(true)
        }
    }

    // 涟漪展开动画状态
    var onboardingRevealCenter by remember { mutableStateOf<Offset?>(null) }
    val onboardingRevealRadius = remember { Animatable(0f) }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { mainPages.size })

    // Tab 点击跳转目标（提升到外层，供 MainScreen 与 MainOverlay 共享）
    var navJumpTarget by remember { mutableStateOf<Int?>(null) }
    val appScope = rememberCoroutineScope()
    val onTabNavigate: (String) -> Unit = { route ->
        val targetIndex = mainPages.indexOfFirst { it.route == route }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            navJumpTarget = targetIndex
            appScope.launch {
                pagerState.animateScrollToPage(targetIndex)
                navJumpTarget = null
            }
        }
    }

    // 当前主页面路由（供 MainOverlay 判断 TopBar/FAB 可见性）
    val currentMainRoute = mainPages.getOrNull(pagerState.targetPage)?.route ?: "home"
    val visibleMainPages = mainPages.map { it.route }

    var lastPagerRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState.currentPage, mainPages) {
        lastPagerRoute = mainPages.getOrNull(pagerState.currentPage)?.route
    }

    LaunchedEffect(mainPages) {
        val route = lastPagerRoute ?: return@LaunchedEffect
        val newIndex = mainPages.indexOfFirst { it.route == route }
        if (newIndex >= 0 && newIndex != pagerState.currentPage) {
            pagerState.scrollToPage(newIndex)
        }
    }

    // 涟漪展开动画
    LaunchedEffect(onboardingRevealCenter) {
        val center = onboardingRevealCenter ?: return@LaunchedEffect
        val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val corners = listOf(
            Offset(0f, 0f), Offset(screenWidth, 0f),
            Offset(0f, screenHeight), Offset(screenWidth, screenHeight)
        )
        val maxRadius = corners.maxOf { corner ->
            sqrt((corner.x - center.x) * (corner.x - center.x) + (corner.y - center.y) * (corner.y - center.y))
        }
        settingsRepository.setFirstLaunchCompleted()
        navController.navigate(Routes.MAIN) {
            popUpTo(Routes.ONBOARDING) { inclusive = true }
            // 开发者选项可会话中途重入向导：pop 后栈顶可能已是 MAIN，singleTop 避免重复压栈
            launchSingleTop = true
        }
        onboardingRevealRadius.snapTo(0f)
        onboardingRevealRadius.animateTo(
            targetValue = maxRadius,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        onboardingRevealCenter = null
    }

    // 等待初始路由确定
    val currentStartRoute = startRoute
    if (currentStartRoute == null) return

    // 跟踪 NavController 当前路由，供 MainOverlay 判断可见性
    val currentNavRoute by navController.currentBackStackEntryAsState()
    val isMainRoute = currentNavRoute?.destination?.route == Routes.MAIN

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = currentStartRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                enterTransition = {
                    enterTransition()
                },
                exitTransition = {
                    exitTransition()
                },
                popEnterTransition = {
                    enterTransition()
                },
                popExitTransition = {
                    exitTransition()
                }
            ) {
                // 引导页
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        settingsRepository = settingsRepository,
                        onStartReveal = { center ->
                            onboardingRevealCenter = center
                        }
                    )
                }

                // 主页
                composable(
                    route = Routes.MAIN,
                    arguments = Routes.mainArguments
                ) { _ ->
                    MainScreen(
                        mainPages = mainPages,
                        pagerState = pagerState,
                        isPagerScrollEnabled = isPagerScrollEnabled.value,
                        navigationStyle = navigationStyle,
                        useSideNavigation = useSideNavigation,
                        paneNavController = paneState.navController,
                        paneWidth = paneState.paneWidth,
                        settingsRepository = settingsRepository,
                        homeViewModel = homeViewModel,
                        hazeState = hazeState,
                        onNavigateToScreen = onNavigateToScreen,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }

                // 详情页 + 设置子页：全屏 NavHost 与大屏 pane NavHost 共用注册
                sharedDestinations(
                    settingsRepository = settingsRepository,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    hostController = navController,
                    onNavigate = onNavigateToScreen
                )

                // 播放器
                composable(
                    route = Routes.PLAYER,
                    arguments = Routes.playerArguments
                ) { backStackEntry ->
                    val animeId = backStackEntry.arguments?.getInt("animeId") ?: return@composable
                    PlayerScreen(
                        animeId = animeId,
                        onBack = { navController.popBackStack() },
                        onBrowseWebDAV = { navController.navigate(Routes.WEBDAV_BROWSE) },
                        onSelectLocalFile = { /* handled within PlayerScreen */ },
                        navController = navController
                    )
                }

                // WebDAV 文件浏览
                composable(Routes.WEBDAV_BROWSE) {
                    WebDAVBrowseScreen(
                        onFileClick = { path, fileName ->
                            navController.previousBackStackEntry?.savedStateHandle?.set("webdav_file_path", path)
                            navController.previousBackStackEntry?.savedStateHandle?.set("webdav_file_name", fileName)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // MainOverlay：在 SharedTransitionLayout 外层渲染 TopBar/BottomBar/FAB，
        // 使其绘制顺序晚于共享元素 Overlay，避免转场期间被飞行卡片遮盖。
        // 仅在 MAIN 路由可见，转场期间保持显示以遮盖飞行卡片。
        if (isMainRoute) {
            MainOverlay(
                navigationStyle = navigationStyle,
                useSideNavigation = useSideNavigation,
                paneWidth = paneState.paneWidth,
                mainPages = mainPages,
                pagerState = pagerState,
                homeViewModel = homeViewModel,
                hazeState = hazeState,
                settingsRepository = settingsRepository,
                fabLocation = fabLocation,
                currentRoute = currentMainRoute,
                visiblePages = visibleMainPages,
                onNavigate = onTabNavigate,
                navJumpTarget = navJumpTarget,
                onAddAnimeClick = { homeViewModel.showBottomSheet() }
            )
        }

        // 涟漪展开遮罩层
        onboardingRevealCenter?.let { center ->
            val backgroundColor = MaterialTheme.colorScheme.background
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                drawRect(color = backgroundColor)
                drawCircle(
                    color = Color.Transparent,
                    radius = onboardingRevealRadius.value,
                    center = center,
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )
            }
        }
    }
}

/**
 * 主屏幕：仅包含 Pager 内容。TopBar/BottomBar/FAB 已提升到 MainOverlay
 * （SharedTransitionLayout 外层）渲染，避免转场期间被共享元素 Overlay 遮盖。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScreen(
    mainPages: List<MainPage>,
    pagerState: PagerState,
    isPagerScrollEnabled: Boolean,
    navigationStyle: NavigationStyle,
    useSideNavigation: Boolean,
    paneNavController: NavHostController,
    paneWidth: Dp,
    settingsRepository: SettingsRepository,
    homeViewModel: HomeViewModel,
    hazeState: HazeState,
    onNavigateToScreen: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    when {
        // 大屏：导航栏移至左侧，Pager 内容避开侧边导航栏区域；详情/设置子页在右侧 pane 打开
        useSideNavigation -> SideNavLayout(
            mainPages = mainPages,
            pagerState = pagerState,
            isPagerScrollEnabled = isPagerScrollEnabled,
            paneNavController = paneNavController,
            paneWidth = paneWidth,
            settingsRepository = settingsRepository,
            homeViewModel = homeViewModel,
            hazeState = hazeState,
            onNavigateToScreen = onNavigateToScreen,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
        navigationStyle == NavigationStyle.CAPSULE -> CapsuleNavLayout(
            mainPages = mainPages,
            pagerState = pagerState,
            isPagerScrollEnabled = isPagerScrollEnabled,
            settingsRepository = settingsRepository,
            homeViewModel = homeViewModel,
            hazeState = hazeState,
            onNavigateToScreen = onNavigateToScreen,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
        else -> BottomNavLayout(
            mainPages = mainPages,
            pagerState = pagerState,
            isPagerScrollEnabled = isPagerScrollEnabled,
            settingsRepository = settingsRepository,
            homeViewModel = homeViewModel,
            onNavigateToScreen = onNavigateToScreen,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}


/**
 * 胶囊导航栏布局：仅渲染 Pager 内容。
 * 实际的 CapsuleNavigationBar 在 MainOverlay 中渲染，胶囊栏为浮动设计，
 * 内容可滚动到其下方（与原行为一致）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun CapsuleNavLayout(
    mainPages: List<MainPage>,
    pagerState: PagerState,
    isPagerScrollEnabled: Boolean,
    settingsRepository: SettingsRepository,
    homeViewModel: HomeViewModel,
    hazeState: HazeState,
    onNavigateToScreen: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = isPagerScrollEnabled
        ) { page ->
            MainPagerContent(
                page = page,
                mainPages = mainPages,
                pagerState = pagerState,
                settingsRepository = settingsRepository,
                navigationStyle = NavigationStyle.CAPSULE,
                homeViewModel = homeViewModel,
                onNavigateToScreen = onNavigateToScreen,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }
}

/**
 * 底部导航栏布局：Pager 内容 + 透明占位 BottomBar（保留高度以维持内容区域 padding）。
 * 实际的 BottomNavigationBar 在 MainOverlay 中渲染。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun BottomNavLayout(
    mainPages: List<MainPage>,
    pagerState: PagerState,
    isPagerScrollEnabled: Boolean,
    settingsRepository: SettingsRepository,
    homeViewModel: HomeViewModel,
    onNavigateToScreen: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    Scaffold(
        bottomBar = {
            // BottomBar 占位：实际 BottomNavigationBar 在 MainOverlay（SharedTransitionLayout 外层）渲染。
            // 保留与 BottomNavigationBar 一致的高度（navigationBarsPadding + bottomNavBarHeight）以维持内容区域 padding。
            val labelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)
            Spacer(modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(bottomNavBarHeight(labelMode)))
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = isPagerScrollEnabled
            ) { page ->
                MainPagerContent(
                    page = page,
                    mainPages = mainPages,
                    pagerState = pagerState,
                    settingsRepository = settingsRepository,
                    navigationStyle = NavigationStyle.BOTTOM,
                    homeViewModel = homeViewModel,
                    onNavigateToScreen = onNavigateToScreen,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    }
}

/** Pager 页面内容路由 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainPagerContent(
    page: Int,
    mainPages: List<MainPage>,
    pagerState: PagerState,
    settingsRepository: SettingsRepository,
    navigationStyle: NavigationStyle,
    homeViewModel: HomeViewModel,
    onNavigateToScreen: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    when (mainPages.getOrNull(page)?.route) {
        "home" -> HomeScreen(
            viewModel = homeViewModel,
            showBottomBar = false,
            onNavigate = { },
            onNavigateToDetail = { animeId, coverUrl ->
                onNavigateToScreen(Routes.animeDetail(animeId, coverUrl))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            settingsRepository = settingsRepository,
            isCapsuleNav = navigationStyle == NavigationStyle.CAPSULE,
            isCurrentPage = pagerState.currentPage == page,
            onNavigateBilibiliLogin = { onNavigateToScreen(Routes.BILIBILI_LOGIN) },
            onNavigateBangumiLogin = { onNavigateToScreen(Routes.BANGUMI_LOGIN) },
            onNavigateBangumiAccount = { onNavigateToScreen(Routes.BANGUMI_ACCOUNT) },
            onNavigateUserLogin = { onNavigateToScreen(Routes.USER_LOGIN) }
        )
        "favorites" -> PlaceholderScreen(title = stringResource(R.string.nav_app_favorites), showBottomBar = false)
        "timeline" -> TimelineScreen(showBottomBar = false, onNavigate = { })
        "schedule" -> ScheduleScreen(
            onAnimeClick = { animeId ->
                onNavigateToScreen(Routes.animeDetail(animeId, null))
            },
            settingsRepository = settingsRepository
        )
        "settings" -> SettingsScreen(
            showBottomBar = false,
            onNavigateAbout = { onNavigateToScreen(Routes.ABOUT) },
            onNavigateCustomize = { onNavigateToScreen(Routes.NAVIGATION_CUSTOMIZE) },
            onNavigateAppearance = { onNavigateToScreen(Routes.APPEARANCE) },
            onNavigateFeatures = { onNavigateToScreen(Routes.FEATURES) },
            onNavigateDataManage = { onNavigateToScreen(Routes.DATA_MANAGE) },
            onNavigateUpdateNotification = { onNavigateToScreen(Routes.UPDATE_NOTIFICATION) },
            onNavigateLogin = { onNavigateToScreen(Routes.LOGIN) },
            onNavigateBangumiProxy = { onNavigateToScreen(Routes.BANGUMI_PROXY) },
            onNavigateFontSettings = { onNavigateToScreen(Routes.FONT_SETTINGS) },
            onNavigatePlayback = { onNavigateToScreen(Routes.PLAYER_SETTINGS) },
            onNavigate = { },
            settingsRepository = settingsRepository
        )
    }
}

/**
 * 主界面 Overlay：在 SharedTransitionLayout 外层渲染 TopBar/导航栏/FAB。
 *
 * 作用：SharedTransitionLayout 在转场期间会于其根节点注入 Overlay 渲染层，
 * 飞行中的共享元素渲染在 Overlay 上，位于其内部所有 Scaffold 内容之上。
 * 将 TopBar/BottomBar/FAB 提升到此 Overlay（即 SharedTransitionLayout 外层）之后，
 * 使其绘制顺序晚于共享元素 Overlay，从而避免被飞行卡片遮盖。
 *
 * 导航形态：大屏（useSideNavigation）渲染左侧 NavigationRail；
 * Compact 按导航样式设置渲染底部导航栏 / 胶囊导航栏。
 *
 * 可见性：仅在 MAIN 路由可见，转场期间保持显示以遮盖飞行卡片。
 *
 * @param currentRoute 当前 Pager 页面对应的路由（用于导航栏高亮与 TopBar/FAB 可见性判断）
 * @param onNavigate Tab 点击导航回调
 * @param navJumpTarget CapsuleNav 直线跳转动画目标
 * @param onAddAnimeClick 添加番剧按钮点击回调
 */
@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
private fun MainOverlay(
    navigationStyle: NavigationStyle,
    useSideNavigation: Boolean,
    paneWidth: Dp,
    mainPages: List<MainPage>,
    pagerState: PagerState,
    homeViewModel: HomeViewModel,
    hazeState: HazeState,
    settingsRepository: SettingsRepository,
    fabLocation: FabLocation,
    currentRoute: String,
    visiblePages: List<String>,
    onNavigate: (String) -> Unit,
    navJumpTarget: Int?,
    onAddAnimeClick: () -> Unit
) {
    val isCapsuleNav = navigationStyle == NavigationStyle.CAPSULE
    val isHomePage = currentRoute == "home"
    val navigationLabelMode by settingsRepository.navigationLabelMode.collectAsState(NavigationLabelMode.ICON_AND_TEXT)
    val capsuleAdvancedBlurEnabled by settingsRepository.capsuleAdvancedBlurEnabled.collectAsState(false)

    // 高级模糊（毛玻璃）自定义参数：悬浮胶囊、主页顶栏与悬浮按钮共用
    val blurRadius by settingsRepository.advancedBlurRadius.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_RADIUS)
    val blurBackgroundAlpha by settingsRepository.advancedBlurBackgroundAlpha.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_BACKGROUND_ALPHA)
    val blurTintAlpha by settingsRepository.advancedBlurTintAlpha.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_TINT_ALPHA)
    val blurNoise by settingsRepository.advancedBlurNoise.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_NOISE)
    val advancedBlurConfig = remember(blurRadius, blurBackgroundAlpha, blurTintAlpha, blurNoise) {
        AdvancedBlurConfig(
            blurRadius = blurRadius.dp,
            backgroundColorAlpha = blurBackgroundAlpha,
            tintAlpha = blurTintAlpha,
            noiseFactor = blurNoise
        )
    }

    // 从 settingsRepository 读取 HomeTopBar 所需状态
    val customGreeting by settingsRepository.customGreeting.collectAsState("")
    val greetingTypingEffect by settingsRepository.greetingTypingEffect.collectAsState(true)
    val showSearchButton by settingsRepository.showSearchButton.collectAsState(true)

    // 从 homeViewModel 读取列表状态，用于判断搜索按钮可见性
    val animeList by homeViewModel.animeList.collectAsState()
    val filteredAnimeListItems by homeViewModel.filteredAnimeListItems.collectAsState()
    val hasAnime = animeList.isNotEmpty()
    val hasFilteredItems = filteredAnimeListItems.isNotEmpty()

    // showScrollToTop：基于 gridState 滚动位置计算（与 HomeScreen 内部逻辑一致）
    val gridState = homeViewModel.gridState
    var showScrollToTop by remember { mutableStateOf(false) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .debounce(50)
            .collect { index ->
                if (index > 2 && !showScrollToTop) showScrollToTop = true
                else if (index <= 1 && showScrollToTop) showScrollToTop = false
            }
    }

    // 顶栏下滑隐藏：开启开关后下滑收起顶栏，滚回列表顶部时展开（手机与平板一致）。
    // MainOverlay 随路由切换销毁重建，collectAsState(false) 的初始 false 会在返回主页时
    // 瞬态触发「开关关闭」重置，清掉记忆的收拢状态——故用 rememberSaveable 持有上次值
    var hideTopBarOnScroll by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settingsRepository) {
        settingsRepository.hideTopBarOnScrollEnabled.collect { hideTopBarOnScroll = it }
    }
    // 顶栏收起后的状态栏处理方式（全屏/留白遮罩/实心），初始值取同步缓存避免闪变
    val statusBarMode by settingsRepository.statusBarMode
        .collectAsState(settingsRepository.cachedStatusBarMode())
    val homeUiState by homeViewModel.uiState.collectAsState()
    LaunchedEffect(gridState, hideTopBarOnScroll, isHomePage) {
        if (!hideTopBarOnScroll) {
            homeViewModel.updateTopBarHidden(false)
            return@LaunchedEffect
        }
        // 回到主页（切页/详情返回）时按当前滚动位置恢复显隐（位置记忆，不强制展开）：
        // 列表在顶部 → 展开顶栏；位置在中途 → 收起（与离开前的状态一致）
        if (isHomePage && !homeViewModel.uiState.value.isLocalSearchActive) {
            if (gridState.firstVisibleItemIndex <= 1) {
                homeViewModel.updateTopBarHidden(false)
            } else if (gridState.firstVisibleItemIndex > 2) {
                homeViewModel.updateTopBarHidden(true)
            }
        }
        var lastIndex = gridState.firstVisibleItemIndex
        var lastOffset = gridState.firstVisibleItemScrollOffset
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val delta = (index - lastIndex) * TOPBAR_SCROLL_DIRECTION_SCALE + (offset - lastOffset)
                lastIndex = index
                lastOffset = offset
                // 悬浮搜索态冻结顶栏显隐：搜索由 FAB 展开的悬浮搜索条承载，不随滚动切换
                if (homeViewModel.uiState.value.isLocalSearchActive) return@collect
                // 列表滚回顶部：展开顶栏（置于 isScrollInProgress 过滤之前，
                // 甩动停稳后最后一帧位置变化也要触发）
                if (index == 0 && offset == 0) {
                    if (homeViewModel.isTopBarHidden) {
                        homeViewModel.updateTopBarHidden(false)
                    }
                    return@collect
                }
                // 仅响应用户真实滚动：pane 开合等布局变化导致列数/索引骤变不视为滚动方向
                if (!gridState.isScrollInProgress) return@collect
                if (delta > TOPBAR_SCROLL_DIRECTION_THRESHOLD) {
                    // 向下浏览内容：收起顶栏
                    if (!homeViewModel.isTopBarHidden) {
                        homeViewModel.updateTopBarHidden(true)
                    }
                }
                // 上滑回滚不再展开顶栏：仅当列表滚回顶部（上方 index/offset 归零分支）才展开
            }
    }

    // 搜索关闭后顶栏保持收拢（搜索条收回为 FAB 的动画可见，不被顶栏展开覆盖）；
    // 顶栏仅在列表滚回顶部时重新展开，上滑回滚途中不触发

    Box(modifier = Modifier.fillMaxSize()) {
        // 导航栏：大屏使用左侧 NavigationRail，Compact 使用底部导航栏 / 胶囊导航栏
        if (useSideNavigation) {
            SideNavigationRail(
                currentRoute = currentRoute,
                visiblePages = visiblePages,
                onNavigate = onNavigate,
                labelMode = navigationLabelMode,
                modifier = Modifier.align(Alignment.CenterStart),
                // 大屏回到顶部按钮：置于 Rail 底部槽位（仅主页可见时提供），替代右下角 FAB
                bottomContent = if (isHomePage) {
                    {
                        AnimatedVisibility(
                            visible = showScrollToTop,
                            enter = fadeIn(tween(300)) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = tween(300)
                            ),
                            exit = fadeOut(tween(200)) + scaleOut(
                                targetScale = 0.8f,
                                animationSpec = tween(200)
                            )
                        ) {
                            IconButton(onClick = {
                                // 点击后立即隐藏按钮，不等滚动完成
                                showScrollToTop = false
                                homeViewModel.scrollToTop()
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.VerticalAlignTop,
                                    contentDescription = stringResource(R.string.home_scroll_to_top),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    null
                }
            )
        } else if (isCapsuleNav) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                CapsuleNavigationBar(
                    currentRoute = currentRoute,
                    visiblePages = visiblePages,
                    onNavigate = onNavigate,
                    pagerState = pagerState,
                    jumpTarget = navJumpTarget,
                    labelMode = navigationLabelMode,
                    hazeState = hazeState,
                    advancedBlurEnabled = capsuleAdvancedBlurEnabled,
                    blurConfig = advancedBlurConfig
                )
            }
        } else {
            BottomNavigationBar(
                currentRoute = currentRoute,
                visiblePages = visiblePages,
                onNavigate = onNavigate,
                pagerState = pagerState,
                labelMode = navigationLabelMode,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 主页 TopBar（仅在 home 页可见）：开启「下滑隐藏顶栏」时，顶栏通过
        // graphics-shapes Morph 真收拢成迁移 FAB（终点=按钮行位置，纵向零位移），
        // 内容由 AnimeGrid contentPadding 预留顶栏高度，收拢/展开时内容零移动
        if (isHomePage) {
            // 收拢进度（0=展开, 1=收拢为 FAB）：顶栏与迁移 FAB 共用，保证 morph 连续
            val topBarCollapse by animateFloatAsState(
                targetValue = if (homeViewModel.isTopBarHidden) 1f else 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "topBarCollapse"
            )
            // 顶栏按钮构成决定收拢终点宽度：单按钮 48dp，搜索+添加组合 97dp
            val topBarShowSearch = showSearchButton && hasAnime && hasFilteredItems
            val topBarShowAdd = fabLocation == FabLocation.TOP_BAR || useSideNavigation
            val morphTargetWidth = if (topBarShowSearch && topBarShowAdd) {
                TopBarActionsCombinedWidth
            } else {
                TopBarActionsSingleWidth
            }
            // 下滑隐藏 + 高级模糊同时开启：顶栏背景毛玻璃化（与 FAB 参数一致）
            val topBarBlurEnabled = capsuleAdvancedBlurEnabled && hideTopBarOnScroll

            // 完全收拢后移除顶栏（避免透明区域拦截点击）
            if (topBarCollapse < 1f) {
                HomeTopBar(
                    viewModel = homeViewModel,
                    customGreeting = customGreeting,
                    greetingTypingEffect = greetingTypingEffect,
                    showSearchButton = showSearchButton,
                    fabLocation = fabLocation,
                    // 大屏无 FAB，顶栏始终提供添加入口（不受 FAB 位置设置约束）
                    alwaysShowAddButton = useSideNavigation,
                    isCurrentPage = pagerState.currentPage == mainPages.indexOfFirst { it.route == "home" },
                    hasAnime = hasAnime,
                    hasFilteredItems = hasFilteredItems,
                    onAddClick = onAddAnimeClick,
                    morphCollapse = topBarCollapse,
                    // 下滑隐藏 + 高级模糊同时开启时，顶栏背景改为毛玻璃（与 FAB 一致），
                    // 收拢全程保持毛玻璃，与 FAB 无缝衔接；否则收拢时向 FAB 容器色插值
                    morphTargetColor = if (topBarBlurEnabled) {
                        null
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    blurEnabled = topBarBlurEnabled,
                    hazeState = hazeState,
                    blurConfig = advancedBlurConfig,
                    // 大屏适配：顶栏从侧边导航栏右侧开始；pane 打开时 end 让出 pane 宽度
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .then(
                            if (useSideNavigation) Modifier.padding(start = SideNavRailWidth) else Modifier
                        )
                        .padding(end = paneWidth)
                        // 真 morph：几何连续变形为按钮行位置的 Squircle（问候语早期淡出在 TopBar 内处理）
                        .topBarCollapseMorph(topBarCollapse, morphTargetWidth)
                )
            }

            // 状态栏处理方式（「下滑隐藏顶栏」开启时生效，绘制在顶栏之后）：
            // 全屏 = 不渲染，内容滚入透明状态栏区域；留白遮罩 = 表面色纵向渐变盖在内容上；
            // 实心状态栏 = 实色条（HomeScreen 顶部预留同步收紧，内容被顶到其下沿），
            // 高级模糊时顶栏为毛玻璃，实色条保证状态栏区域始终实底。随收拢进度淡入，
            // 展开时被顶栏自身背景覆盖，无感知
            if (hideTopBarOnScroll && topBarCollapse > 0f && statusBarMode != StatusBarMode.FULLSCREEN) {
                val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val surfaceColor = MaterialTheme.colorScheme.surface
                val scrimModifier = if (statusBarMode == StatusBarMode.SCRIM) {
                    Modifier
                        // 渐变略高于状态栏，下缘柔和隐入内容；顶部实色确保状态栏图标易读
                        .height(statusBarHeight + 12.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    surfaceColor.copy(alpha = 1f),
                                    surfaceColor.copy(alpha = 0.65f),
                                    Color.Transparent
                                )
                            )
                        )
                } else {
                    Modifier
                        .height(statusBarHeight)
                        .background(surfaceColor)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // 大屏适配：与顶栏一致，从侧边导航栏右侧开始；pane 打开时让出 pane 宽度
                        .then(
                            if (useSideNavigation) Modifier.padding(start = SideNavRailWidth) else Modifier
                        )
                        .padding(end = paneWidth)
                        .fillMaxWidth()
                        .then(scrimModifier)
                        .graphicsLayer { alpha = topBarCollapse }
                )
            }

            // 顶栏动作迁移 FAB：顶栏收拢后期原位淡入（与收拢终点像素级重合）。
            // 点击搜索 → 不展开顶栏，FAB 左边框向左展开为悬浮搜索条
            TopBarActionsFloating(
                collapseProgress = topBarCollapse,
                isSearchActive = homeUiState.isLocalSearchActive,
                searchQuery = homeUiState.localSearchQuery,
                onSearchQueryChange = { homeViewModel.updateLocalSearchQuery(it) },
                onCloseSearch = { homeViewModel.clearLocalSearch() },
                showSearch = topBarShowSearch,
                showAdd = topBarShowAdd,
                onSearchClick = { homeViewModel.startLocalSearch() },
                onAddClick = onAddAnimeClick,
                hazeState = hazeState,
                advancedBlurEnabled = capsuleAdvancedBlurEnabled,
                blurConfig = advancedBlurConfig,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // 大屏适配：从侧边导航栏右侧开始；pane 打开时 end 让出 pane 宽度
                    .then(
                        if (useSideNavigation) Modifier.padding(start = SideNavRailWidth) else Modifier
                    )
                    .padding(end = paneWidth)
                    .statusBarsPadding()
                    // 与原顶栏按钮对齐：顶栏 horizontal 20dp padding，按钮行自 statusBar 底起 48dp
                    .padding(end = 20.dp)
            )

            // 主页 FAB（仅 Compact 渲染）；大屏添加入口在顶栏、回到顶部在侧边导航栏底部槽位
            if (!useSideNavigation) {
                HomeFloatingActions(
                    fabLocation = fabLocation,
                    isCapsuleNav = isCapsuleNav && !useSideNavigation,
                    showScrollToTop = showScrollToTop,
                    onScrollToTop = {
                        // 点击后立即隐藏按钮，不等滚动完成
                        showScrollToTop = false
                        homeViewModel.scrollToTop()
                    },
                    onAddClick = onAddAnimeClick,
                    hazeState = hazeState,
                    advancedBlurEnabled = capsuleAdvancedBlurEnabled,
                    blurConfig = advancedBlurConfig,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

/** 顶栏滚动方向检测：跨 item 位置换算基数与触发阈值（px），阈值过滤微小抖动 */
private const val TOPBAR_SCROLL_DIRECTION_SCALE = 100000
private const val TOPBAR_SCROLL_DIRECTION_THRESHOLD = 24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(
    title: String,
    showBottomBar: Boolean = true
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = title.lowercase(),
                    onNavigate = { }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.nav_app_page_in_development_format, title),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
