# BiliPai 认证、Wbi 签名与追番列表技术解析

> 基于 BiliPai 开源项目源码的深度逆向分析，覆盖登录鉴权、Wbi 风控签名、追番数据模型、网络请求层四大核心模块。

---

## 目录

1. [认证与鉴权模块](#1-认证与鉴权模块)
2. [Wbi 动态加密签名算法](#2-wbi-动态加密签名算法)
3. [追番列表 API 数据模型](#3-追番列表-api-数据模型)
4. [Repository 网络请求层实现](#4-repository-网络请求层实现)
5. [附录：关键文件索引](#5-附录关键文件索引)

---

## 1. 认证与鉴权模块

### 1.1 登录方式概览

BiliPai 支持三种登录方式，均通过 Retrofit 调用 B 站 Passport API：

| 方式 | API 路径 | 获取内容 | 用途 |
|------|---------|---------|------|
| **Web 二维码** | `x/passport-login/web/qrcode/generate` + `poll` | SESSDATA, bili_jct | 基础登录 |
| **TV 二维码**（默认） | `x/passport-tv-login/qrcode/auth_code` + `poll` | access_token + SESSDATA + bili_jct | 高画质视频 |
| **手机号/密码** | `x/passport-login/web/sms/send` / `web/login` | SESSDATA, bili_jct | 备用登录 |

### 1.2 Cookie 提取：Web 二维码登录

Web 端二维码登录成功后，从 HTTP 响应的 `Set-Cookie` Header 中提取：

```kotlin
// LoginViewModel.kt — startPolling()
val cookies = response.headers().values("Set-Cookie")
var sessData = ""
var biliJct = ""

for (line in cookies) {
    if (line.contains("SESSDATA")) {
        val parts = line.split(";")
        for (part in parts) {
            val trimPart = part.trim()
            if (trimPart.startsWith("SESSDATA=")) {
                sessData = trimPart.substringAfter("SESSDATA=")
                break
            }
        }
    }
    if (line.contains("bili_jct")) {
        val parts = line.split(";")
        for (part in parts) {
            val trimPart = part.trim()
            if (trimPart.startsWith("bili_jct=")) {
                biliJct = trimPart.substringAfter("bili_jct=")
                break
            }
        }
    }
}

// 保存
TokenManager.saveCookies(getApplication(), sessData)
if (biliJct.isNotEmpty()) {
    TokenManager.saveCsrf(getApplication(), biliJct)
}
```

### 1.3 Cookie 提取：TV 二维码登录

TV 端登录成功后，从响应体的 `cookie_info.cookies` 数组中提取：

```kotlin
// LoginViewModel.kt — startTvPolling()
val data = response.data
if (data != null) {
    // 保存 access_token (用于 APP API 高画质)
    TokenManager.saveAccessToken(getApplication(), data.accessToken, data.refreshToken)

    // 保存 mid
    if (data.mid > 0) {
        TokenManager.saveMid(getApplication(), data.mid)
    }

    // 从 cookie_info 中提取 SESSDATA, bili_jct
    data.cookieInfo?.cookies?.forEach { cookie ->
        when (cookie.name) {
            "SESSDATA" -> {
                TokenManager.saveCookies(getApplication(), cookie.value)
            }
            "bili_jct" -> {
                TokenManager.saveCsrf(getApplication(), cookie.value)
            }
        }
    }
}
```

### 1.4 Cookie 提取：手机号/密码登录

与 Web 二维码相同，从 `Set-Cookie` Header 提取，统一走 `handleLoginCookies()`：

```kotlin
// LoginViewModel.kt
private suspend fun handleLoginCookies(cookies: List<String>) {
    var sessData = ""
    var biliJct = ""

    for (line in cookies) {
        if (line.contains("SESSDATA")) {
            sessData = line.split(";").firstOrNull { it.trim().startsWith("SESSDATA=") }
                ?.substringAfter("SESSDATA=") ?: ""
        }
        if (line.contains("bili_jct")) {
            biliJct = line.split(";").firstOrNull { it.trim().startsWith("bili_jct=") }
                ?.substringAfter("bili_jct=") ?: ""
        }
    }

    if (sessData.isNotEmpty()) {
        TokenManager.saveCookies(getApplication(), sessData)
        if (biliJct.isNotEmpty()) {
            TokenManager.saveCsrf(getApplication(), biliJct)
        }
        finishLogin("phone")
    }
}
```

### 1.5 登录态持久化：TokenManager

`TokenManager` 采用 **SharedPreferences（同步快速）+ DataStore（异步持久）双写策略**：

```kotlin
// TokenManager.kt
object TokenManager {
    // 内存缓存（@Volatile 保证可见性）
    @Volatile var sessDataCache: String? = null
    @Volatile var buvid3Cache: String? = null
    @Volatile var csrfCache: String? = null       // bili_jct
    @Volatile var midCache: Long? = null           // DedeUserID
    @Volatile var accessTokenCache: String? = null // TV access_token
    @Volatile var refreshTokenCache: String? = null
    @Volatile var isVipCache: Boolean = false

    // SP 备份 key
    private const val SP_NAME = "token_backup_sp"
    private const val SP_KEY_SESS = "sessdata_backup"
    private const val SP_KEY_CSRF = "bili_jct_backup"
    private const val SP_KEY_MID = "mid_backup"
    private const val SP_KEY_ACCESS_TOKEN = "access_token_backup"
    private const val SP_KEY_REFRESH_TOKEN = "refresh_token_backup"

    fun init(context: Context) {
        // 1. 同步读取 SP 备份（主线程立即可用，解决冷启动 DataStore 异步加载慢的问题）
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        sessDataCache = sp.getString(SP_KEY_SESS, null)
        csrfCache = sp.getString(SP_KEY_CSRF, null)
        midCache = sp.getLong(SP_KEY_MID, 0L).takeIf { it > 0 }
        accessTokenCache = sp.getString(SP_KEY_ACCESS_TOKEN, null)
        refreshTokenCache = sp.getString(SP_KEY_REFRESH_TOKEN, null)

        // 2. 启动 DataStore 监听（主要数据源，异步更新内存缓存）
        CoroutineScope(Dispatchers.IO).launch {
            context.dataStore.data.collect { prefs ->
                val dsSess = prefs[SESSDATA_KEY]
                if (!dsSess.isNullOrEmpty()) {
                    sessDataCache = dsSess
                }
                // 同步回写 SP（DataStore → SP 单向同步）
                if (sessDataCache != sp.getString(SP_KEY_SESS, null)) {
                    sp.edit().putString(SP_KEY_SESS, sessDataCache).apply()
                }
            }
        }
    }

    // 保存 SESSDATA（双写）
    suspend fun saveCookies(context: Context, sessData: String) {
        sessDataCache = sessData
        // SP（同步/快速）
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putString(SP_KEY_SESS, sessData).apply()
        // DataStore（异步/持久）
        context.dataStore.edit { prefs -> prefs[SESSDATA_KEY] = sessData }
    }

    // 保存 CSRF / bili_jct
    fun saveCsrf(context: Context, csrf: String) {
        csrfCache = csrf
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putString(SP_KEY_CSRF, csrf).apply()
    }

    // 保存 MID / DedeUserID
    fun saveMid(context: Context, mid: Long) {
        midCache = mid
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit().putLong(SP_KEY_MID, mid).apply()
    }

    // 一键恢复完整会话
    suspend fun applyStoredSession(
        context: Context, sessData: String, csrf: String, mid: Long,
        accessToken: String, refreshToken: String, buvid3: String, isVip: Boolean
    ) { /* ... */ }

    // 清除所有
    suspend fun clear(context: Context) {
        sessDataCache = null; csrfCache = null; midCache = null
        accessTokenCache = null; refreshTokenCache = null; isVipCache = false
        // 清除 SP + DataStore
    }
}
```

**双写策略的核心问题**：冷启动时 DataStore 是异步加载的，`ApiClient` 可能在 DataStore 就绪前就发起请求，导致 Cookie 为空。SP 备份解决了这个问题——`init()` 中同步读取 SP 确保主线程立即可用。

### 1.6 Cookie 自动注入：AppSessionCookieJar

OkHttp 的 `CookieJar` 实现自动将 `TokenManager` 中的 Cookie 注入到每个请求：

```kotlin
// ApiClient.kt
private class AppSessionCookieJar : okhttp3.CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<okhttp3.Cookie>>()

    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        val cookies = mutableListOf<okhttp3.Cookie>()

        // 1. 从内存 cookieStore 加载已有 Cookie
        synchronized(cookieLock) {
            cookieStore[url.host]?.let { cookies.addAll(it) }
        }

        // 2. 自动注入 buvid3（设备指纹）
        var buvid3 = TokenManager.buvid3Cache
        if (buvid3.isNullOrEmpty()) {
            buvid3 = UUID.randomUUID().toString() + "infoc"
            TokenManager.buvid3Cache = buvid3
        }
        if (cookies.none { it.name == "buvid3" }) {
            cookies.add(Cookie.Builder().domain(url.host).name("buvid3").value(buvid3).build())
        }

        // 3. 自动注入 SESSDATA
        val sessData = TokenManager.sessDataCache
        if (!sessData.isNullOrEmpty()) {
            cookies.removeAll { it.name == "SESSDATA" }
            cookies.add(Cookie.Builder().domain(biliBiliDomain).name("SESSDATA").value(sessData).build())
        }

        // 4. 自动注入 bili_jct
        val biliJct = TokenManager.csrfCache
        if (!biliJct.isNullOrEmpty()) {
            cookies.removeAll { it.name == "bili_jct" }
            cookies.add(Cookie.Builder().domain(biliBiliDomain).name("bili_jct").value(biliJct).build())
        }

        return cookies
    }
}
```

---

## 2. Wbi 动态加密签名算法

### 2.1 算法概述

B 站 Wbi 签名是 2023 年起逐步推行的 API 风控机制。核心流程：

```
1. 调用 /x/web-interface/nav 获取 img_url 和 sub_url
2. 从 URL 中提取 img_key 和 sub_key（文件名去掉扩展名）
3. 拼接 img_key + sub_key，通过混淆表提取 32 位 mixin_key
4. 将请求参数排序、URL 编码、拼接 mixin_key 后 MD5 得到 w_rid
5. 将 wts（时间戳）和 w_rid 追加到请求参数中
```

### 2.2 WbiUtils 完整源码

```kotlin
// core/network/WbiUtils.kt
object WbiUtils {
    // 混淆表：64 位定长，决定从拼接字符串中取哪些字符组成 mixin_key
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    // 从 img_key + sub_key 拼接字符串中提取 32 位 mixin_key
    private fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (i in mixinKeyEncTab) {
            if (i < orig.length) sb.append(orig[i])
        }
        return sb.toString().substring(0, 32)
    }

    // 过滤非法字符（B 站要求）
    private fun filterIllegalChars(value: String): String {
        return value.replace(Regex("[!'()*]"), "")
    }

    // 标准化 URL 编码（仅用于计算签名，不改变原始参数）
    private fun encodeURIComponent(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun md5(str: String): String {
        return MessageDigest.getInstance("MD5").digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    // 风控指纹参数（可选，仅部分接口需要）
    private fun MutableMap<String, String>.appendRiskFingerprintParams() {
        this["dm_img_list"] = "[]"
        this["dm_img_str"] = "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ"
        this["dm_cover_img_str"] = "QU5HTEUgKE5WSURJQSwgTlZJRElBIEdlRm9yY2UgR1RYIDEwNjAgNkdCIERpcmVjdDNEMTEgdnNfNV8wIHBzXzVfMCwgRDNEMTEp"
        this["dm_img_inter"] = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
    }

    /**
     * Wbi 签名核心方法
     *
     * @param params 原始请求参数
     * @param imgKey 从 nav API 获取的 img_key
     * @param subKey 从 nav API 获取的 sub_key
     * @param includeRiskFingerprint 是否注入风控指纹参数
     * @return 签名后的参数 Map（含 wts 和 w_rid），值未编码，交给 Retrofit 编码
     */
    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
        includeRiskFingerprint: Boolean = false
    ): Map<String, String> {
        val mixinKey = getMixinKey(imgKey + subKey)
        val currTime = System.currentTimeMillis() / 1000

        // 1. 准备原始参数（加入 wts），过滤非法字符
        val rawParams = mutableMapOf<String, String>()
        for ((key, value) in params) {
            rawParams[key] = filterIllegalChars(value)
        }
        rawParams["wts"] = currTime.toString()
        if (includeRiskFingerprint) {
            rawParams.appendRiskFingerprintParams()
        }

        // 2. 排序 Key
        val sortedKeys = rawParams.keys.sorted()

        // 3. 拼接字符串用于计算 Hash（Key=EncodedValue）
        val queryBuilder = StringBuilder()
        for (key in sortedKeys) {
            val value = rawParams[key]
            if (value != null) {
                val encodedValue = encodeURIComponent(value)
                if (queryBuilder.isNotEmpty()) queryBuilder.append("&")
                queryBuilder.append(key).append("=").append(encodedValue)
            }
        }

        // 4. 计算签名：queryString + mixinKey → MD5
        val strToHash = queryBuilder.toString() + mixinKey
        val wRid = md5(strToHash)

        // 5. 将签名加入参数表
        rawParams["w_rid"] = wRid

        return rawParams
    }
}
```

### 2.3 WbiKeyManager：密钥获取与缓存

```kotlin
// core/network/WbiKeyManager.kt
object WbiKeyManager {
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L  // 24 小时缓存
    private const val PREFRESH_THRESHOLD_MS = 60 * 60 * 1000L   // 剩余 <1h 预刷新

    @Volatile private var cachedKeys: Pair<String, String>? = null
    @Volatile private var cacheTimestamp: Long = 0
    private val refreshMutex = Mutex()  // 防止并发刷新

    suspend fun getWbiKeys(): Result<Pair<String, String>> {
        // 1. 内存缓存命中
        val cached = cachedKeys
        if (cached != null && isCacheValid()) return Result.success(cached)

        // 2. 互斥锁 + 双重检查
        return refreshMutex.withLock {
            val rechecked = cachedKeys
            if (rechecked != null && isCacheValid()) return@withLock Result.success(rechecked)
            refreshKeysInternal()
        }
    }

    private suspend fun refreshKeysInternal(): Result<Pair<String, String>> {
        return try {
            // 调用 /x/web-interface/nav 获取密钥 URL
            val navResp = NetworkModule.api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img

            if (wbiImg != null) {
                // 从 URL 中提取文件名作为 key
                // 例: https://i0.hdslb.com/bfs/wbi/xxx.png → imgKey = "xxx"
                val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")

                cachedKeys = Pair(imgKey, subKey)
                cacheTimestamp = System.currentTimeMillis()

                // 持久化到 SharedPreferences
                persistToStorage(context)

                Result.success(Pair(imgKey, subKey))
            } else {
                Result.failure(Exception("WBI keys not found in nav response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 从 SP 恢复（冷启动时无需网络请求）
    fun restoreFromStorage(context: Context): Boolean { /* ... */ }
}
```

### 2.4 签名调用示例

```kotlin
// BangumiRepository.kt — 播放地址签名
val wbiKeys = WbiKeyManager.getWbiKeys().getOrNull()
    ?: WbiKeyManager.refreshKeys().getOrNull()
val signedParams = WbiUtils.sign(baseParams, wbiKeys!!.first, wbiKeys.second)

// 搜索签名
val navResp = navApi.getNavInfo()
val imgKey = navResp.data?.wbi_img?.img_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
val subKey = navResp.data?.wbi_img?.sub_url?.substringAfterLast("/")?.substringBefore(".") ?: ""
val signedParams = if (imgKey.isNotEmpty()) WbiUtils.sign(params, imgKey, subKey) else params
```

### 2.5 APP 签名（AppSignUtils）

TV 端登录和 APP API 调用使用独立的签名体系：

```kotlin
// core/network/AppSignUtils.kt
object AppSignUtils {
    const val TV_APP_KEY = "4409e2ce8ffd12b8"
    private const val TV_APP_SEC = "59b43e04ad6965f34319062b478f83dd"

    const val ANDROID_APP_KEY = "1d8b6e7d45233436"
    private const val ANDROID_APP_SEC = "560c52ccd288fed045859ed18bffd973"

    // 签名规则：参数按 key 排序 → 拼接 query string → 末尾加 appsec → MD5
    fun sign(params: Map<String, String>, appSec: String = TV_APP_SEC): Map<String, String> {
        val sortedParams = params.toSortedMap()
        val queryString = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val sign = md5(queryString + appSec)
        return sortedParams + ("sign" to sign)
    }

    fun signForTvLogin(params: Map<String, String>) = sign(params, TV_APP_SEC)
    fun signForAndroidApi(params: Map<String, String>) = sign(params, ANDROID_APP_SEC)
}
```

### 2.6 Wbi 端点特殊处理

Wbi 签名接口**不能设置 Referer 头**，否则会失败：

```kotlin
// ApiClient.kt — OkHttp Interceptor
val isWbiEndpoint = url.encodedPath.contains("/wbi/")
if (!isWbiEndpoint) {
    builder.header("Referer", referer)
}
```

---

## 3. 追番列表 API 数据模型

### 3.1 我的追番列表响应模型

API 路径：`GET /x/space/bangumi/follow/list`

```kotlin
// data/model/response/BangumiModels.kt

@Serializable
data class MyFollowBangumiResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MyFollowBangumiData? = null
)

@Serializable
data class MyFollowBangumiData(
    val total: Int = 0,       // 总追番数
    val pn: Int = 1,          // 当前页码
    val ps: Int = 30,         // 每页数量
    val list: List<FollowBangumiItem>? = null
)

@Serializable
data class FollowBangumiItem(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",                    // 名称
    val cover: String = "",                    // 封面 URL
    @SerialName("square_cover")
    val squareCover: String = "",              // 方形封面
    val evaluate: String = "",                  // 简介
    val areas: List<AreaInfo>? = null,
    @SerialName("season_type_name")
    val seasonTypeName: String = "",           // "番剧" "电影" 等
    @SerialName("season_type")
    val seasonType: Int = 0,
    val badge: String = "",                    // 角标 "会员" "独家"
    @SerialName("badge_type")
    val badgeType: Int = 0,
    @SerialName("new_ep")
    val newEp: NewEpInfo? = null,              // 最新一集信息
    val progress: String = "",                 // 观看进度文案 "看到第5话"
    @SerialName("is_finish")
    val isFinish: Int = 0,                     // 是否完结
    @SerialName("follow_status")
    val followStatus: Int = 0,                 // 追番状态
    val total: Int = 0,                        // 总集数
    @SerialName("first_ep")
    val firstEp: Long = 0,
    val url: String = ""
)
```

### 3.2 最新一集信息

```kotlin
@Serializable
data class NewEpInfo(
    val cover: String = "",            // 最新一集封面
    val id: Long = 0,                  // ep_id
    @SerialName("index_show")
    val indexShow: String = ""         // "全13话" "更新至第12话"
)
```

### 3.3 观看进度（番剧详情中的进度）

```kotlin
@Serializable
data class UserStatus(
    val follow: Int = 0,              // 是否追番
    @SerialName("follow_status")
    val followStatus: Int = 0,
    val vip: Int = 0,
    @SerialName("vip_frozen")
    val vipFrozen: Int = 0,
    val progress: WatchProgress? = null  // 观看进度
)

@Serializable
data class WatchProgress(
    @SerialName("last_ep_id")
    val lastEpId: Long = 0,           // 上次观看的 ep_id
    @SerialName("last_ep_index")
    val lastEpIndex: String = "",     // "第5话"
    @SerialName("last_time")
    val lastTime: Long = 0            // 上次观看时间点（毫秒）
)
```

### 3.4 番剧详情模型

API 路径：`GET /pgc/view/web/season`

```kotlin
@Serializable
data class BangumiDetail(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("square_cover")
    val squareCover: String = "",
    val evaluate: String = "",        // 简介
    val rating: BangumiRating? = null,
    val stat: BangumiStat? = null,
    @SerialName("new_ep")
    val newEp: NewEpDetail? = null,
    val episodes: List<BangumiEpisode>? = null,
    val seasons: List<SeasonInfo>? = null,
    val areas: List<AreaInfo>? = null,
    val styles: List<String>? = null,
    val actors: String = "",
    val staff: String = "",
    @SerialName("season_type")
    val seasonType: Int = 0,
    val total: Int = 0,
    val mode: Int = 0,
    val rights: BangumiRights? = null,
    @SerialName("user_status")
    val userStatus: UserStatus? = null,
    val publish: BangumiPublish? = null,
    val payment: BangumiPayment? = null,
    val positive: BangumiPositive? = null,
    val section: List<BangumiSection>? = null,
    @SerialName("season_title")
    val seasonTitle: String = "",
    val subtitle: String = ""
)
```

### 3.5 番剧索引列表项

API 路径：`GET /pgc/season/index/result`

```kotlin
@Serializable
data class BangumiItem(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val badge: String = "",           // "会员专享" "独家"
    val score: String = "",           // 评分 "9.8"
    @SerialName("new_ep")
    val newEp: NewEpInfo? = null,
    val order: String = "",           // 播放量/追番数
    @SerialName("season_type")
    val seasonType: Int = 0,
    @SerialName("season_type_name")
    val seasonTypeName: String = "",
    @SerialName("is_finish")
    val isFinish: Int = 0,
    @SerialName("first_ep")
    val firstEp: BangumiFirstEpisode? = null
)
```

---

## 4. Repository 网络请求层实现

### 4.1 Retrofit 接口定义

```kotlin
// ApiClient.kt — BangumiApi 接口
interface BangumiApi {
    // 我的追番列表
    @GET("x/space/bangumi/follow/list")
    suspend fun getMyFollowBangumi(
        @Query("vmid") vmid: Long,       // 用户 mid
        @Query("type") type: Int = 1,    // 1=追番 2=追剧
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30
    ): MyFollowBangumiResponse

    // 番剧详情（返回 ResponseBody 自行解析，防止 OOM）
    @GET("pgc/view/web/season")
    suspend fun getSeasonDetail(
        @Query("season_id") seasonId: Long? = null,
        @Query("ep_id") epId: Long? = null
    ): ResponseBody

    // 番剧播放地址（Wbi 签名）
    @GET("pgc/player/web/v2/playurl")
    suspend fun getBangumiPlayUrl(
        @QueryMap params: Map<String, String>
    ): ResponseBody

    // 追番
    @FormUrlEncoded
    @POST("pgc/web/follow/add")
    suspend fun followBangumi(
        @Field("season_id") seasonId: Long,
        @Field("csrf") csrf: String
    ): SimpleApiResponse

    // 取消追番
    @FormUrlEncoded
    @POST("pgc/web/follow/del")
    suspend fun unfollowBangumi(
        @Field("season_id") seasonId: Long,
        @Field("csrf") csrf: String
    ): SimpleApiResponse
}
```

### 4.2 Retrofit 实例构建

```kotlin
// NetworkModule.kt
val bangumiApi: BangumiApi by lazy {
    Retrofit.Builder()
        .baseUrl("https://api.bilibili.com/")
        .client(okHttpClient)  // 共享 OkHttpClient（含 CookieJar + Interceptor）
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BangumiApi::class.java)
}
```

### 4.3 OkHttpClient 配置

```kotlin
// ApiClient.kt
val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .protocols(resolveSharedNetworkProtocols())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(object : Dns { /* DNS 解析 + 硬编码 IP 降级 */ })
        .cookieJar(appSessionCookieJar)  // Cookie 自动注入
        .addInterceptor { chain ->
            // 统一 Header 注入
            val builder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 ... Chrome/131.0.0.0 ...")
                .header("Origin", origin)

            // Wbi 端点不设 Referer
            val isWbiEndpoint = url.encodedPath.contains("/wbi/")
            if (!isWbiEndpoint) {
                builder.header("Referer", referer)
            }

            chain.proceed(builder.build())
        }
        .build()
}
```

### 4.4 Repository 层：获取追番列表

```kotlin
// BangumiRepository.kt
object BangumiRepository {
    private val api = NetworkModule.bangumiApi

    suspend fun getMyFollowBangumi(
        type: Int = 1,     // 1=追番 2=追剧
        page: Int = 1,
        pageSize: Int = 30,
        vmid: Long? = null
    ): Result<MyFollowBangumiData> = withContext(Dispatchers.IO) {
        try {
            val mid = vmid?.takeIf { it > 0L }
                ?: TokenManager.midCache
                ?: return@withContext Result.failure(Exception("未登录"))

            val response = api.getMyFollowBangumi(
                vmid = mid,
                type = type,
                pn = page,
                ps = pageSize
            )
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception("获取追番列表失败: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 4.5 Repository 层：Wbi 签名请求（播放地址）

```kotlin
// BangumiRepository.kt — 带 Wbi 签名的请求
suspend fun getBangumiPlayUrl(
    epId: Long, qn: Int = 80, cid: Long = 0L,
    bvid: String? = null, seasonId: Long? = null
): Result<BangumiVideoInfo> = withContext(Dispatchers.IO) {
    try {
        // 1. 构建原始参数
        val baseParams = buildBangumiPlayUrlParams(epId, cid, qn, bvid, seasonId)

        // 2. 获取 Wbi 密钥（优先缓存，失败则刷新）
        val wbiKeys = WbiKeyManager.getWbiKeys().getOrNull()
            ?: WbiKeyManager.refreshKeys().getOrNull()

        // 3. Wbi 签名
        val signedParams = signBangumiPlayUrlParams(baseParams, wbiKeys)

        // 4. 发起请求
        val primaryResponse = decodeBangumiPlayUrlPayload(
            api.getBangumiPlayUrl(signedParams).string()
        )

        // 5. 降级：如果新接口失败，回退到旧接口
        val response = if (shouldFallbackToLegacyBangumiPlayUrl(primaryResponse)) {
            decodeBangumiPlayUrlPayload(api.getBangumiPlayUrlLegacy(signedParams).string())
        } else {
            primaryResponse
        }

        if (response.code == 0 && response.videoInfo != null) {
            Result.success(response.videoInfo)
        } else {
            Result.failure(Exception(errorMsg))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// 签名辅助函数
internal fun signBangumiPlayUrlParams(
    params: Map<String, String>,
    wbiKeys: Pair<String, String>?
): Map<String, String> {
    val (imgKey, subKey) = wbiKeys ?: return params
    if (imgKey.isBlank() || subKey.isBlank()) return params
    return WbiUtils.sign(params, imgKey, subKey)
}
```

### 4.6 请求流程总结

```
┌──────────────────────────────────────────────────────────────┐
│                    完整请求流程                                │
│                                                              │
│  Repository                                                  │
│    │                                                         │
│    ├─ 1. 构建 params Map                                     │
│    ├─ 2. WbiKeyManager.getWbiKeys()                          │
│    │     ├─ 内存缓存命中 → 直接返回                           │
│    │     └─ 缓存失效 → GET /x/web-interface/nav             │
│    │           └─ 提取 img_url/sub_url → img_key/sub_key     │
│    ├─ 3. WbiUtils.sign(params, imgKey, subKey)              │
│    │     ├─ getMixinKey(imgKey + subKey) → 32位盐值         │
│    │     ├─ 过滤非法字符 + 加入 wts                          │
│    │     ├─ 排序 + URL编码 + 拼接                             │
│    │     └─ MD5(queryString + mixinKey) → w_rid             │
│    ├─ 4. Retrofit 发起请求                                   │
│    │     ├─ CookieJar 自动注入 SESSDATA/bili_jct/buvid3      │
│    │     ├─ Interceptor 注入 User-Agent/Origin/Referer      │
│    │     └─ Wbi 端点省略 Referer                            │
│    └─ 5. 解析响应 → Result<Data>                             │
└──────────────────────────────────────────────────────────────┘
```

---

## 5. 附录：关键文件索引

| 文件 | 职责 |
|------|------|
| `core/network/WbiUtils.kt` | Wbi 签名核心算法（mixinKey + MD5） |
| `core/network/WbiKeyManager.kt` | Wbi 密钥获取、缓存、持久化 |
| `core/network/AppSignUtils.kt` | APP 签名（TV/Android appkey + appsec + MD5） |
| `core/store/TokenManager.kt` | Cookie/Token 持久化（SP + DataStore 双写） |
| `core/network/ApiClient.kt` | Retrofit 接口定义 + OkHttpClient 配置 + CookieJar |
| `core/network/TokenRefreshHelper.kt` | TV access_token 刷新 |
| `feature/login/LoginViewModel.kt` | 登录流程（Web/TV/手机号）+ Cookie 提取 |
| `data/model/response/BangumiModels.kt` | 番剧相关全部数据模型 |
| `data/repository/BangumiRepository.kt` | 番剧 Repository（追番列表/详情/播放/签名） |
