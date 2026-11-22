package com.aiexile.animetrack.ui.player

import android.content.res.Configuration
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Locale

/** 在真实 Android 上验证 MediaSession 的异常序列化与三种界面语言。 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackFailureRenderingTest {
    @Test
    fun preservesHttpStatusWithoutSendingExceptionDetailsToTheController() {
        val spec = DataSpec(Uri.parse("https://example.invalid/video.mp4?token=hidden"))
        val cause = HttpDataSource.InvalidResponseCodeException(403, null, null, emptyMap(), spec, ByteArray(0))
        val original = PlaybackException("Source error", IOException(cause), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        val exported = PlaybackFailureResolver.forSession(original)
        val copied = PlaybackException.fromBundle(exported.toBundle())
        val failure = PlaybackFailureResolver.fromSession(copied)
        assertEquals(PlaybackFailureKind.HTTP_FORBIDDEN, failure.kind)
        assertFalse(copied.message.orEmpty().contains("token"))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val messages = listOf("zh-CN", "zh-TW", "en").map { tag ->
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            context.createConfigurationContext(config).getString(failure.kind.messageRes)
        }
        assertTrue(messages[0].contains("服务器拒绝访问"))
        assertTrue(messages[1].contains("伺服器拒絕存取"))
        assertTrue(messages[2].contains("The server denied access"))
        assertTrue(messages.all { "403" in it && "Source error" !in it })
    }
}
