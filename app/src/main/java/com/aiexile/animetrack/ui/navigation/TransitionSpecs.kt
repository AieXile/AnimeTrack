package com.aiexile.animetrack.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

private const val ANIM_DURATION = 300
private const val ANIM_DURATION_NO_SHARED = 200
private val ANIM_EASING = FastOutSlowInEasing

private val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = ANIM_DURATION, easing = ANIM_EASING)
private val fadeSpec = tween<Float>(durationMillis = ANIM_DURATION, easing = ANIM_EASING)
private val scaleSpec = tween<Float>(durationMillis = ANIM_DURATION, easing = ANIM_EASING)

/** 页内侧边面板（右侧半透明抽屉）进出场时长 */
const val SIDE_PANEL_ANIM_DURATION = 250

/** 页内侧边面板进入动画：从右滑入 + 淡入（播放器字幕/音轨选择等） */
fun sidePanelEnter(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(SIDE_PANEL_ANIM_DURATION, easing = ANIM_EASING)
    ) { fullWidth -> fullWidth } +
        fadeIn(animationSpec = tween(SIDE_PANEL_ANIM_DURATION - 80))

/** 页内侧边面板退出动画：向右滑出 + 淡出 */
fun sidePanelExit(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(SIDE_PANEL_ANIM_DURATION, easing = ANIM_EASING)
    ) { fullWidth -> fullWidth } +
        fadeOut(animationSpec = tween((SIDE_PANEL_ANIM_DURATION * 0.6).toInt()))

/** 判断是否为沉浸式全屏页（播放器 / WebDAV 浏览），使用独立的垂直推拉转场。
 *  同时供 NavHost 容器背景切换使用：黑色页面互转时容器底色需为黑，避免缩放淡入露白闪。 */
internal fun isImmersiveRoute(route: String?): Boolean {
    if (route == null) return false
    return route == Routes.PLAYER || route == Routes.WEBDAV_BROWSE
}

/** 设置页进入动画：从右侧滑入 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterSettings() =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = slideSpec
    )

/** 设置页退出动画：缩小 + 淡出 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitSettings() =
    scaleOut(targetScale = 0.95f, animationSpec = scaleSpec) +
        fadeOut(targetAlpha = 0.7f, animationSpec = fadeSpec)

/** 设置页返回时进入动画：放大 + 淡入 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterFromSettings() =
    scaleIn(initialScale = 0.95f, animationSpec = scaleSpec) +
        fadeIn(
            initialAlpha = 0.0f,
            animationSpec = tween(durationMillis = ANIM_DURATION, delayMillis = 80, easing = ANIM_EASING)
        )

/** 设置页返回时退出动画：向右滑出 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.exitToSettings() =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = slideSpec
    )

/** 判断是否为 Main 路由（带参数时 destination.route 为 "main?pageIndex={pageIndex}"） */
private fun isMainRoute(route: String?): Boolean {
    if (route == null) return false
    return route == Routes.MAIN || route.startsWith("${Routes.MAIN}?")
}

/** 判断是否为详情页路由 */
private fun isDetailRoute(route: String?): Boolean {
    if (route == null) return false
    return route.startsWith("animeDetail")
}

/** 判断详情页是否使用 sharedElement 动画（通过 coverUrl 参数区分） */
private fun hasSharedElement(entry: NavBackStackEntry): Boolean {
    return entry.arguments?.getString("coverUrl") != null
}

/** 判断是否为设置子页面路由（不含详情页） */
private fun isSettingsSubRoute(route: String?): Boolean {
    if (route == null) return false
    return route in Routes.settingsSubRoutes
}

/** 判断是否为二级页面过渡（从 parent 进入 child） */
private fun isSecondaryForward(from: String?, to: String?): Boolean {
    if (from == null || to == null) return false
    return Routes.secondaryTransitions.any { it.first == from && it.second == to }
}

/** 判断是否为二级页面返回（从 child 返回 parent） */
private fun isSecondaryBackward(from: String?, to: String?): Boolean {
    if (from == null || to == null) return false
    return Routes.secondaryTransitions.any { it.first == to && it.second == from }
}

