package com.aiexile.animetrack.data.media

import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.webdav.PlayerWebDavDirectory
import com.aiexile.animetrack.data.player.webdav.PlayerWebDavHttpClient
import com.aiexile.animetrack.data.player.webdav.WebDavPlaybackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.coroutineContext

internal object WebDavLocalMediaSource {
    const val URI_PREFIX = "webdav://player-library"

    fun uri(config: PlayerWebDavDirectory.Config): String {
        val namespace = WebDavPlaybackIdentity.namespace(config.server, config.root, config.username)
        return "$URI_PREFIX/${config.server.scheme}/$namespace"
    }
}

/** 将播放器 WebDAV 配置适配为媒体库扫描器所需的只读目录读取器。 */
internal class WebDavLocalMediaTreeReader(
    private val settingsRepository: SettingsRepository
) : LocalMediaTreeReader {
    override fun hasPersistedReadPermission(treeUri: android.net.Uri): Boolean =
        treeUri.authority == "player-library"

    override fun takePersistedReadPermission(treeUri: android.net.Uri) = Unit

    suspend fun currentConfig(): PlayerWebDavDirectory.Config? =
        settingsRepository.playerWebdavDirectoryConfig.first()

    override suspend fun readFiles(
        treeUri: android.net.Uri,
        onFile: suspend (LocalMediaScanFile) -> Unit
    ) {
        val config = currentConfig() ?: throw IOException("播放器 WebDAV 未配置")
        if (treeUri.toString() != WebDavLocalMediaSource.uri(config)) {
            throw IOException("播放器 WebDAV 媒体根目录或账号已变更")
        }
        val pending = ArrayDeque<Directory>()
        val visited = mutableSetOf<String>()
        pending.addLast(Directory(config.root, ""))
        val scanContext = coroutineContext

        while (pending.isNotEmpty()) {
            scanContext.ensureActive()
            val directory = pending.removeLast()
            val directoryKey = directory.url.encodedPath
            if (!visited.add(directoryKey)) throw IOException("WebDAV 目录结构异常")

            val resources = PlayerWebDavDirectory.list(config, directory.url, useCache = false)
            for (resource in resources) {
                scanContext.ensureActive()
                val entry = PlayerWebDavDirectory.entryUrl(directory.url, resource) ?: continue
                if (PlayerWebDavDirectory.samePath(entry, directory.url)) continue
                if (!WebDavPlaybackIdentity.isWithin(entry, config.root)) {
                    throw IOException("WebDAV 条目超出媒体根目录")
                }
                val name = resource.displayName?.takeIf { it.isNotBlank() }
                    ?: entry.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty()
                if (name.isBlank() || '/' in name) throw IOException("WebDAV 条目名称无效")
                val relativePath = if (directory.relativePath.isBlank()) name else "${directory.relativePath}/$name"
                if (resource.isDirectory) {
                    pending.addLast(Directory(entry, relativePath))
                } else {
                    onFile(
                        LocalMediaScanFile(
                            relativePath = relativePath,
                            fileName = name,
                            documentUri = entry.toString(),
                            size = (resource.contentLength ?: 0L).coerceAtLeast(0L),
                            lastModified = resource.modified?.time?.coerceAtLeast(0L) ?: 0L,
                            parentDocumentId = directory.url.toString()
                        )
                    )
                }
            }
        }
    }

    override suspend fun readSidecar(file: LocalMediaScanFile): ByteArray? = withContext(Dispatchers.IO) {
        val config = currentConfig() ?: return@withContext null
        val target = WebDavPlaybackIdentity.target(config.server, config.root, config.username, file.documentUri)
            ?: return@withContext null
        val request = Request.Builder().url(target.requestUrl).apply {
            config.authorization()?.let { header("Authorization", it) }
        }.build()
        PlayerWebDavHttpClient.create(config.trustAllCerts).newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null
            if (body.contentLength() > LocalMediaSidecarParser.MAX_BYTES) return@withContext null
            body.byteStream().use(LocalMediaSidecarParser::readBounded)
        }
    }

    suspend fun readSubtitleDirectory(parentId: String): List<LocalSubtitleLocator.SubtitleFile> {
        val config = currentConfig() ?: return emptyList()
        val directory = WebDavPlaybackIdentity.directory(parentId) ?: return emptyList()
        if (!WebDavPlaybackIdentity.isWithin(directory, config.root)) return emptyList()
        return PlayerWebDavDirectory.list(config, directory, useCache = false).mapNotNull { resource ->
            if (resource.isDirectory) return@mapNotNull null
            val name = resource.displayName?.takeIf { it.isNotBlank() }
                ?: PlayerWebDavDirectory.entryUrl(directory, resource)?.pathSegments?.lastOrNull { it.isNotEmpty() }
                ?: return@mapNotNull null
            val mimeType = com.aiexile.animetrack.domain.playback.SubtitleSpec.mimeTypeForFileName(name)
                ?: return@mapNotNull null
            val uri = PlayerWebDavDirectory.entryUrl(directory, resource)?.toString() ?: return@mapNotNull null
            LocalSubtitleLocator.SubtitleFile(name, uri, mimeType)
        }
    }

    private data class Directory(val url: okhttp3.HttpUrl, val relativePath: String)
}
