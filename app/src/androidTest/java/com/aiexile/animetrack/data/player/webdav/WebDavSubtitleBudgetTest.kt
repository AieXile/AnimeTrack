package com.aiexile.animetrack.data.player.webdav

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.atomic.AtomicInteger

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class WebDavSubtitleBudgetTest {
    @Test
    fun retriesTemporaryHttpErrorButDoesNotRetryMissingFile() {
        for (code in listOf(503, 404, 403)) {
            val requests = AtomicInteger()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val first = requests.getAndIncrement() == 0
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(if (first) code else 200).message("Test")
                    .body("test".toResponseBody()).build()
            }.build()
            val source = WebDAVDataSourceFactory(client, "", "").createDataSource(spec().uri)
            try {
                if (code == 503) {
                    assertEquals(4L, source.open(spec()))
                    assertEquals(2, requests.get())
                } else {
                    assertTrue(runCatching { source.open(spec()) }.exceptionOrNull() is WebDavSubtitleException)
                    assertEquals(1, requests.get())
                }
            } finally {
                source.close()
            }
        }
    }
    @Test
    fun temporaryBasic401RecoversSubtitleBytesButPermanent401RemainsVisible() {
        val script = "[Script Info]\nTitle: 字幕测试\n".toByteArray()
        for (recovers in listOf(true, false)) {
            val calls = mutableListOf<okhttp3.Request>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val req = chain.request()
                calls += req
                val reply = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).message("Test")
                if (recovers && calls.size == 2) reply.code(200).body(script.toResponseBody()).build()
                else reply.code(401).header("WWW-Authenticate", "Basic realm=\"test\"")
                    .body("not subtitle bytes".toResponseBody()).build()
            }.build()
            val spec = DataSpec(Uri.parse("https://example.invalid/season/EP01.ass?sig=abc%2Bdef"))
            val source = WebDAVDataSourceFactory(client, "user", "test").createDataSource(spec.uri)
            try {
                if (recovers) {
                    assertEquals(script.size.toLong(), source.open(spec))
                    val actual = ByteArray(script.size)
                    assertEquals(script.size, source.read(actual, 0, actual.size))
                    org.junit.Assert.assertArrayEquals(script, actual)
                    assertEquals(C.RESULT_END_OF_INPUT, source.read(actual, 0, actual.size))
                } else {
                    val failure = runCatching { source.open(spec) }.exceptionOrNull()
                    assertTrue(failure is WebDavSubtitleException)
                    assertEquals(401, generateSequence(failure) { it.cause }
                        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                        .single().responseCode)
                    assertNull(source.uri)
                }
                assertEquals(2, calls.size)
                assertTrue(calls.all { it.method == "GET" && it.url.toString() == spec.uri.toString() &&
                    it.header("Authorization") == okhttp3.Credentials.basic("user", "test") && it.header("Range") == null })
            } finally { source.close() }
        }
    }

    @Test
    fun basic401DoesNotReplayAnonymousOrRedirectedRequestsOrIgnoreRetryAfter() {
        for (case in listOf("anonymous", "changedOrigin", "redirectChain", "retryAfter", "noBasic")) {
            val requests = AtomicInteger()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                requests.incrementAndGet()
                val req = chain.request()
                val reply = Response.Builder().protocol(Protocol.HTTP_1_1).message("Denied").code(401)
                    .request(if (case == "changedOrigin") req.newBuilder().url("https://other.invalid/sub.ass").build() else req)
                    .header("WWW-Authenticate", if (case == "noBasic") "Bearer" else "Basic realm=\"test\"")
                if (case == "redirectChain") reply.priorResponse(Response.Builder().request(req)
                    .protocol(Protocol.HTTP_1_1).code(302).message("Moved").build())
                if (case == "retryAfter") reply.header("Retry-After", "60")
                reply.body("".toResponseBody()).build()
            }.build()
            val source = WebDAVDataSourceFactory(client, if (case == "anonymous") "" else "user", "test").createDataSource(spec().uri)
            try {
                assertTrue(runCatching { source.open(spec()) }.exceptionOrNull() is WebDavSubtitleException)
                assertEquals(case, 1, requests.get())
            } finally { source.close() }
        }
    }

    @Test
    fun rejectsOversizeFromHeaderBeforeReading() {
        val limit = WebDavSubtitleBudget.MAX_BYTES
        val upstream = CountingSource(actualLength = limit + 1, declaredLength = limit + 1)
        val source = BoundedWebDavSubtitleDataSource(upstream)
        assertTrue(runCatching { source.open(spec()) }.exceptionOrNull() is WebDavSubtitleException)
        assertEquals(0L, upstream.readCount)
        assertTrue(upstream.closed)
        assertNull(source.uri)
    }

    @Test
    fun stopsAtLimitEvenWhenActualLengthWasUnknownOrUnderreported() {
        val limit = WebDavSubtitleBudget.MAX_BYTES
        for (declared in listOf(C.LENGTH_UNSET.toLong(), 100L)) {
            val upstream = CountingSource(actualLength = limit + 128, declaredLength = declared)
            val source = BoundedWebDavSubtitleDataSource(upstream)
            assertTrue(runCatching { source.open(spec()) }.exceptionOrNull() is WebDavSubtitleException)
            assertEquals(limit + 1, upstream.readCount)
            assertTrue(upstream.closed)
            assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
        }
    }

    @Test
    fun exposesExactLengthForChunkedSubtitlesAndClosesUpstreamEarly() {
        val limit = WebDavSubtitleBudget.MAX_BYTES
        val upstream = CountingSource(actualLength = limit, declaredLength = C.LENGTH_UNSET.toLong())
        val source = BoundedWebDavSubtitleDataSource(upstream)
        try {
            assertEquals(limit, source.open(spec()))
            assertTrue(upstream.closed)
            val buffer = ByteArray(8 * 1024)
            var remaining = limit
            while (remaining > 0) {
                val count = source.read(buffer, 0, buffer.size)
                assertTrue(count > 0)
                remaining -= count
            }
            assertEquals(C.RESULT_END_OF_INPUT, source.read(buffer, 0, 1))
        } finally {
            source.close()
        }
    }

    private fun spec() = DataSpec(Uri.parse("https://example.invalid/season/EP01.ass"))

    private class CountingSource(private val actualLength: Long, private val declaredLength: Long) : DataSource {
        var readCount = 0L
            private set
        var closed = false
            private set

        override fun open(dataSpec: DataSpec) = declaredLength
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = minOf(length.toLong(), actualLength - readCount).toInt()
            if (count == 0) return C.RESULT_END_OF_INPUT
            readCount += count
            return count
        }
        override fun getUri(): Uri? = null
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun close() { closed = true }
    }
}
