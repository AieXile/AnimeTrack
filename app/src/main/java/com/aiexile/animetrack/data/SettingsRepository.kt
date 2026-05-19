package com.aiexile.animetrack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
        private val SKIPPED_VERSION_KEY = stringPreferencesKey("skipped_version")
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
            val presetString = preferences[THEME_PRESET_KEY] ?: ThemePreset.VIBRANT_BLUE.name
            try {
                ThemePreset.valueOf(presetString)
            } catch (e: IllegalArgumentException) {
                ThemePreset.VIBRANT_BLUE
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

    val skippedVersion: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SKIPPED_VERSION_KEY] ?: ""
        }

    suspend fun setSkippedVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[SKIPPED_VERSION_KEY] = version
        }
    }
}
