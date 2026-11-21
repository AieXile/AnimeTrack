package com.aiexile.animetrack.data.player.web

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.aiexile.animetrack.data.log.AppLogManager
import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 普通网页/直链的 HTTP 数据源。
 *
 * 与 WebDAV 数据源完全分离：不附加任何 WebDAV 账号，只使用当前网页会话的
 * UA / Referer / Cookie；重定向交给 OkHttp 跟随（跨主机不会自动携带 WebDAV 凭据）。
 */
@UnstableApi
class WebHttpDataSource(private val okHttpClient: OkHttpClient) : DataSource {

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }

    private var inputStream: InputStream? = null
    private var openedUri: Uri? = null
    private var contentLength: Long = C.LENGTH_UNSET.toLong()
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var transferListener: TransferListener? = null
    private var openedDataSpec: DataSpec? = null

    override fun open(dataSpec: DataSpec): Long {
        openedUri = dataSpec.uri
        transferListener?.onTransferInitializing(this, dataSpec, true)
        try {
            var response = executeFollowingRedirects(dataSpec)
            if (response.code == 403) {
                // 反盗链兜底：去掉 Referer/Origin，并模拟 <video> 的 Range: bytes=0-
                response.close()
                response = executeFollowingRedirects(dataSpec) { url ->
                    val host = android.net.Uri.parse(url).host
                    val headers = WebPlaybackSession.headersFor(host)
                        .filterKeys {
                            !it.equals("Referer", ignoreCase = true) &&
                                !it.equals("Origin", ignoreCase = true)
                        }
                        .toMutableMap()
                    if (dataSpec.position == 0L && headers.keys.none { it.equals("Range", true) }) {
                        headers["Range"] = "bytes=0-"
                    }
                    headers
                }
                AppLogManager.i(
                    "WebHttp",
                    "403 后重试（无 Referer/Origin + Range）: ${dataSpec.uri.host}"
                )
            }
            val code = response.code
            if (code == 401 || code == 403 || code !in 200..299) {
                // 失败时记录主机与已附加的请求头名称（不含值），便于定位反盗链校验
                AppLogManager.w(
                    "WebHttp",
                    "HTTP $code ${dataSpec.uri.host} headers=" +
                        WebPlaybackSession.headersFor(dataSpec.uri.host).keys.sorted()
                )
                response.close()
                throw HttpDataSource.InvalidResponseCodeException(code, null, null, emptyMap(), dataSpec, ByteArray(0))
            }
            responseHeaders = response.headers.toMultimap()

            val contentRange = response.header("Content-Range")
            if (contentRange != null) {
                val rangePart = contentRange.substringAfter("bytes ").trim()
                val total = rangePart.substringAfter("/", "")
                if (total.isNotEmpty() && total != "*") {
                    contentLength = total.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
                }
                val dash = rangePart.indexOf('-')
                val slash = rangePart.indexOf('/')
                bytesRemaining = if (dash > 0 && slash > dash) {
                    rangePart.substring(dash + 1, slash).toLongOrNull()
                        ?: C.LENGTH_UNSET.toLong()
                } else {
                    response.body?.contentLength()?.takeIf { it > 0 }
                        ?: C.LENGTH_UNSET.toLong()
                }
            } else {
                contentLength = response.body?.contentLength()?.takeIf { it > 0 }
                    ?: C.LENGTH_UNSET.toLong()
                bytesRemaining = when {
                    dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                    contentLength != C.LENGTH_UNSET.toLong() -> contentLength - dataSpec.position
                    else -> C.LENGTH_UNSET.toLong()
                }
            }

            inputStream = response.body?.byteStream()
                ?: throw IOException("Empty response body for ${dataSpec.uri}")
            openedDataSpec = dataSpec
            transferListener?.onTransferStart(this, dataSpec, true)
            return bytesRemaining
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Failed to open ${dataSpec.uri}", e)
        }
    }

    /**
     * 手动逐跳重定向：每一跳按目标主机的会话请求头重新组装，
     * 避免把上一站的 Cookie 转发给跨域地址。
     */
    private fun executeFollowingRedirects(
        dataSpec: DataSpec,
        headersFor: (String) -> Map<String, String> = { url ->
            WebPlaybackSession.headersFor(android.net.Uri.parse(url).host)
        }
    ): okhttp3.Response {
        var url = dataSpec.uri.toString()
        repeat(MAX_REDIRECTS) {
            val builder = Request.Builder().url(url)
            headersFor(url).forEach { (name, value) ->
                builder.header(name, value)
            }
            if (dataSpec.position > 0) {
                val end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    dataSpec.position + dataSpec.length - 1
                } else {
                    -1L
                }
                builder.header(
                    "Range",
                    if (end > 0) "bytes=${dataSpec.position}-$end" else "bytes=${dataSpec.position}-"
                )
            }

            val response = okHttpClient.newCall(builder.build()).execute()
            if (response.code !in REDIRECT_CODES) return response

            val location = response.header("Location") ?: return response
            val next = response.request.url.resolve(location) ?: return response
            response.close()
            url = next.toString()
        }
        throw IOException("Too many redirects for ${dataSpec.uri}")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        val read = stream.read(buffer, offset, length)
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        openedDataSpec?.let { transferListener?.onBytesTransferred(this, it, true, read) }
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun addTransferListener(transferListener: TransferListener) {
        this.transferListener = transferListener
    }

    override fun close() {
        openedDataSpec?.let { transferListener?.onTransferEnd(this, it, true) }
        openedDataSpec = null
        openedUri = null
        runCatching { inputStream?.close() }
        inputStream = null
    }
}

@UnstableApi
class WebHttpDataSourceFactory(
    private val okHttpClient: OkHttpClient
) : DataSource.Factory {
    override fun createDataSource(): DataSource = WebHttpDataSource(okHttpClient)
}
