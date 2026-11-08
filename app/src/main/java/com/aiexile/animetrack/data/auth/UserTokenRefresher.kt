package com.aiexile.animetrack.data.auth

import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.UserAuthRefreshRequest
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/**
 * 全进程唯一的 access token 刷新器（single-flight）。
 *
 * 为什么必须全局唯一：用户体系有两个独立 OkHttpClient（UserAuthInterceptor.kt 中的
 * userAuthOkHttpClient 与 RetrofitClient.sseOkHttpClient），各自持有一个
 * [UserAuthInterceptor] 实例。如果互斥锁放在拦截器实例里，access token 过期后的
 * 首批请求（例如前台恢复时的同步请求与 SSE 重连同时触发）会并发提交**同一个**
 * refresh token：服务端轮换成功后，第二个请求会命中重放检测并撤销整个会话家族，
 * 用户被迫重新登录。
 */
object UserTokenRefresher {

    private val refreshMutex = Mutex()

    /**
     * 用 [staleAccessToken]（发起请求时携带、已被服务端判定失效的 access token）
     * 换取新 token。
     *
     * @return 新 access token；null 表示刷新失败（已按需标记登录失效），
     *         调用方应原样返回触发刷新的响应。
     */
    fun refreshIfNeeded(staleAccessToken: String?): String? = runBlocking {
        refreshMutex.withLock {
            val userAuthManager = AppContainer.getUserAuthManager()

            // 双重检查：等待锁期间可能已有其他请求（或被踢通知等）换新 token
            val currentToken = userAuthManager.getCachedAccessToken()
            if (currentToken != null && currentToken != staleAccessToken) {
                return@withLock currentToken
            }

            val refreshToken = userAuthManager.getCachedRefreshToken() ?: run {
                userAuthManager.markTokenExpired()
                return@withLock null
            }

            try {
                val refreshResponse = RetrofitClient.userAuthApi.refreshToken(
                    UserAuthRefreshRequest(
                        refreshToken = refreshToken,
                        // 携带设备信息：存量会话（改造前登录）由服务端补齐设备名
                        deviceId = DeviceInfo.getDeviceId(),
                        deviceName = DeviceInfo.deviceName,
                        platform = DeviceInfo.PLATFORM
                    )
                )
                if (refreshResponse.success && refreshResponse.accessToken != null) {
                    userAuthManager.updateAccessToken(refreshResponse.accessToken)
                    // 服务端每次刷新都会轮换 refresh token，必须同步保存新值
                    refreshResponse.refreshToken?.let {
                        userAuthManager.updateRefreshToken(it)
                    }
                    refreshResponse.accessToken
                } else {
                    // 刷新失败，标记失效（保留资料，引导重新登录）
                    userAuthManager.markTokenExpired()
                    null
                }
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    // 401 = 服务端明确判定 refresh token 无效（已过期/被下线/被判重放）。
                    // 但如果是并发刷新的落败方，缓存里的 refresh token 已被同进程另一请求
                    // 换新，此时不能误标登录失效，直接复用新 access token 重试原请求。
                    val latestRefreshToken = userAuthManager.getCachedRefreshToken()
                    val latestAccessToken = userAuthManager.getCachedAccessToken()
                    if (latestAccessToken != null &&
                        latestRefreshToken != null &&
                        latestRefreshToken != refreshToken
                    ) {
                        return@withLock latestAccessToken
                    }
                    // kicked=true 表示会话被其他设备主动撤销，横幅将展示被踢文案
                    val kicked = runCatching {
                        val body = e.response()?.errorBody()?.string().orEmpty()
                        if (body.isBlank()) false
                        else org.json.JSONObject(body).optBoolean("kicked", false)
                    }.getOrDefault(false)
                    userAuthManager.markTokenExpired(kicked = kicked)
                }
                null
            } catch (_: Exception) {
                // 网络错误等瞬时失败，不清除登录状态，让用户稍后重试
                null
            }
        }
    }
}
