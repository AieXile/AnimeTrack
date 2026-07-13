package com.aiexile.animetrack.ui.navigation

import android.util.Log
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.components.CapsuleNavigationBar
import com.aiexile.animetrack.ui.detail.AnimeDetailScreen
import com.aiexile.animetrack.ui.home.HomeScreen
import com.aiexile.animetrack.ui.home.HomeViewModel
import com.aiexile.animetrack.ui.onboarding.OnboardingScreen
import com.aiexile.animetrack.ui.schedule.ScheduleScreen
import com.aiexile.animetrack.ui.settings.AboutScreen
import com.aiexile.animetrack.ui.settings.AppearanceScreen
import com.aiexile.animetrack.ui.settings.BangumiLoginScreen
import com.aiexile.animetrack.ui.settings.BangumiProxyScreen
import com.aiexile.animetrack.ui.settings.BilibiliLoginScreen
import com.aiexile.animetrack.ui.settings.DataManageScreen
import com.aiexile.animetrack.ui.settings.DeveloperScreen
import com.aiexile.animetrack.ui.settings.FeaturesScreen
import com.aiexile.animetrack.ui.settings.FontSettingsScreen
import com.aiexile.animetrack.ui.settings.LoginScreen
import com.aiexile.animetrack.ui.settings.UserLoginScreen
import com.aiexile.animetrack.ui.settings.UserRegisterScreen
import com.aiexile.animetrack.ui.settings.NavigationCustomizeScreen
import com.aiexile.animetrack.ui.settings.SettingsScreen
import com.aiexile.animetrack.ui.settings.UpdateNotificationScreen
import com.aiexile.animetrack.ui.settings.WebDAVAutoSyncScreen
import com.aiexile.animetrack.ui.settings.WebDAVSyncScreen
import com.aiexile.animetrack.ui.player.PlayerScreen
import com.aiexile.animetrack.ui.player.PlayerSettingsScreen
import com.aiexile.animetrack.ui.player.WebDAVBrowseScreen
import com.aiexile.animetrack.ui.timeline.TimelineScreen
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimeTrackApp(
    settingsRepository: SettingsRepository,
    isDataLoaded: java.util.concurrent.atomic.AtomicBoolean
) {
    val showFavorites by settingsRepository.showFavorites.collectAsState(true)
    val showTimeline by settingsRepository.showTimeline.collectAsState(true)
    val showSchedule by settingsRepository.showSchedule.collectAsState(true)
    val isPagerScrollEnabled = remember { mutableStateOf(true) }
    val navigationStyle by settingsRepository.navigationStyle.collectAsState(NavigationStyle.BOTTOM)
    val fabLocation by settingsRepository.fabLocation.collectAsState(FabLocation.BOTTOM_RIGHT)
    val isFirstLaunch by settingsRepository.isFirstLaunch.collectAsState(null)

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory())

    val mainPages = remember(showFavorites, showTimeline, showSchedule) {
        buildMainPages(showFavorites, showTimeline, showSchedule)
    }

    val navController = androidx.navigation.compose.rememberNavController()

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
                        settingsRepository = settingsRepository,
                        fabLocation = fabLocation,
                        homeViewModel = homeViewModel,
                        onNavigateToScreen = { route ->
                            navController.navigate(route)
                        },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }

                // 番剧详情
                composable(
                    route = Routes.ANIME_DETAIL,
                    arguments = Routes.animeDetailArguments
                ) { backStackEntry ->
                    val animeId = backStackEntry.arguments?.getInt("animeId") ?: return@composable
                    val coverUrl = backStackEntry.arguments?.getString("coverUrl")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        AnimeDetailScreen(
                            animeId = animeId,
                            coverUrl = coverUrl,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToPlayer = { id ->
                                navController.navigate(Routes.player(id))
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }
                }

                // 关于
                composable(Routes.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateDeveloper = { navController.navigate(Routes.DEVELOPER) }
                    )
                }

                // 定制导航栏
                composable(Routes.NAVIGATION_CUSTOMIZE) {
                    NavigationCustomizeScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 外观
                composable(Routes.APPEARANCE) {
                    AppearanceScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 功能
                composable(Routes.FEATURES) {
                    FeaturesScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // Bangumi 反向代理
                composable(Routes.BANGUMI_PROXY) {
                    BangumiProxyScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 字体设置
                composable(Routes.FONT_SETTINGS) {
                    FontSettingsScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 数据管理
                composable(Routes.DATA_MANAGE) {
                    DataManageScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() },
                        onNavigateWebDAV = { navController.navigate(Routes.WEBDAV_SYNC) }
                    )
                }

                // WebDAV 同步
                composable(Routes.WEBDAV_SYNC) {
                    WebDAVSyncScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() },
                        onNavigateAutoSync = { navController.navigate(Routes.WEBDAV_AUTO_SYNC) }
                    )
                }

                // WebDAV 自动同步
                composable(Routes.WEBDAV_AUTO_SYNC) {
                    WebDAVAutoSyncScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 更新通知
                composable(Routes.UPDATE_NOTIFICATION) {
                    UpdateNotificationScreen(
                        settingsRepository = settingsRepository,
                        onBack = { navController.popBackStack() }
                    )
                }

                // 登录
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateBilibiliLogin = { navController.navigate(Routes.BILIBILI_LOGIN) },
                        onNavigateBangumiLogin = { navController.navigate(Routes.BANGUMI_LOGIN) },
                        onNavigateUserLogin = { navController.navigate(Routes.USER_LOGIN) },
                        settingsRepository = settingsRepository
                    )
                }

                // B站登录
                composable(Routes.BILIBILI_LOGIN) {
                    BilibiliLoginScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // Bangumi 登录
                composable(Routes.BANGUMI_LOGIN) {
                    BangumiLoginScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // AnimeTrack 账号登录
                composable(Routes.USER_LOGIN) {
                    UserLoginScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateRegister = { navController.navigate(Routes.USER_REGISTER) }
                    )
                }

                // 注册
                composable(Routes.USER_REGISTER) {
                    UserRegisterScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                // 开发者
                composable(Routes.DEVELOPER) {
                    DeveloperScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToPlayerSettings = { navController.navigate(Routes.PLAYER_SETTINGS) }
                    )
                }

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

                // 播放器设置
                composable(Routes.PLAYER_SETTINGS) {
                    PlayerSettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToPlayer = { navController.navigate(Routes.player(0)) },
                        onNavigateToWebDAVBrowse = { navController.navigate(Routes.WEBDAV_BROWSE) }
                    )
                }
            }
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
 * 主屏幕：包含 Pager + 导航栏
 * 根据 navigationStyle 渲染不同的导航栏样式
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MainScreen(
    mainPages: List<MainPage>,
    pagerState: PagerState,
    isPagerScrollEnabled: Boolean,
    navigationStyle: NavigationStyle,
    settingsRepository: SettingsRepository,
    fabLocation: FabLocation,
    homeViewModel: HomeViewModel,
    onNavigateToScreen: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val currentRoute = mainPages.getOrNull(pagerState.targetPage)?.route ?: "home"
    val visiblePages = mainPages.map { it.route }
    val scope = rememberCoroutineScope()

    // 点击 Tab 跳转目标：指示器直线动画到目标，不逐页经过中间项
    var navJumpTarget by remember { mutableStateOf<Int?>(null) }

    val onNavigate: (String) -> Unit = { route ->
        val targetIndex = mainPages.indexOfFirst { it.route == route }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            navJumpTarget = targetIndex
            scope.launch {
                pagerState.animateScrollToPage(targetIndex)
                navJumpTarget = null
            }
        }
    }

    val pagerContent: @Composable () -> Unit = {
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
                fabLocation = fabLocation,
                navigationStyle = navigationStyle,
                homeViewModel = homeViewModel,
                onNavigateToScreen = onNavigateToScreen,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }

    when (navigationStyle) {
        NavigationStyle.CAPSULE -> CapsuleNavLayout(
            pagerContent = pagerContent,
            currentRoute = currentRoute,
            visiblePages = visiblePages,
            onNavigate = onNavigate,
            pagerState = pagerState,
            jumpTarget = navJumpTarget
        )
        NavigationStyle.BOTTOM -> BottomNavLayout(
            pagerContent = pagerContent,
            currentRoute = currentRoute,
            visiblePages = visiblePages,
            onNavigate = onNavigate,
            pagerState = pagerState
        )
    }
}

