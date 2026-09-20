package com.aiexile.animetrack

import android.app.Application
import cn.jpush.android.api.JPushInterface
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aiexile.animetrack.data.crash.CrashReporter
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.sse.SseEventTypes
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Interceptor

class AnimeTrackApp : Application(), ImageLoaderFactory {

    /** 崩溃 tombstone 上报延迟：避开启动高峰（网络、磁盘、UI 并发初始化） */
    private val reportTombstoneDelayMs = 5_000L

    // 应用级协程作用域：供 MainActivity、SettingsRepository 等复用，替代 GlobalScope
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bilibiliRefererInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val newRequest = if (url.contains("hdslb.com", ignoreCase = true)) {
            request.newBuilder()
                .header("Referer", "https://www.bilibili.com/")
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    private val wsrvNlFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        // wsrv.nl 代理失败时回退到原始 lain.bgm.tv URL
        if (!response.isSuccessful && request.url.host == "wsrv.nl") {
            val originalUrl = request.url.queryParameter("url")
            if (originalUrl != null) {
                response.close()
                val fallbackRequest = request.newBuilder().url(originalUrl).build()
                return@Interceptor chain.proceed(fallbackRequest)
            }
        }
        response
    }

    override fun onCreate() {
        super.onCreate()
        // 应用级依赖容器在此初始化：Application 保证先于所有组件（Activity/Service/Receiver）创建。
        // 此前仅在 MainActivity.attachBaseContext 初始化，导致 PlaybackService 等可被系统
        // 独立拉起的组件（通知栏媒体卡片/媒体按键/会话恢复）在冷启动进程中拿到未就绪的容器而崩溃；
        // MainActivity 处的调用保留作幂等双保险
        AppContainer.initialize(this)
        // 文件日志系统：须在其他组件写日志前初始化（崩溃捕获自此刻生效）
        AppLogManager.init(this)
        // xCrash 崩溃捕获（Java/Native/ANR tombstone）：须晚于 AppLogManager 初始化，
        // 保证 rethrow 链为 xCrash 写 tombstone → AppLogManager 写崩溃日志 → 系统默认处理
        // （本地捕获落盘，不上传，可在隐私同意前初始化；上报见 initPrivacyGatedComponents）
        CrashReporter.init(this)
        JPushInterface.setDebugMode(BuildConfig.DEBUG)
        // 隐私合规：JPush 推送与崩溃 tombstone 上报涉及设备信息收集与对外上传，
        // 已同意隐私政策的用户立即初始化；未同意用户在弹窗同意后经 onPrivacyAccepted() 补初始化
        if (AppContainer.getSettingsRepository().isPrivacyPolicyAcceptedBlocking()) {
            initPrivacyGatedComponents()
        }
        // SSE 踢下线事件：立即标记 token 失效（横幅由 AnimeTrackApp UI 层
        // 收集 tokenExpired/tokenKicked 显示，与被动检测链路共用同一状态）
        appScope.launch {
            AppContainer.getSseClient().events.collect { event ->
                if (event.type == SseEventTypes.SESSION_KICKED) {
                    AppContainer.getUserAuthManager().markTokenExpired(kicked = true)
                }
            }
        }
    }

    /** 隐私同意后才允许初始化的组件是否已就绪（防止重复初始化） */
    @Volatile
    private var privacyGatedInitialized = false

    /**
     * 初始化涉及个人信息收集/对外上报的组件：
     * - JPush 推送（设备标识注册）
     * - 崩溃 tombstone 静默上报（含 deviceId，延迟避开启动高峰，IO 线程逐个上传）
     * 冷启动时已同意用户在 onCreate 调用；首启弹窗同意后经 [onPrivacyAccepted] 补初始化
     */
    private fun initPrivacyGatedComponents() {
        if (privacyGatedInitialized) return
        privacyGatedInitialized = true
        appScope.launch {
            JPushInterface.init(this@AnimeTrackApp)
        }
        appScope.launch {
            delay(reportTombstoneDelayMs)
            CrashReporter.reportPendingTombstones()
        }
    }

    /** 用户在首启隐私政策弹窗点击同意后调用：补初始化被延后的上报组件 */
    fun onPrivacyAccepted() {
        initPrivacyGatedComponents()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor(bilibiliRefererInterceptor)
                    .addInterceptor(wsrvNlFallbackInterceptor)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
