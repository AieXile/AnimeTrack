package com.aiexile.animetrack

import android.app.Application
import cn.jpush.android.api.JPushInterface
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aiexile.animetrack.data.crash.CrashReporter
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.sse.SseEventTypes
import com.aiexile.animetrack.data.widget.WidgetSyncScheduler
import com.aiexile.animetrack.data.widget.WidgetUpdateCoordinator
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit

class AnimeTrackApp : Application(), ImageLoaderFactory {

    /** 崩溃 tombstone 上报延迟：避开启动高峰（网络、磁盘、UI 并发初始化） */
    private val reportTombstoneDelayMs = 5_000L

    /** 启动期封面自愈延迟：晚于 tombstone 上报，避开启动期封面批量下载/合批写库的高峰 */
    private val coverHealDelayMs = 10_000L

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
        // 桌面组件：存在组件实例时调度每日同步与午夜刷新（无组件为空操作），
        // 并在进程存活期间观察 Room 变化即时重绘（事件驱动优先于轮询）
        WidgetSyncScheduler.ensureScheduled(this)
        WidgetUpdateCoordinator.observeAnimeChanges(this)
        // 反馈日志的运行时快照：导出附件时汇总登录态/数据规模/开关状态
        AppLogManager.setDiagnosticsProvider(::buildDiagnosticsSnapshot)
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
        // 启动期封面自愈：修复清理缓存后封面文件被误删/本地化失败残留导致的封面空白
        // （本地路径文件缺失 → remoteCoverUrl 重下载；网络 URL 残留 → 幂等重本地化）
        appScope.launch {
            delay(coverHealDelayMs)
            AppContainer.getAnimeRepository().healMissingCoversAsync()
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
            // 「帮助改进应用」崩溃上报开关：关闭时跳过本次上报（tombstone 保留本地，
            // 受 xCrash 份数上限控制；开关打开后下次启动恢复上报）
            if (AppContainer.getSettingsRepository().crashReportEnabled.first()) {
                CrashReporter.reportPendingTombstones()
            }
        }
    }

    /** 用户在首启隐私政策弹窗点击同意后调用：补初始化被延后的上报组件 */
    fun onPrivacyAccepted() {
        initPrivacyGatedComponents()
    }

    /**
     * 反馈日志的运行时快照（导出时由 AppLogManager 调用，运行在 IO 线程）：
     * 登录态、数据规模、排序方式、界面与网络/通知开关 —— 用户描述「同步不上 / 设置失效 / 数据没了 /
     * 列表顺序不对」时，这些状态决定排查方向，但日志正文里往往看不出来。
     * 只记录开关与规模，不写入 token、账号、密码等凭证。
     */
    private fun buildDiagnosticsSnapshot(): String {
        val settings = AppContainer.getSettingsRepository()
        // 导出附件时不能因数据库被长事务占用而卡住反馈发送：超时则省略规模信息
        val counts = runCatching {
            runBlocking {
                withTimeoutOrNull(2_000L) {
                    val dao = AppContainer.getAnimeDatabase().animeDao()
                    Triple(
                        dao.countAnimes(),
                        dao.countPinnedAnimes(),
                        dao.countUnorderedAnimes()
                    )
                }
            }
        }.getOrNull()
        val appLoggedIn = runCatching {
            AppContainer.getUserAuthManager().getCachedAccessToken() != null
        }.getOrDefault(false)
        val bangumiLoggedIn = runCatching {
            AppContainer.getAuthManager().getCachedAccessToken() != null
        }.getOrDefault(false)
        val bilibiliLoggedIn = runCatching {
            AppContainer.getBilibiliAuthManager().getCachedSessData() != null
        }.getOrDefault(false)
        return buildString {
            appendLine("登录: 应用=$appLoggedIn, Bangumi=$bangumiLoggedIn, Bilibili=$bilibiliLoggedIn")
            appendLine("数据: 番剧 ${counts?.first ?: -1} 部, 置顶 ${counts?.second ?: -1} 部")
            // 排序方式与「未参与手动排序」条数：新条目落位、改状态不移动等问题全靠这两项定位
            appendLine(
                "排序: 模式=${settings.cachedListSortMode()}, " +
                    "未参与手动排序=${counts?.third ?: -1} 部"
            )
            appendLine(
                "界面: 材质=${settings.cachedChromeStyle()}, 主题=${settings.cachedThemeMode()}, " +
                    "深色=${settings.cachedDarkStyle()}, 语言=${settings.getAppLanguageBlocking()}"
            )
            appendLine(
                "网络: Bangumi反代=${settings.bangumiProxyEnabled}(${settings.bangumiProxyHost}), " +
                    "镜像=${settings.bangumiMirrorEnabled}, " +
                    "HTTP代理=${settings.httpProxyEnabled}(${settings.httpProxyType} " +
                    "${settings.httpProxyHost}:${settings.httpProxyPort})"
            )
            appendLine(
                "通知: 每日更新=${settings.cachedUpdateNotificationEnabled()}, " +
                    "崩溃上报=${settings.cachedCrashReportEnabled()}, " +
                    "隐私同意=${settings.isPrivacyPolicyAcceptedBlocking()}"
            )
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                // 基于共享基础 client 派生：图片加载与 API 请求共用动态代理
                // （WEB/SOCKS5、认证、保存即生效），封面下载同样走代理
                RetrofitClient.baseOkHttpClient.newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
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
