package com.aiexile.animetrack

import android.app.Application
import cn.jpush.android.api.JPushInterface
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Interceptor

class AnimeTrackApp : Application(), ImageLoaderFactory {

    private val bilibiliRefererInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val newRequest = if (url.contains("hdslb.com", ignoreCase = true)) {
            request.newBuilder()
                .header("Referer", "https://www.bilibili.com/")
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    private val wsrvNlFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        // wsrv.nl 代理失败时回退到原始 lain.bgm.tv URL
        if (!response.isSuccessful && request.url.host == "wsrv.nl") {
            val originalUrl = request.url.queryParameter("url")
            if (originalUrl != null) {
                response.close()
                val fallbackRequest = request.newBuilder().url(originalUrl).build()
                return@Interceptor chain.proceed(fallbackRequest)
            }
        }
        response
    }

    override fun onCreate() {
        super.onCreate()
        JPushInterface.setDebugMode(BuildConfig.DEBUG)
        JPushInterface.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor(bilibiliRefererInterceptor)
                    .addInterceptor(wsrvNlFallbackInterceptor)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
