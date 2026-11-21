package com.aiexile.animetrack.data.player.webdav

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@UnstableApi
@RunWith(AndroidJUnit4::class)
class WebDavDataSourceCleanupTest {
    @Test
    fun closesMalformedResponseAndCanOpenAgain() {
        val badBody = TrackingBody()
        val goodBody = TrackingBody()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val response = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(206).message("Partial Content")
            if (requests++ == 0) response.header("Content-Range", "bytes 5-8/invalid").body(badBody)
            else response.header("Content-Range", "bytes 5-8/9").body(goodBody)
            response.build()
        }.build()
        val source = WebDAVDataSource(client, "", "")
        val spec = DataSpec.Builder().setUri(Uri.parse("https://example.invalid/video.mkv"))
            .setPosition(5).build()

        try {
            assertTrue(runCatching { source.open(spec) }.exceptionOrNull() is IOException)
            assertTrue(badBody.closed)
            assertNull(source.uri)
            assertTrue(source.responseHeaders.isEmpty())
            assertEquals(4L, source.open(spec))
            assertFalse(goodBody.closed)
            assertEquals(4, source.read(ByteArray(4), 0, 4))
        } finally {
            source.close()
        }
        assertTrue(goodBody.closed)
        assertTrue(source.responseHeaders.isEmpty())
    }

    @Test
    fun closesRejectedResponseAndKeepsHttpStatus() {
        val body = TrackingBody()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(403).message("Forbidden").body(body).build()
        }.build()
        val source = WebDAVDataSource(client, "", "")
        val spec = DataSpec(Uri.parse("https://example.invalid/video.mkv"))
        try {
            val failure = runCatching { source.open(spec) }.exceptionOrNull()
            assertEquals(403, (failure as HttpDataSource.InvalidResponseCodeException).responseCode)
            assertTrue(body.closed)
        } finally {
            source.close()
        }
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
            private set
        private val buffer = object : ForwardingSource(Buffer().writeUtf8("data")) {
            override fun close() {
                super.close()
                closed = true
            }
        }.buffer()

        override fun contentType(): MediaType? = null
        override fun contentLength() = 4L
        override fun source() = buffer
    }
}
