package com.aiexile.animetrack.data.player

import android.os.SystemClock
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.domain.playback.SubtitleSpec
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/** 调试包复现用：只记事件、数值和白名单枚举，禁止输出媒体身份、URL、质询或凭据原文。 */
internal object PlaybackTrace {
    private val requests = AtomicLong()
    private val rangeStart = Regex("(?i)^bytes\\s+(\\d+)-\\d+/(?:\\d+|\\*)$")

    inline fun event(message: () -> String) {
        if (BuildConfig.DEBUG) AppLogManager.d("PlaybackTrace", "t=${SystemClock.elapsedRealtime()} ${message()}")
    }

    fun nextRequest(): Long = if (BuildConfig.DEBUG) requests.incrementAndGet() else 0L

    fun response(id: Long, original: Request, response: Response, offset: Long, digestRetry: Boolean) = event {
        responseSummary(id, original, response, offset, digestRetry)
    }

    internal fun responseSummary(id: Long, original: Request, response: Response, offset: Long, digestRetry: Boolean): String {
        val url = original.url
        val final = response.request.url
        val redirects = generateSequence(response.priorResponse) { it.priorResponse }.count { it.isRedirect }
        val kind = if (SubtitleSpec.isSupported(url.pathSegments.lastOrNull().orEmpty())) "subtitle" else "media"
        val sent = when (response.request.header("Authorization")?.substringBefore(' ')?.lowercase()) {
            null -> "none"
            "basic" -> "basic"
            "digest" -> "digest"
            else -> "other"
        }
        val challenges = response.challenges().take(4).joinToString("+") { c ->
            when {
                c.scheme.equals("basic", true) -> "basic"
                c.scheme.equals("digest", true) -> {
                    val algorithm = when (c.authParams["algorithm"]?.lowercase()) {
                        null, "md5" -> "md5"
                        "md5-sess" -> "md5-sess"
                        "sha-256" -> "sha-256"
                        else -> "other"
                    }
                    val offered = c.authParams["qop"]?.split(',')?.map { it.trim().lowercase() }
                    val qop = when {
                        offered == null -> "absent"
                        "auth" in offered && "auth-int" in offered -> "both"
                        "auth" in offered -> "auth"
                        "auth-int" in offered -> "auth-int"
                        else -> "other"
                    }
                    "digest:$algorithm:$qop"
                }
                else -> "other"
            }
        }.ifEmpty { "none" }
        val start = response.header("Content-Range")?.let { rangeStart.matchEntire(it.trim())?.groupValues?.get(1)?.toLongOrNull() }
        return "dav.response req=$id kind=$kind offset=$offset status=${response.code} start=${start ?: -1} " +
            "rangeSent=${original.header("Range") != null} digestRetry=$digestRetry sent=$sent challenge=$challenges " +
            "redirects=$redirects sameOrigin=${url.scheme == final.scheme && url.host == final.host && url.port == final.port} " +
            "targetChanged=${url != final} queryEncodingDiffers=${url.query != url.encodedQuery}"
    }
}
