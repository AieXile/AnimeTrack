package com.aiexile.animetrack.data

import com.aiexile.animetrack.data.BangumiSeriesResolver.SubjectRelation

/** 单协程持有的增量关系缓存；先查库内条目，再有限地补查同系列的库外条目。 */
internal class BangumiSeriesRelationSync(
    private val batchSize: Int = 24,
    private val maxOutsideRequests: Int = 300,
    private val maxOutsideDepth: Int = 8,
    private val fetchRelations: suspend (Int) -> List<SubjectRelation>?
) {
    // null 是请求失败，不缓存；空列表是有效响应。
    private val cache = mutableMapOf<Int, List<SubjectRelation>>()

    suspend fun sync(libraryIds: Set<Int>, publish: suspend (Map<Int, List<SubjectRelation>>) -> Unit) {
        val roots = libraryIds.filterTo(linkedSetOf()) { it > 0 }
        val queue = ArrayDeque(roots.map { it to 0 })
        val enqueued = roots.toMutableSet()
        var outsideRequests = 0
        var batchRequests = 0
        var changed = false

        while (queue.isNotEmpty()) {
            val (id, depth) = queue.removeFirst()
            var relations = cache[id]
            if (relations == null) {
                if (depth > 0) {
                    if (outsideRequests >= maxOutsideRequests) continue
                    outsideRequests++
                }
                batchRequests++
                relations = fetchRelations(id)
                if (relations != null) {
                    cache[id] = relations
                    changed = true
                }
            }

            if (depth < maxOutsideDepth) {
                for (relation in relations.orEmpty()) {
                    if (BangumiSeriesResolver.isSeriesEdge(id, relation) && enqueued.add(relation.subjectId)) {
                        queue.addLast(relation.subjectId to depth + 1)
                    }
                }
            }

            // 按实际请求数发布；大书架分批生效，队列再大也不会跳过库内条目。
            if (batchRequests >= batchSize) {
                if (changed) publish(cache.toMap())
                batchRequests = 0
                changed = false
            }
        }
        if (changed) publish(cache.toMap())
    }
}
