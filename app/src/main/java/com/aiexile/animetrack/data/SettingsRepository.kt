package com.aiexile.animetrack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiexile.animetrack.AnimeTrackApp
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.model.DarkStyle
import com.aiexile.animetrack.model.ProxyType
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.font.DeviceFontFamily
import com.aiexile.animetrack.data.font.DeviceFontSelection
import com.aiexile.animetrack.data.font.FontNameReader
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayMode
import com.aiexile.animetrack.ui.theme.ThemePreset
import com.aiexile.animetrack.ui.theme.isMonetColorSupported
import com.aiexile.animetrack.ui.icons.IconPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class FontFamilyType(val displayName: String) {
    SYSTEM("系统字体"),
    MISANS("MiSans"),
    CUSTOM("自定义字体"),
    DEVICE("本机字体")
}

enum class AppLanguage(val displayName: String, val code: String) {
    SIMPLIFIED_CHINESE("简体中文", "zh-CN"),
    ENGLISH("English", "en"),
    TRADITIONAL_CHINESE("繁體中文", "zh-TW")
}

/** 评分标准：使用源评分或手动打分 */
enum class RatingStandard {
    SOURCE,
    MANUAL
}

/**
 * 主界面卡片信息栏（标题下方一行）左右槽位可选的显示字段。
 * [NONE] 表示该槽位留空，卡片高度保持不变。
 */
enum class CardInfoField {
    /** 观看进度，如 8/12 */
    PROGRESS,
    /** 评分，如 ★8.2 */
    RATING,
    /** 放送日期（首播），当年显示 MM-dd */
    AIR_DATE,
    /** 放送星期，如 周五 */
    AIR_WEEKDAY,
    /** 未看集数，如 +3 */
    UNWATCHED,
    /** 不显示 */
    NONE
}

/**
 * 主界面列表排序方式：
 * - [RECENT_PROGRESS] 自动排序（默认）：按状态区块排列——正在观看 → 计划观看 → 未放送 →
 *   已看完 → 已弃番，区块内部再按各自规则排序（在追 / 计划 / 未放送 / 已弃番按最近修改，
 *   已看完按看完日期）。枚举名沿用历史值，避免已持久化的 DataStore 值需要迁移
 * - [TITLE_ALPHABETICAL] 首字母排序：不切状态区块，整列表按标题拼音升序
 *   （汉字转全拼、字母数字小写、标点跳过）；置顶卡片仍固定在最前
 * - [MANUAL] 按用户手动拖拽保存的顺序（manualOrder），不再跟随进度自动重排；
 *   未参与过手动排序的新条目（manualOrder 为 null）视为最新，排在置顶之后、手动顺序之前
 */
enum class ListSortMode {
    RECENT_PROGRESS,
    TITLE_ALPHABETICAL,
    MANUAL
}

class SettingsRepository(private val context: Context) {

