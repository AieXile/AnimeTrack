package com.aiexile.animetrack.domain.externalplayer

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 哔哩哔哩适配：URI 构建、编码与注册表 */
class BilibiliPlayerTest {

    private fun request(title: String = "葬送的芙莉莲") =
        ExternalPlayRequest(animeId = 1, bangumiId = 46089, title = title)

    @Test
    fun `未解析时兜底为搜索页且关键词完整编码`() {
        val uri = BilibiliPlayer.buildLaunchUri(request())!!
        assertTrue(uri.startsWith("bilibili://search?keyword="))
        val keyword = uri.substringAfter("keyword=")
        assertEquals("葬送的芙莉莲", URLDecoder.decode(keyword, "UTF-8"))
    }

    @Test
    fun `标题中的 and 与空格不会截断参数`() {
        val uri = BilibiliPlayer.buildLaunchUri(request("A&B C"))!!
        assertEquals("A&B C", URLDecoder.decode(uri.substringAfter("keyword="), "UTF-8"))
        assertTrue(!uri.contains("+"))
    }

    @Test
    fun `标题为空时返回 null`() {
        assertNull(BilibiliPlayer.buildLaunchUri(request("  ")))
    }

    @Test
    fun `番剧级与单集 deep link 使用官方网页映射格式`() {
        assertEquals("bilibili://bangumi/season/46089", BilibiliPlayer.buildSeasonUri(46089))
        assertEquals("bilibili://pgc/season/ep/779775", BilibiliPlayer.buildEpisodeUri(779775))
    }

    @Test
    fun `有集数优先直达单集 否则回退番剧页`() {
        assertEquals(
            "bilibili://pgc/season/ep/779775",
            BilibiliPlayer.buildPlayUri(BilibiliPlayLink(seasonId = 46089, episodeId = 779775))
        )
        assertEquals(
            "bilibili://bangumi/season/46089",
            BilibiliPlayer.buildPlayUri(BilibiliPlayLink(seasonId = 46089, episodeId = null))
        )
    }

    @Test
    fun `已定向官方包 避免第三方客户端抢 scheme`() {
        assertEquals("tv.danmaku.bili", BilibiliPlayer.packageName)
        assertEquals("bilibili://search", BilibiliPlayer.probeUri)
    }

    @Test
    fun `注册表包含两个播放器`() {
        assertEquals(BilibiliPlayer, ExternalPlayerRegistry.byId(ExternalPlayerId.BILIBILI))
        assertTrue(ExternalPlayerRegistry.all.contains(AnimekoPlayer))
        assertTrue(ExternalPlayerRegistry.all.contains(BilibiliPlayer))
    }
}
