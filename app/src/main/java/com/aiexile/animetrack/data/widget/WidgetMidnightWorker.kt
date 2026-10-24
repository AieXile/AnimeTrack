package com.aiexile.animetrack.data.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 跨午夜刷新：日期变化后「今天」的口径需要重算，组件内容随工作日切换。
 * 调度为每日周期任务（见 WidgetSyncScheduler），WorkManager 会持久化，重启后仍在。
 */
class WidgetMidnightWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!WidgetSyncScheduler.hasAnyWidget(context)) return Result.success()
        WidgetSyncScheduler.requestWidgetUpdates(context)
        return Result.success()
    }
}