/** 胶囊导航栏布局 */
@Composable
private fun CapsuleNavLayout(
    pagerContent: @Composable () -> Unit,
    currentRoute: String,
    visiblePages: List<String>,
    onNavigate: (String) -> Unit,
    pagerState: PagerState,
    jumpTarget: Int?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        pagerContent()
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
                jumpTarget = jumpTarget
            )
        }
    }
}

/** 底部导航栏布局 */
@Composable
private fun BottomNavLayout(
    pagerContent: @Composable () -> Unit,
    currentRoute: String,
    visiblePages: List<String>,
    onNavigate: (String) -> Unit,
    pagerState: PagerState
) {
    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentRoute,
                visiblePages = visiblePages,
                onNavigate = onNavigate,
                pagerState = pagerState
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            pagerContent()
        }
    }
}

/** Pager 页面内容路由 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MainPagerContent(
    page: Int,
    mainPages: List<MainPage>,
    pagerState: PagerState,
    settingsRepository: SettingsRepository,
    fabLocation: FabLocation,
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
            fabLocation = fabLocation,
            isCapsuleNav = navigationStyle == NavigationStyle.CAPSULE,
            isCurrentPage = pagerState.currentPage == page,
            onNavigateBilibiliLogin = { onNavigateToScreen(Routes.BILIBILI_LOGIN) },
            onNavigateBangumiLogin = { onNavigateToScreen(Routes.BANGUMI_LOGIN) },
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
            onNavigate = { },
            settingsRepository = settingsRepository
        )
    }
}

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
