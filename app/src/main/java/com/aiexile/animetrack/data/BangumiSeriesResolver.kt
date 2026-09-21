package com.aiexile.animetrack.data

import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.home.SeriesMatcher

/**
 * Bangumi 关系链系列识别器（纯算法，不发起网络请求）。
 *
 * 输入：用户库内番剧 + 各条目的关联关系数据（来自 GET /v0/subjects/{id}/subjects，
 * 含库外条目如未收藏的前传），输出每部番剧的系列归属（seriesKey）与显式季数。
 *
 * 与标题正则（SeriesMatcher）的分工：
 * - 正则覆盖"第X季/Season X/篇章名"等命名规律，离线即时
 * - 关系链覆盖纯副标题命名（如"Fate/Grand Order -神圣圆桌领域卡美洛-"），
 *   数据来自 Bangumi 编辑维护的前传/续集关系，是分组的权威来源
 *
 * 算法：
 * 1. 以"同系列关系"（前传/续集/外传/总集篇等）构建无向图，求连通分量
 * 2. 分量内库内条目 ≥2 才成组；seriesKey = 链头（无前传节点）标题归一后的 baseTitle
 * 3. seasonNumber：特殊类型（剧场版/外传/番外/总集篇等）= 99；
 *   其余 = 沿前传链的最短深度（链头 = 1），多个前传时取 min 避免"剧场版也是前传"导致深度虚高
 */
object BangumiSeriesResolver {

    /**
     * 同系列关系白名单：这些关系的两端视为同一系列（无向连通）。
     * "前传"/"续集"是主线；"外传/番外/总集篇/剧场版/主线故事/分支故事"按用户选择并入堆叠；
     * "联动/书籍/游戏/片头曲"等明确排除（如"我独自升级"与"香格里拉边境"是联动，非同系列）。
     */
    private val SERIES_RELATIONS = setOf(
        "前传", "续集", "外传", "外伝", "番外篇", "番外編", "番外编",
        "总集篇", "総集編", "总集编", "剧场版", "劇場版", "主线故事", "分支故事"
    )

    /** 特殊类型关系：以这些关系被指向的条目季数 = 99（排所有正季后） */
    private val SPECIAL_RELATIONS = setOf(
        "外传", "外伝", "番外篇", "番外編", "番外编",
        "总集篇", "総集編", "总集编", "剧场版", "劇場版", "分支故事"
    )

    /** 特殊类型季数值（排所有正季后） */
    const val SPECIAL_SEASON = 99

    /** 系列归属结果 */
    data class SeriesAssignment(
        /** 系列头 baseTitle；null 表示该条目不属于任何 ≥2 部的系列组 */
        val seriesKey: String?,
        /** 关系链推导的季数；null 表示未推导 */
        val seasonNumber: Int?
    )

    /** 关系数据的最小抽象（与网络层 BangumiSubjectRelation 解耦，便于测试） */
    data class SubjectRelation(
        val subjectId: Int,
        val title: String,
        val relation: String,
        val isAnime: Boolean
    )

