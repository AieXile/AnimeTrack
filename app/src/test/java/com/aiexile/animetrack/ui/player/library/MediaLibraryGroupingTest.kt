package com.aiexile.animetrack.ui.player.library

import com.aiexile.animetrack.data.media.LocalMediaAnimeGroup
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.ScannedLibraryGroup
import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLibraryGroupingTest {
    @Test
    fun `同一番剧的本地归档 WebDAV 归档和绑定网页合并`() {
        val anime = Anime(1, "番剧标题", 12, 0, AnimeStatus.PLANNED, null, "", bangumiId = 42, tmdbId = 88)
        val local = group(1, "本地名称", "bgm:42", bangumiId = 42)
        val webDav = group(2, "网盘名称", "tmdb:tv:88", tmdbId = 88, webDav = true)
        val web = WebPlaybackHistoryRepository.Entry("https://example.com/watch/1", "第一集", 10, animeId = anime.id)

        val card = groupMediaLibrary(listOf(local, webDav), listOf(web), listOf(anime)).single()
        assertEquals(anime.title, card.title)
        assertEquals(listOf(local, webDav), card.scanned)
        assertEquals(listOf(web), card.web)
    }

    @Test
    fun `手动命名的网页合入唯一同名条目而不合入同名作品中的任意一个`() {
        val first = group(1, "番剧标题", "bgm:42", bangumiId = 42)
        val second = group(2, "番剧标题", "bgm:88", bangumiId = 88)
        val page = WebPlaybackHistoryRepository.Entry("https://example.com/watch/1", "番剧标题", 0)

        assertEquals(listOf(page), groupMediaLibrary(listOf(first), listOf(page), emptyList()).single().web)
        val ambiguous = groupMediaLibrary(listOf(first, second), listOf(page), emptyList())
        assertEquals(3, ambiguous.size)
        assertEquals(emptyList<ScannedLibraryGroup>(), ambiguous.single { it.web.isNotEmpty() }.scanned)
    }

    @Test
    fun `按播放时间排序且续播排除看完 隐藏 缺失和失效的来源`() {
        val local = group(1, "番剧标题", "bgm:42", bangumiId = 42)
        val webDav = group(2, "番剧标题", "bgm:42", bangumiId = 42, webDav = true)
        val page = WebPlaybackHistoryRepository.Entry("https://example.com/watch/1", "番剧标题", 12, mediaId = "web:1")
        val series = groupMediaLibrary(listOf(local, webDav), listOf(page), emptyList())
        val files = listOf(
            file(1, 1),
            file(2, 2),
            file(3, 2).copy(isHidden = true),
            file(4, 1).copy(isMissing = true),
            file(5, 3)
        )
        val records = mapOf(
            "local:1" to PlaybackProgress(positionMs = 5, durationMs = 10, updatedAt = 10),
            "local:2" to PlaybackProgress(positionMs = 4, durationMs = 10, updatedAt = 20),
            "local:3" to PlaybackProgress(positionMs = 2, durationMs = 10, updatedAt = 41),
            "local:4" to PlaybackProgress(positionMs = 2, durationMs = 10, updatedAt = 42),
            "local:5" to PlaybackProgress(positionMs = 2, durationMs = 10, updatedAt = 43),
            "web:1" to PlaybackProgress(positionMs = 10, durationMs = 10, completed = true, updatedAt = 30)
        )

        val playback = libraryPlayback(series, files, records)

        assertEquals(listOf(30L, 20L, 10L), playback.map { it.playedAt })
        assertEquals(MediaLibrarySource.Web(page), playback.first().source)
        assertEquals(MediaLibrarySource.Scanned(webDav), playback.first { it.canResume }.source)
    }

    private fun file(id: Long, sourceId: Long) = LocalMediaEntity(
        id = id, sourceId = sourceId, documentUri = "content://test/$id",
        relativePath = "$id.mp4", fileName = "$id.mp4", archiveKey = "bgm:42"
    )

    private fun group(
        sourceId: Long,
        title: String,
        archiveKey: String,
        bangumiId: Int? = null,
        tmdbId: Int? = null,
        webDav: Boolean = false
    ) = ScannedLibraryGroup(
        LocalMediaAnimeGroup(sourceId, 0, title, null, 1, 0, archiveKey = archiveKey),
        "来源 $sourceId",
        webDav,
        bangumiId,
        tmdbId
    )
}
