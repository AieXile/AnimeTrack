package com.aiexile.animetrack

import android.app.Application
import cn.jpush.android.api.JPushInterface
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor

class AnimeTrackApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        appScope.launch {
            JPushInterface.init(this@AnimeTrackApp)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor(bilibiliRefererInterceptor)
                    .addInterceptor(wsrvNlFallbackInterceptor)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
