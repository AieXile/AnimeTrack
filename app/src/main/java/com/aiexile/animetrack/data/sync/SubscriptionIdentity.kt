package com.aiexile.animetrack.data.sync

import com.aiexile.animetrack.data.network.Subscription
import com.aiexile.animetrack.data.network.SubscriptionV2Data
import com.aiexile.animetrack.data.network.SubscriptionV2Identity
import com.aiexile.animetrack.data.network.SubscriptionV2SupersededKey
import com.aiexile.animetrack.data.network.parseAnimeStatus
import com.aiexile.animetrack.data.network.toApiString
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.util.formatDate
import com.aiexile.animetrack.util.parseDateToTimestamp
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

    /**
     * LWW 落败（服务端拒绝了更旧的 clientUpdatedAt）时，把 canonical 里
     * 服务端已确认的用户可见字段写回本地。
     *
     * 为什么必须采纳：pull 对「本地已存在」的条目一律跳过，落败方不采纳就会
     * 永远停留在旧进度；而用户下次在旧设备上编辑会带「现在」的时间戳，
     * 反而把新设备的进度顶掉，形成来回覆盖。
     *
     * 采纳范围只限后端会同步的字段；封面本地路径、简介、TMDB/Bangumi 绑定、
     * 置顶/手动顺序等本地专属数据保持不变。服务端为空的可选字段不覆盖本地
     * 已有值（避免把本地尚未上传的评分/备注清空）。
     * 返回 null 表示无变化（无需写库）。
     */
    fun adoptedFromCanonical(local: Anime, canonical: Subscription): Anime? {
        val next = local.copy(
            title = canonical.animeTitle.ifBlank { local.title },
            remoteCoverUrl = canonical.animeImage?.takeIf { it.isNotBlank() } ?: local.remoteCoverUrl,
            airDate = canonical.airDate?.takeIf { it.isNotBlank() } ?: local.airDate,
            airWeekday = canonical.weekday ?: local.airWeekday,
            totalEpisodes = canonical.totalEpisodes ?: local.totalEpisodes,
            watchedEpisodes = canonical.watchedEpisodes ?: local.watchedEpisodes,
            currentEpisodes = canonical.currentEpisodes ?: local.currentEpisodes,
            status = canonical.status?.let { parseAnimeStatus(it) } ?: local.status,
            rating = canonical.rating ?: local.rating,
            notes = canonical.notes?.takeIf { it.isNotBlank() } ?: local.notes,
            startDate = parseDateToTimestamp(canonical.startDate) ?: local.startDate,
            finishDate = parseDateToTimestamp(canonical.finishDate) ?: local.finishDate,
            isFinished = !canonical.isAiring
        )
        return next.takeIf { it != local }
    }

    /**
     * 服务端已把这条订阅换绑到 Bangumi（补 Bangumi ID / 改名）时，
     * 本地旧副本是否应采纳新的行键与标题。
     *
     * 只在本地的旧键被服务端明确列入 supersededAnimeIds（迁移映射），或本地
     * 根本没有身份键（但已按 subscriptionId 命中同一条订阅）时采纳；
     * 本地自己绑定了另一个 Bangumi 时不覆盖，等它自己上传纠正服务端。
     */
    fun shouldAdoptMigratedKey(local: Anime, remote: Subscription): Boolean {
        val serverBangumiId = remote.animeId.toIntOrNull()?.takeIf { it > 0 } ?: return false
        if (local.bangumiId == serverBangumiId) return false
        val localKeys = buildList {
            local.remoteSyncId?.takeIf { it.isNotBlank() }?.let { add(it) }
            local.bangumiId?.takeIf { it > 0 }?.let { add(it.toString()) }
        }
        if (localKeys.isEmpty()) return true
        return localKeys.any { it in remote.supersededAnimeIds.orEmpty() }
    }

    private fun isoOf(timestamp: Long?): String =
        Instant.ofEpochMilli(timestamp ?: System.currentTimeMillis()).toString()
}
