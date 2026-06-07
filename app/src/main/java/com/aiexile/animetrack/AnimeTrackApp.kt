package com.aiexile.animetrack

import android.app.Application
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor(bilibiliRefererInterceptor)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
