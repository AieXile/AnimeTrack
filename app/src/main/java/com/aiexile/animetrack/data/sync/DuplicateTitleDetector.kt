package com.aiexile.animetrack.data.sync

import com.aiexile.animetrack.model.Anime

/**
 * 同标题重复卡片识别（纯函数，便于单测）。
 *
 * 只识别「双方都没有 Bangumi 绑定、规范化标题相同、总集数相同」的组：
 * 绑定 Bangumi 的卡片走服务端权威身份，不允许按标题猜测；集数不同视为不同季。
 * 与 service 端 upload add 收口口径保持一致（normalizeTitle + totalEpisodes 相等）。
 */
object DuplicateTitleDetector {

    data class DuplicateGroup(
        val key: String,
        val title: String,
        val totalEpisodes: Int,
        val animes: List<Anime>
    )

    fun normalizeTitle(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    fun findGroups(animes: List<Anime>): List<DuplicateGroup> {
        val candidates = animes.filter { (it.bangumiId ?: 0) <= 0 }
        return candidates
            .groupBy { normalizeTitle(it.title) to it.totalEpisodes }
            .mapNotNull { (compound, list) ->
                val (normalized, episodes) = compound
                if (normalized.isEmpty() || list.size < 2) return@mapNotNull null
                val keeper = defaultKeeper(list)
                DuplicateGroup(
                    key = "$normalized|$episodes",
                    title = keeper.title,
                    totalEpisodes = episodes,
                    animes = list.sortedBy { it.id }
                )
            }
            .sortedWith(compareByDescending<DuplicateGroup> { it.animes.size }.thenBy { it.title })
    }

    /**
     * 保守预选：只勾选「没有任何用户数据」的重复卡。
     * 有进度/评分/笔记/日期/收藏/置顶的卡片一律不默认勾选，避免误删用户可见数据。
     */
    fun conservativeDeletions(group: DuplicateGroup): Set<Int> {
        val keeper = defaultKeeper(group.animes)
        return group.animes
            .filter { it.id != keeper.id && !hasUserData(it) }
            .map { it.id }
            .toSet()
    }

    /** 默认保留卡：用户数据最完整的一张；同分取更早创建（id 更小）的一张 */
    fun defaultKeeper(animes: List<Anime>): Anime =
        animes.sortedWith(
            compareByDescending<Anime> { hasUserData(it) }
                .thenByDescending { it.watchedEpisodes }
                .thenByDescending { it.notes.length }
                .thenByDescending { it.rating != null }
                .thenByDescending { it.coverUrl != null }
                .thenBy { it.id }
        ).first()

    fun hasUserData(anime: Anime): Boolean =
        anime.watchedEpisodes > 0 ||
            anime.rating != null ||
            anime.notes.isNotBlank() ||
            anime.startDate != null ||
            anime.finishDate != null ||
            anime.isPinned ||
            anime.isFavorite
}
