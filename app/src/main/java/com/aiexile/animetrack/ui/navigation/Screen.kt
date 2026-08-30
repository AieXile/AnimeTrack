package com.aiexile.animetrack.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

data class MainPage(val route: String, val title: String)

/** 路由常量 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val ANIME_DETAIL = "animeDetail/{animeId}"
    const val ABOUT = "about"
    const val NAVIGATION_CUSTOMIZE = "navigationCustomize"
    const val APPEARANCE = "appearance"
    const val FEATURES = "features"
    const val DATA_MANAGE = "dataManage"
    const val WEBDAV_SYNC = "webdavSync"
    const val WEBDAV_AUTO_SYNC = "webdavAutoSync"
    const val LOGIN = "login"
    const val BILIBILI_LOGIN = "bilibiliLogin"
    const val BANGUMI_LOGIN = "bangumiLogin"
    const val BANGUMI_ACCOUNT = "bangumiAccount"
    const val DEVELOPER = "developer"
    const val UPDATE_NOTIFICATION = "updateNotification"
    const val PLAYER = "player/{animeId}"
    const val WEBDAV_BROWSE = "webdavBrowse"
    const val PLAYER_SETTINGS = "playerSettings"
    const val PLAYER_WEBDAV_CONFIG = "playerWebdavConfig"
    const val BANGUMI_PROXY = "bangumiProxy"
    const val USER_LOGIN = "userLogin"
    const val USER_REGISTER = "userRegister"
    const val FONT_SETTINGS = "fontSettings"
    const val FEEDBACK = "feedback"
    const val FEEDBACK_HISTORY = "feedbackHistory"
    const val FEEDBACK_SESSION = "feedbackSession/{sessionId}"

    /** 带参数的详情路由 */
    fun animeDetail(animeId: Int, coverUrl: String?) =
        "animeDetail/$animeId${if (coverUrl != null) "?coverUrl=${android.net.Uri.encode(coverUrl)}" else ""}"

    /** 反馈会话详情路由 */
    fun feedbackSession(sessionId: String) = "feedbackSession/$sessionId"

    /** 播放器路由 */
    fun player(animeId: Int) = "player/$animeId"

    /** 详情页 navArguments */
    val animeDetailArguments = listOf(
        navArgument("animeId") { type = NavType.IntType },
        navArgument("coverUrl") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )

    /** 主页 navArguments */
    val mainArguments = listOf(
        navArgument("pageIndex") {
            type = NavType.IntType
            defaultValue = 0
        }
    )

    /** 反馈会话详情 navArguments */
    val feedbackSessionArguments = listOf(
        navArgument("sessionId") { type = NavType.StringType }
    )

    /** 播放器 navArguments */
    val playerArguments = listOf(
        navArgument("animeId") { type = NavType.IntType }
    )

    /** 设置子页面路由集合（用于动画判断） */
    val settingsSubRoutes = setOf(
        ABOUT, NAVIGATION_CUSTOMIZE, APPEARANCE, FEATURES,
        DATA_MANAGE, WEBDAV_SYNC, WEBDAV_AUTO_SYNC,
        LOGIN, BILIBILI_LOGIN, BANGUMI_LOGIN, BANGUMI_ACCOUNT,
        DEVELOPER, UPDATE_NOTIFICATION,
        PLAYER, WEBDAV_BROWSE, PLAYER_SETTINGS, PLAYER_WEBDAV_CONFIG, BANGUMI_PROXY,
        USER_LOGIN, USER_REGISTER, FONT_SETTINGS,
        FEEDBACK, FEEDBACK_HISTORY, FEEDBACK_SESSION
    )

    /** 大屏 pane 空白根路由（pane 关闭时停留在该页） */
    const val PANE_ROOT = "paneRoot"

    /**
     * 大屏 pane 路由集合：Expanded 宽度下这些路由在右侧 pane 打开而非全屏跳转。
     * 播放器与 WebDAV 文件浏览保持全屏（沉浸式场景）。
     * 注意：集合内为路由模式串（如 "animeDetail/{animeId}"），
     * 匹配时需比较首段路由名（见 [isPaneRoute]）。
     */
    val paneRoutes = setOf(
        ANIME_DETAIL, ABOUT, NAVIGATION_CUSTOMIZE, APPEARANCE, FEATURES,
        DATA_MANAGE, WEBDAV_SYNC, WEBDAV_AUTO_SYNC,
        LOGIN, BILIBILI_LOGIN, BANGUMI_LOGIN, BANGUMI_ACCOUNT,
        DEVELOPER, UPDATE_NOTIFICATION, PLAYER_SETTINGS, PLAYER_WEBDAV_CONFIG, BANGUMI_PROXY,
        USER_LOGIN, USER_REGISTER, FONT_SETTINGS,
        FEEDBACK, FEEDBACK_HISTORY, FEEDBACK_SESSION
    )

    /** 判断路由（可带参数）是否为番剧详情页 */
    fun isAnimeDetailRoute(route: String): Boolean =
        route.substringBefore('/') == ANIME_DETAIL.substringBefore('/')

    /** 判断路由（可带参数）是否属于 pane 路由：按首段路由名匹配，忽略参数段 */
    fun isPaneRoute(route: String): Boolean =
        paneRoutes.any { it.substringBefore('/') == route.substringBefore('/') }

    /** 二级页面过渡对（parent → child），使用 Set<Pair> 避免重复 key 覆盖 */
    val secondaryTransitions = setOf(
        DATA_MANAGE to WEBDAV_SYNC,
        WEBDAV_SYNC to WEBDAV_AUTO_SYNC,
        LOGIN to BILIBILI_LOGIN,
        LOGIN to BANGUMI_LOGIN,
        LOGIN to BANGUMI_ACCOUNT,
        BANGUMI_LOGIN to BANGUMI_ACCOUNT,
        LOGIN to USER_LOGIN,
        USER_LOGIN to USER_REGISTER,
        ABOUT to DEVELOPER,
        DATA_MANAGE to WEBDAV_BROWSE,
        DEVELOPER to PLAYER_SETTINGS,
        PLAYER_SETTINGS to PLAYER_WEBDAV_CONFIG,
        // 视频播放设置 → WebDAV 媒体浏览：pop 返回时走 isSecondaryBackward，
        // 底层页放大淡入就位（否则误入"设置子页间过渡"分支，底层页从右滑入导致退出观感异常）
        PLAYER_SETTINGS to WEBDAV_BROWSE,
        // 反馈层级链：聊天主页 → 历史 → 会话详情
        FEEDBACK to FEEDBACK_HISTORY,
        FEEDBACK_HISTORY to FEEDBACK_SESSION
    )
}

fun buildMainPages(showFavorites: Boolean, showTimeline: Boolean, showSchedule: Boolean): List<MainPage> {
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
