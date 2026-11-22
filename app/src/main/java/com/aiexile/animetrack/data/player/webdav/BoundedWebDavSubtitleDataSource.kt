package com.aiexile.animetrack.data.player.webdav

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import okio.Buffer
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 在工作线程按实际字节数读取 WebDAV 外挂字幕。交给 Media3 时长度已知，避免它以 1 KiB
 * 为步长反复复制未知长度的大数组。主视频完全不经过这一层。
 */
@UnstableApi
internal class BoundedWebDavSubtitleDataSource(private val upstream: DataSource) : DataSource {
    private val bytes = Buffer()
    private var openedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        close()
        try {
            val remaining = try {
                readOnce(dataSpec)
            } catch (e: IOException) {
                if (!shouldRetry(e)) throw e
                bytes.clear()
                val delayMs = retryDelayMs(e)
                if (delayMs > MAX_RETRY_DELAY_MS) throw e
                try {
                    Thread.sleep(delayMs)
                } catch (cancelled: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException().apply { initCause(cancelled) }
                }
                readOnce(dataSpec)
            }
            openedUri = dataSpec.uri
            return remaining
        } catch (e: Exception) {
            close()
            if (e is WebDavSubtitleException || Thread.currentThread().isInterrupted) throw e
            throw WebDavSubtitleException(e)
        }
    }

    private fun readOnce(dataSpec: DataSpec): Long = try {
        val declaredLength = upstream.open(dataSpec)
        WebDavSubtitleBudget.readBounded(bytes, dataSpec.position, declaredLength) { scratch, length ->
            upstream.read(scratch, 0, length)
        }
    } finally {
        upstream.close()
    }

    private fun shouldRetry(error: IOException): Boolean {
        if (Thread.currentThread().isInterrupted || error is WebDavSubtitleException) return false
        return when (error) {
            is WebDavSubtitleBasicChallenge -> true
            is HttpDataSource.InvalidResponseCodeException -> error.responseCode in TRANSIENT_STATUS_CODES
            is SSLException, is UnknownHostException -> false
            is InterruptedIOException, is SocketException, is EOFException -> true
            else -> false
        }
    }

    private fun retryDelayMs(error: IOException): Long {
        val response = (error as? WebDavSubtitleBasicChallenge)?.httpError
            ?: error as? HttpDataSource.InvalidResponseCodeException ?: return DEFAULT_RETRY_DELAY_MS
        val retryAfter = response.headerFields.entries.firstOrNull { it.key.equals("Retry-After", true) }
            ?.value?.firstOrNull() ?: return DEFAULT_RETRY_DELAY_MS
        val seconds = retryAfter.trim().toLongOrNull()?.takeIf { it >= 0 }
            ?: return MAX_RETRY_DELAY_MS + 1 // 不早于服务器指定的日期重试。
        return if (seconds > MAX_RETRY_DELAY_MS / 1_000) MAX_RETRY_DELAY_MS + 1
            else maxOf(DEFAULT_RETRY_DELAY_MS, seconds * 1_000)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        if (length == 0) 0 else bytes.read(buffer, offset, length)

    override fun getUri(): Uri? = openedUri

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun close() {
        openedUri = null
        bytes.clear()
    }

    private companion object {
        const val DEFAULT_RETRY_DELAY_MS = 350L
        const val MAX_RETRY_DELAY_MS = 3_000L
        val TRANSIENT_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)
    }
}
