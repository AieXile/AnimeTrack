package com.aiexile.animetrack.data.network

import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.auth.AuthInterceptor
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.auth.UserAuthInterceptor
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.remote.UpdateApi
import com.aiexile.animetrack.di.AppContainer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.net.Proxy
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
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"
    private const val USER_AUTH_DEFAULT_BASE_URL = "https://www.aiexile.top/api/"

    private val safeDns = SafeDns()

    // ===== 共享基础 OkHttpClient（连接池、线程池、DNS、超时） =====

    internal val baseOkHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .dns(safeDns)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)

        // 应用 HTTP 普通代理（修改后需重启 App 生效）
        try {
            val settings = AppContainer.getSettingsRepository()
            if (settings.httpProxyEnabled && settings.httpProxyHost.isNotBlank() && settings.httpProxyPort > 0) {
                builder.proxy(
                    Proxy(Proxy.Type.HTTP, InetSocketAddress(settings.httpProxyHost, settings.httpProxyPort))
                )
            }
        } catch (_: Exception) {}

        builder.build()
    }

    // ===== Interceptors =====

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
        if (BuildConfig.DEBUG) Log.d("RetrofitClient", "→ ${request.method} ${request.url}")
        val response = chain.proceed(request)
        val body = response.peekBody(1024 * 4)
        if (BuildConfig.DEBUG) Log.d("RetrofitClient", "← ${response.code} ${request.url} body=${body.string().take(500)}")
        response
    }

    private val tmdbInterceptor = Interceptor { chain ->
        val apiKey = AppContainer.getSettingsRepository().currentTmdbApiKey
            ?: com.aiexile.animetrack.data.SettingsRepository.DEFAULT_TMDB_API_KEY
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()
        chain.proceed(request)
    }

    private val bilibiliInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", BILIBILI_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .header("Origin", "https://www.bilibili.com")
            .build()
        chain.proceed(request)
    }

    private val bilibiliLoginInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", BILIBILI_LOGIN_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        chain.proceed(request)
    }

    // Bangumi 反向代理拦截器：开启后将 api.bgm.tv / bgm.tv 请求重写到代理 host
    private val bangumiProxyInterceptor = Interceptor { chain ->
        val settings = AppContainer.getSettingsRepository()
        if (settings.bangumiProxyEnabled && settings.bangumiProxyHost.isNotBlank()) {
            val proxyHost = settings.bangumiProxyHost
            val originalUrl = chain.request().url
            val originalHost = originalUrl.host
            // 仅重写 Bangumi 相关域名
            if (originalHost == "api.bgm.tv" || originalHost == "bgm.tv") {
                val newUrl = originalUrl.newBuilder()
                    .host(proxyHost)
                    .build()
                val newRequest = chain.request().newBuilder()
                    .url(newUrl)
                    .build()
                return@Interceptor chain.proceed(newRequest)
            }
        }
        chain.proceed(chain.request())
    }

    // 用户认证请求头拦截器：注入 Authorization Bearer token
    private val userAuthHeaderInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
        val userAuthManager = try {
            AppContainer.getUserAuthManager()
        } catch (_: Exception) { null }
        val token = userAuthManager?.getCachedAccessToken()
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    // 用户认证 URL 重写拦截器：当配置的基础 URL 与默认值不同时，重写请求 URL
    private val userAuthUrlRewriteInterceptor = Interceptor { chain ->
        val settings = try {
            AppContainer.getSettingsRepository()
        } catch (_: Exception) { return@Interceptor chain.proceed(chain.request()) }

        val configuredUrl = settings.userAuthBaseUrl
        // 默认基础 URL 为 "https://www.aiexile.top/api/" - 若配置与默认一致则无需重写
        if (configuredUrl == SettingsRepository.DEFAULT_USER_AUTH_BASE_URL) {
            return@Interceptor chain.proceed(chain.request())
        }

        // 解析配置的 URL
        val isHttps = configuredUrl.startsWith("https://")
        val urlWithoutScheme = configuredUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val slashIndex = urlWithoutScheme.indexOf('/')
        val newHost = if (slashIndex >= 0) urlWithoutScheme.substring(0, slashIndex) else urlWithoutScheme
        val newPathPrefix = if (slashIndex >= 0) urlWithoutScheme.substring(slashIndex) else ""

        val originalUrl = chain.request().url
        // 移除默认 /api 前缀，再拼接新路径前缀
        val originalPath = originalUrl.encodedPath
        val pathWithoutDefaultPrefix = originalPath.removePrefix("/api")
        val finalPath = newPathPrefix + pathWithoutDefaultPrefix

        val newUrl = originalUrl.newBuilder()
            .scheme(if (isHttps) "https" else "http")
            .host(newHost)
            .encodedPath(finalPath)
            .build()

        chain.proceed(chain.request().newBuilder().url(newUrl).build())
    }

    // 用户认证调试拦截器（临时）：打印请求体和响应体，定位 HTTP 400
    private val userAuthDebugInterceptor = Interceptor { chain ->
        val request = chain.request()
        val reqBodyStr = request.body?.let { body ->
            try {
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            } catch (e: Exception) {
                "[read req body failed: ${e.message}]"
            }
        }
        if (BuildConfig.DEBUG) Log.d("UserAuthHttp", "→ ${request.method} ${request.url}\n  req_body=$reqBodyStr")
        val response = chain.proceed(request)
        val respBodyStr = try {
            response.peekBody(1024 * 16).string()
        } catch (e: Exception) {
            "[read resp body failed: ${e.message}]"
        }
        if (BuildConfig.DEBUG) Log.d("UserAuthHttp", "← ${response.code} ${request.url}\n  resp_body=$respBodyStr")
        response
    }

    // ===== 派生 OkHttpClient（共享基础连接池） =====

    private val okHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(bangumiProxyInterceptor)
            .build()
    }

    private val authOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(bangumiProxyInterceptor)
            .addInterceptor(debugInterceptor)
            .build()
    }

    private val githubOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor(githubInterceptor)
            .build()
    }

    private val tmdbOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor(tmdbInterceptor)
            .build()
    }

    // ===== Bilibili CookieJar =====

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
            if (cookies.none { it.name == "buvid3" }) {
                val buvid3 = java.util.UUID.randomUUID().toString() + "infoc"
                cookies.add(Cookie.Builder().domain("bilibili.com").name("buvid3").value(buvid3).build())
            }
            return cookies
        }
    }

    // Bilibili API 客户端（10s 超时 + CookieJar）
    private val bilibiliOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cookieJar(bilibiliCookieJar)
            .addInterceptor(bilibiliInterceptor)
            .build()
    }

    // Bilibili 登录专用客户端（独立 CookieJar，10s 超时）
    private val bilibiliLoginOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .cookieJar(bilibiliLoginCookieJar)
            .addInterceptor(bilibiliLoginInterceptor)
            .build()
    }

    // 用户认证客户端（10s 超时 + 请求头 + 403自动刷新 + URL 重写）
    private val userAuthOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(userAuthHeaderInterceptor)
            .addInterceptor(UserAuthInterceptor())
            .addInterceptor(userAuthUrlRewriteInterceptor)
            .addInterceptor(userAuthDebugInterceptor)
            .build()
    }

    // ===== Retrofit 实例 =====

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

    private val tmdbRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(tmdbOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val bilibiliRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BILIBILI_BASE_URL)
            .client(bilibiliOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val bilibiliLoginRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BILIBILI_PASSPORT_BASE_URL)
            .client(bilibiliLoginOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val userAuthRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(USER_AUTH_DEFAULT_BASE_URL)
            .client(userAuthOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ===== API 服务 =====

    val bangumiApi: BangumiApiService by lazy {
        retrofit.create(BangumiApiService::class.java)
    }

    val bangumiAuthApi: BangumiApiService by lazy {
        authRetrofit.create(BangumiApiService::class.java)
    }

    val updateApi: UpdateApi by lazy {
        githubRetrofit.create(UpdateApi::class.java)
    }

    val tmdbApi: TmdbApiService by lazy {
        tmdbRetrofit.create(TmdbApiService::class.java)
    }

    val bilibiliApi: BilibiliApiService by lazy {
        bilibiliRetrofit.create(BilibiliApiService::class.java)
    }

    val bilibiliLoginApi: BilibiliLoginApiService by lazy {
        bilibiliLoginRetrofit.create(BilibiliLoginApiService::class.java)
    }

    val userAuthApi: UserAuthApiService by lazy {
        userAuthRetrofit.create(UserAuthApiService::class.java)
    }
}
