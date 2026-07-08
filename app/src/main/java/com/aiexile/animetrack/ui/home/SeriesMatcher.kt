package com.aiexile.animetrack.ui.home

import android.util.Log
import com.aiexile.animetrack.model.Anime

object SeriesMatcher {
    private const val TAG = "SeriesMatcher"

    /**
     * 季数后缀正则列表：按优先级从高到低排列
     *
     * 设计要点：
     * - 对"第X季/期/章"和罗马数字格式使用 \s*（允许无空格）
     * - 对"S2"、"Season X"等英文格式保持 \s+（需空格分隔，避免误匹配）
     * - CJK字符后直接跟数字（如"骸骨骑士大人异世界冒险中2"）使用 lookbehind
     * - 所有正则以 $ 结尾，确保只匹配末尾的季数后缀
     * - (?:\s*\(.*\))? 匹配可选的括号注释
     */
    private val seasonSuffixPatterns = listOf(
        // "第X季" 格式（中文数字 + 阿拉伯数字，\s* 支持无空格）
        Regex("""\s*第[一二三四五六七八九十百千万\d]+季(?:\s*\(.*\))?$"""),
        // "第X期" 格式
        Regex("""\s*第[一二三四五六七八九十百千万\d]+期(?:\s*\(.*\))?$"""),
        // "第X章" 格式
        Regex("""\s*第[一二三四五六七八九十百千万\d]+章(?:\s*\(.*\))?$"""),
        // "最终季" 格式
        Regex("""\s*最终季(?:\s*\(.*\))?$"""),
        // "完結編" / "完結篇" 格式
        Regex("""\s*完結[編篇](?:\s*\(.*\))?$"""),
        // CJK字符后直接跟数字（如"骸骨骑士大人异世界冒险中2"）
        Regex("""(?<=[\u4e00-\u9fff])\d+(?:\s*\(.*\))?$"""),
        // "SX" 格式（\s+ 需空格，避免误匹配标题中的 S）
        Regex("""\s+S\d+(?:\s*\(.*\))?$"""),
        // "Season X" 格式
        Regex("""\s+Season\s*\d+(?:\s*\(.*\))?$"""),
        // "X期" 格式（无"第"字，\s+ 需空格避免误匹配）
        Regex("""\s+\d+期(?:\s*\(.*\))?$"""),
        // 罗马数字格式（\s* 支持无空格，如"OVERLORDⅡ"）
        Regex("""\s*[ⅡⅢⅣⅤⅥⅦⅧⅨⅩ](?:\s*\(.*\))?$"""),
        // "Xst/nd/rd/th Season" 格式
        Regex("""\s+\d+(?:st|nd|rd|th)\s+Season(?:\s*\(.*\))?$"""),
        // "Final Season" 格式
        Regex("""\s+Final\s+Season(?:\s*\(.*\))?$"""),
    )

    // baseTitle 缓存：避免对同一标题重复执行正则匹配
    private val baseTitleCache = mutableMapOf<String, String>()

    fun extractBaseTitle(title: String): String {
        baseTitleCache[title]?.let { return it }

        for (pattern in seasonSuffixPatterns) {
            val result = pattern.replaceFirst(title, "")
            if (result != title && result.isNotBlank()) {
                val baseTitle = result.trim()
                baseTitleCache[title] = baseTitle
                return baseTitle
            }
        }
        baseTitleCache[title] = title.trim()
        return title.trim()
    }

