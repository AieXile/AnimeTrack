package com.aiexile.animetrack.ui.settings

import androidx.annotation.StringRes
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.navigation.Routes
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import java.util.concurrent.ConcurrentHashMap

/**
 * 可搜索的设置项：指向某个设置子页面内的具体选项。
 *
 * @param route 所属子页面路由
 * @param key 子页面内定位 key（用于滚动 + 高亮定位；null 表示仅页面级跳转）
 * @param titleRes 标题资源
 * @param descRes 描述资源
 * @param keywords 额外搜索关键词（同义词、英文等）
 */
data class SearchableSetting(
    val route: String,
    val key: String?,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descRes: Int? = null,
    val keywords: List<String> = emptyList()
)

/** 设置主页路由（用于 TMDB API Key 弹窗等主页内条目） */
const val SETTINGS_MAIN_ROUTE = "settings"

/** 拼音输出格式：小写、无声调（如「主」→ "zhu"） */
private val pinyinFormat = HanyuPinyinOutputFormat().apply {
    caseType = HanyuPinyinCaseType.LOWERCASE
    toneType = HanyuPinyinToneType.WITHOUT_TONE
}

/** 单字拼音缓存：设置条目文本有限，避免每次按键重复转换 */
private val charPinyinCache = ConcurrentHashMap<Char, String>()

/** 取单字拼音（多音字取第一个读音）；非汉字返回 null */
private fun charPinyin(c: Char): String? {
    charPinyinCache[c]?.let { return it }
    val py = try {
        PinyinHelper.toHanyuPinyinStringArray(c, pinyinFormat)?.firstOrNull()
    } catch (_: Exception) {
        null
    }
    if (py != null) charPinyinCache[c] = py
    return py
}

/**
 * 模糊匹配：原文包含（忽略大小写）、拼音全拼、拼音首字母任一命中即匹配。
 * 如「zt」/「zhuti」/「主题」均可命中「主题配色」。
 */
fun fuzzyMatch(query: String, text: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val lowerText = text.lowercase()
    if (lowerText.contains(q)) return true

    // 文本转拼音：全拼串 + 首字母串
    val full = StringBuilder()
    val initials = StringBuilder()
    lowerText.forEach { c ->
        val py = charPinyin(c)
        if (py != null) {
            full.append(py)
            initials.append(py.first())
        } else {
            full.append(c)
            initials.append(c)
        }
    }
    return full.contains(q) || initials.contains(q)
}

/**
 * 设置搜索索引：覆盖设置主页入口与各子页面内的具体设置项。
 * 新增设置项时在此登记即可被搜索到。
 */
