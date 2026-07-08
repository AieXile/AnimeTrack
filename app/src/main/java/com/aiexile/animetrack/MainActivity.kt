package com.aiexile.animetrack

import android.os.Bundle
import android.animation.ObjectAnimator
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.theme.ThemePreset
import com.aiexile.animetrack.ui.navigation.AnimeTrackApp
import com.aiexile.animetrack.ui.theme.AnimeTrackTheme
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.push.PushRegistrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        AppContainer.sessionStartTime = System.currentTimeMillis()
        GlobalScope.launch(Dispatchers.IO) {
            AppContainer.getUsageStatsRepository().incrementOpenCount()
            // 冷启动 / 从后台切回前台时，拉取服务器订阅列表到本地（只下载不上传）
            AppContainer.getAnimeRepository().triggerPullSubscriptionsFromServer()
        }
    }

    override fun onStop() {
        super.onStop()
        val startTime = AppContainer.sessionStartTime
        if (startTime > 0) {
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            AppContainer.sessionStartTime = 0L
            if (elapsedSeconds >= 5) {
                GlobalScope.launch(Dispatchers.IO) {
                    AppContainer.getUsageStatsRepository().addUsageSeconds(elapsedSeconds)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 挂起 Splash Screen 直到 DataStore 加载完毕
        val isDataLoaded = java.util.concurrent.atomic.AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition { !isDataLoaded.get() }

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenView.view, View.ALPHA, 1f, 0f
            )
            fadeOut.duration = 300L
            fadeOut.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    splashScreenView.remove()
                }
            })
            fadeOut.start()
        }

        AppContainer.initialize(applicationContext)
        enableEdgeToEdge()
        // App 启动时检查并上报极光推送 registrationId
        GlobalScope.launch(Dispatchers.IO) {
            PushRegistrationHelper.reportRegistrationIdIfNeeded(applicationContext)
        }
        com.aiexile.animetrack.data.sync.WebDAVAutoSyncManager.getInstance().onAppOpen()
        setContent {
            val settingsRepository = AppContainer.getSettingsRepository()
            val themeMode by settingsRepository.themeMode.collectAsState(ThemeMode.SYSTEM)
            val themePreset by settingsRepository.themePreset.collectAsState(ThemePreset.MONO_BLACK)
            val systemDarkTheme = isSystemInDarkTheme()

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            AnimeTrackTheme(
                darkTheme = darkTheme,
                themePreset = themePreset
            ) {
                AnimeTrackApp(settingsRepository = settingsRepository, isDataLoaded = isDataLoaded)
            }
        }
    }
}
