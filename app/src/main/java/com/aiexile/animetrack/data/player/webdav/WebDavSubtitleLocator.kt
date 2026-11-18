package com.aiexile.animetrack.data.player.webdav

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.domain.playback.SubtitleSpec
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * WebDAV 外挂字幕定位器。
 *
 * 背景：ExoPlayer/Media3 只解析媒体容器内嵌的字幕轨，不会主动发现同目录的
 * 外挂字幕文件（.srt/.ass 等）。播放 WebDAV 视频前用本类 PROPFIND 列出同目录，
 * 将匹配到的字幕文件构建为 [MediaItem.SubtitleConfiguration] 附加到 MediaItem 上。
 *
 * 匹配规则：仅接受与当前视频同名或以其名为前缀的字幕（如 EP01.chs.srt）；
 * 合集目录中他集字幕一律跳过（防轨道污染）。
 *
 * 字幕 URI 为 http(s) 绝对地址，由 [PlaybackService] 的 DataSourceFactory
 * （DefaultDataSource → WebDAV 认证源）加载，认证自动携带，无需额外处理。
 */
object WebDavSubtitleLocator {

    private const val TAG = "WebDavSubtitleLocator"

    /**
     * 单个字幕文件体积上限（防呆值）：防止误把视频等大文件当字幕加载。
     * 渲染已交由 libass 在 native 堆完成，不再受 Java 堆限制。
     */
    private const val MAX_SUBTITLE_BYTES = 20L * 1024 * 1024

    /**
     * 外挂字幕轨 id 起始值。libass 桥接层要求外挂轨 id 与容器内轨道 id 错开
     * （官方建议 >128），否则轨道选择时可能冲突。
     */
    private const val EXTERNAL_TRACK_ID_BASE = 200

    /**
     * 扫描视频所在目录的外挂字幕，返回按匹配度排序的字幕配置列表。
     * 匹配度：与视频同名（不含扩展名）> 以视频名开头（如 EP01.chs.srt）> 其他；
     * 最匹配的一条带 SELECTION_FLAGS_DEFAULT，提高被 ExoPlayer 自动选中的概率。
     *
     * 任何失败（未配置/网络错误/无字幕）均静默返回空列表，不阻塞起播。
     */
    suspend fun findExternalSubtitles(
        videoUrl: String,
        settingsRepository: SettingsRepository
    ): List<MediaItem.SubtitleConfiguration> = withContext(Dispatchers.IO) {
        runCatching {
            val dirUrl = videoUrl.substringBeforeLast('/')
            if (dirUrl.isBlank()) return@runCatching emptyList()

            // 优先复用浏览页刚列过的目录（缓存命中时零网络请求，消除起播延迟）
            var resources = WebDavDirectoryCache.get(dirUrl)
            if (resources == null) {
                val trustAll = settingsRepository.playerWebdavTrustAllCerts.first()
                val sardine = OkHttpSardine(PlayerWebDavHttpClient.create(trustAll))
                val username = settingsRepository.playerWebdavUsername.first()
                if (username.isNotEmpty()) {
                    sardine.setCredentials(username, settingsRepository.playerWebdavPassword.first())
                }

                resources = sardine.list(dirUrl, 1) ?: return@runCatching emptyList()
                WebDavDirectoryCache.put(dirUrl, resources)
            }

            val videoBaseName = baseName(videoUrl)
            data class Candidate(val url: String, val name: String, val mime: String, val score: Int)

            val candidates = mutableListOf<Candidate>()
            for (resource in resources) {
                if (resource.isDirectory) continue
                // 显示名优先 displayName（非 ASCII 不经百分号编码），缺失时解码 href 兜底
                val name = resource.displayName?.takeIf { it.isNotBlank() }
                    ?: Uri.decode(resource.href.toString().trimEnd('/').substringAfterLast('/'))
                if (name.isBlank() || name == "..") continue

                val mime = SubtitleSpec.mimeTypeForFileName(name) ?: continue
                if (resource.contentLength != null && (
                    resource.contentLength == 0L || resource.contentLength > MAX_SUBTITLE_BYTES
                    )
                ) continue

                val score = SubtitleSpec.matchScore(name, videoBaseName)
                // 合集目录中其他集数的字幕（score=2）不附加：避免轨道列表被无关字幕淹没，
                // 也防止误加载大体积的他集字幕（实测 24 条全附曾间接引发 OOM）
                if (score >= SubtitleSpec.MATCH_NONE) continue
                candidates.add(
                    Candidate(url = siblingUrl(dirUrl, name), name = name, mime = mime, score = score)
                )
            }

            if (candidates.isNotEmpty()) {
                AppLogManager.i(
                    TAG,
                    "Candidates in $dirUrl: " +
                        candidates.joinToString(" | ") { "${it.name}(${it.mime}) -> ${it.url}" }
                )
            }

            val configurations = candidates
                .sortedWith(compareBy({ it.score }, { it.name.lowercase() }))
                .mapIndexed { index, candidate ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(candidate.url))
                        .setMimeType(candidate.mime)
                        .setLabel(candidate.name.substringBeforeLast('.'))
                        .setLanguage(SubtitleSpec.guessLanguage(candidate.name))
                        .setId((EXTERNAL_TRACK_ID_BASE + index).toString())
                        .setSelectionFlags(
                            if (index == 0) C.SELECTION_FLAG_DEFAULT else 0
                        )
                        .build()
                }
            AppLogManager.i(TAG, "Attached ${configurations.size} subtitle configurations")
            configurations
        }.onFailure {
            AppLogManager.w(TAG, "Locate external subtitles failed for $videoUrl", it)
        }.getOrDefault(emptyList())
    }

    /** 取路径中的文件名（URL 解码后），用于同名匹配 */
    private fun baseName(url: String): String {
        val raw = url.substringBeforeLast('?').substringAfterLast('/')
        return Uri.decode(raw).substringBeforeLast('.')
    }

    /** 拼接同目录文件 URL：dir 已含尾斜杠前的完整路径，直接补 /name */
    private fun siblingUrl(dirUrl: String, fileName: String): String =
        "$dirUrl/${Uri.encode(fileName)}"
}