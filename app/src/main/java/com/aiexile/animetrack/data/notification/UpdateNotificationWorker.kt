package com.aiexile.animetrack.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.util.getCurrentWeekday
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
        if (!enabled) return Result.success()

        // 计算今天是周几（Bangumi 标准：1=周一, 7=周日）
        val weekday = getCurrentWeekday()

        // 查询当天更新的番剧
        val animeList = animeDao.getAiringAnimesByWeekday(weekday)

        if (animeList.isEmpty()) return Result.success()

        // 发送通知
        NotificationHelper.showUpdateNotification(applicationContext, animeList)

        return Result.success()
    }
}
