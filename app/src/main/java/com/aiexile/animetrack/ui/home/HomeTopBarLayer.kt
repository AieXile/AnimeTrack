package com.aiexile.animetrack.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.StatusBarMode
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.LiquidGlassKind
import com.aiexile.animetrack.ui.components.drawLiquidGlass
import com.aiexile.animetrack.ui.components.liquidGlassSpec
import com.aiexile.animetrack.ui.navigation.SideNavRailWidth
import com.kyant.backdrop.backdrops.LayerBackdrop
import dev.chrisbanes.haze.HazeState

/** 顶栏进出主界面的时长：与主页退出淡出（无共享元素 200ms）对齐，
 *  保证玻璃档顶栏在主页内容层（采样源）退出组合前完成动画 */
private const val TOP_BAR_ANIM_DURATION = 200

/**
 * 主页顶栏层：顶栏本体 + 状态栏遮罩 + 顶栏动作迁移 FAB。
 *
 * 独立于 MainOverlay 挂载：离开主界面（打开详情页等）时顶栏向上收起并淡出，
 * 返回主界面时再自上方落下淡入，两个方向都不再瞬断；底部导航栏与主页 FAB
 * 仍由 MainOverlay 管理，保持原有显隐行为。转场期间主页内容仍在组合中，
 * 玻璃档顶栏的采样源可用。
 *
 * @param visible 主界面（MAIN）路由是否可见：false 时播放向上收起退出动画，
 *   true 时播放自上方落下的入场动画；仅首次显示（冷启动/引导页进入）不播动画
 * @param isHomePage 当前 Pager 页是否为首页；非首页时顶栏内容立即消失（与原行为一致）
 * @param isCurrentPage 首页页签是否为当前页（搜索框自动聚焦用）
 */
