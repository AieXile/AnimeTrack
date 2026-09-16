package com.aiexile.animetrack.util

import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.SearchSource
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 统一网络错误解析：异常 → 用户可读的中文提示。
 *
 * 与旧 resolveSearchError 的区别：
 * - 数据源 host 由调用方显式传入（调用方明确知道请求目标），不再从 e.message 猜域名——
 *   OkHttp 超时/连接重置类异常的 message 不含域名（如 "timeout"），旧逻辑必然漏判"被墙"；
 * - 请求经用户配置的代理（Bangumi 反代 / HTTP 代理）发出时，网络层失败优先提示检查代理设置
 *   而非"被墙"（此时异常 message 中是代理域名，也无法判出目标源）；
 * - 补全 HttpException / SSLException / 连接重置 / EOF 等映射，不再透出英文原始 message。
 */
object NetworkErrorUtils {

    /** Bangumi API 主域 */
    const val HOST_BANGUMI = "api.bgm.tv"

    /** TMDB API 主域 */
    const val HOST_TMDB = "api.themoviedb.org"

    /** 搜索源 → 逻辑目标 host；ALL 模式无法确定单一目标，返回 null 走通用映射 */
    fun hostOfSource(source: SearchSource?): String? = when (source) {
        SearchSource.BANGUMI -> HOST_BANGUMI
        SearchSource.TMDB -> HOST_TMDB
        else -> null
    }

    /**
     * 解析网络异常为用户可读提示。
     *
     * @param e 捕获的异常
     * @param targetHost 逻辑目标 host（如 [HOST_BANGUMI] / [HOST_TMDB]），由调用方按请求源传入；
     *        未知时传 null，仅做通用异常映射
     * @param settings 用于判断该目标当前是否经用户配置的代理发出，null 表示跳过代理感知
     */
    fun resolveNetworkError(
        e: Exception,
        targetHost: String?,
        settings: SettingsRepository? = null
    ): String {
        // HTTP 状态错误按响应码映射
        if (e is HttpException) {
            return when (e.code()) {
                401 -> "未授权(401)"
                403 -> "无权限访问(403)"
                404 -> "未找到该条目(404)"
                429 -> "请求过于频繁，请稍后再试(429)"
                in 500..599 -> "服务端错误(${e.code()})"
                else -> "请求失败(${e.code()})"
            }
        }

        val isNetworkLayer = e is IOException

        // 经用户配置的代理发出且网络层失败：优先提示检查代理，而非"被墙"
        if (isNetworkLayer && targetHost != null && settings != null
            && isProxied(targetHost, settings)
        ) {
            return "代理/反代无法连接，请检查 HTTP 代理与 Bangumi 反代设置"
        }

        val isBangumi = targetHost?.contains("bgm.tv") == true
        val isTmdb = targetHost?.contains("themoviedb.org") == true
        return when {
            isBangumi && isNetworkLayer ->
                "Bangumi 无法直连（可能被墙），请在设置中配置 Bangumi 反代或 HTTP 代理"
            isTmdb && isNetworkLayer ->
                "TMDB 无法直连（可能被墙），请在设置中配置 HTTP 代理"
            else -> describeException(e)
        }
    }

    /** 目标请求当前是否经用户配置的代理（全局 HTTP 代理，或 Bangumi 反代）发出 */
    private fun isProxied(targetHost: String, settings: SettingsRepository): Boolean {
        val httpProxy = settings.httpProxyEnabled
            && settings.httpProxyHost.isNotBlank()
            && settings.httpProxyPort > 0
        if (httpProxy) return true
        val bangumiProxy = settings.bangumiProxyEnabled && settings.bangumiProxyHost.isNotBlank()
        return bangumiProxy && targetHost.contains("bgm.tv")
    }

    private fun describeException(e: Exception): String = when (e) {
        is UnknownHostException -> "网络未连接或 DNS 解析失败"
        is SocketTimeoutException -> "连接超时"
        is SSLException -> "SSL 握手失败"
        is IOException -> "连接中断"
        // 反代/URL 重写配置了非法 host 时 HttpUrl.Builder 抛出，不应透出英文原文
        is IllegalArgumentException -> "网络配置异常（代理地址格式错误）"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    }
}
