package com.aiexile.animetrack.domain.externalplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Animeko 适配：URI 构建与无效 bangumiId 防御 */
class AnimekoPlayerTest {

    @Test
    fun `未关联 bangumiId 为 null 时返回 null`() {
        val request = ExternalPlayRequest(animeId = 1, bangumiId = null, title = "测试")
        assertNull(AnimekoPlayer.buildLaunchUri(request))
    }

    @Test
    fun `历史脏数据 bangumiId 为 0 或负数时返回 null`() {
        val zero = ExternalPlayRequest(animeId = 1, bangumiId = 0, title = "测试")
        val negative = ExternalPlayRequest(animeId = 1, bangumiId = -5, title = "测试")
        assertNull(AnimekoPlayer.buildLaunchUri(zero))
        assertNull(AnimekoPlayer.buildLaunchUri(negative))
    }

    @Test
    fun `详情页级跳转 episodeSort 为 null 时 URI 不带 query`() {
        val request = ExternalPlayRequest(animeId = 1, bangumiId = 12, title = "测试", episodeSort = null)
        assertEquals("ani://subjects/12", AnimekoPlayer.buildLaunchUri(request))
    }

    @Test
    fun `集数级跳转 episodeSort 非 null 时 URI 附加 ep 参数`() {
        val request = ExternalPlayRequest(animeId = 1, bangumiId = 12, title = "测试", episodeSort = 5)
        assertEquals("ani://subjects/12?ep=5", AnimekoPlayer.buildLaunchUri(request))
    }

    @Test
    fun `第一集时 ep 参数为 1`() {
        val request = ExternalPlayRequest(animeId = 1, bangumiId = 401, title = "测试", episodeSort = 1)
        assertEquals("ani://subjects/401?ep=1", AnimekoPlayer.buildLaunchUri(request))
    }

    @Test
    fun `探测 URI 与 Manifest queries 声明一致`() {
        assertEquals("ani://subjects", AnimekoPlayer.probeUri)
    }
}
