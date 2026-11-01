package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.data.sync.SubscriptionTombstoneRules
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * alias 兼容解析：旧版服务端下发字符串数组时必须能解析且不误删本地卡片，
 * 带类型的规格格式保持原有语义。
 */
class SubscriptionV2AliasAdapterTest {

    private val gson = Gson()

    @Test
    fun `规格格式的带类型 alias 按原样解析`() {
        val alias = gson.fromJson("""{"type":"bangumi","value":"123"}""", SubscriptionV2Alias::class.java)
        assertEquals(SubscriptionV2Alias.TYPE_BANGUMI, alias.type)
        assertEquals("123", alias.value)
    }

    @Test
    fun `旧格式字符串 alias 按值形态推断类型`() {
        val numeric = gson.fromJson("\"3\"", SubscriptionV2Alias::class.java)
        assertEquals(SubscriptionV2Alias.TYPE_LEGACY_LOCAL, numeric.type)
        assertEquals("3", numeric.value)

        val uuid = gson.fromJson("\"3ccdfa65-95ef-47ee-96b2-0158006c41fe\"", SubscriptionV2Alias::class.java)
        assertEquals(SubscriptionV2Alias.TYPE_REMOTE_SYNC, uuid.type)
        assertEquals("3ccdfa65-95ef-47ee-96b2-0158006c41fe", uuid.value)
    }

    @Test
    fun `无法识别的字符串 alias 解析为未知类型`() {
        val unknown = gson.fromJson("\"abc\"", SubscriptionV2Alias::class.java)
        assertEquals(SubscriptionV2Alias.TYPE_UNKNOWN, unknown.type)
        assertEquals("", unknown.value)
    }

    @Test
    fun `旧格式数字 alias 不会当成 bangumi 匹配到别的番`() {
        val tombstone = SubscriptionV2Tombstone(
            subscriptionId = "sub-1",
            aliases = gson.fromJson(
                """["3","3ccdfa65-95ef-47ee-96b2-0158006c41fe"]""",
                Array<SubscriptionV2Alias>::class.java
            ).toList(),
            deletedAt = "2026-11-01T12:34:50.698Z"
        )
        // 只有标题一致的 legacy_local 才会被采信；缺失 animeTitle 时绝不删除
        val local = anime(bangumiId = 3, title = "另一部番")
        assertFalse(SubscriptionTombstoneRules.matches(local, tombstone))

        // remote_sync 是精确键，UUID 推断后仍可安全匹配
        val remoteLocal = anime(id = 2, remoteSyncId = "3ccdfa65-95ef-47ee-96b2-0158006c41fe")
        assertTrue(SubscriptionTombstoneRules.matches(remoteLocal, tombstone))
    }

    @Test
    fun `线上混合格式的 v2 列表可完整解析`() {
        val response = gson.fromJson(
            """
            {
              "success": true,
              "apiVersion": 2,
              "subscriptions": [
                {
                  "animeId": "18957ea8-c292-4992-82a1-a0eeebcb4d29",
                  "animeTitle": "AnimeTrack Sync Test",
                  "isAiring": true,
                  "subscriptionId": "92f12882-cfe4-4e88-9656-b51c79d5d5e6"
                }
              ],
              "tombstones": [
                {
                  "subscriptionId": "95fd47a3-c09c-4c7b-9798-f365bcbc46b9",
                  "aliases": ["3", "3ccdfa65-95ef-47ee-96b2-0158006c41fe"],
                  "deletedAt": "2026-11-01T12:34:50.698Z"
                },
                {
                  "subscriptionId": "sub-typed",
                  "aliases": [{"type": "bangumi", "value": "123"}],
                  "deletedAt": "2026-11-01T12:35:00.000Z",
                  "animeTitle": "带标题的墓碑"
                }
              ]
            }
            """.trimIndent(),
            SubscriptionsV2Response::class.java
        )

        assertEquals(2, response.apiVersion)
        assertEquals(1, response.subscriptions?.size)
        val tombstones = response.tombstones.orEmpty()
        assertEquals(2, tombstones.size)
        assertEquals(
            listOf(SubscriptionV2Alias.TYPE_LEGACY_LOCAL, SubscriptionV2Alias.TYPE_REMOTE_SYNC),
            tombstones[0].aliases.orEmpty().map { it.type }
        )
        assertEquals(
            listOf(SubscriptionV2Alias.TYPE_BANGUMI),
            tombstones[1].aliases.orEmpty().map { it.type }
        )
    }

    private fun anime(
        id: Int = 1,
        title: String = "测试番",
        bangumiId: Int? = null,
        remoteSyncId: String? = null,
        serverSubscriptionId: String? = null
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
        serverSubscriptionId = serverSubscriptionId
    )
}
