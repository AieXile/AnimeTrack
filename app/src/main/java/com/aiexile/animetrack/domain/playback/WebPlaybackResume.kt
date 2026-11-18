package com.aiexile.animetrack.domain.playback

import com.aiexile.animetrack.data.player.web.PendingWebPlaybackRequest
import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository

import com.aiexile.animetrack.model.PlaybackProgress

/** 网页历史续播目标：页面地址、标题与对外的剧集文案。 */
data class WebPlaybackResumeTarget(
    val request: PendingWebPlaybackRequest,
    /** 当前/下一集在站点上的名称，用于详情页与历史卡片展示。 */
    val episodeLabel: String?,
    val isNextEpisode: Boolean
)

/**
 * 由网页历史生成续播请求。
 *
 * 进度已完成且历史里记有下一集时回到下一集，否则回到该集页面；
 * 详情页与网页播放页共用这一条规则，避免两处判断不一致。
 */
object WebPlaybackResume {

    fun mediaKey(entry: WebPlaybackHistoryRepository.Entry): String =
        entry.mediaId ?: "web:${entry.url}"

    fun target(
        entry: WebPlaybackHistoryRepository.Entry,
        progress: PlaybackProgress?
    ): WebPlaybackResumeTarget {
        val next = entry.nextEpisode.takeIf { progress?.completed == true }
        val targetUrl = next?.url ?: entry.url
        val direct = WebSourceClassifier.isCandidate(targetUrl)
        val referer = if (entry.referer == entry.url) targetUrl else entry.referer
        val title = next?.let { episode ->
            entry.seriesTitle?.let { "$it · ${episode.name}" } ?: episode.name
        } ?: entry.title

        return WebPlaybackResumeTarget(
            request = PendingWebPlaybackRequest(
                url = targetUrl,
                mimeType = WebSourceClassifier.mimeType(targetUrl),
                title = title,
                pageUrl = targetUrl.takeUnless { direct },
                userAgent = entry.userAgent,
                referer = referer,
                resolvePage = true,
                restoreQueue = !direct,
                progressMediaId = if (next != null) entry.nextMediaId else entry.mediaId,
                seriesUrl = entry.seriesUrl,
                animeId = entry.animeId,
                animeTitle = entry.animeTitle
            ),
            episodeLabel = next?.name ?: entry.episodeName,
            isNextEpisode = next != null
        )
    }
}
