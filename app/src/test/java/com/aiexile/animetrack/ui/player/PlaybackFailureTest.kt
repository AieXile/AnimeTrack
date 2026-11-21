package com.aiexile.animetrack.ui.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

@UnstableApi
class PlaybackFailureTest {
    @Test
    fun mapsHttpStatusInsteadOfGenericSourceError() {
        val cases = mapOf(
            401 to PlaybackFailure(PlaybackFailureKind.HTTP_UNAUTHORIZED),
            403 to PlaybackFailure(PlaybackFailureKind.HTTP_FORBIDDEN),
            404 to PlaybackFailure(PlaybackFailureKind.HTTP_NOT_FOUND),
            410 to PlaybackFailure(PlaybackFailureKind.HTTP_EXPIRED),
            429 to PlaybackFailure(PlaybackFailureKind.HTTP_RATE_LIMITED),
            503 to PlaybackFailure(PlaybackFailureKind.HTTP_SERVER, 503),
            302 to PlaybackFailure(PlaybackFailureKind.HTTP_REDIRECT, 302),
            416 to PlaybackFailure(PlaybackFailureKind.HTTP_OTHER, 416)
        )
        cases.forEach { (status, expected) ->
            assertEquals(expected, PlaybackFailureResolver.fromHttp(status))
        }
    }

    @Test
    fun sessionMessageRetainsSafeClassificationWhenTheCauseIsLost() {
        val result = PlaybackFailureResolver.decodeSessionFailure(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, "AnimeTrackPlaybackFailure:HTTP_SERVER:503", null
        )
        assertEquals(PlaybackFailure(PlaybackFailureKind.HTTP_SERVER, 503), result)
        assertEquals(
            PlaybackFailure(PlaybackFailureKind.UNKNOWN),
            PlaybackFailureResolver.decodeSessionFailure(PlaybackException.ERROR_CODE_UNSPECIFIED, "Source error", null)
        )
    }

    @Test
    fun nestedNetworkAndLocalFailuresStayDistinct() {
        val cases = listOf(
            SocketTimeoutException() to PlaybackFailureKind.TIMEOUT,
            UnknownHostException() to PlaybackFailureKind.DNS,
            ConnectException() to PlaybackFailureKind.CONNECTION,
            SSLHandshakeException("bad certificate") to PlaybackFailureKind.TLS,
            FileNotFoundException() to PlaybackFailureKind.FILE_NOT_FOUND,
            FileNotFoundException().apply { initCause(SecurityException()) } to PlaybackFailureKind.FILE_PERMISSION
        )
        cases.forEach { (error, expected) ->
            assertEquals(expected, PlaybackFailureResolver.classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, IOException(error)).kind)
        }
    }

    @Test
    fun media3CodesDescribeFormatDecoderAndUnknownFailures() {
        val cases = mapOf(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to PlaybackFailureKind.UNSUPPORTED_FORMAT,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED to PlaybackFailureKind.MALFORMED_MEDIA,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED to PlaybackFailureKind.DECODER,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS to PlaybackFailureKind.HTTP_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED to PlaybackFailureKind.DRM,
            PlaybackException.ERROR_CODE_UNSPECIFIED to PlaybackFailureKind.UNKNOWN
        )
        cases.forEach { (code, expected) -> assertEquals(expected, PlaybackFailureResolver.classify(code, null).kind) }
    }
}
