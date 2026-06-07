package com.aiexile.animetrack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.ui.theme.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        private val SKIPPED_VERSION_KEY = stringPreferencesKey("skipped_version")
        private val WEBDAV_URL_KEY = stringPreferencesKey("webdav_url")
        private val WEBDAV_USERNAME_KEY = stringPreferencesKey("webdav_username")
        private val WEBDAV_PASSWORD_KEY = stringPreferencesKey("webdav_password")
        private val WEBDAV_BACKUP_STRATEGY_KEY = intPreferencesKey("webdav_backup_strategy")
        private val WEBDAV_RESTORE_MODE_KEY = intPreferencesKey("webdav_restore_mode")
        private val WEBDAV_LAST_SYNC_TIME_KEY = longPreferencesKey("webdav_last_sync_time")
        private val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
        private val DEVELOPER_MODE_KEY = booleanPreferencesKey("developer_mode")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val modeString = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(modeString)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    val themePreset: Flow<ThemePreset> = context.dataStore.data
        .map { preferences ->
            val presetString = preferences[THEME_PRESET_KEY] ?: ThemePreset.MONO_BLACK.name
            try {
                ThemePreset.valueOf(presetString)
            } catch (e: IllegalArgumentException) {
                ThemePreset.MONO_BLACK
            }
        }

    suspend fun setThemePreset(preset: ThemePreset) {
        context.dataStore.edit { preferences ->
            preferences[THEME_PRESET_KEY] = preset.name
        }
    }

    val showFavorites: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_FAVORITES_KEY] ?: false
        }

    val showTimeline: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_TIMELINE_KEY] ?: true
        }

    suspend fun setShowFavorites(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_FAVORITES_KEY] = show
        }
    }

    suspend fun setShowTimeline(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_TIMELINE_KEY] = show
        }
    }

    val showSchedule: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_SCHEDULE_KEY] ?: true
        }

    suspend fun setShowSchedule(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_SCHEDULE_KEY] = show
        }
    }

    val navigationStyle: Flow<NavigationStyle> = context.dataStore.data
        .map { preferences ->
            val styleString = preferences[NAVIGATION_STYLE_KEY] ?: NavigationStyle.BOTTOM.name
            try {
                NavigationStyle.valueOf(styleString)
            } catch (e: IllegalArgumentException) {
                NavigationStyle.BOTTOM
            }
        }

    suspend fun setNavigationStyle(style: NavigationStyle) {
        context.dataStore.edit { preferences ->
            preferences[NAVIGATION_STYLE_KEY] = style.name
        }
    }

    val fabLocation: Flow<FabLocation> = context.dataStore.data
        .map { preferences ->
            val locationString = preferences[FAB_LOCATION_KEY] ?: FabLocation.BOTTOM_RIGHT.name
            try {
                FabLocation.valueOf(locationString)
            } catch (e: IllegalArgumentException) {
                FabLocation.BOTTOM_RIGHT
            }
        }

    suspend fun setFabLocation(location: FabLocation) {
        context.dataStore.edit { preferences ->
            preferences[FAB_LOCATION_KEY] = location.name
        }
    }

    val customGreeting: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[CUSTOM_GREETING_KEY] ?: ""
        }

    suspend fun setCustomGreeting(greeting: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_GREETING_KEY] = greeting
        }
    }

    val autoCompleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_COMPLETE_KEY] ?: true
        }

    suspend fun setAutoCompleteEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_COMPLETE_KEY] = enabled
        }
    }

    val completedToastEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[COMPLETED_TOAST_KEY] ?: true
        }

    suspend fun setCompletedToastEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[COMPLETED_TOAST_KEY] = enabled
        }
    }

    val hideBangumiAvatar: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HIDE_BANGUMI_AVATAR_KEY] ?: false
        }

    suspend fun setHideBangumiAvatar(hide: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_BANGUMI_AVATAR_KEY] = hide
        }
    }

    val greetingTypingEffect: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[GREETING_TYPING_EFFECT_KEY] ?: true
        }

    suspend fun setGreetingTypingEffect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[GREETING_TYPING_EFFECT_KEY] = enabled
        }
    }

    val showUpdateBanner: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_UPDATE_BANNER_KEY] ?: true
        }

    suspend fun setShowUpdateBanner(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_UPDATE_BANNER_KEY] = show
        }
    }

    val showCalendarButton: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_CALENDAR_BUTTON_KEY] ?: true
        }

    suspend fun setShowCalendarButton(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_CALENDAR_BUTTON_KEY] = show
        }
    }

    val showSearchButton: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_SEARCH_BUTTON_KEY] ?: true
        }

    suspend fun setShowSearchButton(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_SEARCH_BUTTON_KEY] = show
        }
    }

    val skippedVersion: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SKIPPED_VERSION_KEY] ?: ""
        }

    suspend fun setSkippedVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[SKIPPED_VERSION_KEY] = version
        }
    }

    val webdavUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[WEBDAV_URL_KEY] ?: ""
        }

    suspend fun setWebdavUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_URL_KEY] = url
        }
    }

    val webdavUsername: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[WEBDAV_USERNAME_KEY] ?: ""
        }

    suspend fun setWebdavUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_USERNAME_KEY] = username
        }
    }

    val webdavPassword: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[WEBDAV_PASSWORD_KEY] ?: ""
        }

    suspend fun setWebdavPassword(password: String) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_PASSWORD_KEY] = password
        }
    }

    val webdavBackupStrategy: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[WEBDAV_BACKUP_STRATEGY_KEY] ?: 0
        }

    suspend fun setWebdavBackupStrategy(strategy: Int) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_BACKUP_STRATEGY_KEY] = strategy
        }
    }

    val webdavRestoreMode: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[WEBDAV_RESTORE_MODE_KEY] ?: 0
        }

    suspend fun setWebdavRestoreMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_RESTORE_MODE_KEY] = mode
        }
    }

    val webdavLastSyncTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[WEBDAV_LAST_SYNC_TIME_KEY] ?: 0L
        }

    suspend fun setWebdavLastSyncTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[WEBDAV_LAST_SYNC_TIME_KEY] = time
        }
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_FIRST_LAUNCH_KEY] ?: true
        }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { preferences ->
            preferences[IS_FIRST_LAUNCH_KEY] = false
        }
    }

    val developerMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DEVELOPER_MODE_KEY] ?: false
        }

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEVELOPER_MODE_KEY] = enabled
        }
    }
}
