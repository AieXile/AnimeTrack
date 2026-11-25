package com.aiexile.animetrack.data.player.webdav

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.Locale

/** 请求地址与续播身份分离；等价的续播身份不能扩大认证范围。 */
internal object WebDavPlaybackIdentity {
    data class Target(val requestUrl: HttpUrl, val mediaKey: String, val title: String)

    /** 冷恢复只存稳定身份和路径，不复用历史查询参数、签名或主机。 */
    data class Resume(val mediaKey: String, val encodedPath: String) {
        fun resolve(config: PlayerWebDavDirectory.Config): Target? {
            if (!encodedPath.startsWith('/') || encodedPath.startsWith("//") ||
                encodedPath.any { it == '?' || it == '#' || it == '\\' }
            ) return null
            val url = config.server.newBuilder().encodedPath(encodedPath).query(null).build()
            return target(config.server, config.root, config.username, url.toString())
                ?.takeIf { it.mediaKey == mediaKey }
        }
    }

    fun resume(target: Target): Resume? = if (target.requestUrl.encodedQuery == null) {
        Resume(target.mediaKey, target.requestUrl.encodedPath)
    } else null

    fun directory(raw: String): HttpUrl? = parse(raw)?.let(::asDirectory)

    fun mediaRoot(server: HttpUrl, configured: String): HttpUrl = configured.takeIf { it.isNotBlank() }
        ?.let { value -> resolve(server, value) }
        ?.takeIf { sameOrigin(server, it) }
        ?.let(::asDirectory) ?: server

    fun target(server: HttpUrl, root: HttpUrl, username: String, raw: String): Target? {
        val request = resolve(server, raw)?.takeIf { isWithin(it, root) } ?: return null
        val path = pathSegments(request)
        if (path.isEmpty() || path == pathSegments(root)) return null
        val key = "webdav:${namespace(server, root, username)}/${path.joinToString("/")}"
        val title = request.pathSegments.lastOrNull { it.isNotEmpty() } ?: return null
        return Target(request, key, title)
    }

    fun resolve(directory: HttpUrl, raw: String): HttpUrl? {
        val value = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        return asDirectory(directory).resolve(value)?.let(::checkedUrl)
    }

    fun sameOrigin(first: HttpUrl, second: HttpUrl): Boolean =
        first.scheme == second.scheme && first.host == second.host && first.port == second.port

    fun sameDirectory(file: HttpUrl, directory: HttpUrl): Boolean =
        !file.encodedPath.endsWith('/') && isDirectChild(file, directory)

    fun isDirectChild(candidate: HttpUrl, directory: HttpUrl): Boolean {
        if (!sameOrigin(candidate, directory)) return false
        val path = pathSegments(candidate)
        val parent = pathSegments(directory)
        return path.size == parent.size + 1 && path.dropLast(1) == parent
    }

    fun isWithin(candidate: HttpUrl, root: HttpUrl): Boolean {
        if (!sameOrigin(candidate, root)) return false
        val rootPath = pathSegments(root)
        return pathSegments(candidate).take(rootPath.size) == rootPath
    }

    /** 旧记录无账号/库标识，只在能确定是当前请求目标时读取。 */
    fun isLegacyAlias(raw: String, target: Target): Boolean {
        val legacy = parse(raw) ?: return false
        return legacy.host == target.requestUrl.host && stablePort(legacy) == stablePort(target.requestUrl) &&
            pathSegments(legacy) == pathSegments(target.requestUrl) && legacy.encodedQuery == target.requestUrl.encodedQuery
    }

    /** 防止没有账号归属的同一旧记录被重复迁入不同服务命名空间。 */
    fun legacyClaim(target: Target): String = digest(listOf(
        target.requestUrl.host, stablePort(target.requestUrl), pathSegments(target.requestUrl).joinToString("/"),
        target.requestUrl.encodedQuery.orEmpty()
    ))

    fun namespace(server: HttpUrl, root: HttpUrl, username: String): String = digest(listOf(
        server.host, stablePort(server), pathSegments(server).joinToString("/"),
        pathSegments(root).joinToString("/"), username.trim()
    ))

    private fun digest(parts: List<String>): String {
        val identity = parts.joinToString(separator = "") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun stablePort(url: HttpUrl): String =
        if (url.port == HttpUrl.defaultPort(url.scheme)) "" else url.port.toString()

    private fun asDirectory(url: HttpUrl): HttpUrl = url.newBuilder()
        .encodedPath(url.encodedPath.trimEnd('/') + "/").fragment(null).build()

    private fun parse(raw: String): HttpUrl? = raw.trim().toHttpUrlOrNull()?.let(::checkedUrl)

    private fun checkedUrl(url: HttpUrl): HttpUrl? = url.takeIf {
        it.username.isEmpty() && it.password.isEmpty() &&
            it.encodedPathSegments.none { segment -> segment.contains("%5c", ignoreCase = true) }
    }?.newBuilder()?.fragment(null)?.build()

    private fun pathSegments(url: HttpUrl): List<String> = url.encodedPathSegments
        .dropLastWhile { it.isEmpty() }.map(::normalizeSegment)

    private fun normalizeSegment(segment: String): String = HEX_ESCAPE.replace(segment) { match ->
        val decoded = match.value.substring(1).toInt(16).toChar()
        if (decoded in 'a'..'z' || decoded in 'A'..'Z' || decoded in '0'..'9' || decoded in "-._~") {
            decoded.toString()
        } else {
            match.value.uppercase(Locale.ROOT)
        }
    }

    private val HEX_ESCAPE = Regex("%[0-9a-fA-F]{2}")
}
