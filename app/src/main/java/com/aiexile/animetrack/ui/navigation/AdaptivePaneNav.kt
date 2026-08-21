package com.aiexile.animetrack.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.detail.AnimeDetailScreen
import com.aiexile.animetrack.ui.home.HomeViewModel
import com.aiexile.animetrack.ui.player.PlayerSettingsScreen
import com.aiexile.animetrack.ui.schedule.ScheduleScreen
import com.aiexile.animetrack.ui.settings.AboutScreen
import com.aiexile.animetrack.ui.settings.AppearanceScreen
import com.aiexile.animetrack.ui.settings.BangumiAccountScreen
import com.aiexile.animetrack.ui.settings.BangumiLoginScreen
import com.aiexile.animetrack.ui.settings.BangumiProxyScreen
import com.aiexile.animetrack.ui.settings.BilibiliLoginScreen
import com.aiexile.animetrack.ui.settings.DataManageScreen
import com.aiexile.animetrack.ui.settings.DeveloperScreen
import com.aiexile.animetrack.ui.settings.FeaturesScreen
import com.aiexile.animetrack.ui.settings.FontSettingsScreen
import com.aiexile.animetrack.ui.settings.LoginScreen
import com.aiexile.animetrack.ui.settings.NavigationCustomizeScreen
import com.aiexile.animetrack.ui.settings.SettingsScreen
import com.aiexile.animetrack.ui.settings.UpdateNotificationScreen
import com.aiexile.animetrack.ui.settings.UserLoginScreen
import com.aiexile.animetrack.ui.settings.UserRegisterScreen
import com.aiexile.animetrack.ui.settings.WebDAVAutoSyncScreen
import com.aiexile.animetrack.ui.settings.WebDAVSyncScreen
import com.aiexile.animetrack.ui.timeline.TimelineScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** 大屏侧边导航栏（NavigationRail）宽度，Pager 内容与 TopBar 需避开该区域 */
internal val SideNavRailWidth = 80.dp

/**
 * pane 返回策略（仅大屏 pane 模式，手机端全屏返回不受影响）：
 * 仅"同一设置选项内部"的层级导航（[Routes.secondaryTransitions] 定义的 parent → child 链，
 * 如 数据管理 → WebDAV 同步 → 自动同步）逐级返回上一级；
 * 跨界面跳转（如 数据管理 → 登录）与顶级入口的返回直接收起整个 pane。
 */
private fun popPaneOrClose(paneNavController: NavHostController) {
    val entries = paneNavController.currentBackStack.value
    val currentRoute = entries.lastOrNull()?.destination?.route
    val belowRoute = entries.getOrNull(entries.size - 2)?.destination?.route
    // 当前页面在层级链中的父级与栈中下一层匹配时，才视为同一选项内的层级导航
    val isHierarchical = currentRoute != null && belowRoute != null &&
        Routes.secondaryTransitions.any { (parent, child) ->
            child.substringBefore('/') == currentRoute.substringBefore('/') &&
                parent.substringBefore('/') == belowRoute.substringBefore('/')
        }
    if (isHierarchical) {
        // 层级链内：逐级返回父级
        paneNavController.popBackStack()
    } else {
        // 跨界面跳转或顶级入口：收起整个 pane（栈整体弹出）
        paneNavController.popBackStack(Routes.PANE_ROOT, inclusive = false)
    }
}

/**
 * 大屏 List-Detail pane 状态：管理右侧 pane 的导航栈、可见性与宽度动画。
 *
 * pane NavHost 以空白 [Routes.PANE_ROOT] 为起点，当前路由非 PANE_ROOT 即视为 pane 打开；
 * 路由分流逻辑见 [rememberPaneNavState]。
 */
@Stable
class PaneNavState(
    val navController: NavHostController,
    val isPaneVisible: Boolean,
    val paneWidth: Dp,
    val closePane: () -> Unit,
    val navigateTo: (String) -> Unit
)

/**
 * 创建大屏 pane 状态。
 *
 * 路由分流：pane 路由（详情/设置子页/登录系）在大屏下进右侧 pane——
 * 详情页单实例替换（popUpTo PANE_ROOT）避免栈叠加，其余 pane 路由正常压栈保留层级；
 * 沉浸式路由（播放器/WebDAV 浏览等）与 Compact 全部走全屏 NavHost。
 */
