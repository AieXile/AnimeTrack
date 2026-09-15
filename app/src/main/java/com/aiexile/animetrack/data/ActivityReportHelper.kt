package com.aiexile.animetrack.data

import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.network.DeviceActivityRequest
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 用户/设备活跃上报辅助类。
 *
 * 大厂成熟做法（设备维度统计）：
 * 1. **不强制登录**：未登录用户也能上报活跃（按设备 ID 去重）
 * 2. **双维度记录**：已登录用户同时记录 user_id 便于注册转化率分析
 * 3. **每日去重**：本地记录上次上报日期，当日只上报一次
 */
object ActivityReportHelper {

    private const val TAG = "ActivityReport"
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /**
     * 当日首次启动时上报活跃（设备维度 + 可选用户维度）。
     * 无论登录与否都会上报；登录用户额外携带 userId 便于关联。
     * 失败静默处理，不影响主流程。
     */
    suspend fun reportActivityIfNeeded() {
        val settingsRepository = AppContainer.getSettingsRepository()
        val today = LocalDate.now().format(dateFormatter)
        val lastReported = settingsRepository.getLastActivityDate()
        if (lastReported == today) return

        // 设备 ID：未生成则生成 UUID 并持久化，保证同一设备稳定
        val deviceId = try {
            settingsRepository.getOrCreateDeviceId()
        } catch (e: Exception) {
            Log.e(TAG, "获取设备 ID 失败: ${e.message}")
            return
        }
        if (deviceId.isBlank()) return

        // 已登录则附带 userId
        val userAuthManager = AppContainer.getUserAuthManager()
        val userId = runCatching { userAuthManager.getUserId() }.getOrNull()

        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.publicUserAuthApi.reportDeviceActivity(
                    DeviceActivityRequest(deviceId = deviceId, userId = userId)
                )
                if (response.success) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Device activity reported for $today (userId=$userId)")
                    }
                    settingsRepository.setLastActivityDate(today)
                } else {
                    Log.w(TAG, "Device activity report returned success=false")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Device activity report failed: ${e.message}")
            }
        }
    }
}