@Composable
fun HomeTopBarLayer(
    homeViewModel: HomeViewModel,
    settingsRepository: SettingsRepository,
    visible: Boolean,
    isHomePage: Boolean,
    isCurrentPage: Boolean,
    fabLocation: FabLocation,
    style: ChromeStyle,
    hazeState: HazeState,
    backdrop: LayerBackdrop,
    blurConfig: AdvancedBlurConfig,
    useSideNavigation: Boolean,
    isCapsuleNav: Boolean,
    paneWidth: Dp,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 从 settingsRepository 读取 HomeTopBar 所需状态；初始值取同步缓存：
    // 本层随路由切换销毁重建，collectAsState 默认值会使欢迎语瞬态解析为
    // 随机前缀，与已播放标记不匹配导致返回主页时重播打字动画
    val customGreeting by settingsRepository.customGreeting
        .collectAsStateWithLifecycle(settingsRepository.cachedCustomGreeting())
    val greetingEffectStyle by settingsRepository.greetingEffectStyle
        .collectAsStateWithLifecycle(settingsRepository.cachedGreetingEffectStyle())
    val showSearchButton by settingsRepository.showSearchButton.collectAsStateWithLifecycle(true)

    // 从 homeViewModel 读取列表状态，用于判断搜索按钮可见性
    val animeList by homeViewModel.animeList.collectAsStateWithLifecycle()
    val filteredAnimeListItems by homeViewModel.filteredAnimeListItems.collectAsStateWithLifecycle()
    val hasAnime = animeList.isNotEmpty()
    val hasFilteredItems = filteredAnimeListItems.isNotEmpty()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    // 顶栏收起状态由 ViewModel 的 StateFlow 提供，Compose 层只收集
    val isTopBarHidden by homeViewModel.isTopBarHidden.collectAsStateWithLifecycle()
    // 顶栏收起后的状态栏处理方式（全屏/留白遮罩/实心），初始值取同步缓存避免闪变
    val statusBarMode by settingsRepository.statusBarMode
        .collectAsStateWithLifecycle(settingsRepository.cachedStatusBarMode())
    // 主页顶栏外观设置：底部圆角、是否跟随玻璃材质档位（初始值取同步缓存避免闪变）
    val topBarRounded by settingsRepository.topBarRounded
        .collectAsStateWithLifecycle(settingsRepository.cachedTopBarRounded())
    val topBarGlassTheme by settingsRepository.topBarGlassThemeEnabled
        .collectAsStateWithLifecycle(settingsRepository.cachedTopBarGlassThemeEnabled())
    // 「下滑隐藏顶栏」开关仅用于状态栏遮罩随收拢淡入
    val hideTopBarOnScroll by settingsRepository.hideTopBarOnScrollEnabled
        .collectAsStateWithLifecycle(settingsRepository.cachedHideTopBarOnScroll())

    // 首帧可见时（冷启动/引导页刚进入）直接显示，不播放落下动画；
    // 之后每次从二级页返回主界面再播放。LaunchedEffect 在首帧组合之后置位，
    // 首帧的 AnimatedVisibility 读到 false，因此首次显示不会触发 enter 动画
    var hasBeenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) hasBeenVisible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // 返回主界面：顶栏自屏幕上方落下并淡入，与退出动画对称；
        // 首次显示不播动画，避免冷启动时顶栏从屏幕外掉下来
        enter = if (hasBeenVisible) {
            fadeIn(
                animationSpec = tween(durationMillis = TOP_BAR_ANIM_DURATION, easing = FastOutSlowInEasing)
            ) + slideInVertically(
                animationSpec = tween(durationMillis = TOP_BAR_ANIM_DURATION, easing = FastOutSlowInEasing)
            ) { fullHeight -> -fullHeight }
        } else {
            EnterTransition.None
        },
        exit = fadeOut(
            animationSpec = tween(durationMillis = TOP_BAR_ANIM_DURATION, easing = FastOutSlowInEasing)
        ) + slideOutVertically(
            animationSpec = tween(durationMillis = TOP_BAR_ANIM_DURATION, easing = FastOutSlowInEasing)
        ) { fullHeight -> -fullHeight }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 主页 TopBar（仅在 home 页可见）：开启「下滑隐藏顶栏」时，顶栏通过
            // graphics-shapes Morph 真收拢成迁移 FAB（终点=按钮行位置，纵向零位移），
            // 内容由 AnimeGrid contentPadding 预留顶栏高度，收拢/展开时内容零移动
            if (isHomePage) {
                // 收拢进度（0=展开, 1=收拢为 FAB）：顶栏与迁移 FAB 共用，保证 morph 连续
                val topBarCollapse by animateFloatAsState(
                    targetValue = if (isTopBarHidden) 1f else 0f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "topBarCollapse"
                )
                // 顶栏按钮构成决定收拢终点宽度：单按钮 48dp，搜索+添加组合 97dp
                val topBarShowSearch = showSearchButton && hasAnime && hasFilteredItems
                val topBarShowAdd = fabLocation == FabLocation.TOP_BAR || useSideNavigation
                // 加号 FAB 在胶囊导航旁时，右上角只剩单个搜索 FAB：改为与加号一致的全圆，
                // 顶栏 morph 终点圆角同步改为圆形，收拢终点与 FAB 轮廓重合（组合 FAB 不适用）
                val circleTopBarSearchFab = !useSideNavigation && isCapsuleNav &&
                    fabLocation == FabLocation.NAV_BAR && topBarShowSearch && !topBarShowAdd
                // 右上角无任何按钮时无 morph 终点（迁移 FAB 也不会出现），
                // 顶栏不再收缩成按钮形状，改为整体淡出隐藏
                val hasTopBarActions = topBarShowSearch || topBarShowAdd
                val morphTargetWidth = if (topBarShowSearch && topBarShowAdd) {
                    TopBarActionsCombinedWidth
                } else {
                    TopBarActionsSingleWidth
                }
                // 玻璃档顶栏与 FAB/胶囊同材质：HAZE 毛玻璃 / LIQUID_GLASS 折射玻璃；
                // 设置中关闭「顶栏跟随玻璃材质」后顶栏保持实色（收拢时仍向 FAB 容器色插值）
                val topBarGlassEnabled = style != ChromeStyle.STANDARD && topBarGlassTheme

                // 完全收拢后移除顶栏（避免透明区域拦截点击）
                if (topBarCollapse < 1f) {
                    HomeTopBar(
                        viewModel = homeViewModel,
                        customGreeting = customGreeting,
                        greetingEffectStyle = greetingEffectStyle,
                        showSearchButton = showSearchButton,
                        fabLocation = fabLocation,
                        // 大屏无 FAB，顶栏始终提供添加入口（不受 FAB 位置设置约束）
                        alwaysShowAddButton = useSideNavigation,
                        isCurrentPage = isCurrentPage,
                        hasAnime = hasAnime,
                        hasFilteredItems = hasFilteredItems,
                        onAddClick = onAddClick,
                        morphCollapse = topBarCollapse,
                        // 玻璃档收拢全程保持玻璃（与 FAB 无缝衔接，不向实色插值）；
                        // 标准档收拢时向 FAB 容器色插值，避免收拢终点色块断层
                        morphTargetColor = if (topBarGlassEnabled) {
                            null
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        style = style,
                        hazeState = hazeState,
                        backdrop = backdrop,
                        rounded = topBarRounded,
                        glassThemeEnabled = topBarGlassTheme,
                        blurConfig = blurConfig,
                        // 大屏适配：顶栏从侧边导航栏右侧开始；pane 打开时 end 让出 pane 宽度
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .then(
                                if (useSideNavigation) Modifier.padding(start = SideNavRailWidth) else Modifier
                            )
                            .padding(end = paneWidth)
                            .then(
                                if (hasTopBarActions) {
                                    // 真 morph：几何连续变形为按钮行位置的 Squircle / 全圆（问候语早期淡出在 TopBar 内处理）
                                    Modifier.topBarCollapseMorph(
                                        collapseProgress = topBarCollapse,
                                        targetWidth = morphTargetWidth,
                                        targetCornerRadius = if (circleTopBarSearchFab) {
                                            TopBarMorphCircleCorner
                                        } else {
                                            TopBarMorphCorner
                                        }
                                    )
                                } else {
                                    // 无按钮无 morph 终点：整体随收拢进度淡出（问候语已在 TopBar 内先行淡出）
                                    Modifier.graphicsLayer { alpha = 1f - topBarCollapse }
                                }
                            )
                    )
                }

                // 状态栏处理方式（「下滑隐藏顶栏」开启时生效，绘制在顶栏之后）：
                // 全屏 = 不渲染，内容滚入透明状态栏区域；留白遮罩 = 表面色纵向渐变盖在内容上；
                // 实心状态栏 = 实色条（HomeScreen 顶部预留同步收紧，内容被顶到其下沿），
                // 高级模糊时顶栏为毛玻璃，实色条保证状态栏区域始终实底。
                // 液态玻璃开启时实心条改为玻璃条（与 FAB/胶囊同质感），全屏/留白遮罩不受影响。
                // 随收拢进度淡入，展开时被顶栏自身背景覆盖，无感知
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
                    } else if (style == ChromeStyle.LIQUID_GLASS) {
                        // 液态玻璃条：仅模糊+提亮（Bar 规格不做 lens 折射，避免薄条边缘畸变）；
                        // 通栏玻璃关闭默认高光/外阴影，避免顶部出现一条白色亮边
                        Modifier
                            .height(statusBarHeight)
                            .drawLiquidGlass(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                spec = liquidGlassSpec(LiquidGlassKind.Bar),
                                highlight = null,
                                shadow = null
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
                // 点击搜索 → 不展开顶栏，FAB 左边框向左展开为悬浮搜索条。
                // 液态玻璃开启时 FAB/搜索条为折射玻璃渲染（与悬浮胶囊同质感）
                TopBarActionsFloating(
                    collapseProgress = topBarCollapse,
                    isSearchActive = homeUiState.isLocalSearchActive,
                    searchQuery = homeUiState.localSearchQuery,
                    onSearchQueryChange = { homeViewModel.updateLocalSearchQuery(it) },
                    onCloseSearch = { homeViewModel.clearLocalSearch() },
                    showSearch = topBarShowSearch,
                    showAdd = topBarShowAdd,
                    onSearchClick = { homeViewModel.startLocalSearch() },
                    onAddClick = onAddClick,
                    hazeState = hazeState,
                    style = style,
                    blurConfig = blurConfig,
                    backdrop = backdrop,
                    circleFab = circleTopBarSearchFab,
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
            }
        }
    }
}
