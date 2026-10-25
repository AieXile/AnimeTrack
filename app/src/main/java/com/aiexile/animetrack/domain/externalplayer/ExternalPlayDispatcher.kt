package com.aiexile.animetrack.domain.externalplayer

/** 一次外部播放点击的决策结果 */
sealed interface ExternalPlayDecision {
    /** 直接使用指定播放器 */
    data class Launch(val playerId: ExternalPlayerId) : ExternalPlayDecision

    /** 弹窗让用户选择（含都未安装时展示下载选项） */
    data object AskUser : ExternalPlayDecision
}

/**
 * 按设置与安装状态决定播放目标（纯函数，便于单测）。
 *
 * 自动规则的取舍：
 * - 只装一个 → 用它；
 * - 两个都装 → 有 B 站 season_id 时直达哔哩哔哩；没有则交给用户选择，
 *   避免按标题猜测后跳错番剧；
 * - 都没装 → 弹窗展示两个应用的下载入口。
 */
object ExternalPlayDispatcher {

    fun decide(
        mode: ExternalPlayMode,
        installed: Set<ExternalPlayerId>,
        hasBilibiliSeasonId: Boolean
    ): ExternalPlayDecision = when (mode) {
        ExternalPlayMode.ANIMEKO -> ExternalPlayDecision.Launch(ExternalPlayerId.ANIMEKO)
        ExternalPlayMode.BILIBILI -> ExternalPlayDecision.Launch(ExternalPlayerId.BILIBILI)
        ExternalPlayMode.ASK -> ExternalPlayDecision.AskUser
        ExternalPlayMode.AUTO -> when {
            installed.contains(ExternalPlayerId.ANIMEKO) &&
                installed.contains(ExternalPlayerId.BILIBILI) -> {
                if (hasBilibiliSeasonId) {
                    ExternalPlayDecision.Launch(ExternalPlayerId.BILIBILI)
                } else {
                    ExternalPlayDecision.AskUser
                }
            }
            installed.contains(ExternalPlayerId.ANIMEKO) ->
                ExternalPlayDecision.Launch(ExternalPlayerId.ANIMEKO)
            installed.contains(ExternalPlayerId.BILIBILI) ->
                ExternalPlayDecision.Launch(ExternalPlayerId.BILIBILI)
            else -> ExternalPlayDecision.AskUser
        }
    }
}
