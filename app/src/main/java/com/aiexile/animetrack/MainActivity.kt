package com.aiexile.animetrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.animation.ObjectAnimator
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.LocalWindowSizeClass
import com.aiexile.animetrack.ui.theme.ThemePreset
import com.aiexile.animetrack.ui.navigation.AnimeTrackApp
import com.aiexile.animetrack.ui.theme.AnimeTrackTheme
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.push.PushRegistrationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {

    // 自定义字体异步加载结果：null 表示尚未加载完成，先用默认 FontFamily 渲染
    private val customFontFamily = MutableStateFlow<FontFamily?>(null)

    // 应用级协程作用域（生命周期与进程一致，适合“启动即完成、不随 Activity 销毁”的后台任务）
    private val appScope get() = (application as AnimeTrackApp).appScope

    // Android 13+ 通知权限请求（推送通知显示的前提，需在 onCreate 前注册）
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: android.content.Context) {
        // 在 Activity 创建前应用语言设置
        AppContainer.initialize(newBase.applicationContext)
        val languageName = AppContainer.getSettingsRepository()
            .getAppLanguageBlocking()
        val locale = when (languageName) {
            "ENGLISH" -> Locale.forLanguageTag("en")
            "TRADITIONAL_CHINESE" -> Locale.forLanguageTag("zh-TW")
            else -> Locale.forLanguageTag("zh-CN")
        }
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onStart() {
        super.onStart()
        AppContainer.sessionStartTime = System.currentTimeMillis()
        appScope.launch {
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
                appScope.launch {
                    AppContainer.getUsageStatsRepository().addUsageSeconds(elapsedSeconds)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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

        // Android 13+：主界面启动时请求通知权限（推送通知显示的前提）
        // 已授权或用户曾拒绝过（系统不再弹窗）时静默跳过
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // App 启动时检查并上报极光推送 registrationId
        appScope.launch {
            PushRegistrationHelper.reportRegistrationIdIfNeeded(applicationContext)
        }
        // 字体异步加载：先用默认 FontFamily 渲染 UI，后台加载自定义字体完成后通过 StateFlow 触发更新。
        // 保留原 CUSTOM 分支路径判断逻辑（非空 + File.exists），仅将 Typeface.createFromFile 移至 IO 线程。
        val settingsRepository = AppContainer.getSettingsRepository()
        appScope.launch {
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
                // 大屏适配：计算窗口尺寸档位（Compact/Medium/Expanded）并全局下发
                val windowSizeClass = calculateWindowSizeClass(this)
                CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                    AnimeTrackApp(settingsRepository = settingsRepository, isDataLoaded = isDataLoaded)
                }
            }
        }
    }
}
