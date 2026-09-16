package com.aiexile.animetrack.data.auth

import com.aiexile.animetrack.data.network.UserAuthRefreshRequest
import com.aiexile.animetrack.data.network.UserAuthRefreshResponse
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.data.network.RetrofitClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException

class UserAuthInterceptor : Interceptor {

    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val userAuthManager = AppContainer.getUserAuthManager()
        val request = chain.request()

        // auth/refresh 请求本身不参与 403 刷新，避免递归刷新导致死锁
        if (request.url.encodedPath.contains("auth/refresh")) {
            return chain.proceed(request)
        }

        val accessToken = userAuthManager.getCachedAccessToken()

        val authedRequest = if (accessToken != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            request
        }

        val response = chain.proceed(authedRequest)

        // 收到 403 时尝试刷新 accessToken
        if (response.code == 403 && accessToken != null) {
            val newToken = runBlocking {
                refreshMutex.withLock {
                    // 双重检查：锁获取期间可能已有其他线程刷新成功
                    val currentToken = userAuthManager.getCachedAccessToken()
                    if (currentToken != accessToken) {
                        // 已被其他线程刷新，直接使用新 token
                        currentToken
                    } else {
                        // 执行刷新
                        val refreshToken = userAuthManager.getCachedRefreshToken()
                        if (refreshToken == null) {
                            userAuthManager.markTokenExpired()
                            null
                        } else {
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
                                    refreshResponse.accessToken
                                } else {
                                    // 刷新失败，标记失效（保留资料，引导重新登录）
                                    userAuthManager.markTokenExpired()
                                    null
                                }
                            } catch (e: HttpException) {
                                if (e.code() == 401) {
                                    // 服务端明确判定 Refresh Token 无效（已过期/被下线），
                                    // 属确定性失效而非瞬时网络错误：标记失效，引导用户重新登录
                                    val kicked = runCatching {
                                        e.response()?.errorBody()?.string()?.let { body ->
                                            com.google.gson.Gson()
                                                .fromJson(body, UserAuthRefreshResponse::class.java)
                                                .kicked == true
                                        } ?: false
                                    }.getOrDefault(false)
                                    // kicked=true 表示会话被其他设备主动撤销，横幅将展示被踢文案
                                    userAuthManager.markTokenExpired(kicked = kicked)
                                }
                                null
                            } catch (_: Exception) {
                                // 网络错误等，不清除登录状态，让用户稍后重试
                                null
                            }
                        }
                    }
                }
            }

            if (newToken != null) {
                // 刷新成功：关闭原响应，用新 token 重试原请求
                response.close()
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(newRequest)
            }
            // 刷新失败：返回原 403 响应（未关闭），让上层读取错误信息
        }

        return response
    }
}
