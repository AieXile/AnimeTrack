package com.aiexile.animetrack.domain.externalplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.aiexile.animetrack.R

/** 包装 PackageManager 的探测结果（生产实现见 [ExternalPlayerLauncher.launch]；单测注入 fake） */
fun interface IntentResolver {
    /** @return 目标播放器是否已安装 */
    fun resolve(player: ExternalPlayer): Boolean
}

/** 包装 Context.startActivity（单测注入 fake） */
fun interface ActivityStarter {
    /** @return 是否成功唤起（false = ActivityNotFoundException） */
    fun startActivity(player: ExternalPlayer, launchUri: String): Boolean
}

/**
 * 外部播放器启动协调器：校验（注册 → 关联 → 安装）→ 唤起 → 降级。
 *
 * - 便捷入口 [launch]（带 Context）：失败自动降级（NotInstalled → Toast + 跳官方下载页；
 *   LaunchFailed → Toast），同时返回结果供调用方消费。
 * - 纯函数入口 [launch]（无 Android 依赖）：不产生任何副作用，专供单测与自定义呈现。
 * - [launchUri]：已经解析好目标 URI 的场景（如哔哩哔哩 season/集数直达）。
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
            resolver = IntentResolver { player -> isInstalled(context, player) },
            starter = ActivityStarter { player, launchUri ->
                try {
                    context.startActivity(viewIntent(launchUri, player.packageName))
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

    /** 直接唤起已解析好的 URI（安装检测与失败降级同 [launch]） */
    fun launchUri(context: Context, player: ExternalPlayer, launchUri: String): Boolean {
        if (!isInstalled(context, player)) {
            toast(context, context.getString(R.string.external_player_not_installed, player.displayName))
            openDownloadPage(context, player)
            return false
        }
        return try {
            context.startActivity(viewIntent(launchUri, player.packageName))
            true
        } catch (_: ActivityNotFoundException) {
            toast(context, context.getString(R.string.external_player_open_failed))
            false
        }
    }

    /**
     * 安装检测：声明了包名的播放器以包名是否存在为准
     * （哔哩哔哩的 scheme 也会被第三方客户端响应，resolveActivity 会误判为已安装）。
     */
    fun isInstalled(context: Context, player: ExternalPlayer): Boolean {
        val packageName = player.packageName
        return if (packageName != null) {
            runCatching {
                context.packageManager.getPackageInfo(packageName, 0) != null
            }.getOrDefault(false)
        } else {
            runCatching {
                context.packageManager.resolveActivity(viewIntent(player.probeUri, null), 0) != null
            }.getOrDefault(false)
        }
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

        if (!resolver.resolve(player)) {
            return ExternalPlayResult.NotInstalled(player)
        }

        return if (starter.startActivity(player, launchUri)) {
            ExternalPlayResult.Success
        } else {
            ExternalPlayResult.LaunchFailed(
                request = request,
                cause = IllegalStateException("No activity handles $launchUri")
            )
        }
    }

    fun openDownloadPage(context: Context, player: ExternalPlayer) {
        try {
            context.startActivity(viewIntent(player.downloadUrl, null))
        } catch (_: ActivityNotFoundException) {
            // 无浏览器等极端场景静默（已有 Toast 提示过未安装）
        }
    }

    private fun viewIntent(uri: String, packageName: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            // 与 AppNavigator 一致：NEW_TASK 保证任意 Context 下均可安全启动外部 App
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 显式定向官方包，避免同 scheme 的第三方客户端弹出系统选择器
            if (packageName != null) setPackage(packageName)
        }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
