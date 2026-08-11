package com.aiexile.animetrack.data.auth

import android.util.Log
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Bangumi API 鉴权拦截器。
 *
 * 职责：
 * 1. 为所有 bgm.tv 请求注入 `Authorization: Bearer <access_token>` 头。
 * 2. 请求前若发现 token 即将过期（[AuthManager.isTokenExpiringSoon]），主动刷新。
 * 3. 响应 401 时被动刷新 token 并重试一次。
 *
 * 刷新采用互斥锁 + 双重检查，避免并发请求触发多次刷新。
 * 刷新请求本身（oauth/access_token）直接放行，不注入 token、不触发刷新，避免递归。
 *
 * 设计与 [UserAuthInterceptor] 保持一致。
 */
class AuthInterceptor : Interceptor {

    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // oauth/access_token 端点是公开的刷新/换 token 入口，不参与鉴权与刷新逻辑，避免递归
        if (originalRequest.url.encodedPath.contains("oauth/access_token")) {
            return chain.proceed(originalRequest)
        }

        val authManager = AppContainer.getAuthManager()
        val token = authManager.getCachedAccessToken()

        // 仅对 bgm.tv 且已登录的请求注入 Bearer token
        val authedRequest = if (token != null && originalRequest.url.host.contains("bgm.tv")) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        // 主动预判：token 即将过期时先刷新，减少一次 401 往返
        if (token != null && authManager.isTokenExpiringSoon()) {
            val refreshedToken = refreshTokenIfNeeded(authManager, token)
            if (refreshedToken != null && refreshedToken != token) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $refreshedToken")
                    .build()
                return chain.proceed(newRequest)
            }
            // 刷新失败或未实际刷新：继续用原 token 发请求，交由 401 兜底
        }

        val response = chain.proceed(authedRequest)

        // 被动兜底：401 时尝试刷新并重试一次
        if (response.code == 401 && token != null) {
            val newToken = refreshTokenIfNeeded(authManager, token)
            if (newToken != null && newToken != token) {
                response.close()
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(newRequest)
            }
            // 刷新失败：返回原 401 响应（未关闭），让上层读取错误信息
        }

        return response
    }

    /**
     * 在互斥锁保护下刷新 access_token。
     * 进入锁后再次检查 token 是否已被其他线程刷新，避免重复刷新。
     * @param currentToken 触发刷新时内存中的 token，用于双重检查
     * @return 刷新成功后的新 access_token；失败返回 null
     */
    private fun refreshTokenIfNeeded(authManager: AuthManager, currentToken: String): String? {
        return runBlocking {
            refreshMutex.withLock {
                val latestToken = authManager.getCachedAccessToken()
                if (latestToken != currentToken) {
                    // 已被其他线程刷新，直接复用
                    latestToken
                } else {
                    val refreshToken = authManager.getCachedRefreshToken()
                    if (refreshToken.isNullOrBlank()) {
                        // 无 refresh_token，无法刷新，清除登录状态
                        try {
                            authManager.logout()
                        } catch (e: Exception) {
                            Log.e(TAG, "logout after refresh-failure failed", e)
                        }
                        null
                    } else {
                        try {
                            val response = RetrofitClient.bangumiAuthApi.refreshAccessToken(
                                clientId = AuthManager.CLIENT_ID,
                                clientSecret = AuthManager.CLIENT_SECRET,
                                refreshToken = refreshToken,
                                redirectUri = AuthManager.REDIRECT_URI
                            )
                            val newAccess = response.access_token
                            if (newAccess.isNotBlank()) {
                                authManager.updateAccessToken(
                                    newToken = newAccess,
                                    expiresIn = response.expires_in,
                                    newRefreshToken = response.refresh_token
                                )
                                newAccess
                            } else {
                                Log.e(TAG, "Refresh returned empty access_token")
                                null
                            }
                        } catch (e: Exception) {
                            // 刷新失败：refresh_token 可能已失效，清除登录状态避免持续 401
                            Log.e(TAG, "Refresh bangumi token failed, logout", e)
                            try {
                                authManager.logout()
                            } catch (_: Exception) { }
                            null
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BangumiAuth"
    }
}
