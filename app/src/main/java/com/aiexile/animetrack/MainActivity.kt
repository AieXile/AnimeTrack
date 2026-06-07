package com.aiexile.animetrack

import android.os.Bundle
import android.animation.ObjectAnimator
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.components.CapsuleNavigationBar
import com.aiexile.animetrack.ui.detail.AnimeDetailScreen
import com.aiexile.animetrack.ui.home.HomeScreen
import com.aiexile.animetrack.ui.home.HomeViewModel
import com.aiexile.animetrack.ui.schedule.ScheduleScreen
import com.aiexile.animetrack.ui.settings.AboutScreen
import com.aiexile.animetrack.ui.settings.DeveloperScreen
import com.aiexile.animetrack.ui.settings.AppearanceScreen
import com.aiexile.animetrack.ui.settings.DataManageScreen
import com.aiexile.animetrack.ui.settings.WebDAVSyncScreen
import com.aiexile.animetrack.ui.settings.FeaturesScreen
import com.aiexile.animetrack.ui.settings.NavigationCustomizeScreen
import com.aiexile.animetrack.ui.onboarding.OnboardingScreen
import com.aiexile.animetrack.ui.settings.BilibiliLoginScreen
import com.aiexile.animetrack.ui.settings.BangumiLoginScreen
import com.aiexile.animetrack.ui.settings.LoginScreen
import com.aiexile.animetrack.ui.settings.SettingsScreen
import com.aiexile.animetrack.ui.timeline.TimelineScreen
import com.aiexile.animetrack.ui.theme.AnimeTrackTheme
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.ui.theme.ThemeViewModel
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 挂起 Splash Screen 直到 DataStore 加载完毕
        val isDataLoaded = java.util.concurrent.atomic.AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition { !isDataLoaded.get() }

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenView.view, View.ALPHA, 1f, 0f
            )
            fadeOut.duration = 300L
            fadeOut.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    splashScreenView.remove()
                }
            })
            fadeOut.start()
        }

        AppContainer.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory())
            val themeMode by themeViewModel.themeMode.collectAsState()
            val themePreset by themeViewModel.themePreset.collectAsState()
            val systemDarkTheme = isSystemInDarkTheme()
            
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            
            AnimeTrackTheme(
                darkTheme = darkTheme,
                themePreset = themePreset
            ) {
                AnimeTrackApp(themeViewModel = themeViewModel, isDataLoaded = isDataLoaded)
            }
        }
    }
}

private data class MainPage(val route: String, val title: String)

sealed class Screen {
    data class Main(val pageIndex: Int = 0) : Screen()
    data class AnimeDetail(val animeId: Int, val coverUrl: String?) : Screen()
    data object About : Screen()
    data object NavigationCustomize : Screen()
    data object Appearance : Screen()
    data object Features : Screen()
    data object DataManage : Screen()
    data object WebDAVSync : Screen()
    data object Login : Screen()
    data object BilibiliLogin : Screen()
    data object BangumiLogin : Screen()
    data object Developer : Screen()
    data object Onboarding : Screen()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimeTrackApp(
    themeViewModel: ThemeViewModel,
    isDataLoaded: java.util.concurrent.atomic.AtomicBoolean
) {
    val showFavorites by themeViewModel.showFavorites.collectAsState()
    val showTimeline by themeViewModel.showTimeline.collectAsState()
    val showSchedule by themeViewModel.showSchedule.collectAsState()
    val isPagerScrollEnabled by themeViewModel.isPagerScrollEnabled.collectAsState()
    val navigationStyle by themeViewModel.navigationStyle.collectAsState()
    val fabLocation by themeViewModel.fabLocation.collectAsState()
    val isFirstLaunch by themeViewModel.isFirstLaunch.collectAsState()
    
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory())
    
