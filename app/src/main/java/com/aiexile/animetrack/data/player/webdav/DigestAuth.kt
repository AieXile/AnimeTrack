package com.aiexile.animetrack.data.player.webdav

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * HTTP Digest 认证（RFC 2617）Authorization 头构建。
 *
 * 支持 MD5 / MD5-sess 与 qop=auth/auth-int；播放 GET 没有请求体，auth-int 的实体摘要为空。
 */
internal object DigestAuth {

    /** 根据 WWW-Authenticate 质询头生成 Authorization 值；无法满足质询要求时返回 null */
    fun buildAuthorization(
        method: String,
        uri: String,
        challenge: String,
        username: String,
        password: String
    ): String? {
        val params = parseChallenge(challenge) ?: return null

        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val algorithm = params["algorithm"] ?: "MD5"
        val isSess = algorithm.equals("MD5-sess", ignoreCase = true)
        if (!algorithm.equals("MD5", ignoreCase = true) && !isSess) return null

        val qop = params["qop"]
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.equals("auth", ignoreCase = true) }

        val nc = "00000001"
        val cnonce = randomHex(8)

        val ha1Base = md5Hex("$username:$realm:$password")
        val ha1 = if (isSess) md5Hex("$ha1Base:$nonce:$cnonce") else ha1Base
        val ha2 = if (qop.equals("auth-int", ignoreCase = true)) null else md5Hex("$method:$uri")
        if (qop == null && ha2 == null) return null
        val responseHash = if (qop == null) {
            md5Hex("$ha1:$nonce:${checkNotNull(ha2)}")
        } else {
            // auth-int requires the entity hash; all playback requests are empty GETs.
            val effectiveHa2 = if (qop.equals("auth-int", true)) md5Hex("$method:$uri:${md5Hex("")}") else checkNotNull(ha2)
            md5Hex("$ha1:$nonce:$nc:$cnonce:$qop:$effectiveHa2")
        }

        return buildString {
            append("Digest username=\"$username\"")
            append(", realm=\"$realm\"")
            append(", nonce=\"$nonce\"")
            append(", uri=\"$uri\"")
            append(", algorithm=$algorithm")
            append(", response=\"$responseHash\"")
            if (qop != null) {
                append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            }
            params["opaque"]?.let { opaque -> append(", opaque=\"$opaque\"") }
        }
    }

    /** 解析 "Digest realm=\"x\", nonce=\"y\", qop=\"auth\", algorithm=MD5" 形式的质询 */
    private fun parseChallenge(challenge: String): Map<String, String>? {
        val body = challenge.trim().substringAfter(' ', "").trim()
        if (body.isEmpty()) return null

        val regex = Regex("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))")
        val parsed = regex.findAll(body).associate { match ->
            match.groupValues[1].lowercase() to
                (match.groupValues[2].ifEmpty { match.groupValues[3] })
        }
        return parsed.takeIf { it.containsKey("realm") && it.containsKey("nonce") }
    }

    private fun randomHex(byteCount: Int): String =
        ByteArray(byteCount).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
