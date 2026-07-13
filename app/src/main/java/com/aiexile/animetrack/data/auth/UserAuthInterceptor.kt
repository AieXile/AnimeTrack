package com.aiexile.animetrack.data.auth

import com.aiexile.animetrack.data.network.UserAuthRefreshRequest
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.data.network.RetrofitClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

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
                            userAuthManager.logout()
                            null
                        } else {
                            try {
                                val refreshResponse = RetrofitClient.userAuthApi.refreshToken(
                                    UserAuthRefreshRequest(refreshToken = refreshToken)
                                )
                                if (refreshResponse.success && refreshResponse.accessToken != null) {
                                    userAuthManager.updateAccessToken(refreshResponse.accessToken)
                                    refreshResponse.accessToken
                                } else {
                                    // 刷新失败，清除登录状态
                                    userAuthManager.logout()
                                    null
                                }
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
