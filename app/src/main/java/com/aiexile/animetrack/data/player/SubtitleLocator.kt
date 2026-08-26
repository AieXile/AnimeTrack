package com.aiexile.animetrack.data.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import com.aiexile.animetrack.data.SettingsRepository
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
object SubtitleLocator {

    private const val TAG = "SubtitleLocator"

    /** 支持的字幕扩展名 → Media3 MIME 类型（不支持的格式不入列） */
    private val SUBTITLE_MIME_TYPES = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA,
        "ttml" to MimeTypes.APPLICATION_TTML
    )

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

    /** 文件名语言标记 → BCP-47 语言码（按序匹配，先命中先得） */
    private val LANGUAGE_HINTS = linkedMapOf(
        "zh-CN" to listOf("chs", "sc", "hans", "gb2312", "简体", "简中"),
        "zh-TW" to listOf("cht", "tc", "big5", "hant", "繁体", "繁中"),
        "en" to listOf("eng", "english")
    )

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

                val ext = name.substringAfterLast('.', "").lowercase()
                val mime = SUBTITLE_MIME_TYPES[ext] ?: continue
                if (resource.contentLength != null && (
                    resource.contentLength == 0L || resource.contentLength > MAX_SUBTITLE_BYTES
                    )
                ) continue

                val candidateBase = name.substringBeforeLast('.')
                val score = when {
                    candidateBase.equals(videoBaseName, ignoreCase = true) -> 0
                    isPrefixedName(candidateBase, videoBaseName) -> 1
                    else -> 2
                }
                // 合集目录中其他集数的字幕（score=2）不附加：避免轨道列表被无关字幕淹没，
                // 也防止误加载大体积的他集字幕（实测 24 条全附曾间接引发 OOM）
                if (score >= 2) continue
                candidates.add(
                    Candidate(url = siblingUrl(dirUrl, name), name = name, mime = mime, score = score)
                )
            }

            if (candidates.isNotEmpty()) {
                Log.i(
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
                        .setLanguage(guessLanguage(candidate.name))
                        .setId((EXTERNAL_TRACK_ID_BASE + index).toString())
                        .setSelectionFlags(
                            if (index == 0) C.SELECTION_FLAG_DEFAULT else 0
                        )
                        .build()
                }
            Log.i(TAG, "Attached ${configurations.size} subtitle configurations")
            configurations
        }.onFailure {
            Log.w(TAG, "Locate external subtitles failed for $videoUrl", it)
        }.getOrDefault(emptyList())
    }

    /** 取路径中的文件名（URL 解码后），用于同名匹配 */
    private fun baseName(url: String): String {
        val raw = url.substringBeforeLast('?').substringAfterLast('/')
        return Uri.decode(raw).substringBeforeLast('.')
    }

    /**
     * 前缀匹配：candidateBase 以 videoBaseName 开头且其后紧跟分隔符。
     * 避免 EP01 误匹配 EP012/EP013 的字幕（相邻集数数字相连时不算命中）。
     */
    private fun isPrefixedName(candidateBase: String, videoBaseName: String): Boolean {
        if (!candidateBase.startsWith(videoBaseName, ignoreCase = true)) return false
        val next = candidateBase.getOrNull(videoBaseName.length) ?: return true
        return next == '.' || next == '-' || next == '_' || next == ' ' || next == '[' || next == '('
    }

    /** 拼接同目录文件 URL：dir 已含尾斜杠前的完整路径，直接补 /name */
    private fun siblingUrl(dirUrl: String, fileName: String): String =
        "$dirUrl/${android.net.Uri.encode(fileName)}"

    /** 从字幕文件名猜测语言码；无标记返回 null（交由用户手动选择） */
    private fun guessLanguage(fileName: String): String? {
        val lower = fileName.lowercase()
        for ((lang, keys) in LANGUAGE_HINTS) {
            if (keys.any { lower.contains(it) }) return lang
        }
        return null
    }
}
