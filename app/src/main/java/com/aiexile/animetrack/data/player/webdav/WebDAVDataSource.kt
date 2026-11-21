package com.aiexile.animetrack.data.player.webdav

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
    private val password: String
) : DataSource {

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
        if (position > 0) {
            val endPosition = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                position + dataSpec.length - 1
            } else {
                -1L
            }
            val rangeHeader = if (endPosition > 0) {
                "bytes=$position-$endPosition"
            } else {
                "bytes=$position-"
            }
            requestBuilder.header("Range", rangeHeader)
        }

        val request = requestBuilder.build()

        try {
            val response = executeRequest(request)
            openedResponse = response
            val statusCode = response.code
            if (statusCode !in 200..299) {
                throw HttpDataSource.InvalidResponseCodeException(
                    statusCode, response.message, null, response.headers.toMultimap(), dataSpec, ByteArray(0)
                )
            }

            val body = response.body ?: throw IOException("Empty response body for $uri")
            val contentRange = response.header("Content-Range")
            val length = if (contentRange != null) {
                // Content-Range: bytes start-end/total
                val rangePart = contentRange.substringAfter("bytes ").trim()
                val total = rangePart.substringAfter('/', "")
                if (total.isNotEmpty() && total != "*" && total.toLongOrNull() == null) {
                    throw IOException("Invalid Content-Range total length")
                }
                val dash = rangePart.indexOf('-')
                val slash = rangePart.indexOf('/')
                if (dash > 0 && slash > dash) {
                    rangePart.substring(dash + 1, slash).toLong() - rangePart.substring(0, dash).toLong() + 1
                } else {
                    body.contentLength().takeIf { it > 0 } ?: C.LENGTH_UNSET.toLong()
                }
            } else {
                when {
                    dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                    body.contentLength() > 0 -> body.contentLength() - position
                    else -> C.LENGTH_UNSET.toLong()
                }
            }
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

    private fun executeRequest(request: Request): Response {
        var response = okHttpClient.newCall(request).execute()
        if (response.code != 401 || username.isEmpty()) return response
        val challenges = response.headers.values("WWW-Authenticate")
        val digestChallenge = challenges.firstOrNull { it.trim().startsWith("Digest", ignoreCase = true) }
        if (digestChallenge == null) return response
        val digestHeader = DigestAuth.buildAuthorization(
            method = request.method,
            uri = request.url.encodedPath + (request.url.query?.let { "?$it" } ?: ""),
            challenge = digestChallenge,
            username = username,
            password = password
        ) ?: return response
        response.close()
        response = okHttpClient.newCall(request.newBuilder().header("Authorization", digestHeader).build()).execute()
        return response
    }

    private fun closeAfterOpenFailure(cause: Exception) {
        val response = openedResponse
        resetState()
        if (response != null) closeWebDavResponse(response, cause)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        inputStream?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

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

private fun closeWebDavResponse(response: Response, cause: Exception) {
    try {
        response.body?.close()
    } catch (e: Exception) {
        if (e !== cause) cause.addSuppressed(e)
    }
}
