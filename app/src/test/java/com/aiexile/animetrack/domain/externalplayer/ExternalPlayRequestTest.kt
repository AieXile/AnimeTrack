package com.aiexile.animetrack.domain.externalplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** “接着看”语义：watched+1；看完回退详情页级；总集数未知恒给下一集 */
class ExternalPlayRequestTest {

    @Test
    fun `未看过时目标为第一集`() {
        val request = ExternalPlayRequest.continueWatching(
            animeId = 1, bangumiId = 12, title = "测试",
            watchedEpisodes = 0, totalEpisodes = 12
        )
        assertEquals(1, request.episodeSort)
    }

    @Test
    fun `看到一半时目标为下一集`() {
        val request = ExternalPlayRequest.continueWatching(
            animeId = 1, bangumiId = 12, title = "测试",
            watchedEpisodes = 5, totalEpisodes = 12
        )
        assertEquals(6, request.episodeSort)
    }

    @Test
    fun `刚好看完全部集数时回退详情页级`() {
        val request = ExternalPlayRequest.continueWatching(
            animeId = 1, bangumiId = 12, title = "测试",
            watchedEpisodes = 12, totalEpisodes = 12
        )
        assertNull(request.episodeSort)
    }

    @Test
    fun `超额观看同样回退详情页级`() {
        val request = ExternalPlayRequest.continueWatching(
            animeId = 1, bangumiId = 12, title = "测试",
            watchedEpisodes = 13, totalEpisodes = 12
        )
        assertNull(request.episodeSort)
    }

    @Test
    fun `总集数未知时恒给下一集`() {
        val request = ExternalPlayRequest.continueWatching(
            animeId = 1, bangumiId = 12, title = "测试",
            watchedEpisodes = 7, totalEpisodes = null
        )
        assertEquals(8, request.episodeSort)
    }
}
