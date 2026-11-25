package com.aiexile.animetrack.data.player.webdav

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.domain.playback.SubtitleSpec
import com.thegrizzlylabs.sardineandroid.DavResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
     * 扫描视频所在目录的外挂字幕，返回按匹配度排序的字幕配置列表。
     * 匹配度：与视频同名（不含扩展名）> 以视频名开头（如 EP01.chs.srt）> 其他；
     * 最匹配的一条带 SELECTION_FLAGS_DEFAULT，提高被 ExoPlayer 自动选中的概率。
     *
     * 任何失败（未配置/网络错误/无字幕）均静默返回空列表，不阻塞起播。
     */
    internal fun selectTracks(videoUrl: String, resources: List<DavResource>): Selection {
        val selection = selectCandidates(videoUrl, resources)
        val tracks = selection.candidates.mapIndexed { index, candidate ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(candidate.url))
                .setMimeType(SubtitleSpec.mimeTypeForFileName(candidate.name))
                .setLabel(candidate.name.substringBeforeLast('.'))
                .setLanguage(SubtitleSpec.guessLanguage(candidate.name))
                .setId((WebDavSubtitleBudget.TRACK_ID_BASE + index).toString())
                .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        return Selection(tracks, selection.skippedOversized)
    }

    internal suspend fun loadResources(
        videoUrl: String,
        config: PlayerWebDavDirectory.Config
    ): List<DavResource> = withContext(Dispatchers.IO) {
        try {
            val directory = videoUrl.toHttpUrlOrNull()?.resolve("./")
                ?: return@withContext emptyList()
            PlayerWebDavDirectory.list(config, directory)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ensureActive()
            AppLogManager.w(TAG, "List WebDAV directory for playback failed (${e.javaClass.simpleName})")
            emptyList()
        }
    }

    internal data class Selection(
        val tracks: List<MediaItem.SubtitleConfiguration> = emptyList(),
        val skippedOversized: Boolean = false
    )

    internal data class CandidateSelection(
        val candidates: List<Candidate> = emptyList(),
        val skippedOversized: Boolean = false
    )

    internal fun selectCandidates(videoUrl: String, resources: List<DavResource>): CandidateSelection {
        val target = videoUrl.toHttpUrlOrNull() ?: return CandidateSelection()
        val directory = target.resolve("./") ?: return CandidateSelection()
        val videoBase = target.pathSegments.lastOrNull().orEmpty().substringBeforeLast('.')
        var skippedOversized = false
        val candidates = resources.asSequence()
            .filterNot { it.isDirectory }
            .mapNotNull { resource ->
                val subtitle = WebDavPlaybackIdentity.resolve(directory, resource.href.toString())
                    ?.takeIf { WebDavPlaybackIdentity.sameDirectory(it, directory) } ?: return@mapNotNull null
                val name = subtitle.pathSegments.lastOrNull().orEmpty()
                val size = resource.contentLength
                if (!WebDavSubtitleBudget.isCandidate(name, videoBase)) return@mapNotNull null
                if (size != null && size > WebDavSubtitleBudget.MAX_BYTES) {
                    skippedOversized = true
                    return@mapNotNull null
                }
                if (size == 0L) return@mapNotNull null
                Candidate(subtitle.toString(), name, SubtitleSpec.matchScore(name, videoBase))
            }
            .distinctBy { it.url }
            .sortedWith(compareBy({ it.score }, { it.name.lowercase() }, { it.url }))
            .take(WebDavSubtitleBudget.MAX_TRACKS)
            .toList()
        return CandidateSelection(candidates, skippedOversized)
    }

    internal data class Candidate(val url: String, val name: String, val score: Int)
}
