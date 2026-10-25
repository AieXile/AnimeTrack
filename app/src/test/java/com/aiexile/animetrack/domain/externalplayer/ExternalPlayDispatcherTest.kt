package com.aiexile.animetrack.domain.externalplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 自动/手动模式下的目标决策 */
class ExternalPlayDispatcherTest {

    private val both = setOf(ExternalPlayerId.ANIMEKO, ExternalPlayerId.BILIBILI)

    @Test
    fun `自动 两应用都装且有 season_id 时直达哔哩哔哩`() {
        assertEquals(
            ExternalPlayDecision.Launch(ExternalPlayerId.BILIBILI),
            ExternalPlayDispatcher.decide(ExternalPlayMode.AUTO, both, hasBilibiliSeasonId = true)
        )
    }

    @Test
    fun `自动 两应用都装但没有 season_id 时询问用户`() {
        assertEquals(
            ExternalPlayDecision.AskUser,
            ExternalPlayDispatcher.decide(ExternalPlayMode.AUTO, both, hasBilibiliSeasonId = false)
        )
    }

    @Test
    fun `自动 只装 Animeko 时用 Animeko`() {
        assertEquals(
            ExternalPlayDecision.Launch(ExternalPlayerId.ANIMEKO),
            ExternalPlayDispatcher.decide(
                ExternalPlayMode.AUTO,
                setOf(ExternalPlayerId.ANIMEKO),
                hasBilibiliSeasonId = true
            )
        )
    }

    @Test
    fun `自动 只装哔哩哔哩时用哔哩哔哩`() {
        assertEquals(
            ExternalPlayDecision.Launch(ExternalPlayerId.BILIBILI),
            ExternalPlayDispatcher.decide(
                ExternalPlayMode.AUTO,
                setOf(ExternalPlayerId.BILIBILI),
                hasBilibiliSeasonId = false
            )
        )
    }

    @Test
    fun `自动 都未安装时弹窗展示下载入口`() {
        assertEquals(
            ExternalPlayDecision.AskUser,
            ExternalPlayDispatcher.decide(ExternalPlayMode.AUTO, emptySet(), hasBilibiliSeasonId = false)
        )
    }

    @Test
    fun `显式模式忽略安装与关联状态`() {
        assertEquals(
            ExternalPlayDecision.Launch(ExternalPlayerId.ANIMEKO),
            ExternalPlayDispatcher.decide(ExternalPlayMode.ANIMEKO, emptySet(), hasBilibiliSeasonId = false)
        )
        assertEquals(
            ExternalPlayDecision.Launch(ExternalPlayerId.BILIBILI),
            ExternalPlayDispatcher.decide(ExternalPlayMode.BILIBILI, emptySet(), hasBilibiliSeasonId = true)
        )
        assertEquals(
            ExternalPlayDecision.AskUser,
            ExternalPlayDispatcher.decide(ExternalPlayMode.ASK, both, hasBilibiliSeasonId = true)
        )
    }
}
