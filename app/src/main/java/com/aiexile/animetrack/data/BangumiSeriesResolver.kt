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
 * 2. 分量内库内条目 ≥2 才成组；展示标题取链头（无前传节点）的基础标题
 * 3. seasonNumber：特殊类型（剧场版/外传/番外/总集篇等）= 99；
 *   其余 = 沿前传链的最短深度（链头 = 1），多个前传时取 min 避免"剧场版也是前传"导致深度虚高
 */
object BangumiSeriesResolver {

    /**
     * 同系列关系白名单：这些关系的两端视为同一系列（无向连通）。
     * "前传"/"续集"是主线；"外传/番外/总集篇/剧场版/主线故事/分支故事"按用户选择并入堆叠；
     * "联动/书籍/游戏/片头曲"等明确排除（如"我独自升级"与"香格里拉边境"是联动，非同系列）。
     */
    private const val PREQUEL = "前传"
    private const val SEQUEL = "续集"

    /** 只沿唯一主线走一季；分叉、外传和前后关系不明时不给刮削器猜。 */
    fun uniqueMainlineSequel(relations: List<SubjectRelation>): SubjectRelation? =
        relations.filter { it.isAnime && it.relation == SEQUEL }.distinctBy { it.subjectId }.singleOrNull()

    fun isUniqueMainlinePrequel(relations: List<SubjectRelation>, previousId: Int): Boolean =
        relations.filter { it.isAnime && it.relation == PREQUEL }.distinctBy { it.subjectId }
            .singleOrNull()?.subjectId == previousId

    private val SERIES_RELATIONS = setOf(
        PREQUEL, SEQUEL, "外传", "外伝", "番外篇", "番外編", "番外编",
        "总集篇", "総集編", "总集编", "剧场版", "劇場版", "主线故事", "分支故事"
    )

    internal fun isSeriesEdge(from: Int, relation: SubjectRelation): Boolean =
        relation.isAnime && relation.subjectId > 0 && relation.subjectId != from &&
            relation.relation in SERIES_RELATIONS

    /** 特殊类型关系：以这些关系被指向的条目季数 = 99（排所有正季后） */
    private val SPECIAL_RELATIONS = setOf(
        "外传", "外伝", "番外篇", "番外編", "番外编",
        "总集篇", "総集編", "总集编", "剧场版", "劇場版", "分支故事"
    )

    /** 特殊类型季数值（排所有正季后） */
    const val SPECIAL_SEASON = 99

    /** 系列归属结果 */
    data class SeriesAssignment(
        /** 使用分量内最小的本地 ID 区分同名但不连通的系列。 */
        val groupId: Int,
        val baseTitle: String,
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
     * @return anime.id → 已得到正向关系证据的系列归属。未找到关系不代表不同系列。
     */
    fun resolve(
        animes: List<Anime>,
        relationsBySubjectId: Map<Int, List<SubjectRelation>>
    ): Map<Int, SeriesAssignment> {
        val result = mutableMapOf<Int, SeriesAssignment>()
        val libraryBySubjectId = animes.filter { (it.bangumiId ?: 0) > 0 }.groupBy { it.bangumiId!! }

        // 1. 构建无向邻接表（仅同系列关系）
        val adjacency = mutableMapOf<Int, MutableSet<Int>>()
        val titleById = mutableMapOf<Int, String>()
        // 入边关系集合：nodeId → 指向它的关系类型集合（用于判断特殊类型）
        val incomingRelations = mutableMapOf<Int, MutableSet<String>>()

        val prequels = mutableMapOf<Int, MutableSet<Int>>()
        val sequels = mutableMapOf<Int, MutableSet<Int>>()
        for ((from, relations) in relationsBySubjectId) {
            if (from <= 0) continue
            for (rel in relations) {
                if (!isSeriesEdge(from, rel)) continue
                val to = rel.subjectId
                adjacency.getOrPut(from) { mutableSetOf() }.add(to)
                adjacency.getOrPut(to) { mutableSetOf() }.add(from)
                if (rel.title.isNotBlank()) titleById[to] = rel.title
                incomingRelations.getOrPut(to) { mutableSetOf() }.add(rel.relation)
                // Bangumi 只记录单向关系时，也可从「续集」反推「前传」。
                if (rel.relation == PREQUEL || rel.relation == SEQUEL) {
                    val (earlier, later) = if (rel.relation == PREQUEL) Pair(to, from) else Pair(from, to)
                    prequels.getOrPut(later) { mutableSetOf() }.add(earlier)
                    sequels.getOrPut(earlier) { mutableSetOf() }.add(later)
                }
            }
        }
        // 库内条目的标题优先（关系数据可能缺 name_cn）
        for (anime in animes) {
            anime.bangumiId?.let { titleById[it] = anime.title }
        }

        // 2. 求连通分量
        val visited = mutableSetOf<Int>()
        for (start in libraryBySubjectId.keys) {
            if (start in visited || start !in adjacency) continue
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
            val libraryAnimes = component.flatMap { libraryBySubjectId[it].orEmpty() }
            if (libraryAnimes.size < 2) continue

            fun isSpecial(node: Int): Boolean = SeriesMatcher.isSpecialEntry(titleById[node].orEmpty()) ||
                incomingRelations[node].orEmpty().any { it in SPECIAL_RELATIONS }

            // 从所有链头按层遍历，得到最短前传深度；有环且无链头的条目交给标题季号兜底。
            val heads = component.filter { prequels[it].isNullOrEmpty() }
            val depths = heads.associateWith { 1 }.toMutableMap()
            val remaining = ArrayDeque(heads)
            while (remaining.isNotEmpty()) {
                val node = remaining.removeFirst()
                for (sequel in sequels[node].orEmpty()) {
                    if (depths.putIfAbsent(sequel, depths.getValue(node) + 1) == null) remaining.addLast(sequel)
                }
            }

            // 优先选主线链头，避免最短的外传标题变成整个系列的名字。
            val headTitle = heads.filterNot(::isSpecial).mapNotNull(titleById::get)
                .minWithOrNull(compareBy<String> { it.length }.thenBy { it })
                ?: libraryAnimes.minWithOrNull(compareBy<Anime> { it.title.length }.thenBy { it.title })?.title
                ?: continue
            val baseTitle = SeriesMatcher.extractBaseTitle(headTitle)
            val groupId = libraryAnimes.minOf { it.id }

            // 6. 计算每部库内番剧的归属
            for (anime in libraryAnimes) {
                val nodeId = anime.bangumiId ?: continue
                result[anime.id] = SeriesAssignment(
                    groupId = groupId,
                    baseTitle = baseTitle,
                    seasonNumber = if (isSpecial(nodeId)) SPECIAL_SEASON else depths[nodeId]
                )
            }
        }

        return result
    }
}
