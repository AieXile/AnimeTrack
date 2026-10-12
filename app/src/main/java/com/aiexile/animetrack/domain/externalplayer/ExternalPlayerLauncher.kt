package com.aiexile.animetrack.domain.externalplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.aiexile.animetrack.R

/** 包装 PackageManager 的探测结果（生产实现见 [ExternalPlayerLauncher.launch]；单测注入 fake） */
fun interface IntentResolver {
    /** @return 是否有 App 可响应 [probeUri] */
    fun resolve(probeUri: String): Boolean
}

/** 包装 Context.startActivity（单测注入 fake） */
fun interface ActivityStarter {
    /** @return 是否成功唤起（false = ActivityNotFoundException） */
    fun startActivity(launchUri: String): Boolean
}

/**
 * 外部播放器启动协调器：校验（注册 → 关联 → 安装）→ 唤起 → 降级。
 *
 * - 便捷入口 [launch]（带 Context）：失败自动降级（NotInstalled → Toast + 跳官方下载页；
 *   LaunchFailed → Toast），同时返回结果供调用方消费。
 * - 纯函数入口 [launch]（无 Android 依赖）：不产生任何副作用，专供单测与自定义呈现。
 */
object ExternalPlayerLauncher {

    /** 便捷入口：校验 → 唤起 → 失败降级（Toast / 跳官方下载页） */
    fun launch(
        context: Context,
        request: ExternalPlayRequest,
        playerId: ExternalPlayerId
    ): ExternalPlayResult {
        val result = launch(
            request = request,
            playerId = playerId,
            resolver = IntentResolver { probeUri ->
                // runCatching 兜底个别 ROM 的 PackageManager 异常，视为未安装走降级
                runCatching {
                    context.packageManager.resolveActivity(viewIntent(probeUri), 0) != null
                }.getOrDefault(false)
            },
            starter = ActivityStarter { launchUri ->
                try {
                    context.startActivity(viewIntent(launchUri))
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            }
        )

        when (result) {
            is ExternalPlayResult.NotInstalled -> {
                toast(context, context.getString(R.string.external_player_not_installed, result.player.displayName))
                openDownloadPage(context, result.player)
            }
            is ExternalPlayResult.LaunchFailed -> {
                toast(context, context.getString(R.string.external_player_open_failed))
            }
            else -> Unit
        }
        return result
    }

    /**
     * 纯函数入口（单测用）：无任何副作用，只返回结果。
     *
     * @param players 参与查找的播放器列表，默认取注册表全量；传空列表可覆盖“未注册”分支
     */
    fun launch(
        request: ExternalPlayRequest,
        playerId: ExternalPlayerId,
        resolver: IntentResolver,
        starter: ActivityStarter,
        players: List<ExternalPlayer> = ExternalPlayerRegistry.all
    ): ExternalPlayResult {
        val player = players.find { it.id == playerId }
            ?: return ExternalPlayResult.LaunchFailed(
                request = request,
                cause = IllegalStateException("Unknown external player: $playerId")
            )

        val launchUri = player.buildLaunchUri(request)
            ?: return ExternalPlayResult.MissingBangumiLink(request)

        if (!resolver.resolve(player.probeUri)) {
            return ExternalPlayResult.NotInstalled(player)
        }

        return if (starter.startActivity(launchUri)) {
            ExternalPlayResult.Success
        } else {
            ExternalPlayResult.LaunchFailed(
                request = request,
                cause = IllegalStateException("No activity handles $launchUri")
            )
        }
    }

    private fun viewIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            // 与 AppNavigator 一致：NEW_TASK 保证任意 Context 下均可安全启动外部 App
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun openDownloadPage(context: Context, player: ExternalPlayer) {
        try {
            context.startActivity(viewIntent(player.downloadUrl))
        } catch (_: ActivityNotFoundException) {
            // 无浏览器等极端场景静默（已有 Toast 提示过未安装）
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
