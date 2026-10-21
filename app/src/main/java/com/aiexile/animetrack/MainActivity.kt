package com.aiexile.animetrack

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.model.DarkStyle
import com.aiexile.animetrack.model.ThemeMode
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.LocalWindowSizeClass
import com.aiexile.animetrack.ui.components.chrome.LocalAdvancedBlurConfig
import com.aiexile.animetrack.ui.components.chrome.chromeSupportsBlur
import com.aiexile.animetrack.ui.icons.IconPack
import com.aiexile.animetrack.ui.icons.LocalIconPack
import com.aiexile.animetrack.ui.theme.LocalChromeStyle
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

    // Splash 退出动画是否已完全结束：首启隐私同意弹窗需等 splash 消失后再弹
    // （Compose Dialog 是独立 Window，会直接盖在 Activity 窗口的 splash 遮罩之上）
    private val splashDismissed = MutableStateFlow(false)

    // 应用级协程作用域（生命周期与进程一致，适合“启动即完成、不随 Activity 销毁”的后台任务）
    private val appScope get() = (application as AnimeTrackApp).appScope

    // Android 13+ 通知权限请求（推送通知显示的前提，需在 onCreate 前注册）
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 用户拒绝通知权限后「推送收不到」会被当成 bug 反馈，先留一条可检索的结论
        AppLogManager.i("Permission", "通知权限: ${if (granted) "已授予" else "被拒绝"}")
    }

    /** 请求通知权限（已授权或系统不再弹窗时静默跳过）；供冷启动与隐私弹窗同意后复用 */
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
        AppLogManager.i("App", "进入前台")
        AppContainer.sessionStartTime = System.currentTimeMillis()
        // SSE 实时事件：前台保持连接（切后台在 onStop 断开）
        AppContainer.getSseClient().start()
        appScope.launch {
            AppContainer.getUsageStatsRepository().incrementOpenCount()
            // 冷启动 / 从后台切回前台时，拉取服务器订阅列表到本地（只下载不上传）
            AppContainer.getAnimeRepository().triggerPullSubscriptionsFromServer()
            // 用户当日首次启动时上报活跃（失败静默）；隐私政策未同意前不上报（合规）
            if (AppContainer.getSettingsRepository().isPrivacyPolicyAcceptedBlocking()) {
                com.aiexile.animetrack.data.ActivityReportHelper.reportActivityIfNeeded()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // SSE 实时事件：切后台断开，回前台（onStart）重连
        AppContainer.getSseClient().stop()
        val startTime = AppContainer.sessionStartTime
        if (startTime > 0) {
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            AppContainer.sessionStartTime = 0L
            AppLogManager.i("App", "切到后台（前台停留 ${elapsedSeconds} 秒）")
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

        // 冷启动埋点：进程启动 → 首帧耗时（白屏/启动慢类反馈的第一手数据）
        if (savedInstanceState == null) {
            Choreographer.getInstance().postFrameCallback {
                val costMs = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
                AppLogManager.i("App", "首帧完成，冷启动耗时 ${costMs}ms")
            }
        }

        // 挂起 Splash Screen 直到 DataStore 加载完毕
        val isDataLoaded = java.util.concurrent.atomic.AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition { !isDataLoaded.get() }

        // Activity 重建（旋转/进程恢复）时无 Splash，直接视为已消失
        if (savedInstanceState != null) {
            splashDismissed.value = true
        }

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val fadeOut = ObjectAnimator.ofFloat(
                splashScreenView.view, View.ALPHA, 1f, 0f
            )
            fadeOut.duration = 300L
            fadeOut.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    splashScreenView.remove()
                    // Splash 完全消失后才允许首启隐私同意弹窗显示（避免弹窗盖在 splash 上）
                    splashDismissed.value = true
                }
            })
            fadeOut.start()
        }

        // attachBaseContext 已调用 AppContainer.initialize，此处不再重复调用
        enableEdgeToEdge()

        // 隐私合规：未同意隐私政策前不申请通知权限、不初始化/上报极光推送
        // （首启弹窗同意后经 AnimeTrackApp composable 的回调补做）
        val privacyAccepted = AppContainer.getSettingsRepository().isPrivacyPolicyAcceptedBlocking()
        // Android 13+：请求通知权限（推送通知显示的前提）；已授权或用户曾拒绝过（系统不再弹窗）时静默跳过
        if (privacyAccepted) {
            requestNotificationPermissionIfNeeded()
        }
        // App 启动时检查并上报极光推送 registrationId；依赖 JPush 初始化（隐私同意后才会初始化）
        if (privacyAccepted) {
            appScope.launch {
                PushRegistrationHelper.reportRegistrationIdIfNeeded(applicationContext)
            }
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
            val darkStyle by settingsRepository.darkStyle.collectAsState(DarkStyle.BOOST)
            val iconPack by settingsRepository.iconPack.collectAsState(settingsRepository.cachedIconPack())
            // 界面外壳材质：初始值取同步缓存，避免冷启动首帧先渲染默认档再跳变
            val chromeStyle by settingsRepository.chromeStyle
                .collectAsState(settingsRepository.cachedChromeStyle())
            // 高级模糊（HAZE 档）参数：全局下发一次，悬浮胶囊/悬浮按钮/顶栏共用
            val blurRadius by settingsRepository.advancedBlurRadius
                .collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_RADIUS)
            val blurBackgroundAlpha by settingsRepository.advancedBlurBackgroundAlpha
                .collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_BACKGROUND_ALPHA)
            val blurTintAlpha by settingsRepository.advancedBlurTintAlpha
                .collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_TINT_ALPHA)
            val blurNoise by settingsRepository.advancedBlurNoise
                .collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_NOISE)
            val advancedBlurConfig = remember(blurRadius, blurBackgroundAlpha, blurTintAlpha, blurNoise) {
                AdvancedBlurConfig(
                    blurRadius = blurRadius.dp,
                    backgroundColorAlpha = blurBackgroundAlpha,
                    tintAlpha = blurTintAlpha,
                    noiseFactor = blurNoise
                )
            }
            val systemDarkTheme = isSystemInDarkTheme()

            val fontFamily by settingsRepository.fontFamilyFlow.collectAsState(initial = "SYSTEM")
            val customFontLoaded by customFontFamily.collectAsState()

            val currentFontFamily = remember(fontFamily, customFontLoaded) {
                when (fontFamily) {
                    "MISANS" -> FontFamily(
                        Font(R.font.misans_regular, FontWeight.Normal),
                        Font(R.font.misans_medium, FontWeight.Medium),
                        Font(R.font.misans_bold, FontWeight.Bold)
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
                darkStyle = darkStyle,
                fontFamily = currentFontFamily
            ) {
                // 大屏适配：计算窗口尺寸档位（Compact/Medium/Expanded）并全局下发；
                // 图标包与界面外壳材质同步全局下发，切换时整树重组刷新
                // 低版本（不支持 RenderEffect）把档位降级为标准，避免玻璃渲染无效果
                val windowSizeClass = calculateWindowSizeClass(this)
                CompositionLocalProvider(
                    LocalWindowSizeClass provides windowSizeClass,
                    LocalIconPack provides iconPack,
                    LocalChromeStyle provides if (chromeSupportsBlur()) chromeStyle else
                        ChromeStyle.STANDARD,
                    LocalAdvancedBlurConfig provides advancedBlurConfig
                ) {
                    val splashDismissed by splashDismissed.collectAsState()
                    AnimeTrackApp(
                        settingsRepository = settingsRepository,
                        isDataLoaded = isDataLoaded,
                        splashDismissed = splashDismissed
                    )
                }
            }
        }
    }
}
