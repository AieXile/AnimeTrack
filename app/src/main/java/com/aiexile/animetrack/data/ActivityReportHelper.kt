package com.aiexile.animetrack.data

import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.network.EmptyRequestBody
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActivityReportHelper {

    private const val TAG = "ActivityReport"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * 用户当日第一次启动时上报活跃。
     * 条件：已登录 + 当日尚未上报过。失败静默处理。
     */
    suspend fun reportActivityIfNeeded() {
        val userAuthManager = AppContainer.getUserAuthManager()
        val isLoggedIn = userAuthManager.isLoggedIn.first()
        if (!isLoggedIn) return

        val settingsRepository = AppContainer.getSettingsRepository()
        val today = dateFormat.format(Date())
        val lastReported = settingsRepository.getLastActivityDate()
        if (lastReported == today) return

        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.reportActivity(EmptyRequestBody())
                if (response.success) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Activity reported for $today")
                    }
                    settingsRepository.setLastActivityDate(today)
                } else {
                    Log.w(TAG, "Activity report returned success=false")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Activity report failed: ${e.message}")
            }
        }
    }
}
