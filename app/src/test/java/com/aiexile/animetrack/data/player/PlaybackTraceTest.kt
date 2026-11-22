package com.aiexile.animetrack.data.player

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

/** 直接断言完整日志载荷，新增任意自由文本字段都需要重新审视。 */
class PlaybackTraceTest {
    @Test
    fun redirectedSubtitleChallengeOnlyExposesSafeFields() {
        val original = Request.Builder().url("https://user:secret@media.invalid/private.ass?token=private%2Fvalue")
            .header("Authorization", "Basic secret").build()
        val redirected = Request.Builder().url("https://cdn.invalid/private-target").build()
        val prior = Response.Builder().request(original).protocol(Protocol.HTTP_1_1).code(302).message("Found").build()
        val response = Response.Builder().request(redirected).priorResponse(prior)
            .protocol(Protocol.HTTP_1_1).code(401).message("private reason")
            .header("WWW-Authenticate", "Digest realm=\"secret realm\", nonce=\"secret nonce\", qop=\"auth-int\", algorithm=SHA-256")
            .build()
        assertEquals("dav.response req=1 kind=subtitle offset=0 status=401 start=-1 rangeSent=false " +
            "digestRetry=false sent=none challenge=digest:sha-256:auth-int redirects=1 sameOrigin=false " +
            "targetChanged=true queryEncodingDiffers=true", PlaybackTrace.responseSummary(1, original, response, 0, false))
    }

    @Test
    fun partialResponseShowsActualBeginningEvenWhenItDiffersFromRequest() {
        val request = Request.Builder().url("https://media.invalid/private.mkv").header("Range", "bytes=80-")
            .header("Authorization", "Digest username=\"private\"").build()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(206).message("Partial")
            .header("Content-Range", "bytes 20-99/100").build()
        assertEquals("dav.response req=2 kind=media offset=80 status=206 start=20 rangeSent=true " +
            "digestRetry=true sent=digest challenge=none redirects=0 sameOrigin=true targetChanged=false queryEncodingDiffers=false",
            PlaybackTrace.responseSummary(2, request, response, 80, true))
    }
}