/**
 * 全局进入动画：根据目标路由和来源路由决定进入动画
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition(): EnterTransition {
    val fromRoute = initialState.destination.route
    val toRoute = targetState.destination.route

    return when {
        // ===== 沉浸式全屏页（播放器 / WebDAV 浏览）：与二三级页面一致的水平推拉 =====
        // push：新页从右滑入（主页→播放器 / 播放器→浏览页）
        (isMainRoute(fromRoute) && isImmersiveRoute(toRoute)) ||
            (fromRoute == Routes.PLAYER && toRoute == Routes.WEBDAV_BROWSE) ->
            enterSettings()

        // pop 返回（浏览页→播放器 / 播放器→主页）：旧页右滑出，本页放大淡入
        (fromRoute == Routes.WEBDAV_BROWSE && toRoute == Routes.PLAYER) ||
            (isImmersiveRoute(fromRoute) && isMainRoute(toRoute)) ->
            enterFromSettings()

        // 引导页 → 主页：瞬间切换
        isMainRoute(fromRoute) && toRoute == Routes.ONBOARDING ->
            fadeIn(animationSpec = tween(0))

        // 主页 → 详情页：fadeIn 配合 sharedElement 飞入动画
        isDetailRoute(toRoute) -> {
            val duration = if (hasSharedElement(targetState)) ANIM_DURATION else ANIM_DURATION_NO_SHARED
            fadeIn(animationSpec = tween(duration))
        }

        // 详情页 → 主页：fadeIn 配合 sharedElement 飞出动画
        isDetailRoute(fromRoute) && isMainRoute(toRoute) -> {
            val duration = if (hasSharedElement(initialState)) ANIM_DURATION else ANIM_DURATION_NO_SHARED
            fadeIn(animationSpec = tween(duration))
        }

        // 主页 → 设置子页面
        isMainRoute(fromRoute) && isSettingsSubRoute(toRoute) ->
            enterSettings()

        // 设置子页面 → 主页
        isSettingsSubRoute(fromRoute) && isMainRoute(toRoute) ->
            enterFromSettings()

        // 二级页面前进
        isSecondaryForward(fromRoute, toRoute) ->
            enterSettings()

        // 二级页面返回
        isSecondaryBackward(fromRoute, toRoute) ->
            enterFromSettings()

        // 设置子页面之间的过渡（如从 Login 返回时经过其他设置页）
        isSettingsSubRoute(fromRoute) && isSettingsSubRoute(toRoute) ->
            enterSettings()

        else -> fadeIn(animationSpec = tween(300))
    }
}

/**
 * 全局退出动画：根据目标路由和来源路由决定退出动画
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.exitTransition(): ExitTransition {
    val fromRoute = initialState.destination.route
    val toRoute = targetState.destination.route

    return when {
        // ===== 沉浸式全屏页（播放器 / WebDAV 浏览）：与二三级页面一致的水平推拉 =====
        // push 时旧页缩小淡出（主页→播放器 / 播放器→浏览页）
        (isMainRoute(fromRoute) && isImmersiveRoute(toRoute)) ||
            (fromRoute == Routes.PLAYER && toRoute == Routes.WEBDAV_BROWSE) ->
            exitSettings()

        // pop 时当前沉浸式页向右滑出（浏览页→播放器 / 播放器→主页）
        (fromRoute == Routes.WEBDAV_BROWSE && toRoute == Routes.PLAYER) ||
            (isImmersiveRoute(fromRoute) && isMainRoute(toRoute)) ->
            exitToSettings()

        // 引导页 → 主页：瞬间切换
        isMainRoute(fromRoute) && toRoute == Routes.ONBOARDING ->
            fadeOut(animationSpec = tween(0))

        // 详情页退出：fadeOut 配合 sharedElement 飞出动画
        isDetailRoute(fromRoute) -> {
            val duration = if (hasSharedElement(initialState)) ANIM_DURATION else ANIM_DURATION_NO_SHARED
            fadeOut(animationSpec = tween(duration))
        }

        // 进入详情页时主页退出：fadeOut 配合 sharedElement 飞入动画
        isDetailRoute(toRoute) -> {
            val duration = if (hasSharedElement(targetState)) ANIM_DURATION else ANIM_DURATION_NO_SHARED
            fadeOut(animationSpec = tween(duration))
        }

        // 主页 → 设置子页面
        isMainRoute(fromRoute) && isSettingsSubRoute(toRoute) ->
            exitSettings()

        // 设置子页面 → 主页
        isSettingsSubRoute(fromRoute) && isMainRoute(toRoute) ->
            exitToSettings()

        // 二级页面前进
        isSecondaryForward(fromRoute, toRoute) ->
            exitSettings()

        // 二级页面返回
        isSecondaryBackward(fromRoute, toRoute) ->
            exitToSettings()

        // 设置子页面之间的过渡
        isSettingsSubRoute(fromRoute) && isSettingsSubRoute(toRoute) ->
            exitSettings()

        else -> fadeOut(animationSpec = tween(300))
    }
}
