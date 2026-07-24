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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {

    // 自定义字体异步加载结果：null 表示尚未加载完成，先用默认 FontFamily 渲染
    private val customFontFamily = MutableStateFlow<FontFamily?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        // 在 Activity 创建前应用语言设置
        AppContainer.initialize(newBase.applicationContext)
        val languageName = AppContainer.getSettingsRepository()
            .getAppLanguageBlocking()
        val locale = when (languageName) {
            "ENGLISH" -> Locale("en")
            "TRADITIONAL_CHINESE" -> Locale("zh", "TW")
            else -> Locale("zh", "CN")
        }
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onStart() {
        super.onStart()
        AppContainer.sessionStartTime = System.currentTimeMillis()
        GlobalScope.launch(Dispatchers.IO) {
            AppContainer.getUsageStatsRepository().incrementOpenCount()
            // 冷启动 / 从后台切回前台时，拉取服务器订阅列表到本地（只下载不上传）
            AppContainer.getAnimeRepository().triggerPullSubscriptionsFromServer()
            // 用户当日首次启动时上报活跃（失败静默）
            com.aiexile.animetrack.data.ActivityReportHelper.reportActivityIfNeeded()
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

        // attachBaseContext 已调用 AppContainer.initialize，此处不再重复调用
        enableEdgeToEdge()
        // App 启动时检查并上报极光推送 registrationId
        GlobalScope.launch(Dispatchers.IO) {
            PushRegistrationHelper.reportRegistrationIdIfNeeded(applicationContext)
        }
        // 字体异步加载：先用默认 FontFamily 渲染 UI，后台加载自定义字体完成后通过 StateFlow 触发更新。
        // 保留原 CUSTOM 分支路径判断逻辑（非空 + File.exists），仅将 Typeface.createFromFile 移至 IO 线程。
        val settingsRepository = AppContainer.getSettingsRepository()
        GlobalScope.launch(Dispatchers.IO) {
            settingsRepository.customFontPathFlow.collect { path ->
                val loaded = if (!path.isNullOrBlank() && File(path).exists()) {
                    FontFamily(android.graphics.Typeface.createFromFile(path))
                } else {
                    null
                }
                customFontFamily.value = loaded
            }
        }
        com.aiexile.animetrack.data.sync.WebDAVAutoSyncManager.getInstance().onAppOpen()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(ThemeMode.SYSTEM)
            val themePreset by settingsRepository.themePreset.collectAsState(ThemePreset.MONO_BLACK)
            val systemDarkTheme = isSystemInDarkTheme()

            val fontFamily by settingsRepository.fontFamilyFlow.collectAsState(initial = "SYSTEM")
            val customFontLoaded by customFontFamily.collectAsState()

            val currentFontFamily = remember(fontFamily, customFontLoaded) {
                when (fontFamily) {
                    "MISANS" -> FontFamily(
                        Font(R.font.misans_regular),
                        Font(R.font.misans_bold),
                        Font(R.font.misans_medium)
                    )
                    "CUSTOM" -> customFontLoaded ?: FontFamily.Default
                    else -> FontFamily.Default
                }
            }

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDarkTheme
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            AnimeTrackTheme(
                darkTheme = darkTheme,
                themePreset = themePreset,
                fontFamily = currentFontFamily
            ) {
                AnimeTrackApp(settingsRepository = settingsRepository, isDataLoaded = isDataLoaded)
            }
        }
    }
}
