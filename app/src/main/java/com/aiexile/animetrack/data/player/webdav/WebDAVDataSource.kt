package com.aiexile.animetrack.data.player.webdav

import com.aiexile.animetrack.data.player.PlaybackTrace
import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@androidx.annotation.OptIn(UnstableApi::class)
class WebDAVDataSource(
    private val okHttpClient: OkHttpClient,
    private val username: String,
    private val password: String,
    private val isSubtitle: Boolean = false
) : DataSource {

    private companion object {
        val PARTIAL_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
    }

    private var remaining = C.LENGTH_UNSET.toLong()

    private var inputStream: InputStream? = null
    private var openedResponse: Response? = null
    private var openedUri: Uri? = null
    private var responseHeaders: Map<String, List<String>> = emptyMap()

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val requestBuilder = Request.Builder().url(uri.toString())

        // Basic authentication
        if (username.isNotEmpty()) {
            val credentials = "$username:$password"
            val encoded = Base64.encodeToString(
                credentials.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            requestBuilder.header("Authorization", "Basic $encoded")
        }

        // Range request for seeking
        val position = dataSpec.position
        androidx.media3.datasource.HttpUtil.buildRangeRequestHeader(position, dataSpec.length)?.let {
            requestBuilder.header("Range", it)
        }

        val request = requestBuilder.build()

        try {
            val response = executeRequest(request, position)
            openedResponse = response
            val statusCode = response.code
            if (statusCode == 416 && position > 0 &&
                response.header("Content-Range")?.trim()?.equals("bytes */$position", ignoreCase = true) == true
            ) {
                remaining = 0
                openedUri = uri
                responseHeaders = response.headers.toMultimap()
                return 0L
            }
            if (statusCode !in 200..299) {
                val error = HttpDataSource.InvalidResponseCodeException(
                    statusCode, response.message, null, response.headers.toMultimap(), dataSpec, ByteArray(0)
                )
                if (!isSubtitle && (statusCode == 401 || (position > 0 && statusCode == 416))) {
                    throw WebDavResumeOpenException(error)
                }
                throw error
            }

            val body = response.body ?: throw IOException("Empty WebDAV response")
            val available = when {
                statusCode == 206 -> partialLength(response, position)
                position != 0L -> throw WebDavResumeOpenException()
                else -> body.contentLength()
            }
            val requested = dataSpec.length
            val length = when {
                requested == C.LENGTH_UNSET.toLong() -> available
                available == C.LENGTH_UNSET.toLong() -> requested
                else -> minOf(requested, available)
            }
            // 字幕封装需按实际 EOF 查验大小，不能让少报的 Content-Length 隐藏尾部。
            remaining = if (isSubtitle && requested == C.LENGTH_UNSET.toLong()) requested else length
            inputStream = body.byteStream()
            openedUri = uri
            responseHeaders = response.headers.toMultimap()
            return length
        } catch (e: IOException) {
            closeAfterOpenFailure(e)
            throw e
        } catch (e: Exception) {
            closeAfterOpenFailure(e)
            throw IOException("Failed to open $uri", e)
        }
    }

    private fun partialLength(response: Response, position: Long): Long {
        val match = PARTIAL_RANGE.matchEntire(response.header("Content-Range").orEmpty().trim())
            ?: throw WebDavResumeOpenException()
        val (first, last, total) = match.destructured
        val end = last.toLongOrNull() ?: throw WebDavResumeOpenException()
        if (first.toLongOrNull() != position || end < position || end - position == Long.MAX_VALUE ||
            (total != "*" && (total.toLongOrNull() ?: -1L) <= end)
        ) throw WebDavResumeOpenException()
        return end - position + 1
    }

    private fun executeRequest(request: Request, offset: Long): Response {
        val traceId = PlaybackTrace.nextRequest()
        var response = okHttpClient.newCall(request).execute()
        PlaybackTrace.response(traceId, request, response, offset, digestRetry = false)
        if (response.code != 401 || username.isEmpty()) return response
        val challenged = response.request
        if (!WebDavPlaybackIdentity.sameOrigin(request.url, challenged.url)) return response
        val digestChallenge = response.headers.values("WWW-Authenticate")
            .firstOrNull { it.trim().startsWith("Digest", ignoreCase = true) }
        val retry = when {
            digestChallenge != null -> {
                val url = challenged.url
                val header = DigestAuth.buildAuthorization(challenged.method,
                    url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: ""), digestChallenge, username, password)
                    ?: return response
                challenged.newBuilder().header("Authorization", header).build()
            }
            !isSubtitle && offset > 0 && response.priorResponse == null && request.url == challenged.url &&
                response.challenges().any { it.scheme.equals("Basic", ignoreCase = true) } -> request
            else -> return response
        }
        response.close()
        // 恢复同一 GET（包括 Range）一次，不交给提取器从之前的输入位置另行续读。
        PlaybackTrace.event { "dav.authReplay req=$traceId offset=$offset digest=${digestChallenge != null}" }
        response = try {
            okHttpClient.newCall(retry).execute()
        } catch (e: IOException) {
            if (!isSubtitle && offset > 0) throw WebDavResumeOpenException(e)
            throw e
        }
        PlaybackTrace.response(traceId, request, response, offset, digestRetry = digestChallenge != null)
        return response
    }

    private fun closeAfterOpenFailure(cause: Exception) {
        val response = openedResponse
        resetState()
        if (response != null) closeWebDavResponse(response, cause)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = if (remaining < 0) length else minOf(remaining, length.toLong()).toInt()
        val read = inputStream?.read(buffer, offset, count) ?: return C.RESULT_END_OF_INPUT
        if (read > 0 && remaining > 0) remaining -= read
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = responseHeaders

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op: transfer events not supported
    }

    override fun close() {
        val response = openedResponse
        resetState()
        try {
            response?.body?.close()
        } catch (_: IOException) {
        }
    }

    private fun resetState() {
        openedResponse = null
        inputStream = null
        openedUri = null
        responseHeaders = emptyMap()
    }
}

/** 本次打开已被拒绝或没有取得正确范围，不允许加载器从旧提取器位置重试。 */
internal class WebDavResumeOpenException(cause: Throwable? = null) : IOException("WebDAV request could not resume", cause)

private fun closeWebDavResponse(response: Response, cause: Exception) {
    try {
        response.body?.close()
    } catch (e: Exception) {
        if (e !== cause) cause.addSuppressed(e)
    }
}
