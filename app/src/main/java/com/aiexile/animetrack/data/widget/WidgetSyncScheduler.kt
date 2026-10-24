package com.aiexile.animetrack.data.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 桌面组件的刷新调度。
 *
 * 采用分层刷新策略：
 * - 事件驱动由 [WidgetUpdateCoordinator] 负责（Room 变更 → 显式广播）；
 * - 每日 05:00 后台同步一次（网络约束、电量不低），与每日更新通知形成协同；
 * - 每日 00:05 刷新一次，处理跨午夜后「今天」的口径切换；
 * - 只有桌面上存在组件实例时才调度，全部移除后取消，避免无谓唤醒。
 *
 * 这里用显式广播（APPWIDGET_UPDATE）请求组件重绘，而不是直接持有 GlanceAppWidget 实例：
 * 调度层属于 data，不反向依赖 ui 包；广播由 GlanceAppWidgetReceiver 接收后自行入队渲染。
 */
object WidgetSyncScheduler {

    private const val DAILY_SYNC_WORK = "widget_daily_sync"
    private const val MIDNIGHT_REFRESH_WORK = "widget_midnight_refresh"
    private const val SYNC_HOUR = 5
    private const val MIDNIGHT_MINUTE = 5

    /** 与 AndroidManifest 中注册的 Receiver 类名保持一致 */
    private val PROVIDER_CLASS_NAMES = listOf(
        "com.aiexile.animetrack.ui.widget.TodayAiringWidgetReceiver",
        "com.aiexile.animetrack.ui.widget.TodayAiringLargeWidgetReceiver",
        "com.aiexile.animetrack.ui.widget.WatchingWidgetReceiver",
        "com.aiexile.animetrack.ui.widget.StatsWidgetReceiver"
    )

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        if (!isMainProcess(appContext)) return
        if (!hasAnyWidget(appContext)) {
            cancelAll(appContext)
            return
        }
        scheduleDailySync(appContext)
        scheduleMidnightRefresh(appContext)
    }

    /**
     * 组件调度只允许发生在主进程：
     * - JPush 的 :pushcore 进程也会执行 Application.onCreate，WorkManager 官方不支持多进程；
     * - Room 也未开启多进程失效通知，跨进程观察没有意义。
     */
    fun isMainProcess(context: Context): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching {
                File("/proc/self/cmdline").readText().trim('\u0000', '\n', ' ')
            }.getOrNull()
        }
        return processName == null || processName == context.packageName
    }

    fun hasAnyWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return PROVIDER_CLASS_NAMES.any { className ->
            manager.getAppWidgetIds(ComponentName(context.packageName, className)).isNotEmpty()
        }
    }

    /** 向所有已存在的组件实例发送重绘请求 */
    fun requestWidgetUpdates(context: Context) {
        if (!isMainProcess(context)) return
        val manager = AppWidgetManager.getInstance(context)
        PROVIDER_CLASS_NAMES.forEach { className ->
            val component = ComponentName(context.packageName, className)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        this.component = component
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }

    private fun scheduleDailySync(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(SYNC_HOUR, 0), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_SYNC_WORK,
            // KEEP：避免每次打开 app / 调整组件尺寸都重置下一次同步时间
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleMidnightRefresh(context: Context) {
        if (!hasAnyWidget(context)) return
        // 周期任务而非一次性自续约：从 Worker 内部再入队同名一次性任务时，
        // 当前任务仍在 RUNNING，KEEP 策略会忽略新请求，导致跨天刷新链路断裂。
        val request = PeriodicWorkRequestBuilder<WidgetMidnightWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(0, MIDNIGHT_MINUTE), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MIDNIGHT_REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_SYNC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(MIDNIGHT_REFRESH_WORK)
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var target = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(60_000L)
    }
}