    /**
     * 解析系列归属。
     *
     * @param animes 用户库内番剧（须有 bangumiId）
     * @param relationsBySubjectId 各条目的关联关系（key 为 subjectId，含库外前传等条目的关系数据）；
     *        调用方须保证仅包含「拉取成功」的结果（空列表 = Bangumi 确认无关联），
     *        拉取失败的条目不得放入本表（否则会误清正则分组结果）
     * @return anime.id → 系列归属。未出现在返回值中的条目视为无归属（由调用方保留正则结果）
     */
    fun resolve(
        animes: List<Anime>,
        relationsBySubjectId: Map<Int, List<SubjectRelation>>
    ): Map<Int, SeriesAssignment> {
        val result = mutableMapOf<Int, SeriesAssignment>()
        val inLibraryIds = animes.mapNotNull { it.bangumiId }.toSet()

        // 1. 构建无向邻接表（仅同系列关系）
        val adjacency = mutableMapOf<Int, MutableSet<Int>>()
        val titleById = mutableMapOf<Int, String>()
        // 入边关系集合：nodeId → 指向它的关系类型集合（用于判断特殊类型）
        val incomingRelations = mutableMapOf<Int, MutableSet<String>>()

        for ((from, relations) in relationsBySubjectId) {
            for (rel in relations) {
                if (!rel.isAnime || rel.relation !in SERIES_RELATIONS) continue
                adjacency.getOrPut(from) { mutableSetOf() }.add(rel.subjectId)
                adjacency.getOrPut(rel.subjectId) { mutableSetOf() }.add(from)
                titleById[rel.subjectId] = rel.title
                incomingRelations.getOrPut(rel.subjectId) { mutableSetOf() }.add(rel.relation)
            }
        }
        // 库内条目的标题优先（关系数据可能缺 name_cn）
        for (anime in animes) {
            anime.bangumiId?.let { titleById[it] = anime.title }
        }

        // 2. 求连通分量
        val visited = mutableSetOf<Int>()
        for (start in adjacency.keys) {
            if (start in visited) continue
            val component = mutableListOf<Int>()
            val stack = ArrayDeque<Int>()
            stack.addLast(start)
            visited.add(start)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                component.add(node)
                for (neighbor in adjacency[node].orEmpty()) {
                    if (neighbor !in visited) {
                        visited.add(neighbor)
                        stack.addLast(neighbor)
                    }
                }
            }

            // 3. 分量内库内条目 ≥2 才成组
            val componentSet = component.toHashSet()
            val libraryAnimes = animes.filter { it.bangumiId != null && it.bangumiId in componentSet }
            if (libraryAnimes.size < 2) continue

            // 4. 前传有向边（分量内），计算最短深度
            val prequels = mutableMapOf<Int, List<Int>>()
            for (node in component) {
                prequels[node] = relationsBySubjectId[node].orEmpty()
                    .filter { it.isAnime && it.relation == "前传" && it.subjectId in adjacency }
                    .map { it.subjectId }
            }
            val depthCache = mutableMapOf<Int, Int>()
            val computing = mutableSetOf<Int>()
            fun depthOf(node: Int): Int {
                depthCache[node]?.let { return it }
                if (node in computing) return 1 // 环保护
                computing.add(node)
                val preq = prequels[node].orEmpty()
                val d = if (preq.isEmpty()) 1 else 1 + (preq.minOfOrNull { depthOf(it) } ?: 0)
                computing.remove(node)
                depthCache[node] = d
                return d
            }
            component.forEach { depthOf(it) }

            // 5. 链头 = 无前传节点；取标题最短者归一为 seriesKey（标题最短通常最接近系列名）
            val heads = component.filter { prequels[it].orEmpty().isEmpty() }
            val headTitle = heads.minByOrNull { titleById[it]?.length ?: Int.MAX_VALUE }
                ?.let { titleById[it] }
                ?: libraryAnimes.minByOrNull { it.title.length }?.title
                ?: continue
            val seriesKey = SeriesMatcher.extractBaseTitle(headTitle)

            // 6. 计算每部库内番剧的归属
            for (anime in libraryAnimes) {
                val nodeId = anime.bangumiId ?: continue
                val isSpecial = SeriesMatcher.isSpecialEntry(anime.title) ||
                    incomingRelations[nodeId].orEmpty().any { it in SPECIAL_RELATIONS }
                result[anime.id] = SeriesAssignment(
                    seriesKey = seriesKey,
                    seasonNumber = if (isSpecial) SPECIAL_SEASON else (depthCache[nodeId] ?: 1)
                )
            }
        }

        // 7. 不在任何 ≥2 部系列组内的库内条目：显式清空（Bangumi 权威认为它们不成组，
        //    用于覆盖正则可能的误判，如同名不同作品）
        val assigned = result.keys
        for (anime in animes) {
            if (anime.id !in assigned && anime.bangumiId != null && anime.bangumiId in relationsBySubjectId) {
                result[anime.id] = SeriesAssignment(null, null)
            }
        }

        return result
    }
}
