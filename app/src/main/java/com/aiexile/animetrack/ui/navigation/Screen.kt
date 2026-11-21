package com.aiexile.animetrack.ui.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

data class MainPage(val route: String, val title: String)

/** 路由常量 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    /**
     * 详情页路由模板。
     *
     * 注意：query 参数必须写进路由模板，Navigation 才会解析（`isParameterizedQuery`
     * 为 false 时整段 query 会被丢弃）。这里只声明共享元素前缀；`coverUrl` 仍作为
     * 普通 query 传入，供需要的场景使用，但不参与参数解析。
     */
    const val ANIME_DETAIL = "animeDetail/{animeId}?sharedKeyPrefix={sharedKeyPrefix}"
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
    const val WIDGET_GUIDE = "widgetGuide"
    const val DAILY_BROADCAST = "dailyBroadcast"
    const val PLAYER = "player/{animeId}?localUri={localUri}&localTitle={localTitle}&localMediaId={localMediaId}&webPlay={webPlay}&sharedKeyPrefix={sharedKeyPrefix}"
    const val WEBDAV_BROWSE = "webdavBrowse"
    const val PLAYER_SETTINGS = "playerSettings"
    const val LOCAL_MEDIA = "localMedia"
    const val WEB_PLAYBACK = "webPlayback?animeId={animeId}&animeTitle={animeTitle}"
    const val PLAYER_WEBDAV_CONFIG = "playerWebdavConfig"
    const val BANGUMI_PROXY = "bangumiProxy"
    const val USER_LOGIN = "userLogin"
    const val USER_REGISTER = "userRegister"
    const val EMAIL_BIND = "emailBind/{bindToken}"
    const val FORGOT_PASSWORD = "forgotPassword"
    const val FONT_SETTINGS = "fontSettings"
    const val DEVICE_FONT_PICKER = "deviceFontPicker"
    const val FEEDBACK = "feedback"
    const val FEEDBACK_HISTORY = "feedbackHistory"
    const val FEEDBACK_SESSION = "feedbackSession/{sessionId}"
    const val FEEDBACK_ADMIN = "feedbackAdmin"
    const val FEEDBACK_ADMIN_SESSION = "feedbackAdminSession/{sessionId}"
    const val PRIVACY_POLICY = "privacyPolicy"

    /**
     * 带参数的详情路由。
     *
     * [sharedKeyPrefix] 用于区分共享元素来源页（主页 / 收藏页）：两页同在 Pager 内，
     * 滑动时可能同时组合，同 key 的封面会被误判为一次匹配。详情页用同一前缀拼接
     * 封面 key，才能与对应来源页配对。
     */
    fun animeDetail(animeId: Int, coverUrl: String?, sharedKeyPrefix: String? = null) =
        "animeDetail/$animeId" +
            (if (coverUrl != null) "?coverUrl=${android.net.Uri.encode(coverUrl)}" else "") +
            when {
                sharedKeyPrefix == null -> ""
                coverUrl != null -> "&sharedKeyPrefix=${android.net.Uri.encode(sharedKeyPrefix)}"
                else -> "?sharedKeyPrefix=${android.net.Uri.encode(sharedKeyPrefix)}"
            }

    /** 反馈会话详情路由 */
    fun feedbackSession(sessionId: String) = "feedbackSession/$sessionId"

    /** 管理员反馈会话详情路由 */
    fun feedbackAdminSession(sessionId: String) = "feedbackAdminSession/$sessionId"

    /** 绑定邮箱路由（bindToken 为服务端签发的短期 JWT） */
    fun emailBind(bindToken: String) = "emailBind/$bindToken"

    /** 播放器路由，可选传入本地 content URI；无 URI 时保持原有空播放器行为 */
    fun player(
        animeId: Int,
        localUri: String? = null,
        localTitle: String? = null,
        localMediaId: Long? = null,
        webPlay: Boolean = false,
        /** 详情页共享元素命名空间：从详情页进入时携带，播放器小窗按同一 key 做封面 Morph */
        sharedKeyPrefix: String? = null
    ): String {
        val query = buildList {
            localUri?.let { add("localUri=${android.net.Uri.encode(it)}") }
            localTitle?.let { add("localTitle=${android.net.Uri.encode(it)}") }
            localMediaId?.let { add("localMediaId=$it") }
            if (webPlay) add("webPlay=true")
            // null 表示「不是从详情页进入」，不参与封面 Morph；空串是合法的默认命名空间
            sharedKeyPrefix?.let { add("sharedKeyPrefix=${android.net.Uri.encode(it)}") }
        }
        return "player/$animeId" + if (query.isEmpty()) "" else "?${query.joinToString("&")}"
    }

    /** 网页播放页路由；从详情页进入时携带番剧上下文，用于绑定历史与进度 */
    fun webPlayback(animeId: Int? = null, animeTitle: String? = null): String {
        val query = buildList {
            animeId?.takeIf { it > 0 }?.let { add("animeId=$it") }
            animeTitle?.takeIf { it.isNotBlank() }
                ?.let { add("animeTitle=${android.net.Uri.encode(it)}") }
        }
        return "webPlayback" + if (query.isEmpty()) "" else "?${query.joinToString("&")}"
    }

    /** 网页播放页 navArguments */
    val webPlaybackArguments = listOf(
        navArgument("animeId") {
            type = NavType.IntType
            defaultValue = 0
        },
        navArgument("animeTitle") {
            type = NavType.StringType
            defaultValue = ""
        }
    )

    /** 详情页 navArguments */
    val animeDetailArguments = listOf(
        navArgument("animeId") { type = NavType.IntType },
        navArgument("coverUrl") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("sharedKeyPrefix") {
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

    /** 管理员反馈会话详情 navArguments */
    val feedbackAdminSessionArguments = listOf(
        navArgument("sessionId") { type = NavType.StringType }
    )

    /** 绑定邮箱 navArguments */
    val emailBindArguments = listOf(
        navArgument("bindToken") { type = NavType.StringType }
    )

    /** 播放器 navArguments */
    val playerArguments = listOf(
        navArgument("animeId") { type = NavType.IntType },
        navArgument("localUri") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("localTitle") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("localMediaId") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("webPlay") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("sharedKeyPrefix") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )

    /** 设置子页面路由集合（用于动画判断） */
    val settingsSubRoutes = setOf(
        ABOUT, NAVIGATION_CUSTOMIZE, APPEARANCE, FEATURES,
        DATA_MANAGE, WEBDAV_SYNC, WEBDAV_AUTO_SYNC,
        LOGIN, BILIBILI_LOGIN, BANGUMI_LOGIN, BANGUMI_ACCOUNT,
        DEVELOPER, UPDATE_NOTIFICATION, WIDGET_GUIDE, DAILY_BROADCAST,
        PLAYER, WEBDAV_BROWSE, PLAYER_SETTINGS, LOCAL_MEDIA, WEB_PLAYBACK, PLAYER_WEBDAV_CONFIG, BANGUMI_PROXY,
        USER_LOGIN, USER_REGISTER, EMAIL_BIND, FORGOT_PASSWORD, FONT_SETTINGS,
        DEVICE_FONT_PICKER,
        FEEDBACK, FEEDBACK_HISTORY, FEEDBACK_SESSION, PRIVACY_POLICY,
        FEEDBACK_ADMIN, FEEDBACK_ADMIN_SESSION
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
        DEVELOPER, UPDATE_NOTIFICATION, WIDGET_GUIDE, DAILY_BROADCAST, PLAYER_SETTINGS, LOCAL_MEDIA, WEB_PLAYBACK, PLAYER_WEBDAV_CONFIG, BANGUMI_PROXY,
        USER_LOGIN, USER_REGISTER, EMAIL_BIND, FORGOT_PASSWORD, FONT_SETTINGS,
        DEVICE_FONT_PICKER,
        FEEDBACK, FEEDBACK_HISTORY, FEEDBACK_SESSION, PRIVACY_POLICY,
        FEEDBACK_ADMIN, FEEDBACK_ADMIN_SESSION
    )

    /** 判断路由（可带参数）是否为番剧详情页 */
    fun isAnimeDetailRoute(route: String): Boolean =
        route.substringBefore('/') == ANIME_DETAIL.substringBefore('/')

    /** 判断路由（可带参数）是否属于 pane 路由：按首段路由名匹配，忽略参数段 */
    fun isPaneRoute(route: String): Boolean =
        paneRoutes.any { pane ->
            pane.substringBefore('/') == route.substringBefore('/').substringBefore('?')
        }

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
        USER_LOGIN to EMAIL_BIND,
        USER_LOGIN to FORGOT_PASSWORD,
        ABOUT to DEVELOPER,
        ABOUT to PRIVACY_POLICY,
        DATA_MANAGE to WEBDAV_BROWSE,
        DEVELOPER to PLAYER_SETTINGS,
        PLAYER_SETTINGS to PLAYER_WEBDAV_CONFIG,
        // 视频播放设置 → WebDAV 媒体浏览：pop 返回时走 isSecondaryBackward，
        // 底层页放大淡入就位（否则误入"设置子页间过渡"分支，底层页从右滑入导致退出观感异常）
        PLAYER_SETTINGS to WEBDAV_BROWSE,
        PLAYER_SETTINGS to LOCAL_MEDIA,
        PLAYER_SETTINGS to WEB_PLAYBACK,
        PLAYER_SETTINGS to PLAYER,
        LOCAL_MEDIA to PLAYER,
        WEB_PLAYBACK to PLAYER,
        ANIME_DETAIL to WEB_PLAYBACK,
        PLAYER to WEBDAV_BROWSE,
        // 反馈层级链：聊天主页 → 历史 → 会话详情
        FEEDBACK to FEEDBACK_HISTORY,
        FEEDBACK_HISTORY to FEEDBACK_SESSION,
        // 管理员反馈链：聊天主页 → 反馈管理 → 管理会话详情
        FEEDBACK to FEEDBACK_ADMIN,
        FEEDBACK_ADMIN to FEEDBACK_ADMIN_SESSION,
        // 字体设置 → 本机字体选择：pane 内返回时回到字体设置而不是收起整个 pane
        FONT_SETTINGS to DEVICE_FONT_PICKER
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