    companion object {
        private const val TAG = "Settings"
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val THEME_PRESET_KEY = stringPreferencesKey("theme_preset")
        private val DARK_STYLE_KEY = stringPreferencesKey("dark_style")
        private val ICON_PACK_KEY = stringPreferencesKey("icon_pack")
        private val SHOW_FAVORITES_KEY = booleanPreferencesKey("show_favorites")
        private val SHOW_TIMELINE_KEY = booleanPreferencesKey("show_timeline")
        private val SHOW_SCHEDULE_KEY = booleanPreferencesKey("show_schedule")
        private val NAVIGATION_STYLE_KEY = stringPreferencesKey("navigation_style")
        private val FAB_LOCATION_KEY = stringPreferencesKey("fab_location")
        private val NAVIGATION_LABEL_MODE_KEY = stringPreferencesKey("navigation_label_mode")
        // 旧「双布尔」key：仅用于一次性迁移读取，不再作为活跃设置写入
        private val CAPSULE_ADVANCED_BLUR_KEY = booleanPreferencesKey("capsule_advanced_blur")
        private val CAPSULE_LIQUID_GLASS_KEY = booleanPreferencesKey("capsule_liquid_glass")
        private val CAPSULE_LIQUID_GLASS_BACKDROP_BLUR_KEY = booleanPreferencesKey("capsule_liquid_glass_backdrop_blur")
        // 界面外壳材质档位（ChromeStyle.name），替代上面两个互斥布尔
        private val CHROME_STYLE_KEY = stringPreferencesKey("chrome_style")
        private val HIDE_TOPBAR_ON_SCROLL_KEY = booleanPreferencesKey("hide_topbar_on_scroll")
        private val TOP_BAR_ROUNDED_KEY = booleanPreferencesKey("top_bar_rounded")
        private val TOP_BAR_GLASS_THEME_KEY = booleanPreferencesKey("top_bar_glass_theme")
        private val STATUS_BAR_MODE_KEY = stringPreferencesKey("status_bar_mode")
        private val ADVANCED_BLUR_RADIUS_KEY = floatPreferencesKey("advanced_blur_radius")
        private val ADVANCED_BLUR_BACKGROUND_ALPHA_KEY = floatPreferencesKey("advanced_blur_background_alpha")
        private val ADVANCED_BLUR_TINT_ALPHA_KEY = floatPreferencesKey("advanced_blur_tint_alpha")
        private val ADVANCED_BLUR_NOISE_KEY = floatPreferencesKey("advanced_blur_noise")
        private val CUSTOM_GREETING_KEY = stringPreferencesKey("custom_greeting")
        private val AUTO_COMPLETE_KEY = booleanPreferencesKey("auto_complete_enabled")
        private val COMPLETED_TOAST_KEY = booleanPreferencesKey("completed_toast_enabled")
        private val HIDE_BANGUMI_AVATAR_KEY = booleanPreferencesKey("hide_bangumi_avatar")
        private val GREETING_TYPING_EFFECT_KEY = booleanPreferencesKey("greeting_typing_effect")
        private val GREETING_EFFECT_STYLE_KEY = stringPreferencesKey("greeting_effect_style")
        private val SHOW_UPDATE_BANNER_KEY = booleanPreferencesKey("show_update_banner")
        private val FEEDBACK_REPLY_PILL_ENABLED_KEY = booleanPreferencesKey("feedback_reply_pill_enabled")
        private val SHOW_CALENDAR_BUTTON_KEY = booleanPreferencesKey("show_calendar_button")
        private val SHOW_DAILY_BROADCAST_BUTTON_KEY = booleanPreferencesKey("show_daily_broadcast_button")
        private val SHOW_SEARCH_BUTTON_KEY = booleanPreferencesKey("show_search_button")
        private val SERIES_STACK_ENABLED_KEY = booleanPreferencesKey("series_stack_enabled")
        private val SERIES_STACK_TOP_KEY = stringPreferencesKey("series_stack_top_indices")
        // 卡片→详情页状态角标转场策略（StatusBadgeTransitionMode.name），默认飞向标题行
        private val STATUS_BADGE_TRANSITION_MODE_KEY = stringPreferencesKey("status_badge_transition_mode")
        // 卡片信息栏左右槽位字段（CardInfoField.name），默认保持进度 + 评分
        private val CARD_INFO_LEFT_KEY = stringPreferencesKey("card_info_left")
        private val CARD_INFO_RIGHT_KEY = stringPreferencesKey("card_info_right")
        private val LIST_SORT_MODE_KEY = stringPreferencesKey("list_sort_mode")
        private val RATING_STANDARD_KEY = stringPreferencesKey("rating_standard")
        private val SKIPPED_VERSION_KEY = stringPreferencesKey("skipped_version")
        private val WEBDAV_URL_KEY = stringPreferencesKey("webdav_url")
        private val WEBDAV_USERNAME_KEY = stringPreferencesKey("webdav_username")
        private val WEBDAV_PASSWORD_KEY = stringPreferencesKey("webdav_password")
        private val WEBDAV_BACKUP_STRATEGY_KEY = intPreferencesKey("webdav_backup_strategy")
        private val WEBDAV_RESTORE_MODE_KEY = intPreferencesKey("webdav_restore_mode")
        private val WEBDAV_LAST_SYNC_TIME_KEY = longPreferencesKey("webdav_last_sync_time")
        private val WEBDAV_LAST_AUTO_SYNC_TIME_KEY = longPreferencesKey("webdav_last_auto_sync_time")
        private val LAST_COVER_BACKFILL_TIME_KEY = longPreferencesKey("last_cover_backfill_time")
        private val WEBDAV_MEDIA_PATH_KEY = stringPreferencesKey("webdav_media_path")
        private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
        private val PRIVACY_POLICY_ACCEPTED_KEY = booleanPreferencesKey("privacy_policy_accepted")
        private val CRASH_REPORT_ENABLED_KEY = booleanPreferencesKey("crash_report_enabled")
        private val HELP_IMPROVE_DIALOG_SHOWN_KEY = booleanPreferencesKey("help_improve_dialog_shown")
        private val DEVELOPER_MODE_KEY = booleanPreferencesKey("developer_mode")
        private val SHARE_BUTTON_ENABLED_KEY = booleanPreferencesKey("share_button_enabled")
        private val TMDB_API_KEY_KEY = stringPreferencesKey("tmdb_api_key")
        private val WEBDAV_AUTO_SYNC_ENABLED_KEY = booleanPreferencesKey("webdav_auto_sync_enabled")
        private val WEBDAV_AUTO_SYNC_ON_DATA_CHANGE_KEY = booleanPreferencesKey("webdav_auto_sync_on_data_change")
        private val WEBDAV_AUTO_SYNC_ON_APP_OPEN_KEY = booleanPreferencesKey("webdav_auto_sync_on_app_open")
        private val WEBDAV_AUTO_SYNC_SCHEDULED_KEY = booleanPreferencesKey("webdav_auto_sync_scheduled")
        private val WEBDAV_AUTO_SYNC_INTERVAL_KEY = intPreferencesKey("webdav_auto_sync_interval")
        private val WEBDAV_AUTO_SYNC_WIFI_ONLY_KEY = booleanPreferencesKey("webdav_auto_sync_wifi_only")
        private val WEBDAV_AUTO_SYNC_USE_CUSTOM_STRATEGY_KEY = booleanPreferencesKey("webdav_auto_sync_use_custom_strategy")
        private val WEBDAV_AUTO_SYNC_BACKUP_STRATEGY_KEY = intPreferencesKey("webdav_auto_sync_backup_strategy")
        private val WEBDAV_AUTO_SYNC_LAST_SCHEDULED_TIME_KEY = longPreferencesKey("webdav_auto_sync_last_scheduled_time")

        private val UPDATE_NOTIFICATION_ENABLED_KEY = booleanPreferencesKey("update_notification_enabled")
        private val UPDATE_NOTIFICATION_HOUR_KEY = intPreferencesKey("update_notification_hour")
        private val UPDATE_NOTIFICATION_MINUTE_KEY = intPreferencesKey("update_notification_minute")
        private val UPDATE_NOTIFICATION_VISIBLE_KEY = booleanPreferencesKey("update_notification_visible")

        private val PLAYER_HUB_VISIBLE_KEY = booleanPreferencesKey("player_hub_visible")
        private val EXTERNAL_PLAYER_ENTRY_VISIBLE_KEY = booleanPreferencesKey("external_player_entry_visible")
        private val EXTERNAL_PLAY_MODE_KEY = stringPreferencesKey("external_play_mode")
        /** 播放器专属 WebDAV 配置：与备份同步相互独立 */
        private val PLAYER_WEBDAV_URL_KEY = stringPreferencesKey("player_webdav_url")
        private val PLAYER_WEBDAV_USERNAME_KEY = stringPreferencesKey("player_webdav_username")
        private val PLAYER_WEBDAV_PASSWORD_KEY = stringPreferencesKey("player_webdav_password")
        private val PLAYER_WEBDAV_TRUST_ALL_CERTS_KEY = booleanPreferencesKey("player_webdav_trust_all_certs")
        private val PLAYER_DEFAULT_SPEED_KEY = floatPreferencesKey("player_default_speed")
        private val PLAYER_HARDWARE_ACCELERATION_KEY = booleanPreferencesKey("player_hardware_acceleration")
        private val PLAYER_REMEMBER_POSITION_KEY = booleanPreferencesKey("player_remember_position")
        private val PLAYER_AUTO_PLAY_NEXT_KEY = booleanPreferencesKey("player_auto_play_next")
        private val PLAYER_LONG_PRESS_SPEED_KEY = floatPreferencesKey("player_long_press_speed")
        private val PLAYER_AUTO_LANDSCAPE_KEY = booleanPreferencesKey("player_auto_landscape")

        private val BANGUMI_PROXY_ENABLED_KEY = booleanPreferencesKey("bangumi_proxy_enabled")
        private val BANGUMI_PROXY_HOST_KEY = stringPreferencesKey("bangumi_proxy_host")
        private val BANGUMI_MIRROR_ENABLED_KEY = booleanPreferencesKey("bangumi_mirror_enabled")

        const val DEFAULT_BANGUMI_PROXY_HOST = ""

        /** Bangumi 镜像数据源：与账号同步服务器同源，服务器侧含搜索回源兜底 */
        const val BANGUMI_MIRROR_HOST = "www.aiexile.top"

        private val HTTP_PROXY_ENABLED_KEY = booleanPreferencesKey("http_proxy_enabled")
        private val HTTP_PROXY_HOST_KEY = stringPreferencesKey("http_proxy_host")
        private val HTTP_PROXY_PORT_KEY = intPreferencesKey("http_proxy_port")
        private val HTTP_PROXY_TYPE_KEY = stringPreferencesKey("http_proxy_type")
        private val HTTP_PROXY_USERNAME_KEY = stringPreferencesKey("http_proxy_username")
        private val HTTP_PROXY_PASSWORD_KEY = stringPreferencesKey("http_proxy_password")

        const val DEFAULT_HTTP_PROXY_HOST = ""
        const val DEFAULT_HTTP_PROXY_PORT = 0
        const val DEFAULT_HTTP_PROXY_TYPE = "HTTP"
        const val DEFAULT_HTTP_PROXY_USERNAME = ""
        const val DEFAULT_HTTP_PROXY_PASSWORD = ""

        private val USER_AUTH_BASE_URL_KEY = stringPreferencesKey("user_auth_base_url")
        const val DEFAULT_USER_AUTH_BASE_URL = "https://www.aiexile.top/api"

        // 高级模糊（毛玻璃）默认参数：与历史硬编码值保持一致
        const val DEFAULT_ADVANCED_BLUR_RADIUS = 24f
        const val DEFAULT_ADVANCED_BLUR_BACKGROUND_ALPHA = 1f
        const val DEFAULT_ADVANCED_BLUR_TINT_ALPHA = 0.4f
        const val DEFAULT_ADVANCED_BLUR_NOISE = 0f

        const val DEFAULT_TMDB_API_KEY = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIwZTFhNzUyY2Q3ZWI4ZjE4MzljMzBlZDNjZGRmMTI1ZCIsIm5iZiI6MTc3OTk2NzU2Ny4zMTEsInN1YiI6IjZhMTgyNjRmNTNmZTM5ZjRhNzE1ZGM2NyIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.R7iyiJrJR2Fs7uE65xveVGaPnAkzJHnMyQ4OvM0zZ5o"

        private val FONT_FAMILY_KEY = stringPreferencesKey("font_family")
        private val CUSTOM_FONT_PATH_KEY = stringPreferencesKey("custom_font_path")
        // 本机字体选择：家族名 + 代表字面文件 + TTC face 索引（路径失效时按家族名重新解析）
        private val DEVICE_FONT_FAMILY_KEY = stringPreferencesKey("device_font_family")
        private val DEVICE_FONT_PATH_KEY = stringPreferencesKey("device_font_path")
        private val DEVICE_FONT_TTC_INDEX_KEY = intPreferencesKey("device_font_ttc_index")
        private val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
        // SharedPreferences 同步镜像 key（供 attachBaseContext 冷启动时同步读取）
        private const val APP_LANGUAGE_PREF = "app_language"
        private const val PRIVACY_ACCEPTED_PREF = "privacy_policy_accepted"
        private val LAST_ACTIVITY_DATE_KEY = stringPreferencesKey("last_activity_date")
        private val READ_ANNOUNCEMENT_IDS_KEY = stringPreferencesKey("read_announcement_ids")
        // 设备唯一标识（首次生成后持久化，卸载重装会变，作为活跃统计主键）
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

        // 各偏好的唯一默认值：Flow 与 cachedXxx() 共用同一常量，避免两处默认值不一致
        private val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
        private val DEFAULT_THEME_PRESET = ThemePreset.MONO_BLACK
        private val DEFAULT_DARK_STYLE = DarkStyle.BOOST
        private val DEFAULT_ICON_PACK = IconPack.MATERIAL_SYMBOLS
        private val DEFAULT_NAVIGATION_STYLE = NavigationStyle.CAPSULE
        private val DEFAULT_FAB_LOCATION = FabLocation.NAV_BAR
        private val DEFAULT_NAVIGATION_LABEL_MODE = NavigationLabelMode.ICON_AND_TEXT
        private val DEFAULT_CHROME_STYLE = ChromeStyle.STANDARD
        private const val DEFAULT_CAPSULE_LIQUID_GLASS_BACKDROP_BLUR = true
        private const val DEFAULT_HIDE_TOPBAR_ON_SCROLL = false
        private const val DEFAULT_TOP_BAR_ROUNDED = true
        private const val DEFAULT_TOP_BAR_GLASS_THEME = true
        private val DEFAULT_STATUS_BAR_MODE = StatusBarMode.SCRIM
        private const val DEFAULT_CUSTOM_GREETING = ""
        private val DEFAULT_GREETING_EFFECT_STYLE = GreetingEffectStyle.TYPING
        private val DEFAULT_STATUS_BADGE_TRANSITION_MODE = StatusBadgeTransitionMode.FLY_TO_TITLE
        private val DEFAULT_CARD_INFO_LEFT = CardInfoField.PROGRESS
        private val DEFAULT_CARD_INFO_RIGHT = CardInfoField.RATING
        private val DEFAULT_LIST_SORT_MODE = ListSortMode.RECENT_PROGRESS
        private val DEFAULT_RATING_STANDARD = RatingStandard.SOURCE
        private const val DEFAULT_CRASH_REPORT_ENABLED = true
        private const val DEFAULT_UPDATE_NOTIFICATION_ENABLED = false
        private val DEFAULT_EXTERNAL_PLAY_MODE = ExternalPlayMode.AUTO
        private val DEFAULT_PROXY_TYPE = ProxyType.HTTP
    }

    // 偏好内存缓存：DataStore 首次读取是异步的，collectAsState(默认值) 会先渲染默认值
    // 再跳变为持久化值（开关/选项闪变）。缓存 DataStore 首次加载结果后，
    // 每个偏好流的首个发射即为持久化值，消除 UI 初始闪烁
    private val prefCache = ConcurrentHashMap<Preferences.Key<*>, Any?>()

