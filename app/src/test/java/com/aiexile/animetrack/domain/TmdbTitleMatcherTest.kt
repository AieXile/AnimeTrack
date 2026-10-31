package com.aiexile.animetrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TMDB 自动匹配：只接受完全对齐的标题，同名多候选按年份消歧 */
class TmdbTitleMatcherTest {

    private fun candidate(
        id: Int,
        name: String,
        originalName: String = "",
        firstAirDate: String? = null
    ) = TmdbMatchCandidate(id, name, originalName, firstAirDate)

    @Test
    fun `中文标题完全一致时命中`() {
        val picked = TmdbTitleMatcher.pickExact(
            title = "葬送的芙莉莲",
            airDate = "2023-09-29",
            candidates = listOf(
                candidate(1, "葬送的芙莉莲", "葬送のフリーレン", "2023-09-29"),
                candidate(2, "葬送的芙莉莲 第二季", "葬送のフリーレン 第2期", "2026-01-16")
            )
        )
        assertEquals(1, picked?.id)
    }

    @Test
    fun `标点空白与全半角不影响完全匹配`() {
        val picked = TmdbTitleMatcher.pickExact(
            title = "【我推的孩子】",
            airDate = null,
            candidates = listOf(candidate(1, "我推的孩子", "【推しの子】"))
        )
        assertEquals(1, picked?.id)
    }

    @Test
    fun `本地为日文原名时可用 original_name 命中`() {
        val picked = TmdbTitleMatcher.pickExact(
            title = "葬送のフリーレン",
            airDate = null,
            candidates = listOf(candidate(1, "葬送的芙莉莲", "葬送のフリーレン"))
        )
        assertEquals(1, picked?.id)
    }

    @Test
    fun `包含关系不算完全对齐`() {
        assertNull(
            TmdbTitleMatcher.pickExact(
                title = "进击的巨人",
                airDate = null,
                candidates = listOf(candidate(1, "进击的巨人 最终季", "進撃の巨人 The Final Season"))
            )
        )
    }

    @Test
    fun `同名多候选按首播年份消歧`() {
        val picked = TmdbTitleMatcher.pickExact(
            title = "全职猎人",
            airDate = "2011-10-02",
            candidates = listOf(
                candidate(1, "全职猎人", "HUNTER×HUNTER", "1999-10-16"),
                candidate(2, "全职猎人", "HUNTER×HUNTER", "2011-10-02")
            )
        )
        assertEquals(2, picked?.id)
    }

    @Test
    fun `同名多候选且无法消歧时放弃自动匹配`() {
        assertNull(
            TmdbTitleMatcher.pickExact(
                title = "全职猎人",
                airDate = null,
                candidates = listOf(
                    candidate(1, "全职猎人", "HUNTER×HUNTER", "1999-10-16"),
                    candidate(2, "全职猎人", "HUNTER×HUNTER", "2011-10-02")
                )
            )
        )
    }

    @Test
    fun `空标题返回 null`() {
        assertNull(
            TmdbTitleMatcher.pickExact(
                title = "  ",
                airDate = null,
                candidates = listOf(candidate(1, "葬送的芙莉莲"))
            )
        )
    }
}
