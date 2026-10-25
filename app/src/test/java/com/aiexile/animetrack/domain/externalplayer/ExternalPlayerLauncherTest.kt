package com.aiexile.animetrack.domain.externalplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 启动协调器全分支：成功 / 未注册 / 未关联 / 未安装 / 唤起失败；注册表查找 */
class ExternalPlayerLauncherTest {

    private val installedResolver = IntentResolver { true }
    private val okStarter = ActivityStarter { _, _ -> true }

    private fun request(bangumiId: Int?) =
        ExternalPlayRequest(animeId = 1, bangumiId = bangumiId, title = "测试", episodeSort = 5)

    @Test
    fun `校验全部通过时返回 Success`() {
        val result = ExternalPlayerLauncher.launch(
            request = request(12),
            playerId = ExternalPlayerId.ANIMEKO,
            resolver = installedResolver,
            starter = okStarter
        )
        assertEquals(ExternalPlayResult.Success, result)
    }

    @Test
    fun `播放器未注册时返回 LaunchFailed`() {
        val result = ExternalPlayerLauncher.launch(
            request = request(12),
            playerId = ExternalPlayerId.ANIMEKO,
            resolver = installedResolver,
            starter = okStarter,
            players = emptyList()
        )
        assertTrue(result is ExternalPlayResult.LaunchFailed)
    }

    @Test
    fun `未关联 Bangumi 时返回 MissingBangumiLink 且不触发探测与启动`() {
        var resolved = false
        var started = false
        val result = ExternalPlayerLauncher.launch(
            request = request(null),
            playerId = ExternalPlayerId.ANIMEKO,
            resolver = IntentResolver { resolved = true; true },
            starter = ActivityStarter { _, _ -> started = true; true }
        )
        assertTrue(result is ExternalPlayResult.MissingBangumiLink)
        assertEquals(false, resolved)
        assertEquals(false, started)
    }

    @Test
    fun `未安装时返回 NotInstalled 且不尝试启动`() {
        var started = false
        val result = ExternalPlayerLauncher.launch(
            request = request(12),
            playerId = ExternalPlayerId.ANIMEKO,
            resolver = IntentResolver { false },
            starter = ActivityStarter { _, _ -> started = true; true }
        )
        assertEquals(ExternalPlayResult.NotInstalled(AnimekoPlayer), result)
        assertEquals(false, started)
    }

    @Test
    fun `探测通过但启动失败时返回 LaunchFailed`() {
        val result = ExternalPlayerLauncher.launch(
            request = request(12),
            playerId = ExternalPlayerId.ANIMEKO,
            resolver = installedResolver,
            starter = ActivityStarter { _, _ -> false }
        )
        assertTrue(result is ExternalPlayResult.LaunchFailed)
    }

    @Test
    fun `注册表按 id 查找返回对应实现`() {
        assertEquals(AnimekoPlayer, ExternalPlayerRegistry.byId(ExternalPlayerId.ANIMEKO))
        assertTrue(ExternalPlayerRegistry.all.contains(AnimekoPlayer))
    }
}