@Composable
fun rememberPaneNavState(
    useSideNavigation: Boolean,
    fullscreenNavController: NavHostController
): PaneNavState {
    val paneNavController = rememberNavController()
    val paneEntry by paneNavController.currentBackStackEntryAsState()
    val isPaneVisible = useSideNavigation && paneEntry != null &&
        paneEntry!!.destination.route != Routes.PANE_ROOT

    // pane 宽度动画：打开时主界面平滑压缩（300ms，与页面过渡规范一致）
    val paneTargetWidth = (LocalConfiguration.current.screenWidthDp.dp - SideNavRailWidth) * 0.45f
    val paneWidth by animateDpAsState(
        targetValue = if (isPaneVisible) paneTargetWidth else 0.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "paneWidth"
    )

    // 关闭 pane：详情模式直接收起；设置层级链逐级返回（见 popPaneOrClose）
    val closePane: () -> Unit = {
        popPaneOrClose(paneNavController)
    }

    // 大屏返回键：仅在主界面路由生效（沉浸式路由如播放器有自己的返回语义），
    // 策略为详情模式直接收起 pane、设置层级链逐级返回
    val fullscreenEntry by fullscreenNavController.currentBackStackEntryAsState()
    val isMainRoute = fullscreenEntry?.destination?.route == Routes.MAIN
    BackHandler(enabled = isPaneVisible && isMainRoute) {
        closePane()
    }

    // 窗口降级（如折叠屏折叠）时清空 pane 栈，避免残留不可见状态
    LaunchedEffect(useSideNavigation) {
        if (!useSideNavigation) closePane()
    }

    // 路由分流
    val navigateTo: (String) -> Unit = { route ->
        if (useSideNavigation && Routes.isPaneRoute(route)) {
            if (Routes.isAnimeDetailRoute(route)) {
                paneNavController.navigate(route) {
                    popUpTo(Routes.PANE_ROOT) { inclusive = false }
                    launchSingleTop = true
                }
            } else {
                paneNavController.navigate(route)
            }
        } else {
            fullscreenNavController.navigate(route)
        }
    }

    return PaneNavState(
        navController = paneNavController,
        isPaneVisible = isPaneVisible,
        paneWidth = paneWidth,
        closePane = closePane,
        navigateTo = navigateTo
    )
}

/**
 * 侧边导航栏布局（大屏）：List-Detail 双区结构。
 * 左区为主界面 Pager（左侧留出 [SideNavRailWidth] 宽度），右区为详情/设置子页 pane。
 * pane 宽度由 [PaneNavState.paneWidth] 动画驱动（打开时主界面平滑压缩）；
 * 实际的 SideNavigationRail 在 MainOverlay 中渲染。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun SideNavLayout(
    mainPages: List<MainPage>,
    pagerState: PagerState,
    isPagerScrollEnabled: Boolean,
    paneNavController: NavHostController,
    paneWidth: Dp,
    settingsRepository: SettingsRepository,
    homeViewModel: com.aiexile.animetrack.ui.home.HomeViewModel,
    hazeState: HazeState,
    onNavigateToScreen: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = SideNavRailWidth)
    ) {
        // 左区：主界面（pane 打开时随 Row 自动压缩）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
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
                    navigationStyle = NavigationStyle.BOTTOM,
                    homeViewModel = homeViewModel,
                    onNavigateToScreen = onNavigateToScreen,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }

        // 右区：详情/设置子页 pane（宽度动画收起时为 0，内容被裁剪不可见）
        Box(
            modifier = Modifier
                .width(paneWidth)
                .fillMaxHeight()
                .clipToBounds()
        ) {
            PaneNavHost(
                paneNavController = paneNavController,
                settingsRepository = settingsRepository,
                onNavigate = onNavigateToScreen,
                sharedTransitionScope = sharedTransitionScope
            )
        }
    }
}

/**
 * 大屏右侧 pane NavHost：以空白 [Routes.PANE_ROOT] 为起点，承载详情页与设置子页。
 * 位于 SharedTransitionLayout 内部，共享元素转场与全屏 NavHost 使用同一 scope。
 * 沉浸式路由（播放器/WebDAV 浏览）经 onNavigate 分流至全屏 NavHost。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PaneNavHost(
    paneNavController: NavHostController,
    settingsRepository: SettingsRepository,
    onNavigate: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope
) {
    NavHost(
        navController = paneNavController,
        startDestination = Routes.PANE_ROOT,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        composable(Routes.PANE_ROOT) { /* 空白根：pane 收起状态 */ }
        // pane 内返回（返回键/顶栏返回箭头）：详情模式直接收起，设置层级链逐级返回
        val closePane: () -> Unit = {
            popPaneOrClose(paneNavController)
        }
        sharedDestinations(
            settingsRepository = settingsRepository,
            sharedTransitionScope = sharedTransitionScope,
            hostController = paneNavController,
            onNavigate = onNavigate,
            isInPane = true,
            onClosePane = closePane
        )
    }
}

