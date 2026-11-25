package com.aiexile.animetrack.data.player.service

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.webdav.WebDAVDataSourceFactory
import com.aiexile.animetrack.data.player.webdav.WebDavSubtitleBudget
import com.aiexile.animetrack.di.AppContainer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@UnstableApi
@RunWith(AndroidJUnit4::class)
class WebDavSubtitlePlaybackTest {
    @Test
    fun firstVideoRequestAfterSettingsWarmupUsesDecryptedCredentials() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AppContainer.getSettingsRepository()
        val originalUrl = settings.playerWebdavUrl.first()
        val originalUser = settings.playerWebdavUsername.first()
        val originalPassword = settings.playerWebdavPassword.first()
        val payload = byteArrayOf(12, 34, 56, 78)
        val username = "fixture-user"
        val password = "fixture-password"
        FixtureServer(payload, Credentials.basic(username, password)).use { server ->
            try {
                settings.setPlayerWebdavCredentials(server.url, username, password)
                // 重建仓库并等待预热，复现「通用缓存已读到持久化密文」的冷启动时序。
                val coldSettings = SettingsRepository(context)
                coldSettings.awaitNetworkPreferences()
                assertEquals(password, coldSettings.playerWebdavPassword.first())

                val factory = PlaybackDataSourceFactory(context, coldSettings)
                for (position in listOf(0, 2)) {
                    val source = factory.createDataSource()
                    try {
                        val spec = DataSpec.Builder().setUri("${server.url}/EP01.mp4")
                            .setPosition(position.toLong()).build()
                        val expected = payload.copyOfRange(position, payload.size)
                        assertEquals(expected.size.toLong(), source.open(spec))
                        val received = ByteArray(expected.size)
                        var read = 0
                        while (read < received.size) {
                            val count = source.read(received, read, received.size - read)
                            assertTrue("Video response ended early", count > 0)
                            read += count
                        }
                        assertArrayEquals(expected, received)
                    } finally {
                        source.close()
                    }
                }
            } finally {
                settings.setPlayerWebdavCredentials(originalUrl, originalUser, originalPassword)
            }
        }
    }

    @Test
    fun subtitleStillLoadsWhenTransferTakesMoreThanEightSeconds() {
        FixtureServer(ByteArray(0)).use { server ->
            val uri = Uri.parse("${server.url}/slow.ass")
            val source = WebDAVDataSourceFactory(OkHttpClient(), "", "").createDataSource(uri)
            try {
                assertEquals(4L, source.open(DataSpec(uri)))
                val buffer = ByteArray(4)
                assertEquals(4, source.read(buffer, 0, buffer.size))
                assertEquals("test", buffer.toString(Charsets.US_ASCII))
            } finally {
                source.close()
            }
        }
    }

    @Test
    fun oversizedChunkedSubtitleCannotStopVideoPlayback() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pixels = instrumentation.context.assets.open("webdav-budget-video.mp4").use { it.readBytes() }
        val settings = AppContainer.getSettingsRepository()
        val originalUrl = settings.playerWebdavUrl.first()
        val originalUser = settings.playerWebdavUsername.first()
        val originalPassword = settings.playerWebdavPassword.first()
        val skipped = CountDownLatch(1)
        FixtureServer(pixels).use { server ->
            settings.setPlayerWebdavCredentials(server.url, "", "")
            var future: ListenableFuture<MediaController>? = null
            try {
                instrumentation.runOnMainSync {
                    future = MediaController.Builder(context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)))
                        .setApplicationLooper(Looper.getMainLooper())
                        .setListener(object : MediaController.Listener {
                            override fun onCustomCommand(controller: MediaController, command: SessionCommand,
                                args: Bundle): ListenableFuture<SessionResult> {
                                if (command.customAction == PlaybackSessionCommands.ACTION_WEBDAV_SUBTITLE_SKIPPED &&
                                    args.getString(PlaybackSessionCommands.EXTRA_MEDIA_ID) == MEDIA_ID
                                ) skipped.countDown()
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                        }).buildAsync()
                }
                val player = checkNotNull(future).get(15, TimeUnit.SECONDS)
                instrumentation.runOnMainSync {
                    val tracks = listOf("EP01.ass", "EP01.alt.ass").mapIndexed { index, name ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse("${server.url}/$name"))
                            .setId((WebDavSubtitleBudget.TRACK_ID_BASE + index).toString())
                            .setMimeType("text/x-ssa")
                            .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    }
                    player.setMediaItem(MediaItem.Builder().setMediaId(MEDIA_ID)
                        .setUri("${server.url}/EP01.mp4").setSubtitleConfigurations(tracks).build())
                    player.prepare()
                    player.play()
                }
                assertTrue("Subtitle fallback was not triggered", skipped.await(20, TimeUnit.SECONDS))
                withTimeout(15_000) {
                    while (true) {
                        var hasProgress = false
                        instrumentation.runOnMainSync {
                            hasProgress = player.playerError == null && player.currentPosition >= 500L
                        }
                        if (hasProgress) break
                        delay(50)
                    }
                }
                assertEquals(1, server.largeSubtitleRequests.get())
                assertEquals(0, server.unselectedSubtitleRequests.get())
            } finally {
                instrumentation.runOnMainSync { future?.let(MediaController::releaseFuture) }
                settings.setPlayerWebdavCredentials(originalUrl, originalUser, originalPassword)
            }
        }
    }

    /** 仅回环地址，合成视频以及不报长度的超限字幕，不依赖 NAS 或网络服务。 */
    private class FixtureServer(
        private val video: ByteArray,
        private val videoAuthorization: String? = null
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}"
        val largeSubtitleRequests = AtomicInteger()
        val unselectedSubtitleRequests = AtomicInteger()
        private val acceptThread = thread(name = "webdav-fixture", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: java.io.IOException) { break }
                thread(name = "webdav-fixture-response", isDaemon = true) {
                    client.use { runCatching { serve(it) } }
                }
            }
        }

        private fun serve(client: Socket) {
            client.soTimeout = 10_000
            val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
            val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return
            val headers = readHeaders(reader)
            val offset = headers["Range"]?.substringAfter('=')?.substringBefore('-')
                ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val output = client.getOutputStream().buffered()
            when (path) {
                "/EP01.mp4" -> {
                    if (videoAuthorization != null && headers["Authorization"] != videoAuthorization) {
                        output.header("401 Unauthorized", "WWW-Authenticate: Basic realm=\"fixture\"\r\nContent-Length: 0\r\n")
                    } else if (offset >= video.size) {
                        output.header("416 Range Not Satisfiable", "Content-Length: 0\r\n")
                    } else {
                        val length = video.size - offset
                        val range = if (offset > 0) "Content-Range: bytes $offset-${video.lastIndex}/${video.size}\r\n" else ""
                        output.header(if (offset > 0) "206 Partial Content" else "200 OK",
                            "Content-Type: video/mp4\r\nContent-Length: $length\r\n$range")
                        output.write(video, offset, length)
                    }
                }
                "/slow.ass" -> {
                    output.header("200 OK", "Content-Length: 4\r\n")
                    output.write("te".toByteArray(Charsets.US_ASCII))
                    output.flush()
                    Thread.sleep(8_500)
                    output.write("st".toByteArray(Charsets.US_ASCII))
                }
                "/EP01.ass" -> {
                    largeSubtitleRequests.incrementAndGet()
                    output.header("200 OK", "Transfer-Encoding: chunked\r\n")
                    val block = ByteArray(16 * 1024) { ' '.code.toByte() }
                    repeat((WebDavSubtitleBudget.MAX_BYTES / block.size).toInt() + 1) {
                        output.write("4000\r\n".toByteArray(Charsets.US_ASCII))
                        output.write(block)
                        output.write("\r\n".toByteArray(Charsets.US_ASCII))
                    }
                    output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                }
                else -> {
                    if (path == "/EP01.alt.ass") unselectedSubtitleRequests.incrementAndGet()
                    output.header("404 Not Found", "Content-Length: 0\r\n")
                }
            }
            output.flush()
        }

        private fun readHeaders(reader: BufferedReader): Map<String, String> {
            val headers = java.util.TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
            while (true) {
                val line = reader.readLine()?.takeIf { it.isNotEmpty() } ?: return headers
                val separator = line.indexOf(':')
                if (separator > 0) headers[line.substring(0, separator)] = line.substring(separator + 1).trim()
            }
        }

        private fun OutputStream.header(status: String, headers: String) {
            write("HTTP/1.1 $status\r\nConnection: close\r\n$headers\r\n".toByteArray(Charsets.US_ASCII))
        }

        override fun close() {
            socket.close()
            acceptThread.join(1_000)
        }
    }

    private companion object {
        const val MEDIA_ID = "webdav:budget-regression"
    }
}
