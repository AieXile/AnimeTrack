package com.aiexile.animetrack.data.network

import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.auth.AuthInterceptor
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.remote.UpdateApi
import com.aiexile.animetrack.di.AppContainer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.bgm.tv/v0/"
    private const val BANGUMI_AUTH_URL = "https://bgm.tv/"
    private const val GITHUB_API_URL = "https://api.github.com/"
    private const val BILIBILI_BASE_URL = "https://api.bilibili.com/"
    private const val BILIBILI_PASSPORT_BASE_URL = "https://passport.bilibili.com/"
    private const val USER_AGENT = "AieXile/AnimeTrack/1.0 (https://github.com/AieXile)"
    private const val BILIBILI_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val BILIBILI_LOGIN_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

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

    private val debugInterceptor = Interceptor { chain ->
        val request = chain.request()
        Log.d("RetrofitClient", "→ ${request.method} ${request.url}")
        val response = chain.proceed(request)
        val body = response.peekBody(1024 * 4)
        Log.d("RetrofitClient", "← ${response.code} ${request.url} body=${body.string().take(500)}")
        response
    }

    private val safeDns = SafeDns()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(safeDns)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(AuthInterceptor())
            .build()
    }

    private val authOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(safeDns)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(debugInterceptor)
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

    private val authRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BANGUMI_AUTH_URL)
            .client(authOkHttpClient)
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

    val bangumiAuthApi: BangumiApiService by lazy {
        authRetrofit.create(BangumiApiService::class.java)
    }

    val updateApi: UpdateApi by lazy {
        githubRetrofit.create(UpdateApi::class.java)
    }

    private val bilibiliCookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                cookieStore[url.host] = cookies.toMutableList()
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookies = mutableListOf<Cookie>()
            synchronized(cookieStore) {
                cookieStore[url.host]?.let { cookies.addAll(it) }
            }

            val bilibiliAuthManager = try {
                AppContainer.getBilibiliAuthManager()
            } catch (_: Exception) { null }

            val sessData = bilibiliAuthManager?.getCachedSessData()
            if (!sessData.isNullOrEmpty()) {
                cookies.removeAll { it.name == "SESSDATA" }
                cookies.add(Cookie.Builder().domain("bilibili.com").name("SESSDATA").value(sessData).build())
            }

            val biliJct = bilibiliAuthManager?.getCachedBiliJct()
            if (!biliJct.isNullOrEmpty()) {
                cookies.removeAll { it.name == "bili_jct" }
                cookies.add(Cookie.Builder().domain("bilibili.com").name("bili_jct").value(biliJct).build())
            }

            if (cookies.none { it.name == "buvid3" }) {
                val buvid3 = java.util.UUID.randomUUID().toString() + "infoc"
                cookies.add(Cookie.Builder().domain("bilibili.com").name("buvid3").value(buvid3).build())
            }

            return cookies
        }
    }

    private val bilibiliInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", BILIBILI_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .header("Origin", "https://www.bilibili.com")
            .build()
        chain.proceed(request)
    }

    private val bilibiliOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(safeDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cookieJar(bilibiliCookieJar)
            .addInterceptor(bilibiliInterceptor)
            .build()
    }

    private val bilibiliRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BILIBILI_BASE_URL)
            .client(bilibiliOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val bilibiliApi: BilibiliApiService by lazy {
        bilibiliRetrofit.create(BilibiliApiService::class.java)
    }

    // ===== B站登录专用（完全独立、干净的 OkHttpClient） =====

    private val bilibiliLoginInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", BILIBILI_LOGIN_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        chain.proceed(request)
    }

    // 登录专用 CookieJar：仅注入 buvid3 设备指纹，不注入 SESSDATA/bili_jct
    private val bilibiliLoginCookieJar = object : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                cookieStore[url.host] = cookies.toMutableList()
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookies = mutableListOf<Cookie>()
            synchronized(cookieStore) {
                cookieStore[url.host]?.let { cookies.addAll(it) }
            }
            // 注入 buvid3 设备指纹
            if (cookies.none { it.name == "buvid3" }) {
                val buvid3 = java.util.UUID.randomUUID().toString() + "infoc"
                cookies.add(Cookie.Builder().domain("bilibili.com").name("buvid3").value(buvid3).build())
            }
            return cookies
        }
    }

    private val bilibiliLoginOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(safeDns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cookieJar(bilibiliLoginCookieJar)
            .addInterceptor(bilibiliLoginInterceptor)
            .build()
    }

    private val bilibiliLoginRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BILIBILI_PASSPORT_BASE_URL)
            .client(bilibiliLoginOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val bilibiliLoginApi: BilibiliLoginApiService by lazy {
        bilibiliLoginRetrofit.create(BilibiliLoginApiService::class.java)
    }
}
