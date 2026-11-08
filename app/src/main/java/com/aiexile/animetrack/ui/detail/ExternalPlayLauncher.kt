package com.aiexile.animetrack.ui.detail

import android.content.Context
import android.widget.Toast
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.playlink.BilibiliPlayLinkResolver
import com.aiexile.animetrack.domain.externalplayer.BilibiliPlayer
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayDecision
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayDispatcher
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayMode
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayRequest
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayResult
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayerId
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayerLauncher
import com.aiexile.animetrack.domain.externalplayer.ExternalPlayerRegistry
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 详情页外部播放启动器：把「接着看请求 → 直达/询问」与播放器拉起逻辑从 UI 文件里抽出。
 * 只依赖纯数据与回调，不持有 Compose 状态，播放器解析仍在调用方作用域内执行。
 */
class ExternalPlayLauncher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val externalPlayMode: ExternalPlayMode,
    private val setIsResolving: (Boolean) -> Unit,
    private val onSaveBilibiliSeasonId: (Int) -> Unit,
    private val onAskUser: (installed: Set<ExternalPlayerId>) -> Unit
) {

    /**
     * 播放入口点击（FAB / 内联卡片共用）：按设置构造「接着看」请求，
     * 再由 [ExternalPlayDispatcher] 决定直达播放器还是询问用户。
     */
    fun start(anime: Anime) {
        // totalEpisodes<=0 视为未知(null)：continueWatching 语义为“恒给下一集”，
        // 直接传 0 会被误判为“已看完”而回退详情页级跳转
        val request = ExternalPlayRequest.continueWatching(
            animeId = anime.id,
            bangumiId = anime.bangumiId,
            title = anime.title,
            watchedEpisodes = anime.watchedEpisodes,
            totalEpisodes = anime.totalEpisodes.takeIf { it > 0 }
        )
        val installed = ExternalPlayerRegistry.all
            .filter { ExternalPlayerLauncher.isInstalled(context, it) }
            .map { it.id }
            .toSet()
        when (val decision = ExternalPlayDispatcher.decide(
            mode = externalPlayMode,
            installed = installed,
            hasBilibiliSeasonId = (anime.bilibiliSeasonId ?: 0) > 0
        )) {
            is ExternalPlayDecision.Launch ->
                launch(anime, request, decision.playerId, installed)
            ExternalPlayDecision.AskUser -> {
                onAskUser(installed)
            }
        }
    }

    /**
     * 外部播放：Animeko 走既有同步链路；哔哩哔哩先解析 season/集数直达，
     * 解析失败（网络/匹配不到）回退到 B 站搜索页，保证永远有落点。
     */
    fun launch(
        anime: Anime,
        request: ExternalPlayRequest,
        playerId: ExternalPlayerId,
        installed: Set<ExternalPlayerId>
    ) {
        when (playerId) {
            ExternalPlayerId.ANIMEKO -> {
                val result = ExternalPlayerLauncher.launch(context, request, playerId)
                // 未安装/唤起失败已由便捷入口自动 Toast+降级；未关联需调用方提示
                if (result is ExternalPlayResult.MissingBangumiLink) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.external_player_missing_link),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            ExternalPlayerId.BILIBILI -> {
                if (!installed.contains(playerId)) {
                    // 未安装：沿用统一降级（Toast + 官方下载页），无需先联网解析
                    ExternalPlayerLauncher.launchUri(
                        context, BilibiliPlayer, BilibiliPlayer.buildSearchUri(anime.title)
                    )
                    return
                }
                scope.launch {
                    setIsResolving(true)
                    val link = withTimeoutOrNull(5_000L) {
                        BilibiliPlayLinkResolver.resolve(
                            title = anime.title,
                            storedSeasonId = anime.bilibiliSeasonId,
                            watchedEpisodes = anime.watchedEpisodes,
                            totalEpisodes = anime.totalEpisodes.takeIf { it > 0 },
                            seasonNumber = anime.seasonNumber
                        )
                    }
                    setIsResolving(false)
                    // 首次按标题匹配成功时回写 season_id，后续点击可直接直达
                    if (link != null && anime.bilibiliSeasonId == null) {
                        onSaveBilibiliSeasonId(link.seasonId)
                    }
                    val uri = link?.let { BilibiliPlayer.buildPlayUri(it) }
                        ?: BilibiliPlayer.buildSearchUri(anime.title)
                    ExternalPlayerLauncher.launchUri(context, BilibiliPlayer, uri)
                }
            }
        }
    }
}
