package com.aiexile.animetrack.data.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.UpdateCheckUseCase

/**
 * 每日后台同步：只刷新「最新集数 / 待看更新」这类会变的数据。
 *
 * 不执行 Bangumi 收藏双向同步：后台覆盖本地进度会与用户前台操作产生冲突，
 * 订阅列表的合并继续由 app 打开时的既有链路负责。更新检查全量扫描连载中的番，
 * 因为默认的 app 内检查只覆盖 totalEpisodes = 0 的条目（组件需要更完整的新鲜度）。
 */
class WidgetSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!WidgetSyncScheduler.hasAnyWidget(context)) return Result.success()

        return try {
            UpdateCheckUseCase(AppContainer.getAnimeRepository())
                .checkAiringAnimeUpdates(fullScan = true)
            WidgetDataProvider.markSyncedNow(context)
            WidgetSyncScheduler.requestWidgetUpdates(context)
            Result.success()
        } catch (e: Exception) {
            // 单次失败不重试：周期任务次日继续；避免 Doze 下反复唤醒
            AppLogManager.w("WidgetSync", "每日后台同步失败", e)
            Result.success()
        }
    }
}
