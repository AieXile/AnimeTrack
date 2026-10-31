package com.aiexile.animetrack.data.sync

import com.aiexile.animetrack.data.AnimeDao
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.SubscriptionV2Tombstone
import com.aiexile.animetrack.model.Anime
import java.time.Instant

/**
 * 把服务端墓碑应用到本地：另一台设备删除订阅后，本机同步时把对应卡片清掉。
 *
 * 单独成文件的原因：墓碑匹配规则需要精确、可单测，不能继续堆进 AnimeRepository；
 * 这里只负责「判断哪些本地行该删 + 删除」，不涉及上传/合并。
 */
class SubscriptionTombstoneApplier(private val animeDao: AnimeDao) {

    data class Result(
        val deletedAnimeIds: Set<Int>,
        val deletedCount: Int,
        val skippedNewerCount: Int
    )

    suspend fun apply(
        tombstones: List<SubscriptionV2Tombstone>,
        localCandidates: List<Anime>
    ): Result {
        if (tombstones.isEmpty() || localCandidates.isEmpty()) {
            return Result(emptySet(), 0, 0)
        }
        val deletedIds = mutableSetOf<Int>()
        var skippedNewer = 0
        for (tombstone in tombstones) {
            val deletedAt = SubscriptionTombstoneRules.parseInstant(tombstone.deletedAt)
            for (anime in localCandidates) {
                if (anime.id in deletedIds) continue
                if (!SubscriptionTombstoneRules.matches(anime, tombstone)) continue
                if (SubscriptionTombstoneRules.isNewerThanTombstone(anime, deletedAt)) {
                    skippedNewer++
                    AppLogManager.i(
                        TAG,
                        "墓碑跳过本地较新修改: ${anime.title}, lastProgressAt=${anime.lastProgressAt}, " +
                            "deletedAt=${tombstone.deletedAt}"
                    )
                    continue
                }
                animeDao.deleteAnime(anime)
                deletedIds.add(anime.id)
                AppLogManager.i(
                    TAG,
                    "应用墓碑删除本地卡片: ${anime.title}, subscriptionId=${tombstone.subscriptionId}"
                )
            }
        }
        return Result(deletedIds, deletedIds.size, skippedNewer)
    }

    companion object {
        private const val TAG = "SubscriptionTombstone"
    }
}

/**
 * 纯匹配规则（不碰数据库，可直接单测）。
 *
 * 安全规则：
 * - 本地已有 serverSubscriptionId 且与墓碑不同 → 视为另一条订阅，不删；
 * - bangumi / remote_sync 别名可精确匹配；
 * - legacy_local 别名只在标题一致、且本地没有 serverSubscriptionId 时采信；
 * - 本地在墓碑删除之后又改过进度（lastProgressAt > deletedAt）→ 不删，等上传恢复。
 */
internal object SubscriptionTombstoneRules {
    private const val ALIAS_BANGUMI = "bangumi"
    private const val ALIAS_REMOTE_SYNC = "remote_sync"
    private const val ALIAS_LEGACY_LOCAL = "legacy_local"

    fun matches(anime: Anime, tombstone: SubscriptionV2Tombstone): Boolean {
        val localSubscriptionId = anime.serverSubscriptionId
        // 本地已经绑定另一条服务端订阅：别名撞车也不能删
        if (localSubscriptionId != null && localSubscriptionId != tombstone.subscriptionId) {
            return false
        }
        if (localSubscriptionId == tombstone.subscriptionId) return true

        for (alias in tombstone.aliases.orEmpty()) {
            when (alias.type) {
                ALIAS_BANGUMI -> {
                    val bangumiId = alias.value.toIntOrNull()
                    if (bangumiId != null && bangumiId == anime.bangumiId) return true
                }

                ALIAS_REMOTE_SYNC -> {
                    if (alias.value == anime.remoteSyncId) return true
                }

                ALIAS_LEGACY_LOCAL -> {
                    // 旧本地 id 可能与其他番的 Bangumi ID 同值，必须标题一致
                    if (localSubscriptionId != null) continue
                    if (!sameTitle(anime.title, tombstone.animeTitle)) continue
                    val numeric = alias.value.toIntOrNull()
                    if (numeric != null && numeric == anime.bangumiId) return true
                    if (alias.value == anime.remoteSyncId) return true
                }
            }
        }
        return false
    }

    fun isNewerThanTombstone(anime: Anime, deletedAt: Instant?): Boolean {
        if (deletedAt == null) return false
        val last = anime.lastProgressAt ?: return false
        return last > deletedAt.toEpochMilli()
    }

    fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        }

    private fun sameTitle(left: String?, right: String?): Boolean {
        val a = left?.trim().orEmpty()
        val b = right?.trim().orEmpty()
        return a.isNotEmpty() && b.isNotEmpty() && a == b
    }
}
