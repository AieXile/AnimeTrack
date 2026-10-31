package com.aiexile.animetrack.data.sync

import com.aiexile.animetrack.data.network.SubscriptionV2Alias
import com.aiexile.animetrack.data.network.SubscriptionV2Tombstone
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** 墓碑消费规则：只按可信身份删本地卡片，避免数字撞车与「刚重加又被删」 */
class SubscriptionTombstoneRulesTest {

    private fun anime(
        id: Int = 1,
        title: String = "测试番",
        bangumiId: Int? = null,
        remoteSyncId: String? = null,
        serverSubscriptionId: String? = null,
        lastProgressAt: Long? = null
    ) = Anime(
        id = id,
        title = title,
        totalEpisodes = 12,
        watchedEpisodes = 3,
        status = AnimeStatus.WATCHING,
        rating = null,
        notes = "",
        bangumiId = bangumiId,
        remoteSyncId = remoteSyncId,
        serverSubscriptionId = serverSubscriptionId,
        lastProgressAt = lastProgressAt
    )

    @Test
    fun `subscriptionId 精确匹配时直接删除`() {
        val local = anime(serverSubscriptionId = "sub-1")
        val tombstone = SubscriptionV2Tombstone(subscriptionId = "sub-1")
        assertTrue(SubscriptionTombstoneRules.matches(local, tombstone))
    }

    @Test
    fun `本地已绑定另一条订阅时稳定键撞车也不能删`() {
        val local = anime(bangumiId = 123, serverSubscriptionId = "sub-other")
        val tombstone = SubscriptionV2Tombstone(
            subscriptionId = "sub-1",
            aliases = listOf(SubscriptionV2Alias(type = "bangumi", value = "123"))
        )
        assertFalse(SubscriptionTombstoneRules.matches(local, tombstone))
    }

    @Test
    fun `bangumi 与 remote_sync 别名可精确匹配`() {
        val bangumiLocal = anime(bangumiId = 123)
        val remoteLocal = anime(id = 2, remoteSyncId = "uuid-a")
        val tombstone = SubscriptionV2Tombstone(
            subscriptionId = "sub-1",
            aliases = listOf(
                SubscriptionV2Alias(type = "bangumi", value = "123"),
                SubscriptionV2Alias(type = "remote_sync", value = "uuid-a")
            )
        )
        assertTrue(SubscriptionTombstoneRules.matches(bangumiLocal, tombstone))
        assertTrue(SubscriptionTombstoneRules.matches(remoteLocal, tombstone))
    }

    @Test
    fun `legacy_local 别名必须标题一致`() {
        val tombstone = SubscriptionV2Tombstone(
            subscriptionId = "sub-1",
            animeTitle = "缓存番",
            aliases = listOf(SubscriptionV2Alias(type = "legacy_local", value = "42"))
        )
        assertTrue(
            SubscriptionTombstoneRules.matches(
                anime(title = "缓存番", bangumiId = 42),
                tombstone
            )
        )
        assertFalse(
            SubscriptionTombstoneRules.matches(
                anime(title = "另一部番", bangumiId = 42),
                tombstone
            )
        )
    }

    @Test
    fun `本地有 serverSubscriptionId 时不采信 legacy_local`() {
        val local = anime(bangumiId = 42, serverSubscriptionId = "sub-bound")
        val tombstone = SubscriptionV2Tombstone(
            subscriptionId = "sub-1",
            animeTitle = "缓存番",
            aliases = listOf(SubscriptionV2Alias(type = "legacy_local", value = "42"))
        )
        assertFalse(SubscriptionTombstoneRules.matches(local, tombstone))
    }

    @Test
    fun `删除之后本地又改过进度时不删`() {
        val deletedAt = Instant.parse("2026-10-29T00:00:00Z")
        val newer = anime(lastProgressAt = deletedAt.toEpochMilli() + 60_000)
        assertTrue(SubscriptionTombstoneRules.isNewerThanTombstone(newer, deletedAt))

        val older = anime(lastProgressAt = deletedAt.toEpochMilli() - 60_000)
        assertFalse(SubscriptionTombstoneRules.isNewerThanTombstone(older, deletedAt))

        val unknown = anime(lastProgressAt = null)
        assertFalse(SubscriptionTombstoneRules.isNewerThanTombstone(unknown, deletedAt))
    }

    @Test
    fun `deletedAt 非法时不做时间守卫`() {
        assertFalse(
            SubscriptionTombstoneRules.isNewerThanTombstone(
                anime(lastProgressAt = Long.MAX_VALUE),
                SubscriptionTombstoneRules.parseInstant("not-a-date")
            )
        )
    }
}
