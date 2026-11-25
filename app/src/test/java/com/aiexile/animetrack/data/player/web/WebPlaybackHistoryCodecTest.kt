package com.aiexile.animetrack.data.player.web

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.model.WebSourceChannel
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourcePage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class WebPlaybackHistoryCodecTest {
    @Test
    fun `历史记录编码后可以完整解码`() {
        val entries = listOf(
            WebPlaybackHistoryRepository.Entry(
                "https://a.example/watch/1", "第一集", 100L, "RuleUA", "https://source.example/"
            ),
            WebPlaybackHistoryRepository.Entry("https://b.example/video.mp4", null, 200L)
        )

        val decoded = WebPlaybackHistoryRepository.decode(
            WebPlaybackHistoryRepository.encode(entries)
        )

        assertEquals(entries, decoded)
    }

    @Test
    fun `损坏或空数据返回空列表`() {
        assertTrue(WebPlaybackHistoryRepository.decode("").isEmpty())
        assertTrue(WebPlaybackHistoryRepository.decode("{not-json").isEmpty())
    }

    @Test
    fun `旧历史记录缺少规则配置仍可读取`() {
        val entry = WebPlaybackHistoryRepository.decode(
            """[{"url":"https://a.example/watch/1","title":"第一集","lastPlayedAt":100}]"""
        ).single()
        assertEquals("https://a.example/watch/1", entry.url)
        assertNull(entry.userAgent)
        assertNull(entry.referer)
        assertNull(entry.mediaId)
        assertNull(entry.nextEpisode)
    }

    @Test
    fun `续播元数据和下一集地址可完整往返`() {
        val entry = WebPlaybackHistoryRepository.Entry("https://a.example/watch/1", "示例 · 第1集", 100L,
            seriesUrl = "https://a.example/detail/1", mediaId = "web:series|episode:1",
            seriesTitle = "示例", episodeName = "第1集",
            nextEpisode = WebSourceEpisode("第2集", "https://a.example/watch/2"),
            nextMediaId = "web:series|episode:2")
        assertEquals(entry, WebPlaybackHistoryRepository.decode(
            WebPlaybackHistoryRepository.encode(listOf(entry))).single())
    }

    @Test
    fun `已有绑定与手动保存的网页在清空历史后保留`() = runBlocking {
        val bound = WebPlaybackHistoryRepository.Entry("https://example.com/bound", "已绑定", 10, animeId = 5)
        val recent = WebPlaybackHistoryRepository.Entry("https://example.com/recent", "未绑定", 20)
        val manual = WebPlaybackHistoryRepository.Entry("https://example.com/manual", "手动添加", 0)
        val legacyHistory = WebPlaybackHistoryRepository.KEY to WebPlaybackHistoryRepository.encode(listOf(recent, bound))
        val repository = WebPlaybackHistoryRepository(MemoryStore(preferencesOf(legacyHistory)))

        assertEquals(listOf(bound), repository.libraryEntries.first())
        repository.saveToLibrary(manual)
        repository.clear()
        assertTrue(repository.entries.first().isEmpty())
        assertEquals(listOf(manual, bound), repository.libraryEntries.first())

        repository.removeFromLibrary(bound.libraryKey)
        assertEquals(listOf(manual), repository.libraryEntries.first())
    }

    @Test
    fun `保存的网页随选集更新但不受历史上限影响`() = runBlocking {
        val repository = WebPlaybackHistoryRepository(MemoryStore())
        val series = "https://example.com/series"
        val episode = WebSourceEpisode("第2集", "https://example.com/watch/2")
        val manual = WebPlaybackHistoryRepository.Entry(series, "手动名称", 0, seriesTitle = "手动名称")
        repository.saveToLibrary(manual)
        val page = WebSourcePage("网站名称", series, listOf(WebSourceChannel(null, listOf(episode))), detailUrl = series)
        val queue = WebPlaybackQueue(page, 0, 0)
        repository.add(episode.url, episode.name, queue = queue)

        repeat(WebPlaybackHistoryRepository.MAX_ENTRIES) { index ->
            repository.add("https://history.example.com/$index", null)
        }
        assertEquals(WebPlaybackHistoryRepository.MAX_ENTRIES, repository.entries.first().size)
        val saved = repository.libraryEntries.first().single()
        assertEquals(series, saved.libraryKey)
        assertEquals(episode.url, saved.url)
        assertEquals(queue.mediaKey(), saved.mediaId)
        assertEquals(manual.seriesTitle, saved.seriesTitle)
    }

    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(data.value).also { data.value = it } }
    }
}
