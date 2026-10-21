package com.aiexile.animetrack.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.util.getCurrentWeekday
import com.aiexile.animetrack.util.isUnaired
import kotlinx.coroutines.flow.first

class UpdateNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settingsRepository = AppContainer.getSettingsRepository()
        val animeDao = AppContainer.getAnimeDatabase().animeDao()

        // 检查通知开关
        val enabled = settingsRepository.updateNotificationEnabled.first()
        if (!enabled) {
            AppLogManager.i(TAG, "每日更新通知未开启，跳过")
            return Result.success()
        }

        // 计算今天是周几（Bangumi 标准：1=周一, 7=周日）
        val weekday = getCurrentWeekday()

        // 查询当天更新的番剧，并排除未放送的番剧（airWeekday 匹配但 airDate 在未来）
        val animeList = try {
            animeDao.getAiringAnimesByWeekday(weekday).filter { !isUnaired(it.airDate) }
        } catch (e: Exception) {
            // 数据库异常（如迁移失败/被清库）时 Worker 会静默失败，补一条 E 级便于排查
            AppLogManager.e(TAG, "每日更新通知查询失败: weekday=$weekday", e)
            throw e
        }

        if (animeList.isEmpty()) {
            AppLogManager.i(TAG, "今日（周 $weekday）没有需要提醒的更新")
            return Result.success()
        }

        // 发送通知
        NotificationHelper.showUpdateNotification(applicationContext, animeList)

        return Result.success()
    }

    private companion object {
        const val TAG = "Notification"
    }
}
