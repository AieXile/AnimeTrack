package com.aiexile.animetrack.data.sync

import com.aiexile.animetrack.data.network.Subscription
import com.aiexile.animetrack.data.network.SubscriptionV2Data
import com.aiexile.animetrack.data.network.SubscriptionV2Identity
import com.aiexile.animetrack.data.network.SubscriptionV2SupersededKey
import com.aiexile.animetrack.data.network.toApiString
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.util.formatDate
import java.time.Instant

/**
 * 订阅 v2 身份映射（纯函数）。
 *
 * 规则与服务端 subscriptions/identity.js 对齐：
 * - bangumiId > 0 优先作为稳定身份；
 * - 否则 remoteSyncId 作为稳定身份；
 * - 本地自增 id 只放 legacyKeys，服务端不会写入 alias 表；
 * - 换绑后旧 bangumiId / remoteSyncId 放 supersededKeys，服务端登记 alias 防止旧设备重传。
 */
object SubscriptionIdentity {

    fun primaryKey(anime: Anime): String? =
        anime.bangumiId?.takeIf { it > 0 }?.toString()
            ?: anime.remoteSyncId?.takeIf { it.isNotBlank() }

    fun identityOf(anime: Anime): SubscriptionV2Identity? {
        val bangumiId = anime.bangumiId?.takeIf { it > 0 }
        if (bangumiId != null) return SubscriptionV2Identity(bangumiId = bangumiId)
        val remoteSyncId = anime.remoteSyncId?.takeIf { it.isNotBlank() } ?: return null
        return SubscriptionV2Identity(remoteSyncId = remoteSyncId)
    }

    /** 旧本地自增 id：只做一次性清理，不能当跨设备身份 */
    fun legacyKeysOf(anime: Anime): List<String> =
        listOf(anime.id.toString()).filter { it.isNotBlank() && it != "0" }

    /** 删除时可能落在服务端的全部稳定行键（bangumi + remoteSyncId） */
    fun keysForDelete(anime: Anime): List<String> = buildList {
        anime.bangumiId?.takeIf { it > 0 }?.let { add(it.toString()) }
        anime.remoteSyncId?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.distinct()

    fun supersededKeysOf(anime: Anime, oldAnime: Anime? = null): List<SubscriptionV2SupersededKey> {
        val primary = primaryKey(anime) ?: return emptyList()
        val values = LinkedHashSet<String>()
        oldAnime?.bangumiId?.takeIf { it > 0 }?.let { values.add(it.toString()) }
        oldAnime?.remoteSyncId?.takeIf { it.isNotBlank() }?.let { values.add(it) }
        // 换绑 bangumiId 后 copy 仍保留 remoteSyncId，它就是迁移前的旧行键
        anime.remoteSyncId?.takeIf { it.isNotBlank() }?.let { values.add(it) }

        return values
            .filter { it != primary }
            .mapNotNull { value ->
                when {
                    value == "0" -> null
                    value.toIntOrNull() != null -> SubscriptionV2SupersededKey("bangumi", value)
                    else -> SubscriptionV2SupersededKey("remote_sync", value)
                }
            }
    }

    fun dataOf(anime: Anime, imageUrl: String?): SubscriptionV2Data = SubscriptionV2Data(
        animeTitle = anime.title,
        animeImage = imageUrl,
        airDate = anime.airDate,
        isAiring = !anime.isFinished,
        weekday = anime.airWeekday,
        totalEpisodes = anime.totalEpisodes,
        watchedEpisodes = anime.watchedEpisodes,
        currentEpisodes = anime.currentEpisodes,
        status = anime.status.toApiString(),
        rating = anime.rating,
        notes = anime.notes.ifBlank { null },
        startDate = anime.startDate?.let { formatDate(it) },
        finishDate = anime.finishDate?.let { formatDate(it) }
    )

    /**
     * 单条本地更新：进度/状态变化用 lastProgressAt（真实修改时间），
     * 其余元数据变化用当前时间。避免旧设备用「现在」覆盖新设备的真实进度。
     */
    fun clientUpdatedAt(anime: Anime, oldAnime: Anime?): String {
        val progressChanged = oldAnime != null && (
            oldAnime.watchedEpisodes != anime.watchedEpisodes ||
                oldAnime.status != anime.status
            )
        return isoOf(if (progressChanged) anime.lastProgressAt else null)
    }

    /** 全量同步：与远端比对后，只有进度/状态差异才使用本地真实修改时间 */
    fun clientUpdatedAtAgainstRemote(anime: Anime, remote: Subscription?): String {
        val progressChanged = remote != null && (
            (remote.watchedEpisodes ?: 0) != anime.watchedEpisodes ||
                remote.status != anime.status.toApiString()
            )
        return isoOf(if (progressChanged) anime.lastProgressAt else null)
    }

    /** 只有本地明确把已看集数调低时才允许服务端下调 */
    fun isProgressReset(anime: Anime, oldAnime: Anime?): Boolean =
        oldAnime != null && anime.watchedEpisodes < oldAnime.watchedEpisodes

    private fun isoOf(timestamp: Long?): String =
        Instant.ofEpochMilli(timestamp ?: System.currentTimeMillis()).toString()
}
