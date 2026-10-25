package com.aiexile.animetrack.domain.externalplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 标题匹配：中配排除、季数校验、括号归一化、集数辅助 */
class BilibiliTitleMatcherTest {

    private fun candidate(
        id: Int,
        title: String,
        orgTitle: String = "",
        epSize: Int = 0
    ) = BilibiliSeasonCandidate(id, title, orgTitle, epSize)

    @Test
    fun `同名条目优先原版而非中配版`() {
        val picked = BilibiliTitleMatcher.pick(
            title = "葬送的芙莉莲",
            totalEpisodes = 28,
            seasonNumber = null,
            candidates = listOf(
                candidate(102261, "<em class=\"keyword\">葬送的芙莉莲</em> 中配版", "葬送のフリーレン", 28),
                candidate(46089, "<em class=\"keyword\">葬送的芙莉莲</em>", "葬送のフリーレン", 28)
            )
        )
        assertEquals(46089, picked?.seasonId)
    }

    @Test
    fun `季数不匹配的候选被排除`() {
        val picked = BilibiliTitleMatcher.pick(
            title = "间谍过家家 第二季",
            totalEpisodes = 12,
            seasonNumber = 2,
            candidates = listOf(
                candidate(112827, "间谍过家家 第三季", "SPY×FAMILY Season 3", 3),
                candidate(47794, "间谍过家家 第二季 中配版", "SPY×FAMILY Season 2", 12),
                candidate(46085, "间谍过家家 第二季", "SPY×FAMILY Season 2", 12)
            )
        )
        assertEquals(46085, picked?.seasonId)
    }

    @Test
    fun `第一季匹配无季数后缀的条目`() {
        val picked = BilibiliTitleMatcher.pick(
            title = "间谍过家家",
            totalEpisodes = 25,
            seasonNumber = 1,
            candidates = listOf(
                candidate(112827, "间谍过家家 第三季", epSize = 3),
                candidate(46085, "间谍过家家 第二季", epSize = 12),
                candidate(43622, "间谍过家家 中配版", epSize = 25),
                candidate(41410, "间谍过家家", epSize = 25)
            )
        )
        assertEquals(41410, picked?.seasonId)
    }

    @Test
    fun `书名号等标点不影响精确匹配`() {
        val picked = BilibiliTitleMatcher.pick(
            title = "我推的孩子",
            totalEpisodes = 13,
            seasonNumber = null,
            candidates = listOf(candidate(1, "【我推的孩子】", "【推しの子】", 13))
        )
        assertEquals(1, picked?.seasonId)
    }

    @Test
    fun `集数接近的候选优先`() {
        val picked = BilibiliTitleMatcher.pick(
            title = "孤独摇滚",
            totalEpisodes = 24,
            seasonNumber = null,
            candidates = listOf(
                candidate(100, "孤独摇滚！", epSize = 12),
                candidate(200, "孤独摇滚", epSize = 24)
            )
        )
        assertEquals(200, picked?.seasonId)
    }

    @Test
    fun `无关标题返回 null 由调用方回退搜索页`() {
        assertNull(
            BilibiliTitleMatcher.pick(
                title = "不存在的番剧XYZ",
                totalEpisodes = 12,
                seasonNumber = null,
                candidates = listOf(candidate(1, "别的番剧", epSize = 12))
            )
        )
    }

    @Test
    fun `英文 Season 标记同样参与季数校验`() {
        val picked = BilibiliTitleMatcher.pick(
            title = "SPY×FAMILY",
            totalEpisodes = 12,
            seasonNumber = 2,
            candidates = listOf(
                candidate(1, "SPY×FAMILY Season 3", epSize = 12),
                candidate(2, "SPY×FAMILY Season 2", epSize = 12)
            )
        )
        assertEquals(2, picked?.seasonId)
    }
}
