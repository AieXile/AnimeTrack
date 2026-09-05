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
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.ui.theme.ThemePreset
import com.aiexile.animetrack.ui.icons.IconPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class FontFamilyType(val displayName: String) {
    SYSTEM("系统字体"),
    MISANS("MiSans"),
    CUSTOM("自定义字体")
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

class SettingsRepository(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val THEME_PRESET_KEY = stringPreferencesKey("theme_preset")
        private val ICON_PACK_KEY = stringPreferencesKey("icon_pack")
        private val SHOW_FAVORITES_KEY = booleanPreferencesKey("show_favorites")
        private val SHOW_TIMELINE_KEY = booleanPreferencesKey("show_timeline")
        private val SHOW_SCHEDULE_KEY = booleanPreferencesKey("show_schedule")
        private val NAVIGATION_STYLE_KEY = stringPreferencesKey("navigation_style")
        private val FAB_LOCATION_KEY = stringPreferencesKey("fab_location")
        private val NAVIGATION_LABEL_MODE_KEY = stringPreferencesKey("navigation_label_mode")
        private val CAPSULE_ADVANCED_BLUR_KEY = booleanPreferencesKey("capsule_advanced_blur")
        private val CAPSULE_LIQUID_GLASS_KEY = booleanPreferencesKey("capsule_liquid_glass")
        private val HIDE_TOPBAR_ON_SCROLL_KEY = booleanPreferencesKey("hide_topbar_on_scroll")
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
        private val SHOW_UPDATE_BANNER_KEY = booleanPreferencesKey("show_update_banner")
        private val SHOW_CALENDAR_BUTTON_KEY = booleanPreferencesKey("show_calendar_button")
        private val SHOW_SEARCH_BUTTON_KEY = booleanPreferencesKey("show_search_button")
        private val SERIES_STACK_ENABLED_KEY = booleanPreferencesKey("series_stack_enabled")
        private val RATING_STANDARD_KEY = stringPreferencesKey("rating_standard")
        private val SKIPPED_VERSION_KEY = stringPreferencesKey("skipped_version")
        private val WEBDAV_URL_KEY = stringPreferencesKey("webdav_url")
        private val WEBDAV_USERNAME_KEY = stringPreferencesKey("webdav_username")
        private val WEBDAV_PASSWORD_KEY = stringPreferencesKey("webdav_password")
        private val WEBDAV_BACKUP_STRATEGY_KEY = intPreferencesKey("webdav_backup_strategy")
        private val WEBDAV_RESTORE_MODE_KEY = intPreferencesKey("webdav_restore_mode")
        private val WEBDAV_LAST_SYNC_TIME_KEY = longPreferencesKey("webdav_last_sync_time")
        private val WEBDAV_LAST_AUTO_SYNC_TIME_KEY = longPreferencesKey("webdav_last_auto_sync_time")
        private val WEBDAV_MEDIA_PATH_KEY = stringPreferencesKey("webdav_media_path")
        private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
        private val DEVELOPER_MODE_KEY = booleanPreferencesKey("developer_mode")
        private val SHARE_BUTTON_ENABLED_KEY = booleanPreferencesKey("share_button_enabled")
        private val AUTO_SYNC_VISIBLE_KEY = booleanPreferencesKey("auto_sync_visible")
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

        const val DEFAULT_BANGUMI_PROXY_HOST = ""

        private val HTTP_PROXY_ENABLED_KEY = booleanPreferencesKey("http_proxy_enabled")
        private val HTTP_PROXY_HOST_KEY = stringPreferencesKey("http_proxy_host")
        private val HTTP_PROXY_PORT_KEY = intPreferencesKey("http_proxy_port")

        const val DEFAULT_HTTP_PROXY_HOST = ""
        const val DEFAULT_HTTP_PROXY_PORT = 0

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
        private val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
        // SharedPreferences 同步镜像 key（供 attachBaseContext 冷启动时同步读取）
        private const val APP_LANGUAGE_PREF = "app_language"
        private val LAST_ACTIVITY_DATE_KEY = stringPreferencesKey("last_activity_date")
        private val READ_ANNOUNCEMENT_IDS_KEY = stringPreferencesKey("read_announcement_ids")
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
        context.dataStore.edit { it[key] = value }
    }

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
    var userAuthBaseUrl: String = DEFAULT_USER_AUTH_BASE_URL
        private set

    // 语言设置：使用 SharedPreferences 做同步镜像，供 attachBaseContext 冷启动时立即读取
    // （DataStore 首次读取是异步的，冷启动早期 appLanguageCache 尚未填充，会导致语言设置丢失）
    private val localePrefs = context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE)

    init {
        // 复用 Application 级协程作用域（替代 GlobalScope）加载缓存；
        // Application 未就绪时兜底自建一次性作用域，保证初始化任务仍能执行。
        val scope = (context.applicationContext as? AnimeTrackApp)?.appScope
            ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val key = context.dataStore.data.first()[TMDB_API_KEY_KEY]
            currentTmdbApiKey = key ?: DEFAULT_TMDB_API_KEY

            val prefs = context.dataStore.data.first()
            // 填充偏好内存缓存：此后所有 preferenceFlow 首个发射即为持久化值
            prefCache.putAll(prefs.asMap())
            bangumiProxyEnabled = prefs[BANGUMI_PROXY_ENABLED_KEY] ?: false
            bangumiProxyHost = prefs[BANGUMI_PROXY_HOST_KEY] ?: DEFAULT_BANGUMI_PROXY_HOST
            httpProxyEnabled = prefs[HTTP_PROXY_ENABLED_KEY] ?: false
            httpProxyHost = prefs[HTTP_PROXY_HOST_KEY] ?: DEFAULT_HTTP_PROXY_HOST
            httpProxyPort = prefs[HTTP_PROXY_PORT_KEY] ?: DEFAULT_HTTP_PROXY_PORT
            userAuthBaseUrl = prefs[USER_AUTH_BASE_URL_KEY] ?: DEFAULT_USER_AUTH_BASE_URL
            // 将 DataStore 中的语言设置同步回 SharedPreferences 镜像，保证两者一致
            val lang = prefs[APP_LANGUAGE_KEY] ?: AppLanguage.SIMPLIFIED_CHINESE.name
            localePrefs.edit().putString(APP_LANGUAGE_PREF, lang).apply()
        }
    }

    val themeMode: Flow<ThemeMode> = preferenceFlow(THEME_MODE_KEY, ThemeMode.SYSTEM.name)
        .map { modeString ->
            try { ThemeMode.valueOf(modeString) } catch (_: IllegalArgumentException) { ThemeMode.SYSTEM }
        }

    suspend fun setThemeMode(mode: ThemeMode) = setPreference(THEME_MODE_KEY, mode.name)

    val themePreset: Flow<ThemePreset> = preferenceFlow(THEME_PRESET_KEY, ThemePreset.MONO_BLACK.name)
        .map { presetString ->
            try { ThemePreset.valueOf(presetString) } catch (_: IllegalArgumentException) { ThemePreset.MONO_BLACK }
        }

    suspend fun setThemePreset(preset: ThemePreset) = setPreference(THEME_PRESET_KEY, preset.name)

    /** 图标包：Material Symbols（默认）/ Lucide */
    val iconPack: Flow<IconPack> = preferenceFlow(ICON_PACK_KEY, IconPack.MATERIAL_SYMBOLS.name)
        .map { packString ->
            try { IconPack.valueOf(packString) } catch (_: IllegalArgumentException) { IconPack.MATERIAL_SYMBOLS }
        }

    suspend fun setIconPack(pack: IconPack) = setPreference(ICON_PACK_KEY, pack.name)

    /** 图标包的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedIconPack(): IconPack {
        val cached = prefCache[ICON_PACK_KEY] as? String ?: return IconPack.MATERIAL_SYMBOLS
        return try { IconPack.valueOf(cached) } catch (_: IllegalArgumentException) { IconPack.MATERIAL_SYMBOLS }
    }

    val showFavorites: Flow<Boolean> = preferenceFlow(SHOW_FAVORITES_KEY, false)

    val showTimeline: Flow<Boolean> = preferenceFlow(SHOW_TIMELINE_KEY, true)

    suspend fun setShowFavorites(show: Boolean) = setPreference(SHOW_FAVORITES_KEY, show)

    suspend fun setShowTimeline(show: Boolean) = setPreference(SHOW_TIMELINE_KEY, show)

    val showSchedule: Flow<Boolean> = preferenceFlow(SHOW_SCHEDULE_KEY, true)

    suspend fun setShowSchedule(show: Boolean) = setPreference(SHOW_SCHEDULE_KEY, show)

    val navigationStyle: Flow<NavigationStyle> = preferenceFlow(NAVIGATION_STYLE_KEY, NavigationStyle.CAPSULE.name)
        .map { styleString ->
            try { NavigationStyle.valueOf(styleString) } catch (_: IllegalArgumentException) { NavigationStyle.CAPSULE }
        }

    suspend fun setNavigationStyle(style: NavigationStyle) = setPreference(NAVIGATION_STYLE_KEY, style.name)

    val fabLocation: Flow<FabLocation> = preferenceFlow(FAB_LOCATION_KEY, FabLocation.BOTTOM_RIGHT.name)
        .map { locationString ->
            try { FabLocation.valueOf(locationString) } catch (_: IllegalArgumentException) { FabLocation.BOTTOM_RIGHT }
        }

    suspend fun setFabLocation(location: FabLocation) = setPreference(FAB_LOCATION_KEY, location.name)

    val navigationLabelMode: Flow<NavigationLabelMode> = preferenceFlow(NAVIGATION_LABEL_MODE_KEY, NavigationLabelMode.ICON_AND_TEXT.name)
        .map { modeString ->
            try { NavigationLabelMode.valueOf(modeString) } catch (_: IllegalArgumentException) { NavigationLabelMode.ICON_AND_TEXT }
        }

    suspend fun setNavigationLabelMode(mode: NavigationLabelMode) = setPreference(NAVIGATION_LABEL_MODE_KEY, mode.name)

    /** 导航栏标签模式的同步缓存值：作为 collectAsState 初始值，
     *  避免 MainOverlay 路由重建时先闪现默认「图标+文字」再跳变为已选模式 */
    fun cachedNavigationLabelMode(): NavigationLabelMode {
        val cached = prefCache[NAVIGATION_LABEL_MODE_KEY] as? String ?: return NavigationLabelMode.ICON_AND_TEXT
        return try { NavigationLabelMode.valueOf(cached) } catch (_: IllegalArgumentException) { NavigationLabelMode.ICON_AND_TEXT }
    }

    /** 悬浮胶囊高级模糊（毛玻璃背景），默认关闭。主页顶栏与悬浮按钮随此开关一并生效 */
    val capsuleAdvancedBlurEnabled: Flow<Boolean> = preferenceFlow(CAPSULE_ADVANCED_BLUR_KEY, false)

    suspend fun setCapsuleAdvancedBlurEnabled(enabled: Boolean) = setPreference(CAPSULE_ADVANCED_BLUR_KEY, enabled)

    /** 悬浮胶囊高级模糊的同步缓存值：作为 collectAsState 初始值，避免首帧闪变 */
    fun cachedCapsuleAdvancedBlur(): Boolean = prefCache[CAPSULE_ADVANCED_BLUR_KEY] as? Boolean ?: false

    /** 悬浮胶囊液态玻璃效果（折射玻璃质感），默认关闭。开启后替代普通模糊，尺寸与布局不变 */
    val capsuleLiquidGlassEnabled: Flow<Boolean> = preferenceFlow(CAPSULE_LIQUID_GLASS_KEY, false)
    suspend fun setCapsuleLiquidGlassEnabled(enabled: Boolean) = setPreference(CAPSULE_LIQUID_GLASS_KEY, enabled)
    /** 液态玻璃的同步缓存值：作为 collectAsState 初始值，避免首帧闪变 */
    fun cachedCapsuleLiquidGlass(): Boolean = prefCache[CAPSULE_LIQUID_GLASS_KEY] as? Boolean ?: false

    /** 主页顶栏下滑隐藏，默认关闭。开启后向下滑动列表收起顶栏，搜索/添加按钮转为组合悬浮按钮 */
    val hideTopBarOnScrollEnabled: Flow<Boolean> = preferenceFlow(HIDE_TOPBAR_ON_SCROLL_KEY, false)

    suspend fun setHideTopBarOnScrollEnabled(enabled: Boolean) = setPreference(HIDE_TOPBAR_ON_SCROLL_KEY, enabled)

    /** 「下滑隐藏顶部栏」的同步缓存值：作为 collectAsState 的初始值，
     *  避免设置页首帧先渲染默认关闭态、下一帧才跳变为已开启的闪变（缓存未就绪时退回默认值） */
    fun cachedHideTopBarOnScroll(): Boolean = prefCache[HIDE_TOPBAR_ON_SCROLL_KEY] as? Boolean ?: false

    /** 顶栏收起后的状态栏处理方式，默认留白遮罩（仅「下滑隐藏顶栏」开启时生效） */
    val statusBarMode: Flow<StatusBarMode> = preferenceFlow(STATUS_BAR_MODE_KEY, StatusBarMode.SCRIM.name)
        .map { modeString ->
            try { StatusBarMode.valueOf(modeString) } catch (_: IllegalArgumentException) { StatusBarMode.SCRIM }
        }

    suspend fun setStatusBarMode(mode: StatusBarMode) = setPreference(STATUS_BAR_MODE_KEY, mode.name)

    /** 状态栏处理方式的同步缓存值：作为 collectAsState 的初始值，避免首帧闪变 */
    fun cachedStatusBarMode(): StatusBarMode {
        val cached = prefCache[STATUS_BAR_MODE_KEY] as? String ?: return StatusBarMode.SCRIM
        return try { StatusBarMode.valueOf(cached) } catch (_: IllegalArgumentException) { StatusBarMode.SCRIM }
    }

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

    val customGreeting: Flow<String> = preferenceFlow(CUSTOM_GREETING_KEY, "")

    suspend fun setCustomGreeting(greeting: String) = setPreference(CUSTOM_GREETING_KEY, greeting)

    val autoCompleteEnabled: Flow<Boolean> = preferenceFlow(AUTO_COMPLETE_KEY, true)

    suspend fun setAutoCompleteEnabled(enabled: Boolean) = setPreference(AUTO_COMPLETE_KEY, enabled)

    val completedToastEnabled: Flow<Boolean> = preferenceFlow(COMPLETED_TOAST_KEY, true)

    suspend fun setCompletedToastEnabled(enabled: Boolean) = setPreference(COMPLETED_TOAST_KEY, enabled)

    val hideBangumiAvatar: Flow<Boolean> = preferenceFlow(HIDE_BANGUMI_AVATAR_KEY, false)

    suspend fun setHideBangumiAvatar(hide: Boolean) = setPreference(HIDE_BANGUMI_AVATAR_KEY, hide)

    val greetingTypingEffect: Flow<Boolean> = preferenceFlow(GREETING_TYPING_EFFECT_KEY, true)

    suspend fun setGreetingTypingEffect(enabled: Boolean) = setPreference(GREETING_TYPING_EFFECT_KEY, enabled)

    val showUpdateBanner: Flow<Boolean> = preferenceFlow(SHOW_UPDATE_BANNER_KEY, true)

    suspend fun setShowUpdateBanner(show: Boolean) = setPreference(SHOW_UPDATE_BANNER_KEY, show)

    val showCalendarButton: Flow<Boolean> = preferenceFlow(SHOW_CALENDAR_BUTTON_KEY, true)

    suspend fun setShowCalendarButton(show: Boolean) = setPreference(SHOW_CALENDAR_BUTTON_KEY, show)

    val showSearchButton: Flow<Boolean> = preferenceFlow(SHOW_SEARCH_BUTTON_KEY, true)

    /** 多季番剧是否堆叠显示，默认关闭（半成品功能） */
    val seriesStackEnabled: Flow<Boolean> = preferenceFlow(SERIES_STACK_ENABLED_KEY, false)

    suspend fun setSeriesStackEnabled(enabled: Boolean) = setPreference(SERIES_STACK_ENABLED_KEY, enabled)

    /** 评分标准：使用源评分或手动打分，默认使用源评分 */
    val ratingStandard: Flow<RatingStandard> = preferenceFlow(RATING_STANDARD_KEY, RatingStandard.SOURCE.name)
        .map { runCatching { RatingStandard.valueOf(it) }.getOrDefault(RatingStandard.SOURCE) }

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

    val isFirstLaunch: Flow<Boolean> = preferenceFlow(IS_FIRST_LAUNCH_KEY, true)

    suspend fun setFirstLaunchCompleted() = setPreference(IS_FIRST_LAUNCH_KEY, false)

    val developerMode: Flow<Boolean> = preferenceFlow(DEVELOPER_MODE_KEY, false)

    suspend fun setDeveloperMode(enabled: Boolean) = setPreference(DEVELOPER_MODE_KEY, enabled)

    val shareButtonEnabled: Flow<Boolean> = preferenceFlow(SHARE_BUTTON_ENABLED_KEY, false)

    suspend fun setShareButtonEnabled(enabled: Boolean) = setPreference(SHARE_BUTTON_ENABLED_KEY, enabled)

    val autoSyncVisible: Flow<Boolean> = preferenceFlow(AUTO_SYNC_VISIBLE_KEY, false)

    suspend fun setAutoSyncVisible(visible: Boolean) = setPreference(AUTO_SYNC_VISIBLE_KEY, visible)

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
        setPreference(BANGUMI_PROXY_ENABLED_KEY, enabled)
        bangumiProxyEnabled = enabled
    }

    suspend fun setBangumiProxyHost(host: String) {
        val normalized = host.trim()
        setPreference(BANGUMI_PROXY_HOST_KEY, normalized)
        bangumiProxyHost = normalized
    }

    // HTTP 普通代理
    val httpProxyEnabledFlow: Flow<Boolean> = preferenceFlow(HTTP_PROXY_ENABLED_KEY, false)
    val httpProxyHostFlow: Flow<String> = preferenceFlow(HTTP_PROXY_HOST_KEY, DEFAULT_HTTP_PROXY_HOST)
    val httpProxyPortFlow: Flow<Int> = preferenceFlow(HTTP_PROXY_PORT_KEY, DEFAULT_HTTP_PROXY_PORT)

    suspend fun setHttpProxyEnabled(enabled: Boolean) {
        setPreference(HTTP_PROXY_ENABLED_KEY, enabled)
        httpProxyEnabled = enabled
    }

    suspend fun setHttpProxyHost(host: String) {
        val normalized = host.trim()
        setPreference(HTTP_PROXY_HOST_KEY, normalized)
        httpProxyHost = normalized
    }

    suspend fun setHttpProxyPort(port: Int) {
        setPreference(HTTP_PROXY_PORT_KEY, port)
        httpProxyPort = port
    }

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

    suspend fun setUpdateNotificationEnabled(enabled: Boolean) = setPreference(UPDATE_NOTIFICATION_ENABLED_KEY, enabled)

    val updateNotificationHour: Flow<Int> = preferenceFlow(UPDATE_NOTIFICATION_HOUR_KEY, 9)

    suspend fun setUpdateNotificationHour(hour: Int) = setPreference(UPDATE_NOTIFICATION_HOUR_KEY, hour)

    val updateNotificationMinute: Flow<Int> = preferenceFlow(UPDATE_NOTIFICATION_MINUTE_KEY, 0)

    suspend fun setUpdateNotificationMinute(minute: Int) = setPreference(UPDATE_NOTIFICATION_MINUTE_KEY, minute)

    /** 设置页是否显示「视频播放」聚合入口（开发者选项控制，默认隐藏） */
    val playerHubVisible: Flow<Boolean> = preferenceFlow(PLAYER_HUB_VISIBLE_KEY, false)
    suspend fun setPlayerHubVisible(visible: Boolean) = setPreference(PLAYER_HUB_VISIBLE_KEY, visible)

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

    val customFontPathFlow: Flow<String> = preferenceFlow(CUSTOM_FONT_PATH_KEY, "")

    suspend fun setCustomFontPath(path: String) = setPreference(CUSTOM_FONT_PATH_KEY, path)

    val appLanguageFlow: Flow<String> = preferenceFlow(APP_LANGUAGE_KEY, AppLanguage.SIMPLIFIED_CHINESE.name)

    suspend fun setAppLanguage(language: AppLanguage) {
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
