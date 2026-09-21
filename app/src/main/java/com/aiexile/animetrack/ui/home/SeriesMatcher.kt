package com.aiexile.animetrack.ui.home

import android.util.Log
import com.aiexile.animetrack.data.BangumiSeriesResolver
import com.aiexile.animetrack.model.Anime

object SeriesMatcher {
    private const val TAG = "SeriesMatcher"

    /**
     * 可选的尾部副标题：季数标记后允许跟副标题（如"-起于暗影-""～XXX～"": Subtitle"）
     * 或括号注释。仅在季数标记匹配成功时一并剥离，避免误伤标题本体。
     */
    private const val TAIL = """(?:\s*[(（].*[)）]|\s*[-–—～~·:：].*)?"""

    /**
     * 季数/篇章后缀正则列表：按优先级从高到低排列
     *
     * 设计要点：
     * - 每条模式 = 「季数/篇章标记」+ [TAIL]（可选的尾部副标题）
     * - 对"第X季/期/章"和罗马数字格式使用 \s*（允许无空格）
     * - 对"S2"、"Season X"等英文格式保持 \s+（需空格分隔，避免误匹配）
     * - CJK字符后直接跟数字（如"骸骨骑士大人异世界冒险中2"）使用 lookbehind
     * - 篇章名规则（"游郭篇/刀匠村篇/番外編"等）剥离后靠 airDate 兜底排序
     * - 所有正则以 $ 结尾，确保只匹配末尾的后缀
     */
    private val seasonSuffixPatterns = listOf(
        // "第X季/期/部/章/篇/シリーズ/クール" + 副标题（如"我独自升级 第二季 -起于暗影-"）
        Regex("""\s*第[一二三四五六七八九十百千万\d]+(?:季|期|部|章|篇|シリーズ|クール)""" + TAIL + """$"""),
        // "X季/期/部/章/シリーズ/クール/シーズン"（数字开头，需空格，如"2期" "2クール"）
        Regex("""\s+\d+(?:季|期|部|章|シリーズ|クール|シーズン)""" + TAIL + """$"""),
        // "Xst/nd/rd/th Season"（如"2nd Season"）
        Regex("""\s+\d+(?:st|nd|rd|th)\s+Season""" + TAIL + """$""", RegexOption.IGNORE_CASE),
        // "Season X"（如"Season 2"）
        Regex("""\s+Season\s*\d+""" + TAIL + """$""", RegexOption.IGNORE_CASE),
        // "SX" 格式（\s+ 需空格，避免误匹配标题中的 S）
        Regex("""\s+S\d+""" + TAIL + """$"""),
        // 半角罗马数字（如"Overlord II"，V/X 单字符风险高不加）
        Regex("""\s+(?:II|III|IV|VI|VII|VIII|IX|XI|XII)""" + TAIL + """$"""),
        // 全角罗马数字格式（\s* 支持无空格，如"OVERLORDⅡ"）
        Regex("""\s*[ⅡⅢⅣⅤⅥⅦⅧⅨⅩ]""" + TAIL + """$"""),
        // "最終季/最终季/最終シーズン/ファイナルシーズン/Final Season"
        Regex("""\s*(?:最終季|最终季|最終シーズン|ファイナルシーズン)""" + TAIL + """$"""),
        Regex("""\s+Final\s+Season""" + TAIL + """$""", RegexOption.IGNORE_CASE),
        // "完結編/完結篇/完结篇/完结编"
        Regex("""\s*完[結结][編篇]""" + TAIL + """$"""),
        // "最終章/最終篇/最终章/最终篇/終章"
        Regex("""\s*(?:最終|最终)?[章篇]""" + TAIL + """$"""),
        // "前篇/後篇/前編/後編/前编/后编"（同部拆分，前=1 后=2）
        Regex("""\s*[前后後][编編篇]$"""),
        // "上卷/下卷/上巻/下巻"（上=1 下=2）
        Regex("""\s*[上下][卷巻]$"""),
        // 篇章名：<1-8 个汉字>+篇/編（游郭篇/刀匠村篇/无限城决战篇/番外編/総集編/特別編）
        Regex("""\s+[\u4e00-\u9fff]{1,8}[篇編]$"""),
        // "外传/外伝"
        Regex("""\s*(?:外传|外伝)$"""),
        // "劇場版/剧场版" 后缀（如"高达 剧场版"）
        Regex("""\s*(?:劇場版|剧场版)""" + TAIL + """$"""),
        // "OVA/OAD" 后缀
        Regex("""\s+(?:OVA|OAD)$""", RegexOption.IGNORE_CASE),
        // 波浪线/圆点包裹的整段尾部副标题（如"无职转生 ～到了异世界就拿出真本事～"）
        Regex("""\s*[～~][^～~]*[～~]$"""),
        // CJK字符后直接跟数字（如"骸骨骑士大人异世界冒险中2"）
        Regex("""(?<=[\u4e00-\u9fff])\d+""" + TAIL + """$"""),
    )

