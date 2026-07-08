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
    const val DEVELOPER = "developer"
    const val UPDATE_NOTIFICATION = "updateNotification"
    const val PLAYER = "player/{animeId}"
    const val WEBDAV_BROWSE = "webdavBrowse"
    const val PLAYER_SETTINGS = "playerSettings"
    const val BANGUMI_PROXY = "bangumiProxy"
    const val USER_LOGIN = "userLogin"
    const val USER_REGISTER = "userRegister"

    /** 带参数的详情路由 */
    fun animeDetail(animeId: Int, coverUrl: String?) =
        "animeDetail/$animeId${if (coverUrl != null) "?coverUrl=${android.net.Uri.encode(coverUrl)}" else ""}"

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

    /** 播放器 navArguments */
    val playerArguments = listOf(
        navArgument("animeId") { type = NavType.IntType }
    )

    /** 设置子页面路由集合（用于动画判断） */
    val settingsSubRoutes = setOf(
        ABOUT, NAVIGATION_CUSTOMIZE, APPEARANCE, FEATURES,
        DATA_MANAGE, WEBDAV_SYNC, WEBDAV_AUTO_SYNC,
        LOGIN, BILIBILI_LOGIN, BANGUMI_LOGIN,
        DEVELOPER, UPDATE_NOTIFICATION,
        PLAYER, WEBDAV_BROWSE, PLAYER_SETTINGS, BANGUMI_PROXY,
        USER_LOGIN, USER_REGISTER
    )

    /** 二级页面过渡对（parent → child），使用 Set<Pair> 避免重复 key 覆盖 */
    val secondaryTransitions = setOf(
        DATA_MANAGE to WEBDAV_SYNC,
        WEBDAV_SYNC to WEBDAV_AUTO_SYNC,
        LOGIN to BILIBILI_LOGIN,
        LOGIN to BANGUMI_LOGIN,
        LOGIN to USER_LOGIN,
        USER_LOGIN to USER_REGISTER,
        ABOUT to DEVELOPER,
        DATA_MANAGE to WEBDAV_BROWSE,
        DEVELOPER to PLAYER_SETTINGS
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
