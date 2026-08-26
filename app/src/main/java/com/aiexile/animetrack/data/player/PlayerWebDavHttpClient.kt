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

    /** 按信任模式各缓存一个实例：共享连接池/线程池，避免每次请求重新 DNS + TLS 握手 */
    private val clients = arrayOfNulls<OkHttpClient>(2)

    private const val INDEX_STRICT = 0
    private const val INDEX_TRUST_ALL = 1

    @Synchronized
    fun create(trustAllCerts: Boolean): OkHttpClient {
        val index = if (trustAllCerts) INDEX_TRUST_ALL else INDEX_STRICT
        clients[index]?.let { return it }

        val client = if (!trustAllCerts) {
            OkHttpClient()
        } else {
            val trustAllManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
            }

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        }
        clients[index] = client
        return client
    }
}