    /** "剧场版/劇場版" 前缀（如"剧场版 鬼灭之刃 无限列车篇"），剥离后可与 TV 系列归一 */
    private val moviePrefixPattern = Regex("""^(?:劇場版|剧场版)\s*""")

    /** 特殊类型关键词：识别为系列附属（总集篇/外传/剧场版等），季数排 99 */
    private val specialKeywords = listOf(
        "劇場版", "剧场版", "外传", "外伝", "番外", "总集篇", "总集编", "総集編", "総集篇",
        "特別編", "特別篇", "特别编", "特别篇", "THE MOVIE", "Movie版"
    )

    /**
     * 判断标题是否属于系列特殊类型（剧场版/外传/番外/总集编等）。
     * 这类条目按用户选择并入系列堆叠，但排在所有正季后（季数 99）。
     * 同时供 Bangumi 关系链解析复用（标题判断 + relation 入边判断双保险）。
     */
    fun isSpecialEntry(title: String): Boolean {
        return specialKeywords.any { title.contains(it, ignoreCase = true) } ||
            title.trim().endsWith("OVA", ignoreCase = true) ||
            title.trim().endsWith("OAD", ignoreCase = true)
    }

    // baseTitle 缓存：避免对同一标题重复执行正则匹配。
    // 使用 ConcurrentHashMap：列表派生计算现运行在 Default 线程池，可能跨线程访问。
    private val baseTitleCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun extractBaseTitle(title: String): String {
        baseTitleCache[title]?.let { return it }

        // 先剥离"剧场版"前缀（"剧场版 鬼灭之刃 无限列车篇" → "鬼灭之刃 无限列车篇"）
        var working = moviePrefixPattern.replaceFirst(title, "").trim()

        for (pattern in seasonSuffixPatterns) {
            val result = pattern.replaceFirst(working, "")
            if (result != working && result.isNotBlank()) {
                // 清理剥离后残留的尾部悬挂符号（如"XXX -"）
                val baseTitle = result.trim().trimEnd('-', '–', '—', '～', '~', '·', ':', '：').trim()
                if (baseTitle.isNotBlank()) {
                    baseTitleCache[title] = baseTitle
                    return baseTitle
                }
            }
        }
        baseTitleCache[title] = working
        return working
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

    // 半角罗马数字（单词形式）→ 阿拉伯数字
    private val romanWordMap = mapOf(
        "II" to 2, "III" to 3, "IV" to 4, "VI" to 6,
        "VII" to 7, "VIII" to 8, "IX" to 9, "XI" to 11, "XII" to 12
    )

    /**
     * 从标题中提取季数（1-based）。
     * - 无季数后缀（即 baseTitle，如"鬼灭之刃 游郭篇"）= 1，排序由 airDate 兜底
     * - "第X季/期/部/章/篇/シリーズ/クール"（中文数字/阿拉伯数字）= X
     * - "S\d+" / "Season \d+" / "\d+期" / "Xnd Season" / 罗马数字 = 数字
     * - 前篇/上卷 = 1，后篇/下卷 = 2
     * - 特殊类型（剧场版/外传/番外/总集编/OVA 等）= 99（排到所有正季后）
     * - "最终季/完結編/Final Season/最終章" = 99
     *
     * 结果用于对同系列多季排序，确保第一季在前。
     */
    fun extractSeasonNumber(title: String): Int {
        // 特殊类型（剧场版/外传/番外/总集编等）→ 排末尾
        if (isSpecialEntry(title)) return 99

        // 最终季/完结编/Final Season/最終章 → 排末尾
        if (title.contains("最终季") || title.contains("最終季") || title.contains("完結編") ||
            title.contains("完结篇") || title.contains("完結篇") || title.contains("完结编") ||
            title.contains("最終シーズン") || title.contains("ファイナルシーズン") ||
            title.contains("最終章") || title.contains("最终章") || title.contains("終章") ||
            title.contains("Final Season", ignoreCase = true)
        ) {
            return 99
        }

        // 前篇/上卷 = 1，後篇/下卷 = 2（同部拆分）
        if (Regex("""[前后後][编編篇]$""").containsMatchIn(title)) {
            return if (title.contains("前")) 1 else 2
        }
        if (Regex("""[上下][卷巻]$""").containsMatchIn(title)) {
            return if (title.contains("上")) 1 else 2
        }

        // "第X季/期/部/章/篇/シリーズ/クール"（中文数字或阿拉伯数字）
        Regex("""第([一二三四五六七八九十百\d]+)(?:季|期|部|章|篇|シリーズ|クール)""").find(title)?.let { m ->
            return parseChineseOrArabic(m.groupValues[1])
        }

        // "S2" / "Season 2" / "2nd Season" / "2期" / "2クール" / "2シリーズ" / "2シーズン"
        Regex("""S(\d+)""", RegexOption.IGNORE_CASE).find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE).find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""(\d+)(?:st|nd|rd|th)\s+Season""", RegexOption.IGNORE_CASE).find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        Regex("""\s(\d+)(?:季|期|部|章|シリーズ|クール|シーズン)""").find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

        // CJK 字符后直接跟数字（如"骸骨骑士大人异世界冒险中2"）
        Regex("""(?<=[\u4e00-\u9fff])(\d+)$""").find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

        // 半角罗马数字（末尾独立单词）
        Regex("""\s(II|III|IV|VI|VII|VIII|IX|XI|XII)$""").find(title)?.let { m ->
            romanWordMap[m.groupValues[1]]?.let { return it }
        }

        // 全角罗马数字（末尾单个罗马数字字符）
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
        // 组内不排序，保留进入时的主排序顺序（WATCHING 在前 + lastProgressAt 降序）；
        // 季数排序推迟到 displayList，仅在多季堆叠开启时应用
        for ((key, animes) in seriesKeyMap) {
            if (animes.size >= 2) {
                result.add(AnimeListItem.Series(key, animes))
                animes.forEach { groupedIds.add(it.id) }
                Log.d(TAG, "grouped(seriesKey) '$key': ${animes.size} items")
            }
        }

        // 处理 baseTitle 分组（无 seriesKey 的回退逻辑，兼容旧数据）
        for ((baseTitle, animes) in baseTitleMap) {
            if (animes.size >= 2) {
                val hasSeasonSuffix = animes.any { extractBaseTitle(it.title) != it.title.trim() }
                if (hasSeasonSuffix) {
                    result.add(AnimeListItem.Series(baseTitle, animes))
                    animes.forEach { groupedIds.add(it.id) }
                    Log.d(TAG, "grouped(baseTitle) '$baseTitle': ${animes.size} items")
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
     * - [bangumiOverrides]：Bangumi 关系链解析结果（权威数据，优先于正则）。
     *   被覆盖的条目使用 Bangumi 的 seriesKey/seasonNumber；正则分组仅对剩余条目生效。
     *   未覆盖条目保留现有 seasonNumber（不主动清空，由关系链全量重算负责）。
     *
     * 返回更新后的 anime 列表（仅 seriesKey/seasonNumber 变化的项需要持久化）。
     */
    fun assignSeriesKeys(
        animeList: List<Anime>,
        bangumiOverrides: Map<Int, BangumiSeriesResolver.SeriesAssignment> = emptyMap()
    ): List<Anime> {
        val overriddenIds = bangumiOverrides.keys
        val baseTitleMap = mutableMapOf<String, MutableList<Anime>>()
        // 正则分组仅对未被 Bangumi 覆盖的条目生效（避免正则组与关系链组混叠）
        for (anime in animeList) {
            if (anime.id in overriddenIds) continue
            val baseTitle = extractBaseTitle(anime.title)
            baseTitleMap.getOrPut(baseTitle) { mutableListOf() }.add(anime)
        }

        // 被覆盖条目若其 seriesKey 恰为某 baseTitle，仍计入该组的成组资格：
        // 场景：第一二季被关系链覆盖到 key="X"，第三季因 bangumiId 无效（=0 脏数据）
        // 或拉取失败未被覆盖。若只数剩余条目，正则组"X"会从 ≥2 退化为 1 部，
        // 第三季将被误判为单部不成组（seriesKey 被清空 → 从堆叠中拆散）。
        val overrideKeyCounts = bangumiOverrides.values
            .filter { it.seriesKey != null }
            .groupingBy { it.seriesKey!! }
            .eachCount()

        val seriesKeyByBaseTitle = mutableMapOf<String, String?>()
        for ((baseTitle, animes) in baseTitleMap) {
            val overrideCount = overrideKeyCounts[baseTitle] ?: 0
            if (animes.size + overrideCount >= 2) {
                // 未覆盖条目自身有季数后缀，或同组存在 Bangumi 已确认的系列成员
                val hasSeasonSuffix = animes.any { extractBaseTitle(it.title) != it.title.trim() } ||
                    overrideCount > 0
                seriesKeyByBaseTitle[baseTitle] = if (hasSeasonSuffix) baseTitle else null
            } else {
                seriesKeyByBaseTitle[baseTitle] = null
            }
        }

        return animeList.map { anime ->
            val override = bangumiOverrides[anime.id]
            if (override != null) {
                if (anime.seriesKey != override.seriesKey || anime.seasonNumber != override.seasonNumber) {
                    anime.copy(seriesKey = override.seriesKey, seasonNumber = override.seasonNumber)
                } else {
                    anime
                }
            } else {
                val baseTitle = extractBaseTitle(anime.title)
                val newKey = seriesKeyByBaseTitle[baseTitle]
                if (anime.seriesKey != newKey) {
                    anime.copy(seriesKey = newKey)
                } else {
                    anime
                }
            }
        }
    }

    /** 清除缓存（如番剧列表大幅变化时调用） */
    fun clearCache() {
        baseTitleCache.clear()
    }
}
