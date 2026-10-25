package com.aiexile.animetrack.domain.externalplayer

import java.net.URLEncoder

/**
 * 哔哩哔哩适配。
 *
 * Deep link（与官方网页链接的标准 App 唤起格式一致）：
 * - `bilibili://pgc/season/ep/{epId}` 单集播放页（直达「接着看」的那一集）
 * - `bilibili://bangumi/season/{seasonId}` 番剧页（集数未知/看完时回退）
 * - `bilibili://search?keyword=...` 搜索页（标题匹配失败时的兜底）
 *
 * 注意不要用 `bilibili://bangumi/play/ep...`：部分版本会落到首页。
 * 官方包的 scheme 会与部分第三方客户端（如 com.android.purebilibili）冲突，
 * 因此启动时必须显式定向 [PACKAGE_NAME]。
 */
object BilibiliPlayer : ExternalPlayer {
    private const val SCHEME = "bilibili"

    const val PACKAGE_NAME = "tv.danmaku.bili"
    const val DOWNLOAD_URL = "https://app.bilibili.com/"

    override val id: ExternalPlayerId = ExternalPlayerId.BILIBILI
    override val displayName: String = "哔哩哔哩"
    override val downloadUrl: String = DOWNLOAD_URL
    override val packageName: String = PACKAGE_NAME

    /** scheme+host 与 Manifest queries 声明一致 */
    override val probeUri: String = "$SCHEME://search"

    /**
     * 同步可得的兜底 URI：搜索页。
     * season/集数直达需要先走标题匹配（见 BilibiliPlayLinkResolver），
     * 由调用方解析成功后使用 [buildPlayUri]。
     */
    override fun buildLaunchUri(request: ExternalPlayRequest): String? {
        if (request.title.isBlank()) return null
        return buildSearchUri(request.title)
    }

    fun buildSearchUri(title: String): String =
        "$SCHEME://search?keyword=${encode(title)}"

    fun buildSeasonUri(seasonId: Int): String =
        "$SCHEME://bangumi/season/$seasonId"

    fun buildEpisodeUri(episodeId: Long): String =
        "$SCHEME://pgc/season/ep/$episodeId"

    /** 解析结果 → 最优 URI：有集数直达单集，否则打开番剧播放页 */
    fun buildPlayUri(link: BilibiliPlayLink): String =
        link.episodeId?.let(::buildEpisodeUri) ?: buildSeasonUri(link.seasonId)

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/**
 * 一次 B 站播放定位结果。
 *
 * @param seasonId B 站番剧 season_id
 * @param episodeId 目标单集 ep_id；null 表示只定位到番剧级
 */
data class BilibiliPlayLink(
    val seasonId: Int,
    val episodeId: Long? = null
)
