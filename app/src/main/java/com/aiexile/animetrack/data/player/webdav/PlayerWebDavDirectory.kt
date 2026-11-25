package com.aiexile.animetrack.data.player.webdav

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.impl.handler.ResourcesResponseHandler
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 播放器的只读目录查询：配置探测、浏览和字幕发现共用。 */
internal object PlayerWebDavDirectory {
    class Config private constructor(
        val server: HttpUrl,
        val root: HttpUrl,
        val username: String,
        private val password: String,
        val trustAllCerts: Boolean
    ) {
        override fun equals(other: Any?): Boolean = other is Config && server == other.server &&
            root == other.root && username == other.username && password == other.password &&
            trustAllCerts == other.trustAllCerts

        override fun hashCode(): Int = arrayOf(server, root, username, password, trustAllCerts).contentHashCode()

        override fun toString() = "PlayerWebDavDirectory.Config(redacted)"

        internal fun authorization() = if (username.isEmpty()) null else Credentials.basic(username, password)

        internal fun playbackDataSourceFactory() =
            WebDAVDataSourceFactory(PlayerWebDavHttpClient.create(trustAllCerts), username, password)

        companion object {
            fun parse(url: String, root: String, username: String, password: String, trust: Boolean): Config? {
                val server = WebDavPlaybackIdentity.directory(url) ?: return null
                val mediaRoot = if (root.isBlank()) server else {
                    val resolved = WebDavPlaybackIdentity.resolve(server, root) ?: return null
                    if (!WebDavPlaybackIdentity.sameOrigin(server, resolved)) return null
                    WebDavPlaybackIdentity.directory(resolved.toString()) ?: return null
                }
                return Config(server, mediaRoot, username.trim(), password, trust)
            }
        }
    }

    class HttpFailure(val status: Int) : IOException("WebDAV directory HTTP $status")
    class InvalidListing : IOException("Invalid WebDAV directory response")
    class ListingTooLarge : IOException("WebDAV directory response exceeds limit")

    suspend fun probe(config: Config, client: OkHttpClient = PlayerWebDavHttpClient.create(config.trustAllCerts)) {
        val resources = readDirectory(client, config.root.toString(), config.authorization(), depth = 0)
        val root = resources.firstOrNull { resource ->
            entryUrl(config.root, resource)?.let { samePath(it, config.root) } == true
        } ?: throw InvalidListing()
        if (root.statusCode >= 400) throw HttpFailure(root.statusCode)
        if (!root.isDirectory) throw InvalidListing()
    }

    suspend fun list(config: Config, directory: HttpUrl, useCache: Boolean = true): List<DavResource> {
        require(WebDavPlaybackIdentity.isWithin(directory, config.root))
        val normalized = checkNotNull(WebDavPlaybackIdentity.directory(directory.toString()))
        if (useCache) WebDavDirectoryCache.get(config, normalized)?.let { return it }
        val resources = readDirectory(
            PlayerWebDavHttpClient.create(config.trustAllCerts), normalized.toString(), config.authorization()
        )
        if (resources.isEmpty()) throw InvalidListing()
        val result = resources.filter { resource ->
            val url = entryUrl(normalized, resource) ?: return@filter false
            if (samePath(url, normalized) && resource.statusCode >= 400) throw HttpFailure(resource.statusCode)
            resource.statusCode < 400 && (samePath(url, normalized) ||
                WebDavPlaybackIdentity.isDirectChild(url, normalized))
        }
        coroutineContext.ensureActive()
        WebDavDirectoryCache.put(config, normalized, result)
        return result
    }

    fun entryUrl(directory: HttpUrl, resource: DavResource): HttpUrl? =
        WebDavPlaybackIdentity.resolve(directory, resource.href.toString())

    fun samePath(first: HttpUrl, second: HttpUrl): Boolean =
        WebDavPlaybackIdentity.isWithin(first, second) && WebDavPlaybackIdentity.isWithin(second, first)

    internal suspend fun readDirectory(
        client: OkHttpClient,
        dirUrl: String,
        authorization: String? = null,
        depth: Int = 1
    ): List<DavResource> = try {
        query(client, dirUrl, authorization, depth, DIRECTORY_PROPERTIES)
    } catch (failure: HttpFailure) {
        // 仅在服务器明确拒绝属性请求时回退，不重试认证或网络错误。
        if (failure.status != 400 && failure.status != 422) throw failure
        query(client, dirUrl, authorization, depth, ALL_PROPERTIES)
    }

    private suspend fun query(
        client: OkHttpClient,
        dirUrl: String,
        authorization: String?,
        depth: Int,
        properties: String
    ): List<DavResource> = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(dirUrl).header("Depth", depth.toString())
            .method("PROPFIND", properties.toRequestBody("text/xml".toMediaType())).build()
        val directoryClient = client.newBuilder()
            .followRedirects(false)
            .callTimeout(15, TimeUnit.SECONDS)
            .authenticator { _, response ->
                val challenged = response.request
                if (authorization == null || challenged.url != request.url ||
                    challenged.header("Authorization") != null ||
                    response.challenges().none { it.scheme.equals("Basic", ignoreCase = true) }
                ) null else challenged.newBuilder().header("Authorization", authorization).build()
            }.build()
        val call = directoryClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!continuation.isActive) return
                        when {
                            it.code == 207 -> parse(it, if (depth == 0) MAX_PROBE_BYTES else MAX_LIST_BYTES)
                            it.isSuccessful -> throw InvalidListing()
                            else -> throw HttpFailure(it.code)
                        }
                    }
                }
                result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    private fun parse(response: Response, maxBytes: Long): List<DavResource> {
        val body = response.body ?: throw InvalidListing()
        if (body.contentLength() > maxBytes) throw ListingTooLarge()
        val bounded = object : ForwardingSource(body.source()) {
            private var remaining = maxBytes
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, minOf(byteCount, remaining + 1))
                if (read > remaining) throw ListingTooLarge()
                if (read > 0) remaining -= read
                return read
            }
        }.buffer()
        val limitedBody = object : ResponseBody() {
            override fun contentLength() = body.contentLength()
            override fun contentType() = body.contentType()
            override fun source() = bounded
        }
        return try {
            ResourcesResponseHandler().handleResponse(response.newBuilder().body(limitedBody).build())
        } catch (failure: Exception) {
            val causes = generateSequence<Throwable>(failure) { it.cause }
            causes.filterIsInstance<ListingTooLarge>().firstOrNull()?.let { throw it }
            causes.filterIsInstance<InterruptedIOException>().firstOrNull()?.let { throw it }
            throw InvalidListing()
        }
    }

    private const val MAX_PROBE_BYTES = 256L * 1024
    private const val MAX_LIST_BYTES = 4L * 1024 * 1024
    private const val ALL_PROPERTIES = "<d:propfind xmlns:d=\"DAV:\"><d:allprop/></d:propfind>"
    private const val DIRECTORY_PROPERTIES = "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
        "<d:resourcetype/><d:displayname/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"
}
