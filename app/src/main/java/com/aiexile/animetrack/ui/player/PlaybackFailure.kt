package com.aiexile.animetrack.ui.player

import androidx.annotation.StringRes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.aiexile.animetrack.R
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/** 可跨会话传输的失败类别；界面用当前语言渲染，不展示上游异常原文。 */
enum class PlaybackFailureKind(@param:StringRes @get:StringRes val messageRes: Int) {
    HTTP_UNAUTHORIZED(R.string.player_failure_http_unauthorized),
    HTTP_FORBIDDEN(R.string.player_failure_http_forbidden),
    HTTP_NOT_FOUND(R.string.player_failure_http_not_found),
    HTTP_EXPIRED(R.string.player_failure_http_expired),
    HTTP_RATE_LIMITED(R.string.player_failure_http_rate_limited),
    HTTP_SERVER(R.string.player_failure_http_server),
    HTTP_OTHER(R.string.player_failure_http_other),
    HTTP_UNSPECIFIED(R.string.player_failure_http_unspecified),
    HTTP_REDIRECT(R.string.player_failure_http_redirect),
    DNS(R.string.player_failure_dns),
    CONNECTION(R.string.player_failure_connection),
    TIMEOUT(R.string.player_failure_timeout),
    TLS(R.string.player_failure_tls),
    CLEARTEXT(R.string.player_failure_cleartext),
    FILE_NOT_FOUND(R.string.player_failure_file_not_found),
    FILE_PERMISSION(R.string.player_failure_file_permission),
    UNSUPPORTED_FORMAT(R.string.player_failure_unsupported_format),
    MALFORMED_MEDIA(R.string.player_failure_malformed_media),
    DECODER(R.string.player_failure_decoder),
    DRM(R.string.player_failure_drm),
    INVALID_ADDRESS(R.string.player_failure_invalid_address),
    WEBDAV_PREPARE(R.string.player_failure_webdav_prepare),
    NO_LOCAL_MEDIA(R.string.player_failure_no_local_media),
    SERVICE(R.string.player_failure_service),
    RESOLUTION_CANCELLED(R.string.player_resolve_cancelled),
    IO(R.string.player_failure_io),
    UNKNOWN(R.string.player_failure_unknown)
}

data class PlaybackFailure(val kind: PlaybackFailureKind, val httpStatus: Int? = null)

@UnstableApi
internal object PlaybackFailureResolver {
    private const val SESSION_PREFIX = "AnimeTrackPlaybackFailure:"

    /** MediaSession 只保证传输标准异常字段，发送安全类别而不是原始 URL 或异常链。 */
    fun forSession(error: PlaybackException): PlaybackException {
        val failure = classify(error.errorCode, error.cause)
        val message = "$SESSION_PREFIX${failure.kind.name}:${failure.httpStatus ?: 0}"
        return PlaybackException(message, null, error.errorCode)
    }

    fun fromSession(error: PlaybackException): PlaybackFailure =
        decodeSessionFailure(error.errorCode, error.message, error.cause)

    internal fun decodeSessionFailure(errorCode: Int, message: String?, cause: Throwable?): PlaybackFailure {
        if (message != null && message.startsWith(SESSION_PREFIX)) {
            val payload = message.removePrefix(SESSION_PREFIX)
            val kind = PlaybackFailureKind.entries.firstOrNull { it.name == payload.substringBefore(':') }
            val status = payload.substringAfter(':', "").toIntOrNull()?.takeIf { it in 100..599 }
            if (kind != null) return PlaybackFailure(kind, status)
        }
        return classify(errorCode, cause)
    }

    fun classify(code: Int, cause: Throwable?): PlaybackFailure {
        val chain = generateSequence(cause) { it.cause }.take(16).toList()
        chain.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.let {
            return fromHttp(it.responseCode)
        }
        val kind = when {
            chain.any { it is HttpDataSource.CleartextNotPermittedException } -> PlaybackFailureKind.CLEARTEXT
            chain.any { it is SSLException || it is CertificateException } -> PlaybackFailureKind.TLS
            chain.any { it is SocketTimeoutException } -> PlaybackFailureKind.TIMEOUT
            chain.any { it is UnknownHostException } -> PlaybackFailureKind.DNS
            chain.any { it is ConnectException || it is NoRouteToHostException || it is SocketException } -> PlaybackFailureKind.CONNECTION
            chain.any { it is SecurityException } -> PlaybackFailureKind.FILE_PERMISSION
            chain.any { it is FileNotFoundException } -> PlaybackFailureKind.FILE_NOT_FOUND
            else -> when (code) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackFailureKind.CONNECTION
                PlaybackException.ERROR_CODE_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackFailureKind.TIMEOUT
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackFailureKind.FILE_NOT_FOUND
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackFailureKind.FILE_PERMISSION
                PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackFailureKind.CLEARTEXT
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackFailureKind.HTTP_UNSPECIFIED
                PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackFailureKind.UNSUPPORTED_FORMAT
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> PlaybackFailureKind.MALFORMED_MEDIA
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FAILED -> PlaybackFailureKind.DECODER
                in 6000..6999 -> PlaybackFailureKind.DRM
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlaybackFailureKind.IO
                else -> PlaybackFailureKind.UNKNOWN
            }
        }
        return PlaybackFailure(kind)
    }

    internal fun fromHttp(status: Int): PlaybackFailure = when (status) {
        401 -> PlaybackFailure(PlaybackFailureKind.HTTP_UNAUTHORIZED)
        403 -> PlaybackFailure(PlaybackFailureKind.HTTP_FORBIDDEN)
        404 -> PlaybackFailure(PlaybackFailureKind.HTTP_NOT_FOUND)
        410 -> PlaybackFailure(PlaybackFailureKind.HTTP_EXPIRED)
        429 -> PlaybackFailure(PlaybackFailureKind.HTTP_RATE_LIMITED)
        in 300..399 -> PlaybackFailure(PlaybackFailureKind.HTTP_REDIRECT, status)
        in 500..599 -> PlaybackFailure(PlaybackFailureKind.HTTP_SERVER, status)
        in 100..599 -> PlaybackFailure(PlaybackFailureKind.HTTP_OTHER, status)
        else -> PlaybackFailure(PlaybackFailureKind.HTTP_UNSPECIFIED)
    }
}
