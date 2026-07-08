package com.aiexile.animetrack.data.player

import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

class WebDAVDataSource(
    private val okHttpClient: OkHttpClient,
    private val username: String,
    private val password: String
) : DataSource {

    private var inputStream: InputStream? = null
    private var openedUri: Uri? = null
    private var contentLength: Long = C.LENGTH_UNSET.toLong()
    private var responseHeaders: Map<String, List<String>> = emptyMap()
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        openedUri = uri

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
            val call = okHttpClient.newCall(request)
            val response = call.execute()

            val statusCode = response.code
            when {
                statusCode == 401 || statusCode == 403 -> {
                    response.close()
                    throw IOException("Access denied: $statusCode for $uri")
                }
                statusCode in 300..399 -> {
                    response.close()
                    throw IOException("Redirect not followed: $statusCode for $uri")
                }
                statusCode !in 200..299 -> {
                    response.close()
                    throw IOException("Unexpected response: $statusCode for $uri")
                }
            }

            responseHeaders = response.headers.toMultimap()

            // Determine content length from response headers
            val contentRangeHeader = response.header("Content-Range")
            if (contentRangeHeader != null) {
                // Content-Range: bytes start-end/total
                val rangePart = contentRangeHeader.substringAfter("bytes ").trim()
                val totalPart = rangePart.substringAfter("/", "")
                if (totalPart.isNotEmpty() && totalPart != "*") {
                    contentLength = totalPart.toLong()
                }
                // bytes remaining in this partial response
                val dashIndex = rangePart.indexOf('-')
                val slashIndex = rangePart.indexOf('/')
                if (dashIndex > 0 && slashIndex > dashIndex) {
                    val end = rangePart.substring(dashIndex + 1, slashIndex).toLong()
                    val start = rangePart.substring(0, dashIndex).toLong()
                    bytesRemaining = end - start + 1
                } else {
                    bytesRemaining = response.body?.contentLength()?.takeIf { it > 0 }
                        ?: C.LENGTH_UNSET.toLong()
                }
            } else {
                contentLength = response.body?.contentLength()?.takeIf { it > 0 }
                    ?: C.LENGTH_UNSET.toLong()
                bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    dataSpec.length
                } else if (contentLength != C.LENGTH_UNSET.toLong()) {
                    contentLength - position
                } else {
                    C.LENGTH_UNSET.toLong()
                }
            }

            inputStream = response.body?.byteStream()
                ?: throw IOException("Empty response body for $uri")

            opened = true
            return bytesRemaining
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Failed to open $uri", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = inputStream ?: return C.RESULT_END_OF_INPUT
        val bytesRead = stream.read(buffer, offset, length)
        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }
        return bytesRead
    }

    override fun getUri(): Uri? {
        return openedUri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return responseHeaders
    }

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op: transfer events not supported
    }

    override fun close() {
        openedUri = null
        opened = false
        try {
            inputStream?.close()
        } catch (_: IOException) {
        }
        inputStream = null
    }
}
