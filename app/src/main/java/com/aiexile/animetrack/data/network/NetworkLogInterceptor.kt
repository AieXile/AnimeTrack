package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.data.log.AppLogManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * 网络失败日志拦截器（反馈日志数据源之一）：
 * - 覆盖 OkHttp 层全部错误：连接/读取超时、SSL 握手失败、DNS 解析失败等 [IOException]，以及 HTTP 非 2xx 状态码
 * - 挂载于 [RetrofitClient.baseOkHttpClient]，所有派生客户端（Bangumi/Bilibili/TMDb/GitHub/用户认证/更新检查）自动生效
 * - 仅记录失败请求；成功请求静默，避免刷爆单日 2MB 日志上限
 * - 只记录 method + URL + 状态码 + 耗时 + 异常摘要，不记录请求/响应体（不含 token 等敏感数据）
 *
 * 注：Retrofit Converter 层的 JSON 解析异常发生在 OkHttp 拦截器之外，由调用方 catch（业务日志覆盖）
 * 或未捕获升级为崩溃（AppLogManager 崩溃处理器覆盖），本拦截器不负责。
 */
class NetworkLogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startMs = System.currentTimeMillis()
        try {
            val response = chain.proceed(request)
            val costMs = System.currentTimeMillis() - startMs
            if (!response.isSuccessful) {
                AppLogManager.w(
                    TAG,
                    "HTTP ${response.code} ${request.method} ${request.url} (${costMs}ms)"
                )
            }
            return response
        } catch (e: IOException) {
            val costMs = System.currentTimeMillis() - startMs
            AppLogManager.w(
                TAG,
                "网络异常 ${request.method} ${request.url} (${costMs}ms): ${e.javaClass.simpleName}: ${e.message}"
            )
            throw e
        }
    }

    private companion object {
        const val TAG = "Network"
    }
}
