package com.aiexile.animetrack.data.websource

import android.webkit.CookieManager
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.model.WebSourceRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 站点返回非 2xx；[statusCode] 供界面提示。 */
class WebSourceHttpException(val statusCode: Int) : IOException("HTTP $statusCode")

/**
 * 数据源搜索/详情页的 HTTP 客户端。
 *
 * 复用全局基础客户端的代理、DNS 与失败日志；额外注入 WebView 的 Cookie，
 * 用户在网页播放里登录过的站点，规则搜索也能带上登录态。
 */
class WebSourceHttpClient {

    data class Page(val url: String, val bytes: ByteArray)

    suspend fun getPage(url: String, userAgent: String, referer: String?): Page =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .apply { referer?.let { header("Referer", it) } }.build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (!it.isSuccessful) throw WebSourceHttpException(it.code)
                            Page(it.request.url.toString(), it.body?.bytes() ?: throw IOException("empty body"))
                        }
                    }
                    result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }

    private val client: OkHttpClient by lazy {
        RetrofitClient.baseOkHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(cookieInterceptor)
            .build()
    }

    private val cookieInterceptor = Interceptor { chain ->
        val request = chain.request()
        val cookie = runCatching {
            CookieManager.getInstance().getCookie(request.url.toString())
        }.getOrNull()
        val builder = request.newBuilder()
        if (!cookie.isNullOrBlank() && request.header("Cookie").isNullOrBlank()) {
            builder.header("Cookie", cookie)
        }
        chain.proceed(builder.build())
    }

    suspend fun getBytes(url: String, rule: WebSourceRule): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", rule.effectiveUserAgent)
                .apply {
                    rule.effectiveReferer?.let { header("Referer", it) }
                }
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()
            execute(request)
        }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WebSourceRule.DEFAULT_USER_AGENT)
            .get()
            .build()
        String(execute(request), Charsets.UTF_8)
    }

    /** 执行 API 规则渲染出的 GET/POST 请求，返回响应文本。 */
    suspend fun executeApi(call: WebSourceApiCall, rule: WebSourceRule): String =
        withContext(Dispatchers.IO) {
            val base = call.url.toHttpUrlOrNull()
                ?: throw IOException("invalid api url: ${call.url}")
            val url = base.newBuilder().apply {
                call.query.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            val builder = Request.Builder().url(url)
            call.headers.forEach { (key, value) ->
                if (key.isNotBlank()) builder.header(key, value)
            }
            // 规则自身的 UA/Referer 优先于站点配置里的同名头。
            builder.header("User-Agent", rule.effectiveUserAgent)
            rule.effectiveReferer?.let { builder.header("Referer", it) }
            builder.header("Accept", "application/json, text/plain, */*")
            builder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (call.method == "POST") {
                builder.post(
                    (call.body.orEmpty()).toRequestBody(
                        call.contentType?.toMediaTypeOrNull()
                    )
                )
            } else {
                builder.get()
            }
            String(execute(builder.build()), Charsets.UTF_8)
        }

    private fun execute(request: Request): ByteArray {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw WebSourceHttpException(response.code)
            return response.body?.bytes() ?: throw IOException("empty body")
        }
    }

}
