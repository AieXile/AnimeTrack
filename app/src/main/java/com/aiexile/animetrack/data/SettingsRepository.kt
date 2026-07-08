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
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.ui.theme.ThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val THEME_PRESET_KEY = stringPreferencesKey("theme_preset")
        private val SHOW_FAVORITES_KEY = booleanPreferencesKey("show_favorites")
        private val SHOW_TIMELINE_KEY = booleanPreferencesKey("show_timeline")
        private val SHOW_SCHEDULE_KEY = booleanPreferencesKey("show_schedule")
        private val NAVIGATION_STYLE_KEY = stringPreferencesKey("navigation_style")
        private val FAB_LOCATION_KEY = stringPreferencesKey("fab_location")
        private val CUSTOM_GREETING_KEY = stringPreferencesKey("custom_greeting")
        private val AUTO_COMPLETE_KEY = booleanPreferencesKey("auto_complete_enabled")
        private val COMPLETED_TOAST_KEY = booleanPreferencesKey("completed_toast_enabled")
        private val HIDE_BANGUMI_AVATAR_KEY = booleanPreferencesKey("hide_bangumi_avatar")
        private val GREETING_TYPING_EFFECT_KEY = booleanPreferencesKey("greeting_typing_effect")
        private val SHOW_UPDATE_BANNER_KEY = booleanPreferencesKey("show_update_banner")
        private val SHOW_CALENDAR_BUTTON_KEY = booleanPreferencesKey("show_calendar_button")
        private val SHOW_SEARCH_BUTTON_KEY = booleanPreferencesKey("show_search_button")
        private val SERIES_STACK_ENABLED_KEY = booleanPreferencesKey("series_stack_enabled")
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

        private val PLAYER_DEFAULT_SPEED_KEY = floatPreferencesKey("player_default_speed")
        private val PLAYER_HARDWARE_ACCELERATION_KEY = booleanPreferencesKey("player_hardware_acceleration")
        private val PLAYER_REMEMBER_POSITION_KEY = booleanPreferencesKey("player_remember_position")
        private val PLAYER_AUTO_PLAY_NEXT_KEY = booleanPreferencesKey("player_auto_play_next")
        private val PLAYER_LONG_PRESS_SPEED_KEY = floatPreferencesKey("player_long_press_speed")

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

        const val DEFAULT_TMDB_API_KEY = "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIwZTFhNzUyY2Q3ZWI4ZjE4MzljMzBlZDNjZGRmMTI1ZCIsIm5iZiI6MTc3OTk2NzU2Ny4zMTEsInN1YiI6IjZhMTgyNjRmNTNmZTM5ZjRhNzE1ZGM2NyIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.R7iyiJrJR2Fs7uE65xveVGaPnAkzJHnMyQ4OvM0zZ5o"
    }

    private fun <T> preferenceFlow(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data.map { it[key] ?: default }.distinctUntilChanged()

    private suspend fun <T> setPreference(key: Preferences.Key<T>, value: T) {
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

    init {
        GlobalScope.launch(Dispatchers.IO) {
            val key = context.dataStore.data.first()[TMDB_API_KEY_KEY]
            currentTmdbApiKey = key ?: DEFAULT_TMDB_API_KEY

            val prefs = context.dataStore.data.first()
            bangumiProxyEnabled = prefs[BANGUMI_PROXY_ENABLED_KEY] ?: false
            bangumiProxyHost = prefs[BANGUMI_PROXY_HOST_KEY] ?: DEFAULT_BANGUMI_PROXY_HOST
            httpProxyEnabled = prefs[HTTP_PROXY_ENABLED_KEY] ?: false
            httpProxyHost = prefs[HTTP_PROXY_HOST_KEY] ?: DEFAULT_HTTP_PROXY_HOST
            httpProxyPort = prefs[HTTP_PROXY_PORT_KEY] ?: DEFAULT_HTTP_PROXY_PORT
            userAuthBaseUrl = prefs[USER_AUTH_BASE_URL_KEY] ?: DEFAULT_USER_AUTH_BASE_URL
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

    val showFavorites: Flow<Boolean> = preferenceFlow(SHOW_FAVORITES_KEY, false)

    val showTimeline: Flow<Boolean> = preferenceFlow(SHOW_TIMELINE_KEY, true)

    suspend fun setShowFavorites(show: Boolean) = setPreference(SHOW_FAVORITES_KEY, show)

    suspend fun setShowTimeline(show: Boolean) = setPreference(SHOW_TIMELINE_KEY, show)

    val showSchedule: Flow<Boolean> = preferenceFlow(SHOW_SCHEDULE_KEY, true)

    suspend fun setShowSchedule(show: Boolean) = setPreference(SHOW_SCHEDULE_KEY, show)

    val navigationStyle: Flow<NavigationStyle> = preferenceFlow(NAVIGATION_STYLE_KEY, NavigationStyle.BOTTOM.name)
        .map { styleString ->
            try { NavigationStyle.valueOf(styleString) } catch (_: IllegalArgumentException) { NavigationStyle.BOTTOM }
        }

    suspend fun setNavigationStyle(style: NavigationStyle) = setPreference(NAVIGATION_STYLE_KEY, style.name)

    val fabLocation: Flow<FabLocation> = preferenceFlow(FAB_LOCATION_KEY, FabLocation.BOTTOM_RIGHT.name)
        .map { locationString ->
            try { FabLocation.valueOf(locationString) } catch (_: IllegalArgumentException) { FabLocation.BOTTOM_RIGHT }
        }

    suspend fun setFabLocation(location: FabLocation) = setPreference(FAB_LOCATION_KEY, location.name)

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

    /** 多季番剧是否堆叠显示，默认开启 */
    val seriesStackEnabled: Flow<Boolean> = preferenceFlow(SERIES_STACK_ENABLED_KEY, true)

    suspend fun setSeriesStackEnabled(enabled: Boolean) = setPreference(SERIES_STACK_ENABLED_KEY, enabled)

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
}
