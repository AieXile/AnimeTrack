package com.aiexile.animetrack.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.data.NavigationStyle
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _isPagerScrollEnabled = MutableStateFlow(true)
    val isPagerScrollEnabled: StateFlow<Boolean> = _isPagerScrollEnabled.asStateFlow()

    fun setPagerScrollEnabled(enabled: Boolean) {
        _isPagerScrollEnabled.value = enabled
    }

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.SYSTEM
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun cycleThemeMode() {
        val nextMode = when (themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        setThemeMode(nextMode)
    }

    val themePreset: StateFlow<ThemePreset> = settingsRepository.themePreset
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemePreset.MONO_BLACK
        )

    fun setThemePreset(preset: ThemePreset) {
        viewModelScope.launch {
            settingsRepository.setThemePreset(preset)
        }
    }

    val showFavorites: StateFlow<Boolean> = settingsRepository.showFavorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    val showTimeline: StateFlow<Boolean> = settingsRepository.showTimeline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setShowFavorites(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowFavorites(show)
        }
    }

    fun setShowTimeline(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowTimeline(show)
        }
    }

    val showSchedule: StateFlow<Boolean> = settingsRepository.showSchedule
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setShowSchedule(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowSchedule(show)
        }
    }

    val navigationStyle: StateFlow<NavigationStyle> = settingsRepository.navigationStyle
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = NavigationStyle.BOTTOM
        )

    fun setNavigationStyle(style: NavigationStyle) {
        viewModelScope.launch {
            settingsRepository.setNavigationStyle(style)
        }
    }

    val fabLocation: StateFlow<FabLocation> = settingsRepository.fabLocation
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = FabLocation.BOTTOM_RIGHT
        )

    fun setFabLocation(location: FabLocation) {
        viewModelScope.launch {
            settingsRepository.setFabLocation(location)
        }
    }

    val customGreeting: StateFlow<String> = settingsRepository.customGreeting
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    fun setCustomGreeting(greeting: String) {
        viewModelScope.launch {
            settingsRepository.setCustomGreeting(greeting)
        }
    }

    val greetingTypingEffect: StateFlow<Boolean> = settingsRepository.greetingTypingEffect
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setGreetingTypingEffect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setGreetingTypingEffect(enabled)
        }
    }

    val autoCompleteEnabled: StateFlow<Boolean> = settingsRepository.autoCompleteEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setAutoCompleteEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoCompleteEnabled(enabled)
        }
    }

    val completedToastEnabled: StateFlow<Boolean> = settingsRepository.completedToastEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setCompletedToastEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCompletedToastEnabled(enabled)
        }
    }

    val hideBangumiAvatar: StateFlow<Boolean> = settingsRepository.hideBangumiAvatar
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    fun setHideBangumiAvatar(hide: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideBangumiAvatar(hide)
        }
    }

    val showUpdateBanner: StateFlow<Boolean> = settingsRepository.showUpdateBanner
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setShowUpdateBanner(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowUpdateBanner(show)
        }
    }

    val showCalendarButton: StateFlow<Boolean> = settingsRepository.showCalendarButton
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setShowCalendarButton(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowCalendarButton(show)
        }
    }

    val showSearchButton: StateFlow<Boolean> = settingsRepository.showSearchButton
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    fun setShowSearchButton(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowSearchButton(show)
        }
    }

    val webdavUrl: StateFlow<String> = settingsRepository.webdavUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    fun setWebdavUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setWebdavUrl(url)
        }
    }

    val webdavUsername: StateFlow<String> = settingsRepository.webdavUsername
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    fun setWebdavUsername(username: String) {
        viewModelScope.launch {
            settingsRepository.setWebdavUsername(username)
        }
    }

    val webdavPassword: StateFlow<String> = settingsRepository.webdavPassword
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ""
        )

    fun setWebdavPassword(password: String) {
        viewModelScope.launch {
            settingsRepository.setWebdavPassword(password)
        }
    }

    val webdavBackupStrategy: StateFlow<Int> = settingsRepository.webdavBackupStrategy
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    fun setWebdavBackupStrategy(strategy: Int) {
        viewModelScope.launch {
            settingsRepository.setWebdavBackupStrategy(strategy)
        }
    }

    val webdavRestoreMode: StateFlow<Int> = settingsRepository.webdavRestoreMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    fun setWebdavRestoreMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setWebdavRestoreMode(mode)
        }
    }

    val webdavLastSyncTime: StateFlow<Long> = settingsRepository.webdavLastSyncTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0L
        )

    fun setWebdavLastSyncTime(time: Long) {
        viewModelScope.launch {
            settingsRepository.setWebdavLastSyncTime(time)
        }
    }

    val isFirstLaunch: StateFlow<Boolean?> = settingsRepository.isFirstLaunch
        .map { it as Boolean? }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun completeFirstLaunch() {
        viewModelScope.launch {
            settingsRepository.setFirstLaunchCompleted()
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ThemeViewModel(AppContainer.getSettingsRepository()) as T
        }
    }
}
