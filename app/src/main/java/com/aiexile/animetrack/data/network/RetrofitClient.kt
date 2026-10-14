package com.aiexile.animetrack.data.network

import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.auth.AuthInterceptor
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.auth.UserAuthInterceptor
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.ProxyType
import com.aiexile.animetrack.data.remote.GitHubUpdateApi
import com.aiexile.animetrack.data.remote.HitokotoApi
import com.aiexile.animetrack.data.remote.UpdateApi
import com.aiexile.animetrack.di.AppContainer
import okhttp3.Authenticator
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
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
    private const val UPDATE_SERVER_URL = "https://www.aiexile.top/"
    private const val HITOKOTO_URL = "https://v1.hitokoto.cn/"

    // ===== 共享基础 OkHttpClient（连接池、线程池、超时） =====

    internal val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            // 网络失败日志：所有派生客户端共享，失败请求进入反馈日志（见 NetworkLogInterceptor）
            .addInterceptor(NetworkLogInterceptor())
            // 动态代理（保存即生效，新请求立即应用，无需重启）：
            // - WEB(HTTP) 代理：ProxySelector 返回 HTTP 代理，407 认证由 proxyAuthenticator 应答
            // - SOCKS5：隧道由 DynamicProxySocketFactory 在 socket 层建立（支持用户名密码认证）
            .proxySelector(dynamicProxySelector)
            .proxyAuthenticator(dynamicProxyAuthenticator)
            .socketFactory(DynamicProxySocketFactory())
            .dns(dynamicDns)
            .build()
    }

    /**
     * 动态代理选择器：每次建连时读取运行时代理配置。
     * SOCKS5 模式返回 NO_PROXY——让 OkHttp 按"直连"路由传递目标地址，
     * 实际隧道由 DynamicProxySocketFactory 在 socket 层接管。
     */
    private val dynamicProxySelector = object : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            val settings = try {
                AppContainer.getSettingsRepository()
            } catch (_: Exception) {
                null
            }

            return if (settings != null &&
                settings.httpProxyEnabled &&
                settings.httpProxyType == ProxyType.HTTP &&
                settings.httpProxyHost.isNotBlank() &&
                settings.httpProxyPort in 1..65535
            ) {
                listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(settings.httpProxyHost, settings.httpProxyPort)))
            } else {
                listOf(Proxy.NO_PROXY)
            }
        }

        override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {}
    }

    /**
     * HTTP 代理认证：代理返回 407 时注入 Proxy-Authorization（Basic）。
     * 已携带凭据仍被拒（密码错误等）时返回 null 终止，避免认证死循环。
     */
    private val dynamicProxyAuthenticator = Authenticator { _, response ->
        val settings = try {
            AppContainer.getSettingsRepository()
        } catch (_: Exception) {
            null
        }

        if (settings == null ||
            !settings.httpProxyEnabled ||
            settings.httpProxyType != ProxyType.HTTP ||
            settings.httpProxyUsername.isEmpty() ||
            response.request.header("Proxy-Authorization") != null
        ) {
            return@Authenticator null
        }

        response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(settings.httpProxyUsername, settings.httpProxyPassword))
            .build()
    }

    /**
     * SOCKS5 模式下的 DNS 占位：返回保留原始 hostname 的回环地址，
     * 使 InetSocketAddress.hostString 仍为域名，由代理侧完成真实解析
     * （socks5h 语义），规避本地 DNS 污染或不可达导致的解析失败。
     */
    private val dynamicDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return if (currentSocks5Settings() != null) {
                listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    // ===== Interceptors =====

    /**
     * 归一化用户配置的代理 host：支持完整 URL（提取 host）与裸 host（可带端口）。
     * 格式非法时返回 null，由调用方忽略重写，避免 HttpUrl.Builder.host() 抛异常。
     */
    private fun normalizeHost(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        return try {
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed.toHttpUrlOrNull()?.host
            } else {
                HttpUrl.Builder().scheme("https").host(trimmed).build().host
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

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

    // Bangumi 反向代理/镜像数据源拦截器：
    // - 镜像开关开启：仅条目数据请求（搜索/详情/关联/集数，api.bgm.tv 非 /v0/users、/v0/me 路径）
    //   重写到镜像服务器（www.aiexile.top）；账户相关请求（OAuth 换 token、/v0/me、收藏拉取与同步）
    //   不重写，继续走下方反向代理或直连官方
    // - 反向代理开启：重写 api.bgm.tv / bgm.tv 到用户自填的代理 host
    //   （镜像开启时账户请求仍可经反代访问，两者各管各的路径）
    private val bangumiProxyInterceptor = Interceptor { chain ->
        val settings = AppContainer.getSettingsRepository()
        val originalUrl = chain.request().url
        val originalHost = originalUrl.host
        if (originalHost != "api.bgm.tv" && originalHost != "bgm.tv") {
            return@Interceptor chain.proceed(chain.request())
        }

        // 镜像数据源：条目数据走镜像服务器；账户路径（/v0/users、/v0/me）与 bgm.tv OAuth 放行
        if (settings.bangumiMirrorEnabled && originalHost == "api.bgm.tv") {
            val path = originalUrl.encodedPath
            val isAccountPath =
                path.startsWith("/v0/users") || path == "/v0/me" || path.startsWith("/v0/me/")
            if (!isAccountPath) {
                val newUrl = originalUrl.newBuilder()
                    .host(SettingsRepository.BANGUMI_MIRROR_HOST)
                    .build()
                val newRequest = chain.request().newBuilder()
                    .url(newUrl)
                    .build()
                return@Interceptor chain.proceed(newRequest)
            }
        }

        // 反向代理：账户相关请求在镜像开启时也经此通道回源官方
        if (settings.bangumiProxyEnabled && settings.bangumiProxyHost.isNotBlank()) {
            // 支持完整 URL（提取 host）与裸 host 两种配置；非法配置忽略重写并记录，避免 host() 抛异常
            val proxyHost = normalizeHost(settings.bangumiProxyHost)
            if (proxyHost != null) {
                val newUrl = originalUrl.newBuilder()
                    .host(proxyHost)
                    .build()
                val newRequest = chain.request().newBuilder()
                    .url(newUrl)
                    .build()
                return@Interceptor chain.proceed(newRequest)
            } else {
                com.aiexile.animetrack.data.log.AppLogManager.w(
                    "BangumiProxy",
                    "Invalid bangumi proxy host ignored: ${settings.bangumiProxyHost}"
                )
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
        // 请求已手动指定 Authorization（如 bind-email 的 bindToken）时不覆盖
        if (token != null && chain.request().header("Authorization") == null) {
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

        // 用 HttpUrl 解析配置（替代手工字符串拆分）：支持带端口/路径，
        // 无 scheme 或格式非法时忽略重写并记录，避免 host() 抛异常
        val parsed = try {
            configuredUrl.toHttpUrlOrNull()
        } catch (_: IllegalArgumentException) {
            null
        }
        if (parsed == null) {
            com.aiexile.animetrack.data.log.AppLogManager.w(
                "UserAuthUrl",
                "Invalid user auth base url ignored: $configuredUrl"
            )
            return@Interceptor chain.proceed(chain.request())
        }

        val originalUrl = chain.request().url
        // 移除默认 /api 前缀，再拼接新基础路径
        val originalPath = originalUrl.encodedPath
        val pathWithoutDefaultPrefix = originalPath.removePrefix("/api")
        val newPath = parsed.encodedPath.trimEnd('/') + pathWithoutDefaultPrefix

        val newUrl = originalUrl.newBuilder()
            .scheme(parsed.scheme)
            .host(parsed.host)
            .port(parsed.port)
            .encodedPath(newPath)
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

    // 更新检查服务器客户端（自建服务器，仅需 UA + Accept JSON，无需鉴权）
    private val updateServerOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor(headerInterceptor)
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
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(userAuthDebugInterceptor)
                }
            }
            .build()
    }

    // 公开（无鉴权）用户体系客户端：用于设备活跃上报等不要求登录的接口
    // 共享 URL 重写，但不注入 Authorization Bearer token
    private val publicUserAuthOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(headerInterceptor)
            .addInterceptor(userAuthUrlRewriteInterceptor)
            .build()
    }

    // SSE 实时事件客户端（长连接）：鉴权与 URL 重写与用户体系一致，
    // 读超时 90 秒 > 服务端 30 秒心跳间隔，心跳丢失即断开重连
    val sseOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .addInterceptor(userAuthHeaderInterceptor)
            .addInterceptor(UserAuthInterceptor())
            .addInterceptor(userAuthUrlRewriteInterceptor)
            .build()
    }

    /** SSE 事件通道地址（默认域名，自定义基础 URL 由重写拦截器替换） */
    val sseEventsUrl: String
        get() = USER_AUTH_DEFAULT_BASE_URL + "events"

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

    private val updateServerRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(UPDATE_SERVER_URL)
            .client(updateServerOkHttpClient)
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

    // 一言 API 客户端（复用更新服务器客户端：仅需 UA + Accept JSON，无需鉴权）
    private val hitokotoRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(HITOKOTO_URL)
            .client(updateServerOkHttpClient)
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

    // 公开（无鉴权）用户体系 Retrofit：同 baseUrl，无 Bearer token 注入
    private val publicUserAuthRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(USER_AUTH_DEFAULT_BASE_URL)
            .client(publicUserAuthOkHttpClient)
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

    /** 更新检查（自建服务器优先源） */
    val updateApi: UpdateApi by lazy {
        updateServerRetrofit.create(UpdateApi::class.java)
    }

    /** 更新检查（GitHub 兜底源） */
    val githubUpdateApi: GitHubUpdateApi by lazy {
        githubRetrofit.create(GitHubUpdateApi::class.java)
    }

    val tmdbApi: TmdbApiService by lazy {
        tmdbRetrofit.create(TmdbApiService::class.java)
    }

    /** 一言 API：关于页彩蛋随机动漫语录 */
    val hitokotoApi: HitokotoApi by lazy {
        hitokotoRetrofit.create(HitokotoApi::class.java)
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

    /** 公开（无鉴权）用户体系 API：用于设备活跃上报等不要求登录的接口 */
    val publicUserAuthApi: PublicUserAuthApiService by lazy {
        publicUserAuthRetrofit.create(PublicUserAuthApiService::class.java)
    }

    /** 反馈 API：与用户体系同源同鉴权（Bearer + 401 自动刷新 + URL 重写） */
    val feedbackApi: FeedbackApiService by lazy {
        userAuthRetrofit.create(FeedbackApiService::class.java)
    }

    /** 管理员反馈 API：同用户体系鉴权链路，服务端校验 ADMIN_USER_IDS 白名单 */
    val adminFeedbackApi: AdminFeedbackApiService by lazy {
        userAuthRetrofit.create(AdminFeedbackApiService::class.java)
    }
}
