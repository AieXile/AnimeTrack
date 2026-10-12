package com.aiexile.animetrack.domain.externalplayer

/**
 * Animeko 适配（https://github.com/open-ani/animeko，官方下载页 https://myani.org/downloads）。
 *
 * Deep link：ani://subjects/{bangumiSubjectId} → 番剧详情页（仅手机版 default flavor，TV 变体无此能力）。
 *
 * 集数参数（混合路线 C）：Animeko 的 intent-filter 只匹配 scheme+host，其解析只读首个
 * path 段并忽略 query，故附加 ?ep= 不影响现有行为；将来其支持集数级跳转（或开放协议
 * PR 被合入）后此格式自动生效，发送端零改动。
 */
object AnimekoPlayer : ExternalPlayer {
    private const val SCHEME = "ani"
    private const val HOST = "subjects"

    const val PACKAGE_NAME = "me.him188.ani"
    const val DOWNLOAD_URL = "https://myani.org/downloads"

    override val id: ExternalPlayerId = ExternalPlayerId.ANIMEKO
    override val displayName: String = "Animeko"
    override val downloadUrl: String = DOWNLOAD_URL

    override val probeUri: String = "$SCHEME://$HOST"

    override fun buildLaunchUri(request: ExternalPlayRequest): String? {
        val id = request.bangumiId
        if (id == null || id <= 0) return null
        val base = "$SCHEME://$HOST/$id"
        return request.episodeSort?.let { "$base?ep=$it" } ?: base
    }
}
