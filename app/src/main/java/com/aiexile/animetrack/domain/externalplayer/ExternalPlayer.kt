package com.aiexile.animetrack.domain.externalplayer

/**
 * 外部播放器跳转框架契约。
 *
 * 设计要点：本层只产出 URI 字符串（纯逻辑，可被纯 JVM 单测覆盖），
 * Intent 包装集中在 [ExternalPlayerLauncher] 的 Android 分支，
 * 避免 android.content.Intent 在单测中抛 "not mocked"。
 */

/** 外部播放器唯一标识；新增播放器在此登记 */
enum class ExternalPlayerId { ANIMEKO }

/**
 * 一次外部播放请求。
 *
 * @param animeId AnimeTrack 内部 ID，用于将来引导关联等场景
 * @param bangumiId Bangumi 条目 ID；null 或 <=0 视为未关联
 * @param title 展示用（Toast/日志）
 * @param episodeSort 目标集数（sort 序号）；null 表示详情页级跳转
 */
data class ExternalPlayRequest(
    val animeId: Int,
    val bangumiId: Int?,
    val title: String,
    val episodeSort: Int? = null
) {
    companion object {
        /**
         * “接着看”语义：
         * - totalEpisodes 未知（null）→ ep = watched + 1（无法判断完结，交给用户）
         * - watched >= totalEpisodes（看完）→ ep = null，回退详情页级
         * - 其余 → ep = watched + 1
         */
        fun continueWatching(
            animeId: Int,
            bangumiId: Int?,
            title: String,
            watchedEpisodes: Int,
            totalEpisodes: Int?
        ): ExternalPlayRequest = ExternalPlayRequest(
            animeId = animeId,
            bangumiId = bangumiId,
            title = title,
            episodeSort = if (totalEpisodes != null && watchedEpisodes >= totalEpisodes) {
                null
            } else {
                watchedEpisodes + 1
            }
        )
    }
}

/** 外部播放器适配器；接入新播放器实现本接口并在 [ExternalPlayerRegistry] 登记 */
interface ExternalPlayer {
    val id: ExternalPlayerId

    /** 展示名 */
    val displayName: String

    /** 官方下载页 */
    val downloadUrl: String

    /** 探测 URI（须与 Manifest queries 声明一致，用于安装检测） */
    val probeUri: String

    /**
     * 构建目标 URI；request 不满足要求（如未关联 Bangumi）时返回 null。
     * 集数参数由实现自行决定是否附加（接收方“能处理就处理，不能就忽略”）。
     */
    fun buildLaunchUri(request: ExternalPlayRequest): String?
}

/** 启动结果（供现在/将来的 UI 消费） */
sealed interface ExternalPlayResult {
    data object Success : ExternalPlayResult

    /** 目标播放器未安装 */
    data class NotInstalled(val player: ExternalPlayer) : ExternalPlayResult

    /** 番剧未关联 Bangumi，无法定位目标条目 */
    data class MissingBangumiLink(val request: ExternalPlayRequest) : ExternalPlayResult

    data class LaunchFailed(val request: ExternalPlayRequest, val cause: Exception) : ExternalPlayResult
}
