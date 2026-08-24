package com.aiexile.animetrack.data.player

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 播放器 WebDAV 专用 OkHttpClient。
 *
 * trustAllCerts = true 时跳过证书链校验与主机名校验，用于：
 * - 用 IP 直连仅持有域名证书的 NAS（如飞牛 OS 内网 IP 访问）
 * - 自签名证书的服务器
 *
 * 仅作用于播放器 WebDAV 链路，不影响 App 内其他网络请求。
 */
object PlayerWebDavHttpClient {

    fun create(trustAllCerts: Boolean): OkHttpClient {
        if (!trustAllCerts) return OkHttpClient()

        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
