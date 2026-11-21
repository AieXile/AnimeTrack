package com.aiexile.animetrack.data.player.webdav

import com.thegrizzlylabs.sardineandroid.impl.handler.ResourcesResponseHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WebDavSubtitleDirectoryTest {
    @Test
    fun `取消字幕目录查询会取消正在执行的网络请求`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            })
            .addInterceptor {
                started.complete(Unit)
                check(cancelled.await(5, TimeUnit.SECONDS))
                throw IOException("Call cancelled")
            }
            .build()

        val pending = async { WebDavSubtitleLocator.readDirectory(client, DIRECTORY) }
        withTimeout(5_000) { started.await() }
        pending.cancelAndJoin()
        assertEquals(0L, cancelled.count)
    }

    @Test
    fun `目录请求失败后关闭响应体`() = runBlocking {
        val bodyClosed = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("PROPFIND", chain.request().method)
            assertEquals("1", chain.request().header("Depth"))
            val source = object : ForwardingSource(Buffer().writeUtf8("unavailable")) {
                override fun close() {
                    super.close()
                    bodyClosed.countDown()
                }
            }.buffer()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(503).message("Unavailable")
                .body(object : okhttp3.ResponseBody() {
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun contentLength() = 11L
                    override fun source() = source
                }).build()
        }.build()

        val failure = runCatching { WebDavSubtitleLocator.readDirectory(client, DIRECTORY) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(0L, bodyClosed.count)
    }

    @Test
    fun `目录响应保留字幕文件名和大小`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val xml = """
                <d:multistatus xmlns:d="DAV:"><d:response>
                  <d:href>/season/EP01.chs.ass</d:href><d:propstat><d:prop>
                    <d:displayname>EP01.chs.ass</d:displayname><d:getcontentlength>128</d:getcontentlength>
                    <d:resourcetype/>
                  </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                </d:response></d:multistatus>
            """.trimIndent()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(207).message("Multi-Status").body(xml.toResponseBody()).build()
        }.build()

        val resource = WebDavSubtitleLocator.readDirectory(client, DIRECTORY).single()
        assertEquals("EP01.chs.ass", resource.displayName)
        assertEquals(128L, resource.contentLength)
    }

    @Test
    fun `24集合集只挂当前集且同集轨道数有上限`() {
        val entries = (1..24).map { "/season/EP${it.toString().padStart(2, '0')}.ass" to 120L }
        val resources = directoryEntries(entries + listOf(
            "/season/EP01.chs.ass" to 120L, "/season/EP01.cht.ass" to 120L,
            "/season/EP01.en.ass" to 120L, "/season/EP01.chs.ass" to 120L,
            "/season/EP012.ass" to 120L,
            "/season/EP01.large.ass" to WebDavSubtitleBudget.MAX_BYTES + 1,
            "/season/other/EP01.ass" to 240L,
            "https://other.invalid/season/EP01.ass" to 240L
        ))
        val selection = WebDavSubtitleLocator.selectCandidates("${DIRECTORY}EP01.mkv", resources)
        assertTrue(selection.skippedOversized)
        assertEquals(listOf("EP01.ass", "EP01.chs.ass", "EP01.cht.ass"), selection.candidates.map { it.name })
        assertTrue(selection.candidates.all { it.url.startsWith(DIRECTORY) })
    }

    @Test
    fun `大小未知仍可候选且保留 NAS 原始编码地址`() {
        val basename = "%E7%AC%AC%201%20%E9%9B%86"
        val result = WebDavSubtitleLocator.selectCandidates("$DIRECTORY$basename.mkv",
            directoryEntries(listOf("/season/$basename.chs.ass" to null)))
        assertEquals("$DIRECTORY$basename.chs.ass", result.candidates.single().url)
    }

    @Test
    fun `目录视频按集数排序且不把字幕子目录或其他主机加进队列`() {
        val server = checkNotNull(WebDavPlaybackIdentity.directory(DIRECTORY))
        val selected = checkNotNull(WebDavPlaybackIdentity.target(server, server, "user", "Show.E02.mkv"))
        val resources = directoryEntries(listOf(
            "/season/Show.E10.mkv", "/season/Show.E02.mkv", "/season/Show.E01.mkv",
            "/season/Show.E02.ass", "/season/nested/Show.E03.mkv", "https://other.invalid/season/Show.E04.mkv"
        ).map { it to 120L })
        val queue = WebDavDirectoryQueue.build(server, server, "user", selected, "选中的视频", resources)
        assertEquals(listOf("Show.E01.mkv", "Show.E02.mkv", "Show.E10.mkv"), queue.map { it.target.title })
        assertEquals(listOf(1, 2, 10), queue.map { it.number })
        assertEquals(selected.mediaKey, queue[1].target.mediaKey)
        assertEquals("选中的视频", queue[1].title)
    }

    private fun directoryEntries(entries: List<Pair<String, Long?>>) = Response.Builder()
        .request(okhttp3.Request.Builder().url(DIRECTORY).build())
        .protocol(Protocol.HTTP_1_1).code(207).message("Multi-Status")
        .body(buildString {
            append("<d:multistatus xmlns:d=\"DAV:\">")
            for ((path, bytes) in entries) {
                append("<d:response><d:href>$path</d:href><d:propstat><d:prop><d:resourcetype/>")
                if (bytes != null) append("<d:getcontentlength>$bytes</d:getcontentlength>")
                append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>")
            }
            append("</d:multistatus>")
        }.toResponseBody()).build().use { ResourcesResponseHandler().handleResponse(it) }

    private companion object {
        const val DIRECTORY = "https://example.invalid/season/"
    }
}
