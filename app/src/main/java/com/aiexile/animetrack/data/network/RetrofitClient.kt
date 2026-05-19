package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.remote.UpdateApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.bgm.tv/v0/"
    private const val GITHUB_API_URL = "https://api.github.com/"
    private const val USER_AGENT = "AieXile/AnimeTrack/1.0 (https://github.com/AieXile)"

    private val headerInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private val githubInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
        val token = BuildConfig.GITHUB_TOKEN
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "token $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .build()
    }

    private val githubOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(githubInterceptor)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val githubRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_API_URL)
            .client(githubOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val bangumiApi: BangumiApiService by lazy {
        retrofit.create(BangumiApiService::class.java)
    }

    val updateApi: UpdateApi by lazy {
        githubRetrofit.create(UpdateApi::class.java)
    }
}