val settingsSearchIndex: List<SearchableSetting> = listOf(
    // ---- 登录 ----
    SearchableSetting(
        route = Routes.LOGIN, key = null,
        titleRes = R.string.settings_login, descRes = R.string.settings_login_subtitle,
        keywords = listOf("账号", "账户", "同步", "授权", "Bilibili", "B站", "哔哩哔哩", "Bangumi", "AnimeTrack", "login", "account", "sync")
    ),
    SearchableSetting(
        route = Routes.LOGIN, key = "auto_sync",
        titleRes = R.string.login_screen_auto_sync, descRes = R.string.login_screen_auto_sync_desc,
        keywords = listOf("自动同步", "bilibili同步", "autosync")
    ),
    SearchableSetting(
        route = Routes.LOGIN, key = "hide_avatar",
        titleRes = R.string.login_screen_hide_avatar, descRes = R.string.login_screen_hide_avatar_desc,
        keywords = listOf("头像", "avatar")
    ),

    // ---- 外观与主题 ----
    SearchableSetting(
        route = Routes.APPEARANCE, key = "mode",
        titleRes = R.string.appearance_mode_title,
        keywords = listOf("深色", "暗色", "夜间", "浅色", "跟随系统", "自动", "dark", "light", "auto")
    ),
    SearchableSetting(
        route = Routes.APPEARANCE, key = "color",
        titleRes = R.string.appearance_color_title, descRes = R.string.appearance_color_subtitle,
        keywords = listOf("配色", "颜色", "主题色", "色彩", "color")
    ),

    // ---- 字体 ----
    SearchableSetting(
        route = Routes.FONT_SETTINGS, key = "font",
        titleRes = R.string.font_title, descRes = R.string.font_select_font,
        keywords = listOf("系统字体", "MiSans", "自定义字体", "font")
    ),
    SearchableSetting(
        route = Routes.FONT_SETTINGS, key = "custom_font",
        titleRes = R.string.font_custom_title, descRes = R.string.font_custom_subtitle,
        keywords = listOf("导入字体", "ttf", "字体文件")
    ),
    SearchableSetting(
        route = Routes.FONT_SETTINGS, key = "language",
        titleRes = R.string.font_language_title, descRes = R.string.font_language_subtitle,
        keywords = listOf("切换语言", "简体中文", "English", "language")
    ),

    // ---- 定制导航栏 ----
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "nav_style",
        titleRes = R.string.nav_custom_style_title,
        keywords = listOf("底部导航", "悬浮胶囊", "经典", "导航样式")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "advanced_blur",
        titleRes = R.string.nav_custom_advanced_blur, descRes = R.string.nav_custom_advanced_blur_desc,
        keywords = listOf("毛玻璃", "模糊", "blur")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "topbar",
        titleRes = R.string.nav_custom_topbar_title, descRes = R.string.nav_custom_topbar_subtitle,
        keywords = listOf("顶栏", "顶部栏")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "hide_topbar",
        titleRes = R.string.nav_custom_hide_topbar_on_scroll, descRes = R.string.nav_custom_hide_topbar_on_scroll_desc,
        keywords = listOf("下滑隐藏", "收起顶栏")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "statusbar",
        titleRes = R.string.nav_custom_statusbar_mode,
        keywords = listOf("状态栏", "全屏", "留白遮罩", "实心")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "label_mode",
        titleRes = R.string.nav_custom_label_mode_title, descRes = R.string.nav_custom_label_mode_subtitle,
        keywords = listOf("图标与文字", "仅图标", "仅文字")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "fab",
        titleRes = R.string.nav_custom_fab_title,
        keywords = listOf("添加番剧", "悬浮按钮", "右下角", "fab")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "content",
        titleRes = R.string.nav_custom_content_title, descRes = R.string.nav_custom_content_subtitle,
        keywords = listOf("收藏", "时间线", "追番看板", "导航入口")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "greeting",
        titleRes = R.string.nav_custom_greeting_title, descRes = R.string.nav_custom_greeting_subtitle,
        keywords = listOf("欢迎语", "首页标题", "打字机")
    ),
    SearchableSetting(
        route = Routes.NAVIGATION_CUSTOMIZE, key = "typing_effect",
        titleRes = R.string.nav_custom_typing_effect, descRes = R.string.nav_custom_typing_effect_desc,
        keywords = listOf("打字效果", "逐字显示", "动画")
    ),

    // ---- 功能 ----
    SearchableSetting(
        route = Routes.FEATURES, key = "search_button",
        titleRes = R.string.features_search_button, descRes = R.string.features_search_button_desc,
        keywords = listOf("主界面", "右上角")
    ),
    SearchableSetting(
        route = Routes.FEATURES, key = "update_reminder",
        titleRes = R.string.features_today_update_reminder, descRes = R.string.features_today_update_reminder_desc,
        keywords = listOf("今日更新", "横幅", "提醒")
    ),
    SearchableSetting(
        route = Routes.FEATURES, key = "calendar_button",
        titleRes = R.string.features_calendar_preview_button, descRes = R.string.features_calendar_preview_button_desc,
        keywords = listOf("日历", "预览")
    ),
    SearchableSetting(
        route = Routes.FEATURES, key = "series_stack",
        titleRes = R.string.features_series_stack, descRes = R.string.features_series_stack_desc,
        keywords = listOf("系列", "折叠", "同系列")
    ),
    SearchableSetting(
        route = Routes.FEATURES, key = "auto_complete",
        titleRes = R.string.features_auto_complete, descRes = R.string.features_auto_complete_desc,
        keywords = listOf("自动补全", "补全")
    ),
    SearchableSetting(
        route = Routes.FEATURES, key = "celebration",
        titleRes = R.string.features_completed_celebration, descRes = R.string.features_completed_celebration_desc,
        keywords = listOf("庆祝", "看完", "动画")
    ),
    SearchableSetting(
        route = Routes.FEATURES, key = "rating",
        titleRes = R.string.features_rating_standard_source, descRes = R.string.features_rating_standard_source_desc,
        keywords = listOf("评分标准", "评分来源", "手动评分", "tmdb", "rating")
    ),

    // ---- 代理 ----
    SearchableSetting(
        route = Routes.BANGUMI_PROXY, key = "reverse_proxy",
        titleRes = R.string.bangumi_proxy_reverse_group, descRes = R.string.bangumi_proxy_enable_reverse,
        keywords = listOf("反向代理", "镜像", "bangumi", "proxy")
    ),
    SearchableSetting(
        route = Routes.BANGUMI_PROXY, key = "http_proxy",
        titleRes = R.string.bangumi_proxy_http_group, descRes = R.string.bangumi_proxy_enable_http,
        keywords = listOf("http代理", "网络", "端口")
    ),

    // ---- 数据管理 ----
    SearchableSetting(
        route = Routes.DATA_MANAGE, key = "import",
        titleRes = R.string.data_manage_import_markdown, descRes = R.string.data_manage_import_markdown_subtitle,
        keywords = listOf("导入", "markdown", "md")
    ),
    SearchableSetting(
        route = Routes.DATA_MANAGE, key = "export",
        titleRes = R.string.data_manage_export_data, descRes = R.string.data_manage_export_data_subtitle,
        keywords = listOf("导出", "备份")
    ),
    SearchableSetting(
        route = Routes.DATA_MANAGE, key = "webdav",
        titleRes = R.string.data_manage_webdav_sync, descRes = R.string.data_manage_webdav_sync_subtitle,
        keywords = listOf("云同步", "webdav", "云端备份", "恢复")
    ),
    SearchableSetting(
        route = Routes.WEBDAV_SYNC, key = null,
        titleRes = R.string.webdav_sync_title, descRes = R.string.data_manage_webdav_sync_subtitle,
        keywords = listOf("webdav", "服务器", "地址", "用户名", "密码", "备份策略")
    ),

    // ---- 更新通知 ----
    SearchableSetting(
        route = Routes.UPDATE_NOTIFICATION, key = "toggle",
        titleRes = R.string.update_notif_anime_update, descRes = R.string.update_notif_anime_update_desc,
        keywords = listOf("通知", "推送", "提醒", "notification", "push")
    ),
    SearchableSetting(
        route = Routes.UPDATE_NOTIFICATION, key = "time",
        titleRes = R.string.update_notif_time, descRes = R.string.update_notif_time_desc,
        keywords = listOf("通知时间", "推送时间", "提醒时间")
    ),

    // ---- TMDB API Key（设置主页弹窗） ----
    SearchableSetting(
        route = SETTINGS_MAIN_ROUTE, key = "tmdb",
        titleRes = R.string.settings_tmdb_api_key, descRes = R.string.settings_tmdb_api_key_desc,
        keywords = listOf("刮削", "元数据", "tmdb", "api", "key")
    ),

    // ---- 关于 ----
    SearchableSetting(
        route = Routes.ABOUT, key = null,
        titleRes = R.string.settings_about,
        keywords = listOf("版本", "更新日志", "开源", "github", "许可证", "about", "version")
    )
)