/**
 * 详情页与设置子页的共享 destination 注册：全屏 NavHost 与大屏 pane NavHost 复用。
 *
 * @param hostController 本 NavHost 的 controller（返回栈与 popUpTo 语义按宿主栈处理）
 * @param onNavigate 分流导航：大屏下 pane 路由进右侧 pane、沉浸式路由（播放器等）进全屏；Compact 下全部进全屏
 * @param isInPane 是否渲染在右侧 pane：pane 中详情页禁用 sharedElement 飞行动画并强制单列布局
 * @param onClosePane pane 返回动作（详情模式直接收起，设置层级链逐级返回，见 popPaneOrClose）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun NavGraphBuilder.sharedDestinations(
    settingsRepository: SettingsRepository,
    sharedTransitionScope: SharedTransitionScope,
    hostController: NavHostController,
    onNavigate: (String) -> Unit,
    isInPane: Boolean = false,
    onClosePane: () -> Unit = {}
) {
    // 统一返回行为：pane 模式直接收起整个 pane；全屏模式逐层返回（保留返回栈记忆）
    val navigateBack: () -> Unit = if (isInPane) onClosePane else { hostController::popBackStack }

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
                onNavigateBack = navigateBack,
                onNavigateToPlayer = { id ->
                    onNavigate(Routes.player(id))
                },
                isInPane = isInPane,
                // pane 模式禁用 sharedElement 飞行动画（pane 展开动画已提供进入动效）
                sharedTransitionScope = if (isInPane) null else sharedTransitionScope,
                animatedVisibilityScope = if (isInPane) null else this@composable
            )
        }
    }

    // 关于
    composable(Routes.ABOUT) {
        AboutScreen(
            onBack = navigateBack,
            onNavigateDeveloper = { onNavigate(Routes.DEVELOPER) }
        )
    }

    // 定制导航栏
    composable(Routes.NAVIGATION_CUSTOMIZE) {
        NavigationCustomizeScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack
        )
    }

    // 外观
    composable(Routes.APPEARANCE) {
        AppearanceScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack
        )
    }

    // 功能
    composable(Routes.FEATURES) {
        FeaturesScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack
        )
    }

    // Bangumi 反向代理
    composable(Routes.BANGUMI_PROXY) {
        BangumiProxyScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack
        )
    }

    // 字体设置
    composable(Routes.FONT_SETTINGS) {
        FontSettingsScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack
        )
    }

    // 数据管理
    composable(Routes.DATA_MANAGE) {
        DataManageScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack,
            onNavigateWebDAV = { onNavigate(Routes.WEBDAV_SYNC) }
        )
    }

    // WebDAV 同步
    composable(Routes.WEBDAV_SYNC) {
        WebDAVSyncScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack,
            onNavigateAutoSync = { onNavigate(Routes.WEBDAV_AUTO_SYNC) }
        )
    }

    // WebDAV 自动同步
    composable(Routes.WEBDAV_AUTO_SYNC) {
        WebDAVAutoSyncScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack
        )
    }

    // 更新通知
    composable(Routes.UPDATE_NOTIFICATION) {
        UpdateNotificationScreen(
            settingsRepository = settingsRepository,
            onBack = navigateBack
        )
    }

    // 登录
    composable(Routes.LOGIN) {
        LoginScreen(
            onBack = navigateBack,
            onNavigateBilibiliLogin = { onNavigate(Routes.BILIBILI_LOGIN) },
            onNavigateBangumiLogin = { onNavigate(Routes.BANGUMI_LOGIN) },
            onNavigateBangumiAccount = { onNavigate(Routes.BANGUMI_ACCOUNT) },
            onNavigateUserLogin = { onNavigate(Routes.USER_LOGIN) },
            settingsRepository = settingsRepository
        )
    }

    // B站登录
    composable(Routes.BILIBILI_LOGIN) {
        BilibiliLoginScreen(
            onBack = navigateBack
        )
    }

    // Bangumi 登录
    composable(Routes.BANGUMI_LOGIN) {
        BangumiLoginScreen(
            onBack = navigateBack,
            onLoginSuccess = {
                hostController.navigate(Routes.BANGUMI_ACCOUNT) {
                    popUpTo(Routes.BANGUMI_LOGIN) { inclusive = true }
                }
            }
        )
    }

    // Bangumi 账号管理
    composable(Routes.BANGUMI_ACCOUNT) {
        BangumiAccountScreen(
            onBack = navigateBack
        )
    }

    // AnimeTrack 账号登录
    composable(Routes.USER_LOGIN) {
        UserLoginScreen(
            onBack = navigateBack,
            onNavigateRegister = { onNavigate(Routes.USER_REGISTER) }
        )
    }

    // 注册
    composable(Routes.USER_REGISTER) {
        UserRegisterScreen(
            onBack = navigateBack
        )
    }

    // 开发者
    composable(Routes.DEVELOPER) {
        DeveloperScreen(
            onBack = navigateBack,
            onNavigateToPlayerSettings = { onNavigate(Routes.PLAYER_SETTINGS) },
            // 重触发向导：大屏先收起 pane，向导为非 pane 路由会经 onNavigate 分流至全屏
            onNavigateToOnboarding = {
                onClosePane()
                onNavigate(Routes.ONBOARDING)
            }
        )
    }

    // 播放器设置
    composable(Routes.PLAYER_SETTINGS) {
        com.aiexile.animetrack.ui.player.PlayerSettingsScreen(
            onBack = navigateBack,
            onNavigateToPlayer = { onNavigate(Routes.player(0)) },
            onNavigateToWebDAVBrowse = { onNavigate(Routes.WEBDAV_BROWSE) }
        )
    }
}
