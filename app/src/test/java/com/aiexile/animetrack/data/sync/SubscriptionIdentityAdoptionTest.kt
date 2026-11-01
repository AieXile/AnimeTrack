package com.aiexile.animetrack.data.sync

import com.aiexile.animetrack.data.network.Subscription
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨设备采纳规则：
 * - LWW 落败方采纳服务端 canonical，否则旧进度永远不收敛；
 * - 服务端换绑 Bangumi 后，本地旧副本采纳新键/标题，避免一直显示旧名。
 */
class SubscriptionIdentityAdoptionTest {

    @Test
    fun `LWW 落败时采纳服务端进度与状态并保留本地修改时间`() {
        val local = anime(watchedEpisodes = 2, status = AnimeStatus.WATCHING, lastProgressAt = 1_000L)
        val canonical = remote(watchedEpisodes = 1, status = "watching")

        val adopted = SubscriptionIdentity.adoptedFromCanonical(local, canonical)

        assertEquals(1, adopted?.watchedEpisodes)
        // 本地 id 与其他本地专属字段不能被 canonical 覆盖
        assertEquals(local.id, adopted?.id)
        // 保留本地 lastProgressAt：采纳后不应制造一个「现在」的时间戳去反压服务端
        assertEquals(local.lastProgressAt, adopted?.lastProgressAt)
    }

    @Test
    fun `服务端为空的可选字段不覆盖本地评分备注`() {
        val local = anime(watchedEpisodes = 2, rating = 8.0f, notes = "本地备注")
        val canonical = remote(watchedEpisodes = 1, rating = null, notes = null)

        val adopted = SubscriptionIdentity.adoptedFromCanonical(local, canonical)

        assertEquals(1, adopted?.watchedEpisodes)
        assertEquals(8.0f, adopted?.rating)
        assertEquals("本地备注", adopted?.notes)
    }

    @Test
    fun `数据完全一致时不产生无谓写库`() {
        val local = anime(watchedEpisodes = 5, totalEpisodes = 12, status = AnimeStatus.WATCHING)
        val canonical = remote(watchedEpisodes = 5, totalEpisodes = 12, status = "watching")

        assertNull(SubscriptionIdentity.adoptedFromCanonical(local, canonical))
    }

    @Test
    fun `本地旧 remote_sync 在服务端迁移映射中时采纳新 bangumi 键`() {
        val local = anime(bangumiId = null, remoteSyncId = "uuid-old")
        val migrated = remote(
            animeId = "3425",
            supersededAnimeIds = listOf("uuid-old"),
            subscriptionId = "sub-1"
        )

        assertTrue(SubscriptionIdentity.shouldAdoptMigratedKey(local, migrated))
    }

    @Test
    fun `本地已绑定相同 bangumi 时无需采纳`() {
        val local = anime(bangumiId = 3425)
        val migrated = remote(animeId = "3425", supersededAnimeIds = listOf("uuid-old"))

        assertFalse(SubscriptionIdentity.shouldAdoptMigratedKey(local, migrated))
    }

    @Test
    fun `本地绑定另一个 bangumi 且不在迁移映射时不覆盖`() {
        val local = anime(bangumiId = 111, remoteSyncId = "uuid-keep")
        val migrated = remote(animeId = "3425", supersededAnimeIds = listOf("uuid-old"))

        assertFalse(SubscriptionIdentity.shouldAdoptMigratedKey(local, migrated))
    }

    @Test
    fun `本地没有身份键时采纳服务端新键`() {
        val local = anime(bangumiId = null, remoteSyncId = null)
        val migrated = remote(animeId = "3425")

        assertTrue(SubscriptionIdentity.shouldAdoptMigratedKey(local, migrated))
    }

    @Test
    fun `服务端行键仍是 UUID 时不涉及 bangumi 换绑`() {
        val local = anime(bangumiId = null, remoteSyncId = "uuid-old")
        val unchanged = remote(animeId = "uuid-old")

        assertFalse(SubscriptionIdentity.shouldAdoptMigratedKey(local, unchanged))
    }

    private fun anime(
        id: Int = 7,
        title: String = "测试番",
        totalEpisodes: Int = 12,
        watchedEpisodes: Int = 0,
        status: AnimeStatus = AnimeStatus.WATCHING,
        rating: Float? = null,
        notes: String = "",
        bangumiId: Int? = null,
        remoteSyncId: String? = null,
        serverSubscriptionId: String? = "sub-1",
        lastProgressAt: Long? = null
    ) = Anime(
        id = id,
        title = title,
        totalEpisodes = totalEpisodes,
        watchedEpisodes = watchedEpisodes,
        status = status,
        rating = rating,
        notes = notes,
        bangumiId = bangumiId,
        remoteSyncId = remoteSyncId,
        serverSubscriptionId = serverSubscriptionId,
        lastProgressAt = lastProgressAt
    )

    private fun remote(
        animeId: String = "uuid-1",
        animeTitle: String = "测试番",
        isAiring: Boolean = true,
        totalEpisodes: Int = 12,
        watchedEpisodes: Int = 0,
        status: String? = "watching",
        rating: Float? = null,
        notes: String? = null,
        subscriptionId: String? = "sub-1",
        supersededAnimeIds: List<String>? = null
    ) = Subscription(
        animeId = animeId,
        animeTitle = animeTitle,
        isAiring = isAiring,
        totalEpisodes = totalEpisodes,
        watchedEpisodes = watchedEpisodes,
        currentEpisodes = 0,
        status = status,
        rating = rating,
        notes = notes,
        subscriptionId = subscriptionId,
        supersededAnimeIds = supersededAnimeIds
    )
}
