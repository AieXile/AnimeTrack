package com.aiexile.animetrack.data.auth

import com.aiexile.animetrack.di.AppContainer
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val authManager = AppContainer.getAuthManager()
        val token = authManager.getCachedAccessToken()

        val request = if (token != null && originalRequest.url.host.contains("bgm.tv")) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(request)
    }
}
