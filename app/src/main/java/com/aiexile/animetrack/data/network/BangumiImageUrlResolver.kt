package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer

/**
 * Bangumi 封面 URL 的运行时策略（单一入口，避免各处重复拼 wsrv.nl）。
 *
 * - 开启「Bangumi 镜像」：`lain.bgm.tv/<path>` → `https://<镜像 host>/v0/bgmimg/<path>`，
 *   由自有服务器经 SOCKS 回源官方图片；
 * - 未开启：保持原有的 `https://wsrv.nl/?url=<原图>` 公共图片代理；
 * - 非 lain.bgm.tv URL 原样返回，本地路径/null 由调用方处理。
 */
object BangumiImageUrlResolver {

    private const val LAIN_PREFIX = "lain.bgm.tv/"
    private const val WSRV_PREFIX = "https://wsrv.nl/?url="
    private const val MIRROR_IMAGE_PATH = "/v0/bgmimg/"
    private val MIRROR_PREFIX =
        "https://${SettingsRepository.BANGUMI_MIRROR_HOST}$MIRROR_IMAGE_PATH"

    fun resolve(url: String?): String? {
        val raw = url?.takeIf { it.isNotBlank() } ?: return null
        // 兼容已存成 wsrv / 镜像 URL 的历史数据，先还原成原图再按当前开关决策，
        // 保证开关切换后旧 URL 也能跟着切换，且不会二次包装。
        val direct = when {
            raw.startsWith(WSRV_PREFIX) -> raw.removePrefix(WSRV_PREFIX)
            raw.startsWith(MIRROR_PREFIX) ->
                "https://lain.bgm.tv/" + raw.removePrefix(MIRROR_PREFIX)
            else -> raw
        }
        val index = direct.indexOf(LAIN_PREFIX)
        if (index < 0) return raw
        val imagePath = direct.substring(index + LAIN_PREFIX.length)
        if (imagePath.isEmpty()) return raw

        val mirrorEnabled = runCatching {
            AppContainer.getSettingsRepository().bangumiMirrorEnabled
        }.getOrDefault(false)

        return if (mirrorEnabled) {
            "https://${SettingsRepository.BANGUMI_MIRROR_HOST}$MIRROR_IMAGE_PATH$imagePath"
        } else {
            "$WSRV_PREFIX$direct"
        }
    }
}