    private fun <T> preferenceFlow(key: Preferences.Key<T>, default: T): Flow<T> = flow {
        // 缓存命中（App 启动后 DataStore 已完成首读）时立即发射持久化值，无闪变；
        // 未命中（冷启动早期）退化为原行为：仅 DataStore 流
        @Suppress("UNCHECKED_CAST")
        (prefCache[key] as? T)?.let { emit(it) }
        emitAll(context.dataStore.data.map { it[key] ?: default })
    }.distinctUntilChanged()

    private suspend fun <T> setPreference(key: Preferences.Key<T>, value: T) {
        prefCache[key] = value
        try {
            context.dataStore.edit { it[key] = value }
        } catch (e: Exception) {
            // 内存缓存已改而磁盘未写：本次会话表现正常、重启后回退，
            // 是「设置不保存 / 开关自己变回去」类反馈的唯一线索
            AppLogManager.e(TAG, "偏好写入失败: key=${key.name}", e)
        }
    }

    /** 枚举偏好的统一模板：存储层只保存 name，缺失 / 非法值回退 [default] */
    private inline fun <reified T : Enum<T>> enumPreference(
        key: Preferences.Key<String>,
        default: T
    ): Flow<T> = preferenceFlow(key, default.name)
        .map { value -> enumValueOrDefault(value, default) }

    /** 按 name 解析枚举，非法值回退 [default] */
    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        T::class.java.enumConstants?.firstOrNull { it.name == value } ?: default

    /** 同步缓存读取：缓存未就绪（冷启动早期）时回退 [default] */
    @Suppress("UNCHECKED_CAST")
    private fun <T> cachedOrDefault(key: Preferences.Key<T>, default: T): T =
        prefCache[key] as? T ?: default

    /** 枚举偏好的同步缓存读取：与 [enumPreference] 共用同一 [default] */
    private inline fun <reified T : Enum<T>> cachedEnumOrDefault(
        key: Preferences.Key<String>,
        default: T
    ): T = enumValueOrDefault(prefCache[key] as? String, default)

    @Volatile
    var currentTmdbApiKey: String? = null
        private set

    // Bangumi 反向代理运行时缓存：拦截器直接读取，避免每次请求查 DataStore
    @Volatile
    var bangumiProxyEnabled: Boolean = false
        private set

    @Volatile
    var bangumiProxyHost: String = DEFAULT_BANGUMI_PROXY_HOST
        private set

    // Bangumi 镜像数据源运行时缓存：拦截器直接读取（优先级高于反向代理）
    @Volatile
    var bangumiMirrorEnabled: Boolean = false
        private set

    // HTTP 普通代理运行时缓存：OkHttpClient 直接读取
    @Volatile
    var httpProxyEnabled: Boolean = false
        private set

    @Volatile
    var httpProxyHost: String = DEFAULT_HTTP_PROXY_HOST
        private set

    @Volatile
    var httpProxyPort: Int = DEFAULT_HTTP_PROXY_PORT
        private set

    @Volatile
    var httpProxyType: ProxyType = ProxyType.HTTP
        private set

    @Volatile
    var httpProxyUsername: String = DEFAULT_HTTP_PROXY_USERNAME
        private set

    @Volatile
    var httpProxyPassword: String = DEFAULT_HTTP_PROXY_PASSWORD
        private set

    @Volatile
    var userAuthBaseUrl: String = DEFAULT_USER_AUTH_BASE_URL
        private set

