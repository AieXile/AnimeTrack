package com.aiexile.animetrack.data.player.webdav

import com.sun.net.httpserver.HttpServer
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
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

        val pending = async { PlayerWebDavDirectory.readDirectory(client, DIRECTORY) }
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

        val failure = runCatching { PlayerWebDavDirectory.readDirectory(client, DIRECTORY) }.exceptionOrNull()
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

        val resource = PlayerWebDavDirectory.readDirectory(client, DIRECTORY).single()
        assertEquals("EP01.chs.ass", resource.displayName)
        assertEquals(128L, resource.contentLength)
    }

    @Test
    fun `连接探测对草稿媒体根做 Depth 0 并在同目标处理 Basic`() = runBlocking {
        val requests = java.util.concurrent.ConcurrentLinkedQueue<Triple<String, String?, String?>>()
        loopback { server, url ->
            server.createContext("/media/") { exchange ->
                val authorization = exchange.requestHeaders.getFirst("Authorization")
                requests += Triple(exchange.requestMethod, exchange.requestHeaders.getFirst("Depth"), authorization)
                exchange.requestBody.close()
                if (authorization == null) {
                    exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"nas\"")
                    exchange.sendResponseHeaders(401, -1)
                } else {
                    val xml = ROOT_LISTING.toByteArray()
                    exchange.sendResponseHeaders(207, xml.size.toLong())
                    exchange.responseBody.use { it.write(xml) }
                }
                exchange.close()
            }
            val draft = checkNotNull(PlayerWebDavDirectory.Config.parse(url, "/media", "user", "test", false))
            PlayerWebDavDirectory.probe(draft)
        }
        assertEquals(listOf(Triple("PROPFIND", "0", null), Triple("PROPFIND", "0", "Basic dXNlcjp0ZXN0")), requests.toList())
    }

    @Test
    fun `认证请求不会跟随重定向到其他目标`() = runBlocking {
        val redirectedRequests = java.util.concurrent.atomic.AtomicInteger()
        loopback { target, targetUrl ->
            target.createContext("/") { exchange ->
                redirectedRequests.incrementAndGet()
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
            }
            loopback { server, url ->
                server.createContext("/media/") { exchange ->
                    exchange.requestBody.close()
                    if (exchange.requestHeaders.getFirst("Authorization") == null) {
                        exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"nas\"")
                        exchange.sendResponseHeaders(401, -1)
                    } else {
                        exchange.responseHeaders.add("Location", targetUrl)
                        exchange.sendResponseHeaders(307, -1)
                    }
                    exchange.close()
                }
                val draft = checkNotNull(PlayerWebDavDirectory.Config.parse(url, "/media", "user", "test", false))
                val failure = runCatching { PlayerWebDavDirectory.probe(draft) }.exceptionOrNull()
                assertEquals(307, (failure as? PlayerWebDavDirectory.HttpFailure)?.status)
            }
        }
        assertEquals(0, redirectedRequests.get())
    }

    @Test
    fun `缓存不会跨账号密码媒体根或证书设置复用`() {
        fun config(root: String = "", user: String = "u", password: String = "p", trust: Boolean = false) =
            checkNotNull(PlayerWebDavDirectory.Config.parse(DIRECTORY, root, user, password, trust))
        val saved = config()
        val resources = directoryEntries(listOf("/season/EP01.ass" to 120L))
        try {
            WebDavDirectoryCache.put(saved, saved.root, resources)
            assertEquals(resources, WebDavDirectoryCache.get(config(), saved.root))
            assertNull(WebDavDirectoryCache.get(config(user = "other"), saved.root))
            assertNull(WebDavDirectoryCache.get(config(password = "new"), saved.root))
            assertNull(WebDavDirectoryCache.get(config(trust = true), saved.root))
            assertNull(WebDavDirectoryCache.get(config(root = "/"), saved.root))
            assertNull(PlayerWebDavDirectory.Config.parse(DIRECTORY, "http://example.invalid/season", "", "", false))
            assertNull(PlayerWebDavDirectory.Config.parse(DIRECTORY, "https://other.invalid/season", "", "", false))
        } finally {
            WebDavDirectoryCache.clear()
        }
    }

    private suspend fun loopback(test: suspend (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            test(server, "http://127.0.0.1:${server.address.port}/")
        } finally {
            server.stop(0)
        }
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

    @Test
    fun `手选保留目录视频但逐集导航不跳到混合番剧跨季合集或有歧义的版本`() {
        val server = checkNotNull(WebDavPlaybackIdentity.directory(DIRECTORY))
        val selected = checkNotNull(WebDavPlaybackIdentity.target(server, server, "user", "Show.S01E02.mkv"))
        val resources = directoryEntries(listOf(
            "Show.S01E01.mkv", "Show.S01E02.mp4", "Show.S01E03.mkv", "Show.S01E03.mp4",
            "Show.S01E04.mkv", "Other.S01E05.mkv", "Show.S02E06.mkv",
            "Show.S01E07-E08.mkv", "unmarked.mkv"
        ).map { "/season/" + it to 120L })
        val queue = WebDavDirectoryQueue.build(server, server, "user", selected, null, resources)
        assertEquals(10, queue.size)
        val current = queue.indexOfFirst { it.target == selected }
        val previous = WebDavDirectoryQueue.adjacentIndex(queue, current, forward = false)
        assertEquals("Show.S01E01.mkv", previous?.let { queue[it].target.title })
        assertEquals(null, WebDavDirectoryQueue.adjacentIndex(queue, current, forward = true))
        val fourth = queue.indexOfFirst { it.target.title == "Show.S01E04.mkv" }
        assertEquals(null, WebDavDirectoryQueue.adjacentIndex(queue, fourth, forward = true))
        assertEquals(null, queue.first { it.target.title == "Show.S01E07-E08.mkv" }.number)

        val unmarked = checkNotNull(WebDavPlaybackIdentity.target(server, server, "user", "unmarked.mkv"))
        val manual = WebDavDirectoryQueue.build(server, server, "user", unmarked, null, resources)
        assertEquals(9, manual.size)
        val unmarkedIndex = manual.indexOfFirst { it.target == unmarked }
        assertEquals(null, manual[unmarkedIndex].number)
        assertEquals(null, WebDavDirectoryQueue.adjacentIndex(manual, unmarkedIndex, forward = true))
    }

    @Test
    fun `同目录括号短文件名和显式集号可组成选集`() {
        val server = checkNotNull(WebDavPlaybackIdentity.directory(DIRECTORY))
        val selected = checkNotNull(WebDavPlaybackIdentity.target(server, server, "user", "Show/S02/%5B01%5D.mkv"))
        val resources = directoryEntries(listOf(
            "/season/Show/S02/Episode%2002.mkv" to 120L,
            "/season/Show/S02/unmarked.mkv" to 120L
        ))
        val queue = WebDavDirectoryQueue.build(server, server, "user", selected, null, resources)
        assertEquals(listOf(1, 2, null), queue.map { it.number })
        assertEquals(1, WebDavDirectoryQueue.adjacentIndex(queue, 0, forward = true))
    }

    @Test
    fun `包含特典字样的 WebDAV 合集仍可识别 DBD Raws 正片集号`() {
        val server = checkNotNull(WebDavPlaybackIdentity.directory(DIRECTORY))
        fun path(episode: String) =
            "/season/Grand%20Blue%20%2B%20%E7%89%B9%E5%85%B8/" +
                "%5BDBD-Raws%5D%5BGrand%20Blue%5D%5B$episode%5D%5B1080P%5D%5BBDRip%5D%5BHEVC-10bit%5D%5BFLACx2%5D.mkv"
        val selected = checkNotNull(WebDavPlaybackIdentity.target(server, server, "user", path("01")))
        val queue = WebDavDirectoryQueue.build(server, server, "user", selected, null,
            directoryEntries(listOf(path("01") to 120L, path("02") to 120L)))
        assertEquals(listOf(1, 2), queue.map { it.number })
        assertEquals(1, WebDavDirectoryQueue.adjacentIndex(queue, 0, forward = true))
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
        const val ROOT_LISTING = "<d:multistatus xmlns:d=\"DAV:\"><d:response>" +
            "<d:href>/media/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/>" +
            "</d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"
    }
}
