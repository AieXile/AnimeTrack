package com.aiexile.animetrack.ui.home

import android.util.Log
import com.aiexile.animetrack.data.BangumiSeriesResolver
import com.aiexile.animetrack.model.Anime

object SeriesMatcher {
    private const val JOJO = "JOJO的奇妙冒险"
    private const val KAMISAMA = "元气少女缘结神"

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

    // 提取季数会被排序比较器重复调用；预编译相同规则，避免每次比较都编译正则。
    private val splitPartPattern = Regex("""[前后後][编編篇]$""")
    private val volumePartPattern = Regex("""[上下][卷巻]$""")
    private val numberedSeasonPattern = Regex("""第([一二三四五六七八九十百\d]+)(?:季|期|部|章|篇|シリーズ|クール)""")
    private val shortSeasonNumberPattern = Regex("""S(\d+)""", RegexOption.IGNORE_CASE)
    private val seasonNumberPattern = Regex("""Season\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val ordinalSeasonNumberPattern = Regex("""(\d+)(?:st|nd|rd|th)\s+Season""", RegexOption.IGNORE_CASE)
    private val suffixSeasonNumberPattern = Regex("""\s(\d+)(?:季|期|部|章|シリーズ|クール|シーズン)""")
    private val cjkSeasonNumberPattern = Regex("""(?<=[一-鿿])(\d+)$""")
    private val romanSeasonNumberPattern = Regex("""\s(II|III|IV|VI|VII|VIII|IX|XI|XII)$""")

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
        if (splitPartPattern.containsMatchIn(title)) {
            return if (title.contains("前")) 1 else 2
        }
        if (volumePartPattern.containsMatchIn(title)) {
            return if (title.contains("上")) 1 else 2
        }

        // "第X季/期/部/章/篇/シリーズ/クール"（中文数字或阿拉伯数字）
        numberedSeasonPattern.find(title)?.let { m ->
            return parseChineseOrArabic(m.groupValues[1])
        }

        // "S2" / "Season 2" / "2nd Season" / "2期" / "2クール" / "2シリーズ" / "2シーズン"
        shortSeasonNumberPattern.find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        seasonNumberPattern.find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        ordinalSeasonNumberPattern.find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }
        suffixSeasonNumberPattern.find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

        // CJK 字符后直接跟数字（如"骸骨骑士大人异世界冒险中2"）
        cjkSeasonNumberPattern.find(title)?.let { return it.groupValues[1].toIntOrNull() ?: 1 }

        // 半角罗马数字（末尾独立单词）
        romanSeasonNumberPattern.find(title)?.let { m ->
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

    private val bangumiKeyPrefix = Regex("^bangumi:[0-9]+:")

    /** 显示层只消费已识别的系列，不能重新合并识别器主动排除的同名条目。 */
    fun groupAnimeList(animeList: List<Anime>): List<AnimeListItem> {
        val groups = animeList.filter { it.seriesKey != null }.groupBy { it.seriesKey!! }
        val emittedGroups = mutableSetOf<String>()
        return animeList.mapNotNull { anime ->
            val key = anime.seriesKey
            val group = groups[key]
            when {
                key == null || group == null || group.size < 2 -> AnimeListItem.Single(anime)
                emittedGroups.add(key) -> AnimeListItem.Series(bangumiKeyPrefix.replaceFirst(key, ""), group)
                else -> null
            }
        }
    }

    private data class StackTitle(val key: String, val order: Int)

    // JOJO 的主线、分段和旧版改编可能使用副标题，甚至被标为「不同世界观」。
    // 精确匹配已知 Bangumi 条目，或接受已知标题别名；不把这个关系扩展到其他作品。
    private val knownJojoOrderById = mapOf(
        43558 to 1, 85631 to 2, 113292 to 3, 150490 to 4, 235128 to 5,
        332261 to 6, 375913 to 7, 395782 to 8, 551918 to 9, 639938 to 10,
        38326 to 99, 61534 to 99, 99859 to 99, 237852 to 99, 289138 to 99
    )
    private val jojoPrefix = Regex("^(?:jojo的奇妙冒[险險]|ジョジョの奇妙な冒険|jojo['’]sbizarreadventure)")
    private val kamisamaTitles = setOf(KAMISAMA, "元氣少女緣結神", "神様はじめました", "kamisamakiss")
    private val excludedFallbackRelations = setOf("联动", "不同演绎", "不同世界观")

    private fun knownStackTitle(anime: Anime): StackTitle? {
        when (anime.bangumiId) {
            43525 -> return StackTitle(KAMISAMA, 1)
            109724 -> return StackTitle(KAMISAMA, 2)
        }
        knownJojoOrderById[anime.bangumiId]?.let { return StackTitle(JOJO, it) }
        val title = anime.title.filterNot { it.isWhitespace() || it in "・·:：-–—" }.lowercase()
        if (title in kamisamaTitles) return StackTitle(KAMISAMA, 1)
        if (title.endsWith('◎') && title.dropLast(1) in kamisamaTitles) return StackTitle(KAMISAMA, 2)

        when (title) {
            "飙马野郎jojo的奇妙冒险第一赛段", "スティールボールランジョジョの奇妙な冒険1ststage" -> return StackTitle(JOJO, 9)
            "飙马野郎jojo的奇妙冒险第二&第三赛段", "スティールボールランジョジョの奇妙な冒険2nd&3rdstage" -> return StackTitle(JOJO, 10)
            "岸边露伴一动不动ova", "岸边露伴一动不动六壁坂", "岸辺露伴は動かないova" ->
                return StackTitle(JOJO, BangumiSeriesResolver.SPECIAL_SEASON)
        }

        val prefix = jojoPrefix.find(title) ?: return null
        val order = when (title.substring(prefix.range.last + 1)) {
            "", "(2012)", "（2012）" -> 1
            "星尘斗士", "星塵鬥士", "スターダストクルセイダース", "stardustcrusaders" -> 2
            "星尘斗士埃及篇", "星塵鬥士埃及篇", "スターダストクルセイダースエジプト編",
            "stardustcrusadersbattleinegypt" -> 3
            "不灭钻石", "不滅鑽石", "ダイヤモンドは砕けない", "diamondisunbreakable" -> 4
            "黄金之风", "黃金之風", "黄金の風", "goldenwind" -> 5
            "石之海", "ストーンオーシャン", "stoneocean" -> 6
            "石之海第2部分", "石之海part2", "ストーンオーシャン第2クール", "stoneoceanpart2" -> 7
            "石之海第3部分", "石之海part3", "ストーンオーシャン第3クール", "stoneoceanpart3" -> 8
            "飙马野郎", "飆馬野郎", "スティールボールラン", "steelballrun" -> 9
            "幻影血脉", "ファントムブラッド", "总集篇", "総集編", "(1993)", "（1993）" -> BangumiSeriesResolver.SPECIAL_SEASON
            else -> return null
        }
        return StackTitle(JOJO, order)
    }

    /** 先应用关系和季号规则，最后用完整片名前缀补齐尚未归组的条目。 */
    fun assignSeriesKeys(
        animeList: List<Anime>,
        bangumiOverrides: Map<Int, BangumiSeriesResolver.SeriesAssignment> = emptyMap(),
        relations: Map<Int, List<BangumiSeriesResolver.SubjectRelation>> = emptyMap()
    ): List<Anime> {
        val known = animeList.mapNotNull { anime -> knownStackTitle(anime)?.let { anime.id to it } }.toMap()
        fun baseOf(anime: Anime): String = known[anime.id]?.key ?: extractBaseTitle(anime.title)

        val knownBySubject = animeList.mapNotNull { anime ->
            val subjectId = anime.bangumiId ?: return@mapNotNull null
            known[anime.id]?.let { subjectId to it.key }
        }.toMap()
        val excludedBySubject = mutableMapOf<Int, MutableSet<Int>>()
        for ((id, edges) in relations) {
            for (edge in edges) {
                if (!edge.isAnime || edge.relation !in excludedFallbackRelations) continue
                // 仅明确收录的同系列条目可跨越不同演绎或世界观。
                val knownKey = knownBySubject[id]
                if (edge.relation != "联动" && knownKey != null && knownKey == knownBySubject[edge.subjectId]) continue
                excludedBySubject.getOrPut(id) { mutableSetOf() }.add(edge.subjectId)
                excludedBySubject.getOrPut(edge.subjectId) { mutableSetOf() }.add(id)
            }
        }
        fun conflicts(anime: Anime, other: Anime): Boolean =
            other.bangumiId?.let { it in excludedBySubject[anime.bangumiId].orEmpty() } == true

        val keys = mutableMapOf<Int, String>()
        val confirmed = animeList.filter { it.id in bangumiOverrides }.groupBy { bangumiOverrides.getValue(it.id).groupId }
        val groups = mutableMapOf<String, MutableList<Anime>>()
        val keysByBase = mutableMapOf<String, MutableSet<String>>()
        for ((groupId, members) in confirmed) {
            val graphTitle = bangumiOverrides.getValue(members.first().id).baseTitle
            val curatedTitle = members.mapNotNull { known[it.id]?.key }.distinct().singleOrNull()
            val key = curatedTitle ?: "bangumi:$groupId:$graphTitle"
            groups.getOrPut(key) { mutableListOf() }.addAll(members)
            keysByBase.getOrPut(graphTitle) { mutableSetOf() }.add(key)
            for (anime in members) {
                keys[anime.id] = key
                keysByBase.getOrPut(baseOf(anime)) { mutableSetOf() }.add(key)
            }
        }

        for ((base, candidates) in animeList.filterNot { it.id in keys }.groupBy(::baseOf)) {
            val matchingKeys = keysByBase[base].orEmpty()
            if (matchingKeys.isEmpty()) {
                if (candidates.size >= 2 && candidates.any { known[it.id] != null || base != it.title.trim() } &&
                    candidates.none { anime -> candidates.any { other -> anime.id != other.id && conflicts(anime, other) } }
                ) {
                    candidates.forEach { keys[it.id] = base }
                    groups[base] = candidates.toMutableList()
                    keysByBase.getOrPut(base) { mutableSetOf() }.add(base)
                }
                continue
            }
            for (anime in candidates) {
                // 明确的系列信息优先；普通季号必须只匹配一个关系组。
                val knownKey = known[anime.id]?.key
                val key = knownKey?.takeIf { it in matchingKeys } ?: matchingKeys.singleOrNull() ?: continue
                val members = groups.getValue(key)
                val explicitSeason = base != anime.title.trim() && extractSeasonNumber(anime.title) in 2..98
                if ((anime.id in known || explicitSeason) && members.none { conflicts(anime, it) }) {
                    keys[anime.id] = key
                    members.add(anime)
                }
            }
        }

        supplementByTitlePrefix(animeList, keys, groups, keysByBase, ::baseOf, ::conflicts)

        return animeList.map { anime ->
            val key = keys[anime.id]
            val season = if (key != null) known[anime.id]?.order ?: bangumiOverrides[anime.id]?.seasonNumber else null
            if (anime.seriesKey == key && anime.seasonNumber == season) anime
            else anime.copy(seriesKey = key, seasonNumber = season)
        }
    }

    private data class PrefixAnchor(val key: String, val standalone: Anime? = null)

    private fun supplementByTitlePrefix(
        animeList: List<Anime>,
        keys: MutableMap<Int, String>,
        groups: Map<String, List<Anime>>,
        keysByBase: Map<String, Set<String>>,
        baseOf: (Anime) -> String,
        conflicts: (Anime, Anime) -> Boolean
    ) {
        val unmatched = animeList.filterNot { it.id in keys }
        if (unmatched.isEmpty()) return
        val anchorsByTitle = mutableMapOf<String, MutableSet<PrefixAnchor>>()
        for ((base, groupKeys) in keysByBase) {
            val anchors = anchorsByTitle.getOrPut(base) { mutableSetOf() }
            groupKeys.mapTo(anchors) { PrefixAnchor(it) }
        }
        for (anime in unmatched) {
            val title = anime.title.trim()
            // 只有确实在库里的完整片名能作为锚点；不能从两个副标题推造一个主标题。
            if (title.isNotEmpty() && title == baseOf(anime) && !isSpecialEntry(title)) {
                anchorsByTitle.getOrPut(title) { mutableSetOf() }.add(PrefixAnchor(title, anime))
            }
        }

        // 一次性确定候选，不把刚补入的副标题继续作为锚点，也不按输入顺序打破歧义。
        val candidatesByAnchor = mutableMapOf<PrefixAnchor, MutableList<Anime>>()
        for (anime in unmatched) {
            val title = anime.title.trim()
            val matches = mutableSetOf<PrefixAnchor>()
            // 分隔符之后必须有实际副标题，不能只多一个冒号或空括号。
            for (index in 1 until title.indexOfLast(Char::isLetterOrDigit)) {
                if (!isSubtitleBoundary(title, index)) continue
                val anchors = anchorsByTitle[title.substring(0, index).trimEnd()] ?: continue
                matches.addAll(anchors)
                if (matches.size > 1) break
            }
            val anchor = matches.singleOrNull() ?: continue
            candidatesByAnchor.getOrPut(anchor) { mutableListOf() }.add(anime)
        }

        for ((anchor, candidates) in candidatesByAnchor) {
            val existing = anchor.standalone?.let(::listOf) ?: groups.getValue(anchor.key)
            val compatible = candidates.filter { anime -> existing.none { conflicts(anime, it) } }
            val accepted = compatible.filter { anime -> compatible.none { it.id != anime.id && conflicts(anime, it) } }
            if (accepted.isEmpty()) continue
            anchor.standalone?.let { keys[it.id] = anchor.key }
            accepted.forEach { keys[it.id] = anchor.key }
        }
    }

    private fun isSubtitleBoundary(title: String, index: Int): Boolean {
        val char = title[index]
        return char.isWhitespace() || char in ":：—–《〈「『（(【[" ||
            (char == '-' && (title[index - 1].isWhitespace() || title.getOrNull(index + 1)?.isWhitespace() == true))
    }

    /** 清除缓存（如番剧列表大幅变化时调用） */
    fun clearCache() {
        baseTitleCache.clear()
    }
}