    val mainPages = remember(showFavorites, showTimeline, showSchedule) {
        buildMainPages(showFavorites, showTimeline, showSchedule)
    }
    
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main()) }
    var isInitialScreenSet by remember { mutableStateOf(false) }
    var lastMainPageIndex by remember { mutableIntStateOf(0) }
    
    // 动态响应 ViewModel 状态，DataStore 加载完成后决定初始屏幕并释放 Splash Screen
    LaunchedEffect(isFirstLaunch) {
        if (!isInitialScreenSet && isFirstLaunch != null) {
            currentScreen = if (isFirstLaunch!!) Screen.Onboarding else Screen.Main()
            isInitialScreenSet = true
            isDataLoaded.set(true)
        }
    }
    
    // 涟漪展开动画状态
    var onboardingRevealCenter by remember { mutableStateOf<Offset?>(null) }
    val onboardingRevealRadius = remember { Animatable(0f) }
    
    val pagerState = rememberPagerState(pageCount = { mainPages.size })
    val scope = rememberCoroutineScope()
    
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
        // 立即切换到主界面
        themeViewModel.completeFirstLaunch()
        currentScreen = Screen.Main()
        // 播放涟漪展开动画
        onboardingRevealRadius.snapTo(0f)
        onboardingRevealRadius.animateTo(
            targetValue = maxRadius,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        onboardingRevealCenter = null
    }
    
    BackHandler(enabled = currentScreen !is Screen.Main && currentScreen !is Screen.Onboarding) {
        currentScreen = when (currentScreen) {
            is Screen.WebDAVSync -> Screen.DataManage
            is Screen.BilibiliLogin -> Screen.Login
            is Screen.BangumiLogin -> Screen.Login
            is Screen.Developer -> Screen.About
            is Screen.Login -> Screen.Main(lastMainPageIndex)
            else -> Screen.Main(lastMainPageIndex)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                val settingsSubPages = setOf(
                    Screen.About::class, Screen.NavigationCustomize::class, Screen.Appearance::class, Screen.Features::class, Screen.DataManage::class, Screen.WebDAVSync::class, Screen.Login::class, Screen.BilibiliLogin::class, Screen.BangumiLogin::class, Screen.Developer::class
                )
                val isEnterSettings = targetState::class in settingsSubPages && initialState is Screen.Main
                val isExitSettings = initialState::class in settingsSubPages && targetState is Screen.Main

                val animDuration = 300
                val easing = FastOutSlowInEasing
                val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = animDuration, easing = easing)
                val fadeSpec = tween<Float>(durationMillis = animDuration, easing = easing)
                val scaleSpec = tween<Float>(durationMillis = animDuration, easing = easing)

                when {
                    // 引导页 → 主页：瞬间切换（涟漪动画已在 OnboardingScreen 内完成）
                    initialState is Screen.Onboarding && targetState is Screen.Main -> {
                        fadeIn(animationSpec = tween(0)) togetherWith
                            fadeOut(animationSpec = tween(0))
                    }
                    isEnterSettings -> {
                        slideInHorizontally(animationSpec = slideSpec) { it } togetherWith
                            scaleOut(targetScale = 0.95f, animationSpec = scaleSpec) +
                                fadeOut(targetAlpha = 0.7f, animationSpec = fadeSpec)
                    }
                    isExitSettings -> {
                        scaleIn(initialScale = 0.95f, animationSpec = scaleSpec) +
                                fadeIn(initialAlpha = 0.0f, animationSpec = tween(durationMillis = animDuration, delayMillis = 80, easing = easing)) togetherWith
                            slideOutHorizontally(animationSpec = slideSpec) { it }
                    }
                    // 二级页面之间的过渡（如 WebDAVSync ↔ DataManage）
                    initialState is Screen.DataManage && targetState is Screen.WebDAVSync -> {
                        slideInHorizontally(animationSpec = slideSpec) { it } togetherWith
                            scaleOut(targetScale = 0.95f, animationSpec = scaleSpec) +
                                fadeOut(targetAlpha = 0.7f, animationSpec = fadeSpec)
                    }
                    initialState is Screen.WebDAVSync && targetState is Screen.DataManage -> {
                        scaleIn(initialScale = 0.95f, animationSpec = scaleSpec) +
                                fadeIn(initialAlpha = 0.0f, animationSpec = tween(durationMillis = animDuration, delayMillis = 80, easing = easing)) togetherWith
                            slideOutHorizontally(animationSpec = slideSpec) { it }
                    }
                    // 二级页面之间的过渡（如 BilibiliLogin ↔ Login）
                    initialState is Screen.Login && targetState is Screen.BilibiliLogin -> {
                        slideInHorizontally(animationSpec = slideSpec) { it } togetherWith
                            scaleOut(targetScale = 0.95f, animationSpec = scaleSpec) +
                                fadeOut(targetAlpha = 0.7f, animationSpec = fadeSpec)
                    }
                    initialState is Screen.BilibiliLogin && targetState is Screen.Login -> {
                        scaleIn(initialScale = 0.95f, animationSpec = scaleSpec) +
                                fadeIn(initialAlpha = 0.0f, animationSpec = tween(durationMillis = animDuration, delayMillis = 80, easing = easing)) togetherWith
                            slideOutHorizontally(animationSpec = slideSpec) { it }
                    }
                    // 二级页面之间的过渡（如 BangumiLogin ↔ Login）
                    initialState is Screen.Login && targetState is Screen.BangumiLogin -> {
                        slideInHorizontally(animationSpec = slideSpec) { it } togetherWith
                            scaleOut(targetScale = 0.95f, animationSpec = scaleSpec) +
                                fadeOut(targetAlpha = 0.7f, animationSpec = fadeSpec)
                    }
                    initialState is Screen.BangumiLogin && targetState is Screen.Login -> {
                        scaleIn(initialScale = 0.95f, animationSpec = scaleSpec) +
                                fadeIn(initialAlpha = 0.0f, animationSpec = tween(durationMillis = animDuration, delayMillis = 80, easing = easing)) togetherWith
                            slideOutHorizontally(animationSpec = slideSpec) { it }
                    }
                    // 二级页面之间的过渡（如 Developer ↔ About）
                    initialState is Screen.About && targetState is Screen.Developer -> {
                        slideInHorizontally(animationSpec = slideSpec) { it } togetherWith
                            scaleOut(targetScale = 0.95f, animationSpec = scaleSpec) +
                                fadeOut(targetAlpha = 0.7f, animationSpec = fadeSpec)
                    }
                    initialState is Screen.Developer && targetState is Screen.About -> {
                        scaleIn(initialScale = 0.95f, animationSpec = scaleSpec) +
                                fadeIn(initialAlpha = 0.0f, animationSpec = tween(durationMillis = animDuration, delayMillis = 80, easing = easing)) togetherWith
                            slideOutHorizontally(animationSpec = slideSpec) { it }
                    }
                    targetState is Screen.AnimeDetail && initialState is Screen.Main -> {
                        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    }
                    initialState is Screen.AnimeDetail && targetState is Screen.Main -> {
                        fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(300, easing = FastOutSlowInEasing))
                    }
                    else -> fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            label = "ScreenTransition"
        ) { targetScreen ->
            when (val screen = targetScreen) {
                is Screen.Onboarding -> {
                    OnboardingScreen(
                        onStartReveal = { center ->
                            onboardingRevealCenter = center
                        }
                    )
                }
                is Screen.Main -> {
                    val currentRoute = mainPages.getOrNull(pagerState.targetPage)?.route ?: "home"
                    val visiblePages = mainPages.map { it.route }
                    val onNavigate: (String) -> Unit = { route ->
                        val targetIndex = mainPages.indexOfFirst { it.route == route }
                        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                            scope.launch {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        }
                    }

                    if (navigationStyle == NavigationStyle.CAPSULE) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = isPagerScrollEnabled
                            ) { page ->
                                MainPagerContent(
                                    page = page,
                                    mainPages = mainPages,
                                    pagerState = pagerState,
                                    isPagerScrollEnabled = isPagerScrollEnabled,
                                    themeViewModel = themeViewModel,
                                    fabLocation = fabLocation,
                                    navigationStyle = navigationStyle,
                                    homeViewModel = homeViewModel,
                                    onNavigateToDetail = { animeId, coverUrl ->
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.AnimeDetail(animeId, coverUrl)
                                    },
                                    onNavigateAbout = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.About
                                    },
                                    onNavigateCustomize = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.NavigationCustomize
                                    },
                                    onNavigateAppearance = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.Appearance
                                    },
                                    onNavigateFeatures = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.Features
                                    },
                                    onNavigateDataManage = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.DataManage
                                    },
                                    onNavigateLogin = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.Login
                                    },
                                    onNavigateBilibiliLogin = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.BilibiliLogin
                                    },
                                    onNavigateBangumiLogin = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.BangumiLogin
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                CapsuleNavigationBar(
                                    currentRoute = currentRoute,
                                    visiblePages = visiblePages,
                                    onNavigate = onNavigate,
                                    pagerState = pagerState
                                )
                            }
                        }
                    } else {
                        Scaffold(
                            bottomBar = {
                                BottomNavigationBar(
                                    currentRoute = currentRoute,
                                    visiblePages = visiblePages,
                                    onNavigate = onNavigate
                                )
                            }
                        ) { paddingValues ->
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = paddingValues.calculateBottomPadding()),
                                userScrollEnabled = isPagerScrollEnabled
                            ) { page ->
                                MainPagerContent(
                                    page = page,
                                    mainPages = mainPages,
                                    pagerState = pagerState,
                                    isPagerScrollEnabled = isPagerScrollEnabled,
                                    themeViewModel = themeViewModel,
                                    fabLocation = fabLocation,
                                    navigationStyle = navigationStyle,
                                    homeViewModel = homeViewModel,
                                    onNavigateToDetail = { animeId, coverUrl ->
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.AnimeDetail(animeId, coverUrl)
                                    },
                                    onNavigateAbout = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.About
                                    },
                                    onNavigateCustomize = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.NavigationCustomize
                                    },
                                    onNavigateAppearance = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.Appearance
                                    },
                                    onNavigateFeatures = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.Features
                                    },
                                    onNavigateDataManage = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.DataManage
                                    },
                                    onNavigateLogin = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.Login
                                    },
                                    onNavigateBilibiliLogin = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.BilibiliLogin
                                    },
                                    onNavigateBangumiLogin = {
                                        lastMainPageIndex = pagerState.currentPage
                                        currentScreen = Screen.BangumiLogin
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent
                                )
                            }
                        }
                    }
                }
                is Screen.AnimeDetail -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        AnimeDetailScreen(
                            animeId = screen.animeId,
                            coverUrl = screen.coverUrl,
                            onNavigateBack = { currentScreen = Screen.Main(lastMainPageIndex) },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent
                        )
                    }
                }
                is Screen.About -> {
                    AboutScreen(
                        onBack = { currentScreen = Screen.Main(lastMainPageIndex) },
                        onNavigateDeveloper = { currentScreen = Screen.Developer }
                    )
                }
                is Screen.NavigationCustomize -> {
                    NavigationCustomizeScreen(
                        themeViewModel = themeViewModel,
                        onBack = { currentScreen = Screen.Main(lastMainPageIndex) }
                    )
                }
                is Screen.Appearance -> {
                    AppearanceScreen(
                        themeViewModel = themeViewModel,
                        onBack = { currentScreen = Screen.Main(lastMainPageIndex) }
                    )
                }
                is Screen.Features -> {
                    FeaturesScreen(
                        themeViewModel = themeViewModel,
                        onBack = { currentScreen = Screen.Main(lastMainPageIndex) }
                    )
                }
                is Screen.DataManage -> {
                    DataManageScreen(
                        themeViewModel = themeViewModel,
                        onBack = { currentScreen = Screen.Main(lastMainPageIndex) },
                        onNavigateWebDAV = {
                            currentScreen = Screen.WebDAVSync
                        }
                    )
                }
                is Screen.WebDAVSync -> {
                    WebDAVSyncScreen(
                        themeViewModel = themeViewModel,
                        onBack = { currentScreen = Screen.DataManage }
                    )
                }
                is Screen.Login -> {
                    LoginScreen(
                        onBack = { currentScreen = Screen.Main(lastMainPageIndex) },
                        onNavigateBilibiliLogin = {
                            currentScreen = Screen.BilibiliLogin
                        },
                        onNavigateBangumiLogin = {
                            currentScreen = Screen.BangumiLogin
                        },
                        themeViewModel = themeViewModel
                    )
                }
                is Screen.BilibiliLogin -> {
                    BilibiliLoginScreen(
                        onBack = { currentScreen = Screen.Login }
                    )
                }
                is Screen.BangumiLogin -> {
                    BangumiLoginScreen(
                        onBack = { currentScreen = Screen.Login }
                    )
                }
                is Screen.Developer -> {
                    DeveloperScreen(
                        onBack = { currentScreen = Screen.About }
                    )
                }
            }
        }
        }
        
        // 涟漪展开遮罩层：覆盖在主界面上方，圆形区域透明，露出底层主界面
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MainPagerContent(
    page: Int,
    mainPages: List<MainPage>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isPagerScrollEnabled: Boolean,
    themeViewModel: ThemeViewModel,
    fabLocation: FabLocation,
    navigationStyle: NavigationStyle,
    homeViewModel: HomeViewModel,
    onNavigateToDetail: (Int, String?) -> Unit,
    onNavigateAbout: () -> Unit,
    onNavigateCustomize: () -> Unit,
    onNavigateAppearance: () -> Unit,
    onNavigateFeatures: () -> Unit,
    onNavigateDataManage: () -> Unit,
    onNavigateLogin: () -> Unit = {},
    onNavigateBilibiliLogin: () -> Unit = {},
    onNavigateBangumiLogin: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    when (mainPages.getOrNull(page)?.route) {
        "home" -> HomeScreen(
            viewModel = homeViewModel,
            showBottomBar = false,
            onNavigate = { },
            onNavigateToDetail = onNavigateToDetail,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            themeViewModel = themeViewModel,
            fabLocation = fabLocation,
            isCapsuleNav = navigationStyle == NavigationStyle.CAPSULE,
            isCurrentPage = pagerState.currentPage == page,
            onNavigateBilibiliLogin = onNavigateBilibiliLogin,
            onNavigateBangumiLogin = onNavigateBangumiLogin
        )
        "favorites" -> PlaceholderScreen(title = "收藏", showBottomBar = false)
        "timeline" -> TimelineScreen(showBottomBar = false, onNavigate = { })
        "schedule" -> ScheduleScreen(
            onAnimeClick = { animeId ->
                onNavigateToDetail(animeId, null)
            },
            themeViewModel = themeViewModel
        )
        "settings" -> SettingsScreen(
            showBottomBar = false,
            onNavigateAbout = onNavigateAbout,
            onNavigateCustomize = onNavigateCustomize,
            onNavigateAppearance = onNavigateAppearance,
            onNavigateFeatures = onNavigateFeatures,
            onNavigateDataManage = onNavigateDataManage,
            onNavigateLogin = onNavigateLogin,
            onNavigate = { },
            themeViewModel = themeViewModel
        )
    }
}

private fun buildMainPages(showFavorites: Boolean, showTimeline: Boolean, showSchedule: Boolean): List<MainPage> {
    val pages = mutableListOf<MainPage>()
    pages.add(MainPage("home", "首页"))
    if (showFavorites) {
        pages.add(MainPage("favorites", "收藏"))
    }
    if (showTimeline) {
        pages.add(MainPage("timeline", "时间线"))
    }
    if (showSchedule) {
        pages.add(MainPage("schedule", "看板"))
    }
    pages.add(MainPage("settings", "设置"))
    return pages
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
                text = "$title 页面开发中...",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
