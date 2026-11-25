package com.aiexile.animetrack.domain.playback

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.AnimeRepositoryImpl
import com.aiexile.animetrack.data.media.LocalEpisodeType
import com.aiexile.animetrack.data.media.LocalMediaArchiveEntity
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaMatchState
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.media.LocalMediaSourceEntity
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalWatchProgressUseCaseTest {
    private lateinit var database: AnimeDatabase
    private lateinit var media: LocalMediaRepository
    private lateinit var watchProgress: LocalWatchProgressUseCase
    private val updatesEnabled = MutableStateFlow(true)
    private val autoCompleteEnabled = MutableStateFlow(false)
    private val pushed = mutableListOf<Triple<Int, Int, AnimeStatus>>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java).build()
        val dao = database.animeDao()
        val repository = object : AnimeRepository by AnimeRepositoryImpl(dao, context) {
            override suspend fun updateAnime(anime: Anime) = dao.updateAnime(anime)
        }
        media = LocalMediaRepository(context, database.localMediaDao())
        watchProgress = LocalWatchProgressUseCase(
            media, repository, updatesEnabled, autoCompleteEnabled
        ) { bangumiId, episode, status -> pushed.add(Triple(bangumiId, episode, status)) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun confirmedLocalAndWebDavEpisodesResolveAWatchEntryAddedAfterArchiving() = runBlocking {
        val local = source("content://test/tree/local")
        val webDav = source("webdav://player-library/https/dav")
        val archiveKey = "bgm:42"
        for (sourceId in listOf(local, webDav)) {
            database.localMediaDao().putArchive(LocalMediaArchiveEntity(sourceId, archiveKey, "Series", bangumiId = 42))
        }
        val first = episode(local, number = 1, archiveKey = archiveKey)
        val second = episode(webDav, number = 2, archiveKey = archiveKey)
        assertFalse(watchProgress.onLocalEpisodeCompleted(first))
        val animeId = addAnime(bangumiId = 42)

        assertTrue(watchProgress.onLocalEpisodeCompleted(second))
        assertEquals(setOf(2), media.getCompletedEpisodes(animeId))
        assertEquals(0, anime(animeId).watchedEpisodes)

        assertTrue(watchProgress.onLocalEpisodeCompleted(first))
        assertEquals(setOf(1, 2), media.getCompletedEpisodes(animeId))
        assertEquals(2, anime(animeId).watchedEpisodes)
        assertEquals(AnimeStatus.WATCHING, anime(animeId).status)
        assertEquals(listOf(Triple(42, 2, AnimeStatus.WATCHING)), pushed)
    }

    @Test
    fun confirmedDirectMatchWorksAfterReenablingAndSkipsUnconfirmedOrExtraEpisodes() = runBlocking {
        val animeId = addAnime()
        val source = source("content://test/tree/local")
        val first = episode(source, number = 1, animeId = animeId)
        val pending = episode(source, number = 2, animeId = animeId, match = LocalMediaMatchState.AUTO_MATCHED)
        val extra = episode(source, number = 3, animeId = animeId, type = LocalEpisodeType.EXTRA)

        updatesEnabled.value = false
        assertFalse(watchProgress.onLocalEpisodeCompleted(first))
        updatesEnabled.value = true
        assertFalse(watchProgress.onLocalEpisodeCompleted(pending))
        assertFalse(watchProgress.onLocalEpisodeCompleted(extra))
        assertTrue(media.getCompletedEpisodes(animeId).isEmpty())

        assertTrue(watchProgress.onLocalEpisodeCompleted(first))
        assertEquals(1, anime(animeId).watchedEpisodes)
        assertEquals(setOf(1), media.getCompletedEpisodes(animeId))
    }

    @Test
    fun boundWebEpisodeUsesTheSameProgressAndAutoCompletionRules() = runBlocking {
        val animeId = addAnime(bangumiId = 42, totalEpisodes = 2)
        assertFalse(watchProgress.onEpisodeCompleted(animeId, null, 0))
        assertTrue(watchProgress.onEpisodeCompleted(animeId, null, 1))
        assertEquals(AnimeStatus.WATCHING, anime(animeId).status)

        autoCompleteEnabled.value = true
        assertTrue(watchProgress.onEpisodeCompleted(animeId, null, 2))
        assertEquals(2, anime(animeId).watchedEpisodes)
        assertEquals(AnimeStatus.COMPLETED, anime(animeId).status)
        assertEquals(listOf(Triple(42, 1, AnimeStatus.WATCHING), Triple(42, 2, AnimeStatus.COMPLETED)), pushed)
    }

    private suspend fun addAnime(bangumiId: Int? = null, totalEpisodes: Int = 12): Int =
        database.animeDao().insertAnime(
            Anime(title = "Series", totalEpisodes = totalEpisodes, watchedEpisodes = 0,
                status = AnimeStatus.PLANNED, rating = null, notes = "", bangumiId = bangumiId)
        ).toInt()

    private suspend fun anime(id: Int): Anime = checkNotNull(database.animeDao().getAnimeById(id))

    private suspend fun source(uri: String): Long =
        database.localMediaDao().insertSource(LocalMediaSourceEntity(treeUri = uri, displayName = "Source"))

    private suspend fun episode(
        sourceId: Long,
        number: Int,
        animeId: Int? = null,
        archiveKey: String? = null,
        match: String = LocalMediaMatchState.CONFIRMED,
        type: String = LocalEpisodeType.MAIN
    ): Long {
        val name = "$number.mkv"
        val dao = database.localMediaDao()
        dao.insertMedia(
            listOf(
                LocalMediaEntity(
                    sourceId = sourceId, documentUri = "https://example.test/$name", relativePath = name,
                    fileName = name, episodeNumber = number, matchedAnimeId = animeId, matchState = match,
                    archiveKey = archiveKey, episodeType = type
                )
            )
        )
        return dao.getMedia(sourceId).single { it.relativePath == name }.id
    }
}
