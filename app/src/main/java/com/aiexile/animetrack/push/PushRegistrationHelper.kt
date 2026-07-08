package com.aiexile.animetrack.push

import android.content.Context
import android.util.Log
import cn.jpush.android.api.JPushInterface
import com.aiexile.animetrack.data.network.RegistrationIdRequest
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object PushRegistrationHelper {

    private const val TAG = "PushRegHelper"

    /**
     * 上报极光推送 registrationId 到后端。
     * 仅在已登录且当前 registrationId 未上报时执行。
     */
    suspend fun reportRegistrationIdIfNeeded(context: Context) {
        val userAuthManager = AppContainer.getUserAuthManager()
        val isLoggedIn = userAuthManager.isLoggedIn.first()
        if (!isLoggedIn) return

        var registrationId = JPushInterface.getRegistrationID(context)
        if (registrationId.isNullOrBlank()) {
            delay(2000)
            registrationId = JPushInterface.getRegistrationID(context)
        }
        if (registrationId.isNullOrBlank()) {
            Log.w(TAG, "JPush registrationId is still null after retry")
            return
        }

        val reportedId = userAuthManager.getReportedRegistrationId()
        if (reportedId == registrationId) return

        withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.userAuthApi.reportRegistrationId(
                    RegistrationIdRequest(registrationId = registrationId)
                )
                if (response.success) {
                    userAuthManager.setRegistrationIdReported(registrationId)
                    Log.d(TAG, "RegistrationId reported: $registrationId")
                } else {
                    Log.w(TAG, "Report failed: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Report error: ${e.message}")
            }
        }
    }
}
