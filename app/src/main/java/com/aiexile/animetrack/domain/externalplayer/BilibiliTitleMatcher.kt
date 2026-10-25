package com.aiexile.animetrack.domain.externalplayer

import java.text.Normalizer
import kotlin.math.abs

/** B 站搜索结果中参与匹配的番剧条目（与网络 DTO 解耦，便于纯 JVM 单测） */
data class BilibiliSeasonCandidate(
    val seasonId: Int,
    val title: String,
    val orgTitle: String = "",
    val epSize: Int = 0
)

/**
 * 按标题把本地番剧匹配到 B 站番剧条目。
 *
 * B 站搜索是字面匹配且结果常混有中配版/其他季，单靠「包含关键词」会跳错。
 * 这里用标题归一化打分 + 季数/中配排除 + 集数辅助，置信度不足时返回 null，
 * 由调用方回退到搜索页跳转（最坏情况多一次点击，绝不跳到别的番剧）。
 */
object BilibiliTitleMatcher {

    private val HTML_TAG = Regex("<[^>]+>")
    private val SEASON_CN = Regex("第\\s*([一二三四五六七八九十\\d]+)\\s*[季期]")
    private val SEASON_EN = Regex("season\\s*(\\d+)", RegexOption.IGNORE_CASE)

    /** 配音版本标识：候选带这些后缀而本地标题没有时，视为同名不同版本 */
    private val DUB_TOKENS = listOf(
        "中配", "中配版", "中文配音", "国语", "国语版",
        "粤配", "粤配版", "粤语", "粤语版", "台配", "台配版", "台语",
        "日语", "日配", "英配", "英文配音", "海外版"
    )

    private const val SCORE_EXACT = 100
    private const val SCORE_ORG_TITLE = 90
    private const val SCORE_CONTAINS = 60
    private const val SCORE_CONTAINED_BY = 50
    private const val MIN_ACCEPTED_SCORE = 50

    /**
     * @param title 本地番剧标题
     * @param totalEpisodes 本地总集数（0/null 表示未知）
     * @param seasonNumber 关系链推导的季数（null 表示未知）
     * @param candidates B 站搜索结果
     */
    fun pick(
        title: String,
        totalEpisodes: Int?,
        seasonNumber: Int?,
        candidates: List<BilibiliSeasonCandidate>
    ): BilibiliSeasonCandidate? {
        if (title.isBlank() || candidates.isEmpty()) return null

        val localTitle = clean(title)
        val localNormalized = normalize(localTitle)
        if (localNormalized.isEmpty()) return null
        val localSeason = seasonNumber?.takeIf { it in 1..99 } ?: parseSeason(localTitle)

        return candidates
            .asSequence()
            .filter { it.seasonId > 0 }
            .mapNotNull { candidate -> score(candidate, localTitle, localNormalized, localSeason, totalEpisodes) }
            .filter { it.first >= MIN_ACCEPTED_SCORE }
            .maxWithOrNull(
                compareBy<Pair<Int, BilibiliSeasonCandidate>> { it.first }
                    .thenBy { candidate -> episodeDistance(candidate.second.epSize, totalEpisodes) }
            )
            ?.second
    }

    /** 返回 (得分, 候选)；不满足硬性条件时返回 null */
    private fun score(
        candidate: BilibiliSeasonCandidate,
        localTitle: String,
        localNormalized: String,
        localSeason: Int?,
        totalEpisodes: Int?
    ): Pair<Int, BilibiliSeasonCandidate>? {
        val candidateTitle = clean(candidate.title)
        if (isDubMismatch(candidateTitle, localTitle)) return null

        val candidateSeason = parseSeason(candidateTitle)
        if (localSeason != null && candidateSeason != null && localSeason != candidateSeason) return null
        // 本地明确是第 2 季及以后时，无季数后缀的条目基本是第一季，排除；
        // seasonNumber=99（剧场版/外传等）不适用该规则
        if (localSeason in 2..50 && candidateSeason == null) return null

        val candidateNormalized = normalize(candidateTitle)
        val orgNormalized = normalize(clean(candidate.orgTitle))

        val titleScore = when {
            candidateNormalized.isNotEmpty() && candidateNormalized == localNormalized -> SCORE_EXACT
            orgNormalized.isNotEmpty() && orgNormalized == localNormalized -> SCORE_ORG_TITLE
            candidateNormalized.length >= 3 && localNormalized.contains(candidateNormalized) -> SCORE_CONTAINED_BY
            localNormalized.length >= 3 && candidateNormalized.contains(localNormalized) -> SCORE_CONTAINS
            else -> 0
        }
        if (titleScore == 0) return null

        var total = titleScore
        if (localSeason != null && candidateSeason == localSeason) total += 10
        // 本地未标注季数时，无季数后缀的条目更像第一季，轻微加权
        if (localSeason == null && candidateSeason == null) total += 3
        total += when {
            totalEpisodes == null || totalEpisodes <= 0 || candidate.epSize <= 0 -> 0
            candidate.epSize == totalEpisodes -> 15
            abs(candidate.epSize - totalEpisodes) <= 1 -> 8
            abs(candidate.epSize - totalEpisodes) <= 3 -> 4
            else -> 0
        }
        return total to candidate
    }

    private fun episodeDistance(epSize: Int, totalEpisodes: Int?): Int {
        if (totalEpisodes == null || totalEpisodes <= 0 || epSize <= 0) return Int.MAX_VALUE / 2
        return abs(epSize - totalEpisodes)
    }

    private fun isDubMismatch(candidateTitle: String, localTitle: String): Boolean =
        DUB_TOKENS.any { token -> candidateTitle.contains(token) && !localTitle.contains(token) }

    private fun parseSeason(title: String): Int? {
        SEASON_CN.find(title)?.groupValues?.getOrNull(1)?.let { return chineseNumberToInt(it) }
        SEASON_EN.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        return null
    }

    private fun chineseNumberToInt(raw: String): Int? {
        if (raw.all { it.isDigit() }) return raw.toIntOrNull()
        val digits = mapOf(
            '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
            '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        return when {
            raw == "十" -> 10
            raw.startsWith("十") -> 10 + (raw.getOrNull(1)?.let { digits[it] } ?: 0)
            raw.endsWith("十") -> (raw.firstOrNull()?.let { digits[it] } ?: return null) * 10
            raw.contains("十") -> {
                val parts = raw.split("十")
                if (parts.size != 2) return null
                val tens = parts[0].firstOrNull()?.let { digits[it] } ?: return null
                tens * 10 + (parts[1].firstOrNull()?.let { digits[it] } ?: 0)
            }
            else -> raw.firstOrNull()?.let { digits[it] }
        }
    }

    private fun clean(text: String): String =
        Normalizer.normalize(HTML_TAG.replace(text, ""), Normalizer.Form.NFKC).trim()

    /** 去掉空白与常见标点后用于比较（【我推的孩子】与「我推的孩子」视为同一标题） */
    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }
}