    // 语言设置：使用 SharedPreferences 做同步镜像，供 attachBaseContext 冷启动时立即读取
    // （DataStore 首次读取是异步的，冷启动早期 appLanguageCache 尚未填充，会导致语言设置丢失）
    private val localePrefs = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)

    // 隐私政策同意状态同步镜像：Application/MainActivity 冷启动需同步判断是否同意，
    // 决定 JPush、崩溃上报等个人信息收集组件是否初始化（DataStore 首读是异步的，来不及）
    private val privacyPrefs = context.getSharedPreferences("privacy_prefs", Context.MODE_PRIVATE)

    /**
     * 一次性迁移：旧的「高级模糊 / 液态玻璃」互斥布尔 → 新 [ChromeStyle] 枚举。
     *
     * 必须在 init 首读协程的 `prefCache.putAll(...)` 之前完成：否则冷启动首帧
     * `cachedChromeStyle()` 读不到新 key，会先渲染 STANDARD 再跳变为持久化档位。
     * 迁移结果立即写入 prefCache，保证迁移完成与缓存填充之间也不闪变。
     */
    private suspend fun migrateChromeStyleIfNeeded() {
        var migrated: String? = null
        context.dataStore.edit { prefs ->
            if (prefs[CHROME_STYLE_KEY] == null) {
                val legacy = chromeStyleFromLegacy(
                    liquidGlass = prefs[CAPSULE_LIQUID_GLASS_KEY] == true,
                    advancedBlur = prefs[CAPSULE_ADVANCED_BLUR_KEY] == true
                )
                prefs[CHROME_STYLE_KEY] = legacy.name
                migrated = legacy.name
            }
        }
        migrated?.let { prefCache[CHROME_STYLE_KEY] = it }
    }

    /**
     * 一次性迁移：旧「自定义字体」合并进「本机字体」。
     *
     * 旧版本只保存一个自定义字体文件路径；合并后统一用本机字体选择的
     * 「家族名 + 文件 + ttcIndex」持久化。文件不存在时回退系统字体，
     * 避免继续停留在已失效的 CUSTOM 类型。
     */
    private suspend fun migrateCustomFontToDeviceIfNeeded() {
        var migratedToDevice = false
        context.dataStore.edit { prefs ->
            if (prefs[FONT_FAMILY_KEY] != FontFamilyType.CUSTOM.name) return@edit
            val path = prefs[CUSTOM_FONT_PATH_KEY].orEmpty()
            val file = File(path)
            val names = if (path.isNotBlank() && file.isFile && file.canRead()) {
                FontNameReader.read(file, 0)
            } else {
                null
            }
            if (names != null) {
                prefs[DEVICE_FONT_FAMILY_KEY] = names.family.trim()
                    .ifBlank { file.nameWithoutExtension }
                prefs[DEVICE_FONT_PATH_KEY] = file.absolutePath
                prefs[DEVICE_FONT_TTC_INDEX_KEY] = 0
                prefs[FONT_FAMILY_KEY] = FontFamilyType.DEVICE.name
                migratedToDevice = true
            } else {
                prefs[FONT_FAMILY_KEY] = FontFamilyType.SYSTEM.name
            }
        }
        if (migratedToDevice) {
            AppLogManager.i(TAG, "旧自定义字体已迁移为本机字体")
        }
    }

    init {
        // 复用 Application 级协程作用域（替代 GlobalScope）加载缓存；
        // Application 未就绪时兜底自建一次性作用域，保证初始化任务仍能执行。
        val scope = (context.applicationContext as? AnimeTrackApp)?.appScope
            ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            // 首读失败（文件损坏 / IO 异常）不能拖垮整个应用：记录后保留默认值。
            // 此前异常直接逃逸会导致偏好缓存全空，表现为「设置全部失效 / 开关闪回」且无任何线索
            try {
                // 旧双布尔 → ChromeStyle 迁移必须先于缓存填充与任何首读
                migrateChromeStyleIfNeeded()
                // 旧「自定义字体」→ 本机字体迁移同样必须先于首读，避免 UI 先显示 CUSTOM
                migrateCustomFontToDeviceIfNeeded()

                val prefs = context.dataStore.data.first()
                currentTmdbApiKey = prefs[TMDB_API_KEY_KEY] ?: DEFAULT_TMDB_API_KEY
                // 填充偏好内存缓存：此后所有 preferenceFlow 首个发射即为持久化值
                prefCache.putAll(prefs.asMap())
                bangumiProxyEnabled = prefs[BANGUMI_PROXY_ENABLED_KEY] ?: false
                bangumiProxyHost = prefs[BANGUMI_PROXY_HOST_KEY] ?: DEFAULT_BANGUMI_PROXY_HOST
                bangumiMirrorEnabled = prefs[BANGUMI_MIRROR_ENABLED_KEY] ?: false
                httpProxyEnabled = prefs[HTTP_PROXY_ENABLED_KEY] ?: false
                httpProxyHost = prefs[HTTP_PROXY_HOST_KEY] ?: DEFAULT_HTTP_PROXY_HOST
                httpProxyPort = prefs[HTTP_PROXY_PORT_KEY] ?: DEFAULT_HTTP_PROXY_PORT
                httpProxyType = parseProxyType(prefs[HTTP_PROXY_TYPE_KEY] ?: DEFAULT_HTTP_PROXY_TYPE)
                httpProxyUsername = prefs[HTTP_PROXY_USERNAME_KEY] ?: DEFAULT_HTTP_PROXY_USERNAME
                httpProxyPassword = prefs[HTTP_PROXY_PASSWORD_KEY] ?: DEFAULT_HTTP_PROXY_PASSWORD
                userAuthBaseUrl = prefs[USER_AUTH_BASE_URL_KEY] ?: DEFAULT_USER_AUTH_BASE_URL
                // 将 DataStore 中的语言设置同步回 SharedPreferences 镜像，保证两者一致
                val lang = prefs[APP_LANGUAGE_KEY] ?: AppLanguage.SIMPLIFIED_CHINESE.name
                localePrefs.edit().putString(APP_LANGUAGE_PREF, lang).apply()
                AppLogManager.i(TAG, "偏好加载完成: ${prefs.asMap().size} 项，语言 $lang")
            } catch (e: Exception) {
                AppLogManager.e(TAG, "偏好加载失败（本次全部使用默认值，设置可能不生效）", e)
            }
        }
    }

    val themeMode: Flow<ThemeMode> = enumPreference(THEME_MODE_KEY, DEFAULT_THEME_MODE)

    suspend fun setThemeMode(mode: ThemeMode) {
        AppLogManager.i(TAG, "主题模式切换: $mode")
        setPreference(THEME_MODE_KEY, mode.name)
    }

    val themePreset: Flow<ThemePreset> = enumPreference(THEME_PRESET_KEY, DEFAULT_THEME_PRESET)
        // 莫奈取色的 seed 来自 Android 12+ 系统动态配色：
        // 备份恢复到低版本设备后回退默认主题，避免界面显示为莫奈取色却实际渲染黑白简洁
        .map { if (it.seedColor == null && !isMonetColorSupported()) DEFAULT_THEME_PRESET else it }

    suspend fun setThemePreset(preset: ThemePreset) = setPreference(THEME_PRESET_KEY, preset.name)

    /** 深色风格：增强（纯黑）/ 标准（适中）/ 柔和（偏灰）/ 高对比 */
    val darkStyle: Flow<DarkStyle> = enumPreference(DARK_STYLE_KEY, DEFAULT_DARK_STYLE)

    suspend fun setDarkStyle(style: DarkStyle) {
        AppLogManager.i(TAG, "深色风格切换: $style")
        setPreference(DARK_STYLE_KEY, style.name)
    }

    /** 主题模式的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedThemeMode(): ThemeMode = cachedEnumOrDefault(THEME_MODE_KEY, DEFAULT_THEME_MODE)

    /** 深色风格的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedDarkStyle(): DarkStyle = cachedEnumOrDefault(DARK_STYLE_KEY, DEFAULT_DARK_STYLE)

    /** 图标包：Material Symbols（默认）/ Lucide */
    val iconPack: Flow<IconPack> = enumPreference(ICON_PACK_KEY, DEFAULT_ICON_PACK)

    suspend fun setIconPack(pack: IconPack) = setPreference(ICON_PACK_KEY, pack.name)

    /** 图标包的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedIconPack(): IconPack = cachedEnumOrDefault(ICON_PACK_KEY, DEFAULT_ICON_PACK)

    val showFavorites: Flow<Boolean> = preferenceFlow(SHOW_FAVORITES_KEY, false)

    val showTimeline: Flow<Boolean> = preferenceFlow(SHOW_TIMELINE_KEY, true)

    suspend fun setShowFavorites(show: Boolean) = setPreference(SHOW_FAVORITES_KEY, show)

    suspend fun setShowTimeline(show: Boolean) = setPreference(SHOW_TIMELINE_KEY, show)

    val showSchedule: Flow<Boolean> = preferenceFlow(SHOW_SCHEDULE_KEY, true)

    suspend fun setShowSchedule(show: Boolean) = setPreference(SHOW_SCHEDULE_KEY, show)

    val navigationStyle: Flow<NavigationStyle> = enumPreference(NAVIGATION_STYLE_KEY, DEFAULT_NAVIGATION_STYLE)

    suspend fun setNavigationStyle(style: NavigationStyle) = setPreference(NAVIGATION_STYLE_KEY, style.name)

    val fabLocation: Flow<FabLocation> = enumPreference(FAB_LOCATION_KEY, DEFAULT_FAB_LOCATION)

    suspend fun setFabLocation(location: FabLocation) = setPreference(FAB_LOCATION_KEY, location.name)

    val navigationLabelMode: Flow<NavigationLabelMode> =
        enumPreference(NAVIGATION_LABEL_MODE_KEY, DEFAULT_NAVIGATION_LABEL_MODE)

    suspend fun setNavigationLabelMode(mode: NavigationLabelMode) = setPreference(NAVIGATION_LABEL_MODE_KEY, mode.name)

    /** 导航栏标签模式的同步缓存值：作为 collectAsState 初始值，
     *  避免 MainOverlay 路由重建时先闪现默认「图标+文字」再跳变为已选模式 */
    fun cachedNavigationLabelMode(): NavigationLabelMode =
        cachedEnumOrDefault(NAVIGATION_LABEL_MODE_KEY, DEFAULT_NAVIGATION_LABEL_MODE)

    /** 界面外壳材质档位：标准 / 高级模糊（haze 毛玻璃）/ 液态玻璃（backdrop 折射），默认标准 */
    val chromeStyle: Flow<ChromeStyle> = enumPreference(CHROME_STYLE_KEY, DEFAULT_CHROME_STYLE)

    suspend fun setChromeStyle(style: ChromeStyle) {
        AppLogManager.i(TAG, "界面材质切换: $style")
        setPreference(CHROME_STYLE_KEY, style.name)
    }

    /** 界面风格的同步缓存值：作为 CompositionLocal 初值，避免冷启动首帧闪变 */
    fun cachedChromeStyle(): ChromeStyle = cachedEnumOrDefault(CHROME_STYLE_KEY, DEFAULT_CHROME_STYLE)

    /** 导航栏四周背景模糊（类 iOS Tab Bar 底部磨砂区域），默认开启；液态玻璃与高级模糊档共用 */
    val capsuleLiquidGlassBackdropBlurEnabled: Flow<Boolean> =
        preferenceFlow(CAPSULE_LIQUID_GLASS_BACKDROP_BLUR_KEY, DEFAULT_CAPSULE_LIQUID_GLASS_BACKDROP_BLUR)
    suspend fun setCapsuleLiquidGlassBackdropBlurEnabled(enabled: Boolean) =
        setPreference(CAPSULE_LIQUID_GLASS_BACKDROP_BLUR_KEY, enabled)
    /** 导航栏背景模糊的同步缓存值：作为 collectAsState 初始值，避免首帧闪变 */
    fun cachedCapsuleLiquidGlassBackdropBlur(): Boolean =
        cachedOrDefault(CAPSULE_LIQUID_GLASS_BACKDROP_BLUR_KEY, DEFAULT_CAPSULE_LIQUID_GLASS_BACKDROP_BLUR)

    /** 主页顶栏下滑隐藏，默认关闭。开启后向下滑动列表收起顶栏，搜索/添加按钮转为组合悬浮按钮 */
    val hideTopBarOnScrollEnabled: Flow<Boolean> =
        preferenceFlow(HIDE_TOPBAR_ON_SCROLL_KEY, DEFAULT_HIDE_TOPBAR_ON_SCROLL)

    suspend fun setHideTopBarOnScrollEnabled(enabled: Boolean) = setPreference(HIDE_TOPBAR_ON_SCROLL_KEY, enabled)

    /** 「下滑隐藏顶部栏」的同步缓存值：作为 collectAsState 的初始值，
     *  避免设置页首帧先渲染默认关闭态、下一帧才跳变为已开启的闪变（缓存未就绪时退回默认值） */
    fun cachedHideTopBarOnScroll(): Boolean =
        cachedOrDefault(HIDE_TOPBAR_ON_SCROLL_KEY, DEFAULT_HIDE_TOPBAR_ON_SCROLL)

    /** 主页顶栏底部两角是否圆角，默认开启 */
    val topBarRounded: Flow<Boolean> = preferenceFlow(TOP_BAR_ROUNDED_KEY, DEFAULT_TOP_BAR_ROUNDED)

    suspend fun setTopBarRounded(enabled: Boolean) = setPreference(TOP_BAR_ROUNDED_KEY, enabled)

    /** 顶栏圆角开关的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedTopBarRounded(): Boolean =
        cachedOrDefault(TOP_BAR_ROUNDED_KEY, DEFAULT_TOP_BAR_ROUNDED)

    /** 主页顶栏是否跟随界面外壳材质档位使用毛玻璃/液态玻璃，默认开启 */
    val topBarGlassThemeEnabled: Flow<Boolean> =
        preferenceFlow(TOP_BAR_GLASS_THEME_KEY, DEFAULT_TOP_BAR_GLASS_THEME)

    suspend fun setTopBarGlassThemeEnabled(enabled: Boolean) = setPreference(TOP_BAR_GLASS_THEME_KEY, enabled)

    /** 顶栏玻璃材质开关的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedTopBarGlassThemeEnabled(): Boolean =
        cachedOrDefault(TOP_BAR_GLASS_THEME_KEY, DEFAULT_TOP_BAR_GLASS_THEME)

    /** 顶栏收起后的状态栏处理方式，默认留白遮罩（仅「下滑隐藏顶栏」开启时生效） */
    val statusBarMode: Flow<StatusBarMode> = enumPreference(STATUS_BAR_MODE_KEY, DEFAULT_STATUS_BAR_MODE)

    suspend fun setStatusBarMode(mode: StatusBarMode) = setPreference(STATUS_BAR_MODE_KEY, mode.name)

    /** 状态栏处理方式的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedStatusBarMode(): StatusBarMode =
        cachedEnumOrDefault(STATUS_BAR_MODE_KEY, DEFAULT_STATUS_BAR_MODE)

    /** 高级模糊半径（dp），默认 24 */
    val advancedBlurRadius: Flow<Float> = preferenceFlow(ADVANCED_BLUR_RADIUS_KEY, DEFAULT_ADVANCED_BLUR_RADIUS)

    suspend fun setAdvancedBlurRadius(radius: Float) = setPreference(ADVANCED_BLUR_RADIUS_KEY, radius.coerceIn(0f, 50f))

    /** 高级模糊底色不透明度（0..1），默认 1。调低后毛玻璃更透 */
    val advancedBlurBackgroundAlpha: Flow<Float> = preferenceFlow(ADVANCED_BLUR_BACKGROUND_ALPHA_KEY, DEFAULT_ADVANCED_BLUR_BACKGROUND_ALPHA)

    suspend fun setAdvancedBlurBackgroundAlpha(alpha: Float) = setPreference(ADVANCED_BLUR_BACKGROUND_ALPHA_KEY, alpha.coerceIn(0f, 1f))

    /** 高级模糊着色不透明度（0..1），默认 0.4。调高可让浅色背景下玻璃更明显 */
    val advancedBlurTintAlpha: Flow<Float> = preferenceFlow(ADVANCED_BLUR_TINT_ALPHA_KEY, DEFAULT_ADVANCED_BLUR_TINT_ALPHA)

    suspend fun setAdvancedBlurTintAlpha(alpha: Float) = setPreference(ADVANCED_BLUR_TINT_ALPHA_KEY, alpha.coerceIn(0f, 1f))

    /** 高级模糊噪点强度（0..1），默认 0。噪点可增强浅色背景下的磨砂质感 */
    val advancedBlurNoise: Flow<Float> = preferenceFlow(ADVANCED_BLUR_NOISE_KEY, DEFAULT_ADVANCED_BLUR_NOISE)

    suspend fun setAdvancedBlurNoise(noise: Float) = setPreference(ADVANCED_BLUR_NOISE_KEY, noise.coerceIn(0f, 1f))

    val customGreeting: Flow<String> = preferenceFlow(CUSTOM_GREETING_KEY, DEFAULT_CUSTOM_GREETING)

    suspend fun setCustomGreeting(greeting: String) = setPreference(CUSTOM_GREETING_KEY, greeting)

    /** 自定义欢迎语同步缓存：MainOverlay 随路由切换销毁重建，collectAsState("")
     * 的空初始值会使欢迎语瞬态解析为随机前缀，与已播放标记不匹配导致重播动画 */
    fun cachedCustomGreeting(): String = cachedOrDefault(CUSTOM_GREETING_KEY, DEFAULT_CUSTOM_GREETING)

    val autoCompleteEnabled: Flow<Boolean> = preferenceFlow(AUTO_COMPLETE_KEY, true)

    suspend fun setAutoCompleteEnabled(enabled: Boolean) = setPreference(AUTO_COMPLETE_KEY, enabled)

    val completedToastEnabled: Flow<Boolean> = preferenceFlow(COMPLETED_TOAST_KEY, true)

    suspend fun setCompletedToastEnabled(enabled: Boolean) = setPreference(COMPLETED_TOAST_KEY, enabled)

    val hideBangumiAvatar: Flow<Boolean> = preferenceFlow(HIDE_BANGUMI_AVATAR_KEY, false)

    suspend fun setHideBangumiAvatar(hide: Boolean) = setPreference(HIDE_BANGUMI_AVATAR_KEY, hide)

    /**
     * 欢迎语入场效果。新 key（greeting_effect_style）优先；未设置过新 key 的老用户
     * 按旧布尔开关（greeting_typing_effect）迁移：false → NONE，默认 true → TYPING，
     * 保证升级后行为与原开关一致
     */
    val greetingEffectStyle: Flow<GreetingEffectStyle> = flow {
        (prefCache[GREETING_EFFECT_STYLE_KEY] as? String)
            ?.let { enumValueOrDefault(it, DEFAULT_GREETING_EFFECT_STYLE) }
            ?.let { emit(it) }
        emitAll(
            context.dataStore.data.map { prefs ->
                prefs[GREETING_EFFECT_STYLE_KEY]?.let { enumValueOrDefault(it, DEFAULT_GREETING_EFFECT_STYLE) }
                    ?: if (prefs[GREETING_TYPING_EFFECT_KEY] == false) GreetingEffectStyle.NONE
                       else DEFAULT_GREETING_EFFECT_STYLE
            }
        )
    }.distinctUntilChanged()

    suspend fun setGreetingEffectStyle(style: GreetingEffectStyle) =
        setPreference(GREETING_EFFECT_STYLE_KEY, style.name)

    /** 入场效果同步缓存：避免 MainOverlay 重建瞬态默认值使已关闭的效果误触发动画 */
    fun cachedGreetingEffectStyle(): GreetingEffectStyle =
        (prefCache[GREETING_EFFECT_STYLE_KEY] as? String)
            ?.let { enumValueOrDefault(it, DEFAULT_GREETING_EFFECT_STYLE) }
            ?: DEFAULT_GREETING_EFFECT_STYLE

    val showUpdateBanner: Flow<Boolean> = preferenceFlow(SHOW_UPDATE_BANNER_KEY, true)

    suspend fun setShowUpdateBanner(show: Boolean) = setPreference(SHOW_UPDATE_BANNER_KEY, show)

    /** 主页头像旁「反馈有新回复」胶囊提示，默认开启 */
    val feedbackReplyPillEnabled: Flow<Boolean> = preferenceFlow(FEEDBACK_REPLY_PILL_ENABLED_KEY, true)

    suspend fun setFeedbackReplyPillEnabled(enabled: Boolean) = setPreference(FEEDBACK_REPLY_PILL_ENABLED_KEY, enabled)

    val showCalendarButton: Flow<Boolean> = preferenceFlow(SHOW_CALENDAR_BUTTON_KEY, true)

    suspend fun setShowCalendarButton(show: Boolean) = setPreference(SHOW_CALENDAR_BUTTON_KEY, show)

    val showDailyBroadcastButton: Flow<Boolean> = preferenceFlow(SHOW_DAILY_BROADCAST_BUTTON_KEY, true)

    suspend fun setShowDailyBroadcastButton(show: Boolean) =
        setPreference(SHOW_DAILY_BROADCAST_BUTTON_KEY, show)

    val showSearchButton: Flow<Boolean> = preferenceFlow(SHOW_SEARCH_BUTTON_KEY, true)

    /** 多季番剧是否堆叠显示，默认关闭 */
    val seriesStackEnabled: Flow<Boolean> = preferenceFlow(SERIES_STACK_ENABLED_KEY, false)

    suspend fun setSeriesStackEnabled(enabled: Boolean) = setPreference(SERIES_STACK_ENABLED_KEY, enabled)

    /** 卡片→详情页状态角标转场策略，默认 [StatusBadgeTransitionMode.FLY_TO_TITLE]（与线上既有行为一致） */
    val statusBadgeTransitionMode: Flow<StatusBadgeTransitionMode> =
        enumPreference(STATUS_BADGE_TRANSITION_MODE_KEY, DEFAULT_STATUS_BADGE_TRANSITION_MODE)

    suspend fun setStatusBadgeTransitionMode(mode: StatusBadgeTransitionMode) {
        AppLogManager.i(TAG, "状态角标转场切换: $mode")
        setPreference(STATUS_BADGE_TRANSITION_MODE_KEY, mode.name)
    }

    /** 状态角标转场策略的同步缓存值：作为 collectAsState 初值，避免冷启动首帧按默认策略渲染再跳变 */
    fun cachedStatusBadgeTransitionMode(): StatusBadgeTransitionMode =
        cachedEnumOrDefault(STATUS_BADGE_TRANSITION_MODE_KEY, DEFAULT_STATUS_BADGE_TRANSITION_MODE)

    /** 卡片信息栏左槽位字段，默认观看进度 */
    val cardInfoLeftField: Flow<CardInfoField> = enumPreference(CARD_INFO_LEFT_KEY, DEFAULT_CARD_INFO_LEFT)

    suspend fun setCardInfoLeftField(field: CardInfoField) = setPreference(CARD_INFO_LEFT_KEY, field.name)

    /** 卡片信息栏右槽位字段，默认评分 */
    val cardInfoRightField: Flow<CardInfoField> = enumPreference(CARD_INFO_RIGHT_KEY, DEFAULT_CARD_INFO_RIGHT)

    suspend fun setCardInfoRightField(field: CardInfoField) = setPreference(CARD_INFO_RIGHT_KEY, field.name)

    /** 卡片左槽位的同步缓存值：作为 CompositionLocal 初值，避免冷启动首帧按默认字段渲染再跳变 */
    fun cachedCardInfoLeftField(): CardInfoField =
        cachedEnumOrDefault(CARD_INFO_LEFT_KEY, DEFAULT_CARD_INFO_LEFT)

    /** 卡片右槽位的同步缓存值 */
    fun cachedCardInfoRightField(): CardInfoField =
        cachedEnumOrDefault(CARD_INFO_RIGHT_KEY, DEFAULT_CARD_INFO_RIGHT)

    /** 主界面列表排序方式（默认为自动排序，历史持久化值沿用 RECENT_PROGRESS） */
    val listSortMode: Flow<ListSortMode> = enumPreference(LIST_SORT_MODE_KEY, DEFAULT_LIST_SORT_MODE)

    suspend fun setListSortMode(mode: ListSortMode) {
        AppLogManager.i(TAG, "列表排序方式切换: $mode")
        setPreference(LIST_SORT_MODE_KEY, mode.name)
    }

    /**
     * 排序方式的同步缓存值：反馈日志的运行时快照在 IO 线程现算，
     * 不能等 DataStore 首读，故与 [cachedChromeStyle] 一样读 prefCache。
     */
    fun cachedListSortMode(): ListSortMode =
        cachedEnumOrDefault(LIST_SORT_MODE_KEY, DEFAULT_LIST_SORT_MODE)

    /**
     * 堆叠卡片顶层页码记忆：系列 stableKey（"series_<第一季id>"）→ 顶层索引。
     * 翻页结算时写入，冷启动时作为初始 topIndex 恢复，杀死后台后不再重置回第一张。
     * 序列化为 "series_1:2,series_3:0" 格式（stableKey 不含分隔符）。
     */
    val seriesStackTopIndices: Flow<Map<String, Int>> = flow {
        (prefCache[SERIES_STACK_TOP_KEY] as? String)?.let { emit(decodeSeriesStackTop(it)) }
        emitAll(
            context.dataStore.data.map { prefs ->
                decodeSeriesStackTop(prefs[SERIES_STACK_TOP_KEY] ?: "")
            }
        )
    }.distinctUntilChanged()

    suspend fun setSeriesStackTopIndex(seriesKey: String, topIndex: Int) {
        val current = seriesStackTopIndices.first().toMutableMap()
        if (current[seriesKey] == topIndex) return
        current[seriesKey] = topIndex
        setPreference(SERIES_STACK_TOP_KEY, encodeSeriesStackTop(current))
    }

    private fun encodeSeriesStackTop(map: Map<String, Int>): String =
        map.entries.joinToString(",") { "${it.key}:${it.value}" }

    private fun decodeSeriesStackTop(raw: String): Map<String, Int> =
        if (raw.isBlank()) {
            emptyMap()
        } else {
            raw.split(',').mapNotNull { entry ->
                val sep = entry.lastIndexOf(':')
                val idx = entry.substring(sep + 1).toIntOrNull()
                if (sep <= 0 || idx == null) null else entry.substring(0, sep) to idx
            }.toMap()
        }


    /** 评分标准：使用源评分或手动打分，默认使用源评分 */
    val ratingStandard: Flow<RatingStandard> = enumPreference(RATING_STANDARD_KEY, DEFAULT_RATING_STANDARD)

    suspend fun setRatingStandard(standard: RatingStandard) = setPreference(RATING_STANDARD_KEY, standard.name)

    suspend fun setShowSearchButton(show: Boolean) = setPreference(SHOW_SEARCH_BUTTON_KEY, show)

    val skippedVersion: Flow<String> = preferenceFlow(SKIPPED_VERSION_KEY, "")

    suspend fun setSkippedVersion(version: String) = setPreference(SKIPPED_VERSION_KEY, version)

    val webdavUrl: Flow<String> = preferenceFlow(WEBDAV_URL_KEY, "")

    suspend fun setWebdavUrl(url: String) = setPreference(WEBDAV_URL_KEY, url)

    val webdavUsername: Flow<String> = preferenceFlow(WEBDAV_USERNAME_KEY, "")

    suspend fun setWebdavUsername(username: String) = setPreference(WEBDAV_USERNAME_KEY, username)

    val webdavPassword: Flow<String> = preferenceFlow(WEBDAV_PASSWORD_KEY, "")

    suspend fun setWebdavPassword(password: String) = setPreference(WEBDAV_PASSWORD_KEY, password)

    val webdavBackupStrategy: Flow<Int> = preferenceFlow(WEBDAV_BACKUP_STRATEGY_KEY, 0)

    suspend fun setWebdavBackupStrategy(strategy: Int) = setPreference(WEBDAV_BACKUP_STRATEGY_KEY, strategy)

    val webdavRestoreMode: Flow<Int> = preferenceFlow(WEBDAV_RESTORE_MODE_KEY, 0)

    suspend fun setWebdavRestoreMode(mode: Int) = setPreference(WEBDAV_RESTORE_MODE_KEY, mode)

    val webdavLastSyncTime: Flow<Long> = preferenceFlow(WEBDAV_LAST_SYNC_TIME_KEY, 0L)

    suspend fun setWebdavLastSyncTime(time: Long) = setPreference(WEBDAV_LAST_SYNC_TIME_KEY, time)

    /** 上次启动期自动补全番剧元数据的时间戳，用于 24 小时节流 */
    val lastCoverBackfillTime: Flow<Long> = preferenceFlow(LAST_COVER_BACKFILL_TIME_KEY, 0L)

    suspend fun setLastCoverBackfillTime(time: Long) = setPreference(LAST_COVER_BACKFILL_TIME_KEY, time)

    val isFirstLaunch: Flow<Boolean> = preferenceFlow(IS_FIRST_LAUNCH_KEY, true)

    suspend fun setFirstLaunchCompleted() = setPreference(IS_FIRST_LAUNCH_KEY, false)

    // ========== 隐私政策同意状态（合规：未同意前不得初始化个人信息收集组件） ==========

    /** 隐私政策是否已同意（UI 观察） */
    val privacyPolicyAccepted: Flow<Boolean> = preferenceFlow(PRIVACY_POLICY_ACCEPTED_KEY, false)

    suspend fun setPrivacyPolicyAccepted(accepted: Boolean) {
        // 同步写 SharedPreferences 镜像：保证同意后立即杀进程，下次冷启动同步读取也生效
        privacyPrefs.edit().putBoolean(PRIVACY_ACCEPTED_PREF, accepted).apply()
        setPreference(PRIVACY_POLICY_ACCEPTED_KEY, accepted)
    }

    /**
     * 隐私政策同意状态的同步读取：供 Application/MainActivity 冷启动判断
     * （此时 DataStore 首读可能尚未完成）。优先读 prefCache（DataStore 加载后已填充），
     * 未填充时退化为 SharedPreferences 镜像。
     */
    fun isPrivacyPolicyAcceptedBlocking(): Boolean {
        (prefCache[PRIVACY_POLICY_ACCEPTED_KEY] as? Boolean)?.let { return it }
        return privacyPrefs.getBoolean(PRIVACY_ACCEPTED_PREF, false)
    }

    // ========== 帮助改进应用（崩溃上报开关 + 首启弹窗标记） ==========

    /**
     * 崩溃上报是否开启（默认开启）。仅控制 tombstone 静默上传，
     * xCrash 本地捕获落盘不受影响（本地文件有保留上限，可用于用户主动导出反馈）
     */
    val crashReportEnabled: Flow<Boolean> = preferenceFlow(CRASH_REPORT_ENABLED_KEY, true)

    suspend fun setCrashReportEnabled(enabled: Boolean) {
        AppLogManager.i(TAG, "崩溃上报开关: $enabled")
        setPreference(CRASH_REPORT_ENABLED_KEY, enabled)
    }

    /** 崩溃上报开关的内存缓存值（导出诊断快照时同步读取） */
    fun cachedCrashReportEnabled(): Boolean =
        cachedOrDefault(CRASH_REPORT_ENABLED_KEY, DEFAULT_CRASH_REPORT_ENABLED)

    /** 首启「帮助改进应用」弹窗是否已展示（展示过即不再弹） */
    val helpImproveDialogShown: Flow<Boolean> = preferenceFlow(HELP_IMPROVE_DIALOG_SHOWN_KEY, false)

    suspend fun setHelpImproveDialogShown(shown: Boolean) = setPreference(HELP_IMPROVE_DIALOG_SHOWN_KEY, shown)

    val developerMode: Flow<Boolean> = preferenceFlow(DEVELOPER_MODE_KEY, false)

    suspend fun setDeveloperMode(enabled: Boolean) = setPreference(DEVELOPER_MODE_KEY, enabled)

    val shareButtonEnabled: Flow<Boolean> = preferenceFlow(SHARE_BUTTON_ENABLED_KEY, false)

    suspend fun setShareButtonEnabled(enabled: Boolean) = setPreference(SHARE_BUTTON_ENABLED_KEY, enabled)

    val tmdbApiKey: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[TMDB_API_KEY_KEY]
        }
        .distinctUntilChanged()

    suspend fun setTmdbApiKey(key: String) {
        setPreference(TMDB_API_KEY_KEY, key)
        currentTmdbApiKey = key.ifBlank { null }
    }

    val bangumiProxyEnabledFlow: Flow<Boolean> = preferenceFlow(BANGUMI_PROXY_ENABLED_KEY, false)

    val bangumiProxyHostFlow: Flow<String> = preferenceFlow(BANGUMI_PROXY_HOST_KEY, DEFAULT_BANGUMI_PROXY_HOST)

    suspend fun setBangumiProxyEnabled(enabled: Boolean) {
        AppLogManager.i(TAG, "Bangumi 反向代理开关: $enabled")
        setPreference(BANGUMI_PROXY_ENABLED_KEY, enabled)
        bangumiProxyEnabled = enabled
    }

    suspend fun setBangumiProxyHost(host: String) {
        val normalized = host.trim()
        AppLogManager.i(TAG, "Bangumi 反向代理地址: $normalized")
        setPreference(BANGUMI_PROXY_HOST_KEY, normalized)
        bangumiProxyHost = normalized
    }

    // Bangumi 镜像数据源
    val bangumiMirrorEnabledFlow: Flow<Boolean> = preferenceFlow(BANGUMI_MIRROR_ENABLED_KEY, false)

    suspend fun setBangumiMirrorEnabled(enabled: Boolean) {
        AppLogManager.i(TAG, "Bangumi 镜像数据源开关: $enabled")
        setPreference(BANGUMI_MIRROR_ENABLED_KEY, enabled)
        bangumiMirrorEnabled = enabled
    }

    // HTTP 普通代理
    val httpProxyEnabledFlow: Flow<Boolean> = preferenceFlow(HTTP_PROXY_ENABLED_KEY, false)
    val httpProxyHostFlow: Flow<String> = preferenceFlow(HTTP_PROXY_HOST_KEY, DEFAULT_HTTP_PROXY_HOST)
    val httpProxyPortFlow: Flow<Int> = preferenceFlow(HTTP_PROXY_PORT_KEY, DEFAULT_HTTP_PROXY_PORT)
    val httpProxyTypeFlow: Flow<ProxyType> =
        preferenceFlow(HTTP_PROXY_TYPE_KEY, DEFAULT_HTTP_PROXY_TYPE)
            .map { enumValueOrDefault(it, DEFAULT_PROXY_TYPE) }
    val httpProxyUsernameFlow: Flow<String> = preferenceFlow(HTTP_PROXY_USERNAME_KEY, DEFAULT_HTTP_PROXY_USERNAME)
    val httpProxyPasswordFlow: Flow<String> = preferenceFlow(HTTP_PROXY_PASSWORD_KEY, DEFAULT_HTTP_PROXY_PASSWORD)

    suspend fun setHttpProxyEnabled(enabled: Boolean) {
        AppLogManager.i(TAG, "HTTP 代理开关: $enabled")
        setPreference(HTTP_PROXY_ENABLED_KEY, enabled)
        httpProxyEnabled = enabled
    }

    suspend fun setHttpProxyHost(host: String) {
        val normalized = host.trim()
        AppLogManager.i(TAG, "HTTP 代理地址: $normalized")
        setPreference(HTTP_PROXY_HOST_KEY, normalized)
        httpProxyHost = normalized
    }

    suspend fun setHttpProxyPort(port: Int) {
        AppLogManager.i(TAG, "HTTP 代理端口: $port")
        setPreference(HTTP_PROXY_PORT_KEY, port)
        httpProxyPort = port
    }

    suspend fun setHttpProxyType(type: ProxyType) {
        setPreference(HTTP_PROXY_TYPE_KEY, type.name)
        httpProxyType = type
    }

    suspend fun setHttpProxyUsername(username: String) {
        setPreference(HTTP_PROXY_USERNAME_KEY, username)
        httpProxyUsername = username
    }

    suspend fun setHttpProxyPassword(password: String) {
        setPreference(HTTP_PROXY_PASSWORD_KEY, password)
        httpProxyPassword = password
    }

    /** 存储值解析为 ProxyType，非法值兜底默认代理类型 */
    private fun parseProxyType(value: String): ProxyType =
        enumValueOrDefault(value, DEFAULT_PROXY_TYPE)

    val userAuthBaseUrlFlow: Flow<String> = preferenceFlow(USER_AUTH_BASE_URL_KEY, DEFAULT_USER_AUTH_BASE_URL)

    suspend fun setUserAuthBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/')
        setPreference(USER_AUTH_BASE_URL_KEY, normalized)
        userAuthBaseUrl = normalized
    }

    val webdavAutoSyncEnabled: Flow<Boolean> = preferenceFlow(WEBDAV_AUTO_SYNC_ENABLED_KEY, false)

    suspend fun setWebdavAutoSyncEnabled(enabled: Boolean) = setPreference(WEBDAV_AUTO_SYNC_ENABLED_KEY, enabled)

    val webdavAutoSyncOnDataChange: Flow<Boolean> = preferenceFlow(WEBDAV_AUTO_SYNC_ON_DATA_CHANGE_KEY, true)

    suspend fun setWebdavAutoSyncOnDataChange(enabled: Boolean) = setPreference(WEBDAV_AUTO_SYNC_ON_DATA_CHANGE_KEY, enabled)

    val webdavAutoSyncOnAppOpen: Flow<Boolean> = preferenceFlow(WEBDAV_AUTO_SYNC_ON_APP_OPEN_KEY, false)

    suspend fun setWebdavAutoSyncOnAppOpen(enabled: Boolean) = setPreference(WEBDAV_AUTO_SYNC_ON_APP_OPEN_KEY, enabled)

    val webdavAutoSyncScheduled: Flow<Boolean> = preferenceFlow(WEBDAV_AUTO_SYNC_SCHEDULED_KEY, false)

    suspend fun setWebdavAutoSyncScheduled(enabled: Boolean) = setPreference(WEBDAV_AUTO_SYNC_SCHEDULED_KEY, enabled)

    val webdavAutoSyncInterval: Flow<Int> = preferenceFlow(WEBDAV_AUTO_SYNC_INTERVAL_KEY, 2)

    suspend fun setWebdavAutoSyncInterval(interval: Int) = setPreference(WEBDAV_AUTO_SYNC_INTERVAL_KEY, interval)

    val webdavAutoSyncWifiOnly: Flow<Boolean> = preferenceFlow(WEBDAV_AUTO_SYNC_WIFI_ONLY_KEY, true)

    suspend fun setWebdavAutoSyncWifiOnly(wifiOnly: Boolean) = setPreference(WEBDAV_AUTO_SYNC_WIFI_ONLY_KEY, wifiOnly)

    val webdavAutoSyncUseCustomStrategy: Flow<Boolean> = preferenceFlow(WEBDAV_AUTO_SYNC_USE_CUSTOM_STRATEGY_KEY, false)

    suspend fun setWebdavAutoSyncUseCustomStrategy(useCustom: Boolean) = setPreference(WEBDAV_AUTO_SYNC_USE_CUSTOM_STRATEGY_KEY, useCustom)

    val webdavAutoSyncBackupStrategy: Flow<Int> = preferenceFlow(WEBDAV_AUTO_SYNC_BACKUP_STRATEGY_KEY, 0)

    suspend fun setWebdavAutoSyncBackupStrategy(strategy: Int) = setPreference(WEBDAV_AUTO_SYNC_BACKUP_STRATEGY_KEY, strategy)

    val webdavAutoSyncLastScheduledTime: Flow<Long> = preferenceFlow(WEBDAV_AUTO_SYNC_LAST_SCHEDULED_TIME_KEY, 0L)

    suspend fun setWebdavAutoSyncLastScheduledTime(time: Long) = setPreference(WEBDAV_AUTO_SYNC_LAST_SCHEDULED_TIME_KEY, time)

    val webdavLastAutoSyncTime: Flow<Long> = preferenceFlow(WEBDAV_LAST_AUTO_SYNC_TIME_KEY, 0L)

    suspend fun setWebdavLastAutoSyncTime(time: Long) = setPreference(WEBDAV_LAST_AUTO_SYNC_TIME_KEY, time)

    val webdavMediaPath: Flow<String> = preferenceFlow(WEBDAV_MEDIA_PATH_KEY, "")

    suspend fun setWebdavMediaPath(path: String) = setPreference(WEBDAV_MEDIA_PATH_KEY, path)

    val updateNotificationEnabled: Flow<Boolean> = preferenceFlow(UPDATE_NOTIFICATION_ENABLED_KEY, false)

    suspend fun setUpdateNotificationEnabled(enabled: Boolean) {
        AppLogManager.i(TAG, "每日更新通知开关: $enabled")
        setPreference(UPDATE_NOTIFICATION_ENABLED_KEY, enabled)
    }

    /** 每日更新通知开关的内存缓存值（导出诊断快照时同步读取） */
    fun cachedUpdateNotificationEnabled(): Boolean =
        cachedOrDefault(UPDATE_NOTIFICATION_ENABLED_KEY, DEFAULT_UPDATE_NOTIFICATION_ENABLED)

    val updateNotificationHour: Flow<Int> = preferenceFlow(UPDATE_NOTIFICATION_HOUR_KEY, 9)

    suspend fun setUpdateNotificationHour(hour: Int) = setPreference(UPDATE_NOTIFICATION_HOUR_KEY, hour)

    val updateNotificationMinute: Flow<Int> = preferenceFlow(UPDATE_NOTIFICATION_MINUTE_KEY, 0)

    suspend fun setUpdateNotificationMinute(minute: Int) = setPreference(UPDATE_NOTIFICATION_MINUTE_KEY, minute)

    /** 设置页是否显示「视频播放」聚合入口（开发者选项控制，默认隐藏） */
    val playerHubVisible: Flow<Boolean> = preferenceFlow(PLAYER_HUB_VISIBLE_KEY, false)
    suspend fun setPlayerHubVisible(visible: Boolean) = setPreference(PLAYER_HUB_VISIBLE_KEY, visible)

    /** 详情页顶栏是否显示外部播放按钮（功能设置，默认隐藏） */
    val externalPlayerEntryVisible: Flow<Boolean> = preferenceFlow(EXTERNAL_PLAYER_ENTRY_VISIBLE_KEY, false)
    suspend fun setExternalPlayerEntryVisible(visible: Boolean) = setPreference(EXTERNAL_PLAYER_ENTRY_VISIBLE_KEY, visible)

    /** 外部播放目标选择方式（自动 / Animeko / 哔哩哔哩 / 每次询问） */
    val externalPlayMode: Flow<ExternalPlayMode> =
        enumPreference(EXTERNAL_PLAY_MODE_KEY, DEFAULT_EXTERNAL_PLAY_MODE)
    suspend fun setExternalPlayMode(mode: ExternalPlayMode) = setPreference(EXTERNAL_PLAY_MODE_KEY, mode.name)

    // ---- 播放器专属 WebDAV（与备份同步的 webdavUrl 三件套互不影响）----
    val playerWebdavUrl: Flow<String> = preferenceFlow(PLAYER_WEBDAV_URL_KEY, "")
    val playerWebdavUsername: Flow<String> = preferenceFlow(PLAYER_WEBDAV_USERNAME_KEY, "")
    val playerWebdavPassword: Flow<String> = preferenceFlow(PLAYER_WEBDAV_PASSWORD_KEY, "")

    suspend fun setPlayerWebdavCredentials(url: String, username: String, password: String) {
        setPreference(PLAYER_WEBDAV_URL_KEY, url)
        setPreference(PLAYER_WEBDAV_USERNAME_KEY, username)
        setPreference(PLAYER_WEBDAV_PASSWORD_KEY, password)
    }

    /** 信任所有证书（自签名/IP 直连 NAS 场景），仅作用于播放器 WebDAV 链路 */
    val playerWebdavTrustAllCerts: Flow<Boolean> = preferenceFlow(PLAYER_WEBDAV_TRUST_ALL_CERTS_KEY, false)
    suspend fun setPlayerWebdavTrustAllCerts(enabled: Boolean) = setPreference(PLAYER_WEBDAV_TRUST_ALL_CERTS_KEY, enabled)

    val updateNotificationVisible: Flow<Boolean> = preferenceFlow(UPDATE_NOTIFICATION_VISIBLE_KEY, false)

    suspend fun setUpdateNotificationVisible(visible: Boolean) = setPreference(UPDATE_NOTIFICATION_VISIBLE_KEY, visible)

    val playerDefaultSpeed: Flow<Float> = preferenceFlow(PLAYER_DEFAULT_SPEED_KEY, 1f)

    suspend fun setPlayerDefaultSpeed(speed: Float) = setPreference(PLAYER_DEFAULT_SPEED_KEY, speed)

    val playerHardwareAcceleration: Flow<Boolean> = preferenceFlow(PLAYER_HARDWARE_ACCELERATION_KEY, true)

    suspend fun setPlayerHardwareAcceleration(enabled: Boolean) = setPreference(PLAYER_HARDWARE_ACCELERATION_KEY, enabled)

    val playerRememberPosition: Flow<Boolean> = preferenceFlow(PLAYER_REMEMBER_POSITION_KEY, true)

    suspend fun setPlayerRememberPosition(enabled: Boolean) = setPreference(PLAYER_REMEMBER_POSITION_KEY, enabled)

    val playerAutoPlayNext: Flow<Boolean> = preferenceFlow(PLAYER_AUTO_PLAY_NEXT_KEY, false)

    suspend fun setPlayerAutoPlayNext(enabled: Boolean) = setPreference(PLAYER_AUTO_PLAY_NEXT_KEY, enabled)

    val playerLongPressSpeed: Flow<Float> = preferenceFlow(PLAYER_LONG_PRESS_SPEED_KEY, 2f)

    suspend fun setPlayerLongPressSpeed(speed: Float) = setPreference(PLAYER_LONG_PRESS_SPEED_KEY, speed)

    /** 自动横屏：播放横屏视频时自动进入全屏并旋转。默认关闭（默认保持竖屏） */
    val playerAutoLandscape: Flow<Boolean> = preferenceFlow(PLAYER_AUTO_LANDSCAPE_KEY, false)

    suspend fun setPlayerAutoLandscape(enabled: Boolean) = setPreference(PLAYER_AUTO_LANDSCAPE_KEY, enabled)

    val fontFamilyFlow: Flow<String> = preferenceFlow(FONT_FAMILY_KEY, FontFamilyType.SYSTEM.name)

    suspend fun setFontFamily(type: FontFamilyType) = setPreference(FONT_FAMILY_KEY, type.name)

    /** 旧「自定义字体」路径：仅用于迁移与旧值兜底，新导入字体统一走本机字体选择 */
    val customFontPathFlow: Flow<String> = preferenceFlow(CUSTOM_FONT_PATH_KEY, "")

    val deviceFontSelectionFlow: Flow<DeviceFontSelection> = combine(
        preferenceFlow(DEVICE_FONT_FAMILY_KEY, ""),
        preferenceFlow(DEVICE_FONT_PATH_KEY, ""),
        preferenceFlow(DEVICE_FONT_TTC_INDEX_KEY, 0)
    ) { family, path, ttcIndex -> DeviceFontSelection(family, path, ttcIndex) }

    // 字体文件被覆盖/改名时，选择项的值可能不变，用一个进程内信号触发重新加载 Typeface
    private val deviceFontReload = MutableStateFlow(0L)

    val deviceFontReloadFlow: Flow<Long> = deviceFontReload.asStateFlow()

    /**
     * 应用本机字体：家族名 / 文件 / ttcIndex 与字体类型在一次 DataStore 事务中写入，
     * 避免启动加载逻辑观察到中间态后闪切字体。
     */
    suspend fun applyDeviceFont(family: DeviceFontFamily) {
        val face = family.representativeFace
        prefCache[DEVICE_FONT_FAMILY_KEY] = family.name
        prefCache[DEVICE_FONT_PATH_KEY] = face.filePath
        prefCache[DEVICE_FONT_TTC_INDEX_KEY] = face.ttcIndex
        prefCache[FONT_FAMILY_KEY] = FontFamilyType.DEVICE.name
        try {
            context.dataStore.edit { prefs ->
                prefs[DEVICE_FONT_FAMILY_KEY] = family.name
                prefs[DEVICE_FONT_PATH_KEY] = face.filePath
                prefs[DEVICE_FONT_TTC_INDEX_KEY] = face.ttcIndex
                prefs[FONT_FAMILY_KEY] = FontFamilyType.DEVICE.name
            }
            AppLogManager.i(TAG, "应用本机字体: ${family.name} ${face.filePath}#${face.ttcIndex}")
        } catch (e: Exception) {
            AppLogManager.e(TAG, "本机字体写入失败: ${family.name}", e)
        } finally {
            notifyDeviceFontChanged()
        }
    }

    /** 清除本机字体选择并回退系统字体（删除当前正在使用的导入字体时调用） */
    suspend fun clearDeviceFont() {
        prefCache.remove(DEVICE_FONT_FAMILY_KEY)
        prefCache.remove(DEVICE_FONT_PATH_KEY)
        prefCache.remove(DEVICE_FONT_TTC_INDEX_KEY)
        prefCache[FONT_FAMILY_KEY] = FontFamilyType.SYSTEM.name
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(DEVICE_FONT_FAMILY_KEY)
                prefs.remove(DEVICE_FONT_PATH_KEY)
                prefs.remove(DEVICE_FONT_TTC_INDEX_KEY)
                prefs[FONT_FAMILY_KEY] = FontFamilyType.SYSTEM.name
            }
            AppLogManager.i(TAG, "已清除本机字体选择，回退系统字体")
        } catch (e: Exception) {
            AppLogManager.e(TAG, "清除本机字体选择失败", e)
        } finally {
            notifyDeviceFontChanged()
        }
    }

    private fun notifyDeviceFontChanged() {
        deviceFontReload.value = deviceFontReload.value + 1
    }

    val appLanguageFlow: Flow<String> = preferenceFlow(APP_LANGUAGE_KEY, AppLanguage.SIMPLIFIED_CHINESE.name)

    suspend fun setAppLanguage(language: AppLanguage) {
        AppLogManager.i(TAG, "语言切换: $language")
        setPreference(APP_LANGUAGE_KEY, language.name)
        // 同步写入 SharedPreferences 镜像，保证下次冷启动 attachBaseContext 能立即读到
        localePrefs.edit().putString(APP_LANGUAGE_PREF, language.name).apply()
    }

    /**
     * 同步读取语言设置，仅用于 attachBaseContext（无法使用 suspend）。
     * 从 SharedPreferences 镜像同步读取，即使冷启动早期 DataStore 尚未读完也能返回正确值。
     */
    fun getAppLanguageBlocking(): String {
        return localePrefs.getString(APP_LANGUAGE_PREF, AppLanguage.SIMPLIFIED_CHINESE.name)
            ?: AppLanguage.SIMPLIFIED_CHINESE.name
    }

    // ========== 活跃上报 ==========

    /** 上次活跃上报日期（yyyy-MM-dd），用于每日只上报一次 */
    val lastActivityDateFlow: Flow<String> = preferenceFlow(LAST_ACTIVITY_DATE_KEY, "")

    suspend fun setLastActivityDate(date: String) = setPreference(LAST_ACTIVITY_DATE_KEY, date)

    suspend fun getLastActivityDate(): String = lastActivityDateFlow.first()

    // ========== 设备 ID（用于未登录用户的活跃统计） ==========

    /** 当前设备唯一标识（UUID），首次访问时生成并持久化；后续保持稳定 */
    val deviceIdFlow: Flow<String> = preferenceFlow(DEVICE_ID_KEY, "")

    /**
     * 获取设备 ID，若不存在则生成 UUID 并持久化。
     * 使用内存缓存避免重复生成，调用方可在任意协程上下文使用。
     */
    suspend fun getOrCreateDeviceId(): String {
        val current = deviceIdFlow.first()
        if (current.isNotBlank()) return current
        val newId = java.util.UUID.randomUUID().toString()
        setPreference(DEVICE_ID_KEY, newId)
        return newId
    }

    // ========== 公告已读记录 ==========

    /** 已读公告 ID 列表（逗号分隔），用于避免重复弹窗 */
    val readAnnouncementIdsFlow: Flow<String> = preferenceFlow(READ_ANNOUNCEMENT_IDS_KEY, "")

    suspend fun markAnnouncementAsRead(id: Int) {
        val current = readAnnouncementIdsFlow.first()
        val ids = if (current.isBlank()) emptyList() else current.split(",").mapNotNull { it.toIntOrNull() }
        if (id !in ids) {
            setPreference(READ_ANNOUNCEMENT_IDS_KEY, (ids + id).joinToString(","))
        }
    }

    suspend fun getReadAnnouncementIds(): Set<Int> {
        val current = readAnnouncementIdsFlow.first()
        return if (current.isBlank()) emptySet()
        else current.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }
}
