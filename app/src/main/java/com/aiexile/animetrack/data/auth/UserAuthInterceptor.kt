package com.aiexile.animetrack.data.auth

import com.aiexile.animetrack.di.AppContainer
import okhttp3.Interceptor
import okhttp3.Response

class UserAuthInterceptor : Interceptor {

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

        // 收到 403 时尝试刷新 accessToken。
        // 刷新必须走全局唯一的 UserTokenRefresher：本拦截器会被多个 OkHttpClient
        // 分别实例化，实例内的锁无法阻止跨客户端并发刷新导致的服务端重放撤销。
        if (response.code == 403 && accessToken != null) {
            val newToken = UserTokenRefresher.refreshIfNeeded(accessToken)
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