    // 中文数字 → 阿拉伯数字映射（仅 1-10，足够季数使用）
    private val chineseNumberMap = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10
    )

    // 罗马数字 → 阿拉伯数字
    private val romanNumeralMap = mapOf(
        'Ⅱ' to 2, 'Ⅲ' to 3, 'Ⅳ' to 4, 'Ⅴ' to 5,
        'Ⅵ' to 6, 'Ⅶ' to 7, 'Ⅷ' to 8, 'Ⅸ' to 9, 'Ⅹ' to 10
    )

    /**
     * 从标题中提取季数（1-based）。
     * - 无季数后缀（即 baseTitle）= 1
     * - "第X季/期/章"（中文数字/阿拉伯数字）= X
     * - "S\d+" / "Season \d+" / "\d+期" / "\d+(st|nd|rd|th) Season" = 数字
     * - CJK 字符后直接跟数字（如"冒险中2"）= 该数字
     * - 罗马数字 Ⅱ-Ⅹ = 2-10
     * - "最终季" / "完结编/篇" / "Final Season" = 99（排到末尾）
     *
     * 结果用于对同系列多季排序，确保第一季在前。
     */
    fun extractSeasonNumber(title: String): Int {
        // 最终季/完结编/Final Season → 排末尾
        if (title.contains("最终季") || title.contains("完結編") || title.contains("完结篇") ||
            title.contains("Final Season", ignoreCase = true)
        ) {
            return 99
        }

        // "第X季/期/章"（中文数字或阿拉伯数字）
        Regex("""第([一二三四五六七八九十百\d]+)[季期章]""").find(title)?.let { m ->
            return parseChineseOrArabic(m.groupValues[1])
        }

        // "S2" / "Season 2" / "2期" / "2nd Season"
        Regex("""S(\d+)""", RegexOption.IGNORE_CASE).find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""(\d+)(?:st|nd|rd|th)\s+Season""", RegexOption.IGNORE_CASE).find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""\s(\d+)期""").find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

        // CJK 字符后直接跟数字（如"骸骨骑士大人异世界冒险中2"）
        Regex("""(?<=[\u4e00-\u9fff])(\d+)$""").find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

        // 罗马数字（末尾单个罗马数字字符）
        for ((ch, num) in romanNumeralMap) {
            if (title.endsWith(ch)) return num
        }

        // 无季数后缀 = 第一季
        return 1
    }

    private fun parseChineseOrArabic(s: String): Int {
        s.toIntOrNull()?.let { return it }
        // 纯中文数字（如 "二" "十" "二十三"）
        if (s.all { it in chineseNumberMap }) {
            return s.sumOf { chineseNumberMap[it] ?: 0 }.let { if (it == 0) 1 else it }
        }
        // 含"十"的复合（如 "二十三" "十一"）—— 简单处理
        if ('十' in s) {
            val parts = s.split('十')
            val tens = if (parts[0].isEmpty()) 1 else parts[0].toIntOrNull() ?: run {
                parts[0].sumOf { chineseNumberMap[it] ?: 0 }
            }
            val ones = if (parts.size > 1 && parts[1].isNotEmpty()) {
                parts[1].toIntOrNull() ?: parts[1].sumOf { chineseNumberMap[it] ?: 0 }
            } else 0
            return tens * 10 + ones
        }
        return 1
    }

    fun groupAnimeList(animeList: List<Anime>): List<AnimeListItem> {
        // 优先按 seriesKey 分组（持久化识别结果），无 seriesKey 的回退到原标题匹配
        val seriesKeyMap = mutableMapOf<String, MutableList<Anime>>()
        val baseTitleMap = mutableMapOf<String, MutableList<Anime>>()
        val orderMap = mutableMapOf<String, Int>()

        for ((index, anime) in animeList.withIndex()) {
            // 优先使用 seriesKey
            val groupKey = anime.seriesKey ?: extractBaseTitle(anime.title)
            val useSeriesKey = anime.seriesKey != null
            val targetMap = if (useSeriesKey) seriesKeyMap else baseTitleMap
            targetMap.getOrPut(groupKey) { mutableListOf() }.add(anime)
            if (groupKey !in orderMap) orderMap[groupKey] = index
        }

        val result = mutableListOf<AnimeListItem>()
        val groupedIds = mutableSetOf<Int>()

        // 处理 seriesKey 分组（已持久化识别的系列）
        for ((key, animes) in seriesKeyMap) {
            if (animes.size >= 2) {
                val sortedAnimes = animes.sortedWith(
                    compareBy<Anime> { extractSeasonNumber(it.title) }
                        .thenBy { it.airDate ?: "" }
                        .thenBy { it.id }
                )
                result.add(AnimeListItem.Series(key, sortedAnimes))
                sortedAnimes.forEach { groupedIds.add(it.id) }
                Log.d(TAG, "grouped(seriesKey) '$key': ${sortedAnimes.size} items, order=${sortedAnimes.map { extractSeasonNumber(it.title) to it.title }}")
            }
        }

        // 处理 baseTitle 分组（无 seriesKey 的回退逻辑，兼容旧数据）
        for ((baseTitle, animes) in baseTitleMap) {
            if (animes.size >= 2) {
                val hasSeasonSuffix = animes.any { extractBaseTitle(it.title) != it.title.trim() }
                if (hasSeasonSuffix) {
                    val sortedAnimes = animes.sortedWith(
                        compareBy<Anime> { extractSeasonNumber(it.title) }
                            .thenBy { it.airDate ?: "" }
                            .thenBy { it.id }
                    )
                    result.add(AnimeListItem.Series(baseTitle, sortedAnimes))
                    sortedAnimes.forEach { groupedIds.add(it.id) }
                    Log.d(TAG, "grouped(baseTitle) '$baseTitle': ${sortedAnimes.size} items, order=${sortedAnimes.map { extractSeasonNumber(it.title) to it.title }}")
                }
            }
        }

        for (anime in animeList) {
            if (anime.id !in groupedIds) {
                result.add(AnimeListItem.Single(anime))
            }
        }

        return result.sortedBy { item ->
            when (item) {
                is AnimeListItem.Single -> animeList.indexOf(item.anime)
                is AnimeListItem.Series -> orderMap[item.baseTitle] ?: Int.MAX_VALUE
                is AnimeListItem.ExpandedSeriesCard -> animeList.indexOf(item.anime)
            }
        }
    }

    /**
     * 为番剧列表分配 seriesKey（= baseTitle），持久化识别结果。
     * - 同 baseTitle 有 ≥2 个且至少一个有季数后缀 → 该组所有 anime 的 seriesKey = baseTitle
     * - 否则 seriesKey = null
     *
     * 返回更新后的 anime 列表（仅 seriesKey 变化的项需要持久化）。
     */
    fun assignSeriesKeys(animeList: List<Anime>): List<Anime> {
        val baseTitleMap = mutableMapOf<String, MutableList<Anime>>()
        for (anime in animeList) {
            val baseTitle = extractBaseTitle(anime.title)
            baseTitleMap.getOrPut(baseTitle) { mutableListOf() }.add(anime)
        }

        val seriesKeyByBaseTitle = mutableMapOf<String, String?>()
        for ((baseTitle, animes) in baseTitleMap) {
            if (animes.size >= 2) {
                val hasSeasonSuffix = animes.any { extractBaseTitle(it.title) != it.title.trim() }
                seriesKeyByBaseTitle[baseTitle] = if (hasSeasonSuffix) baseTitle else null
            } else {
                seriesKeyByBaseTitle[baseTitle] = null
            }
        }

        return animeList.map { anime ->
            val baseTitle = extractBaseTitle(anime.title)
            val newKey = seriesKeyByBaseTitle[baseTitle]
            if (anime.seriesKey != newKey) {
                anime.copy(seriesKey = newKey)
            } else {
                anime
            }
        }
    }

    /** 清除缓存（如番剧列表大幅变化时调用） */
    fun clearCache() {
        baseTitleCache.clear()
    }
}
