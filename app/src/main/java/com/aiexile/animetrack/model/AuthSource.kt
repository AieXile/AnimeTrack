package com.aiexile.animetrack.model

import com.aiexile.animetrack.ui.navigation.Routes

/**
 * 数据源枚举：统一三源（Bangumi / Bilibili / 自有服务器）的 token 失效状态映射。
 */
enum class AuthSource {
    BANGUMI,
    BILIBILI,
    USER;

    /** 展示名称（与登录管理页条目标题一致） */
    val displayName: String
        get() = when (this) {
            BANGUMI -> "Bangumi"
            BILIBILI -> "Bilibili"
            USER -> "AnimeTrack"
        }

    /** 对应的登录页路由 */
    val loginRoute: String
        get() = when (this) {
            BANGUMI -> Routes.BANGUMI_LOGIN
            BILIBILI -> Routes.BILIBILI_LOGIN
            USER -> Routes.USER_LOGIN
        }
}
