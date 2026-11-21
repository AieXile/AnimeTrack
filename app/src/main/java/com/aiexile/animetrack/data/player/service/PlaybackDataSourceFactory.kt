package com.aiexile.animetrack.data.player.service

import com.aiexile.animetrack.data.player.web.WebHttpDataSourceFactory
import com.aiexile.animetrack.data.player.webdav.WebDAVDataSourceFactory

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 按目标主机分流：
 * - 配置的 WebDAV 服务器 → WebDAV 数据源（Basic/Digest）；
 * - 其他 HTTP(S) → 网页数据源（不携带 WebDAV 凭据）。
 *
 * 认证范围以 host + 有效端口为准；网页重定向到其他主机时不会复用 WebDAV 账号。
 */
@UnstableApi
class RoutingHttpDataSource(
    private val webDavFactory: WebDAVDataSourceFactory?,
    private val webDavHost: String?,
    private val webDavPort: Int,
    private val webFactory: DataSource.Factory
) : DataSource {

    private var webDavSource: DataSource? = null
    private var webDavSourceUri: Uri? = null
    private var webSource: DataSource? = null
    private var openedSource: DataSource? = null
    private var transferListener: TransferListener? = null

    override fun open(dataSpec: DataSpec): Long {
        val source = selectSource(dataSpec.uri)
        openedSource = source
        return source.open(dataSpec)
    }

    private fun selectSource(uri: Uri): DataSource {
        val isWebDav = webDavHost != null &&
            uri.scheme in setOf("http", "https") &&
            uri.host.equals(webDavHost, ignoreCase = true) &&
            effectivePort(uri) == webDavPort
        return if (isWebDav) {
            webDavSource?.takeIf { webDavSourceUri == uri } ?: requireNotNull(webDavFactory) {
                "WebDAV 数据源未初始化"
            }.createDataSource(uri).also {
                webDavSource = it
                webDavSourceUri = uri
                transferListener?.let(it::addTransferListener)
            }
        } else {
            webSource ?: webFactory.createDataSource()
                .also { webSource = it; transferListener?.let(it::addTransferListener) }
        }
    }

    private fun effectivePort(uri: Uri): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme == "https" -> 443
        else -> 80
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        openedSource?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = openedSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        openedSource?.responseHeaders ?: emptyMap()

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
        webDavSource?.addTransferListener(transferListener)
        webSource?.addTransferListener(transferListener)
    }

    override fun close() {
        openedSource?.close()
        openedSource = null
    }
}

/**
 * 播放器统一数据源工厂：本地 content/file 由 [DefaultDataSource] 处理，
 * HTTP(S) 交给 [RoutingHttpDataSource] 按主机分流。
 */
@UnstableApi
class PlaybackDataSourceFactory(
    private val context: Context,
    private val webDavClient: OkHttpClient
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val settings = AppContainer.getSettingsRepository()
        val url = runBlocking(Dispatchers.IO) { settings.playerWebdavUrl.first() }
        val username = runBlocking(Dispatchers.IO) { settings.playerWebdavUsername.first() }
        val password = runBlocking(Dispatchers.IO) { settings.playerWebdavPassword.first() }

        val httpUrl = url.trim().toHttpUrlOrNull()
        val webDavFactory = if (httpUrl != null) {
            WebDAVDataSourceFactory(webDavClient, username, password)
        } else {
            null
        }

        val routing = RoutingHttpDataSource(
            webDavFactory = webDavFactory,
            webDavHost = httpUrl?.host,
            webDavPort = httpUrl?.port ?: if (httpUrl?.scheme == "https") 443 else 80,
            webFactory = WebHttpDataSourceFactory(webClient)
        )
        return DefaultDataSource(context, routing)
    }

    private companion object {
        /**
         * 网页播放客户端：复用应用代理/日志能力，但关闭自动重定向，
         * 由数据源逐跳重算请求头；不携带任何 WebDAV 认证或证书豁免。
         */
        val webClient: OkHttpClient by lazy {
            RetrofitClient.baseOkHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
