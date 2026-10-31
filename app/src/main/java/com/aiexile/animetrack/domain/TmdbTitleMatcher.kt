package com.aiexile.animetrack.domain

import com.aiexile.animetrack.util.normalizeTitleForMatch

/** TMDB 搜索结果中参与匹配的条目（与网络 DTO 解耦，便于纯 JVM 单测） */
data class TmdbMatchCandidate(
    val id: Int,
    val name: String,
    val originalName: String = "",
    val firstAirDate: String? = null
)

/**
 * 按标题把本地番剧精确匹配到 TMDB 条目。
 *
 * 只做「完全对齐」：归一化后的标题与候选的 name / original_name 完全一致才算命中，
 * 不做包含或模糊匹配；命中多个同名候选时用首播年份消歧，仍无法确定则返回 null，
 * 由详情页保留手动匹配入口。
 */
object TmdbTitleMatcher {

    fun pickExact(
        title: String,
        airDate: String?,
        candidates: List<TmdbMatchCandidate>
    ): TmdbMatchCandidate? {
        val target = normalizeTitleForMatch(title)
        if (target.isEmpty()) return null

        val exact = candidates.filter { candidate ->
            candidate.id > 0 && (
                normalizeTitleForMatch(candidate.name) == target ||
                    normalizeTitleForMatch(candidate.originalName) == target
                )
        }
        if (exact.isEmpty()) return null

        // 同名多候选（如重制版）时用首播年份消歧；唯一候选直接采纳
        val localYear = yearOf(airDate)
        if (exact.size > 1 && localYear != null) {
            exact.firstOrNull { yearOf(it.firstAirDate) == localYear }?.let { return it }
        }
        return exact.singleOrNull()
    }

    private fun yearOf(date: String?): Int? =
        date?.takeIf { it.length >= 4 }?.take(4)?.toIntOrNull()
}
