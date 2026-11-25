package com.aiexile.animetrack.data.favorites

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.backup.BackupManager
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FavoriteFolderPersistenceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun membershipIsUniqueAndDeletingAFolderKeepsFavoriteAnime() = runBlocking {
        Room.inMemoryDatabaseBuilder(app, AnimeDatabase::class.java).build().withDatabase { db ->
            val animes = db.animeDao()
            val dao = db.favoriteFolderDao()
            val folders = FavoriteFolderRepository(db) {}
            val favorite = animes.insertAnime(anime("Favorite")).toInt()
            val other = animes.insertAnime(anime("Other", favorite = false)).toInt()
            folders.create("First")
            folders.create("Second")
            val (first, second) = dao.getFolderIds()

            folders.addAnimes(first, setOf(favorite, other))
            folders.addToFolders(favorite, setOf(first, second))
            folders.reorderFolders(listOf(second, first))
            assertEquals(listOf(second, first), dao.getFolderIds())
            assertEquals(listOf(favorite), dao.getAnimeIds(first))
            assertEquals(listOf(favorite), dao.getAnimeIds(second))
            assertEquals(listOf("Second", "First"), dao.getFolderNamesForAnime(favorite))
            assertEquals(listOf(1, 1), dao.observeFolders().first().map { it.animeCount })

            folders.removeAnime(first, favorite)
            folders.delete(second)
            assertTrue(dao.observeFolderIdsForAnime(favorite).first().isEmpty())
            assertTrue(animes.getAnimeById(favorite)!!.isFavorite)

            folders.addAnimes(first, setOf(favorite))
            animes.deleteAnime(animes.getAnimeById(favorite)!!)
            assertTrue(dao.getAnimeIds(first).isEmpty())
        }
    }

    @Test
    fun previewsUseUpToThreeAvailableCoversInEachFoldersMemberOrder() = runBlocking {
        Room.inMemoryDatabaseBuilder(app, AnimeDatabase::class.java).build().withDatabase { db ->
            val animes = db.animeDao()
            val folders = FavoriteFolderRepository(db) {}
            val covers = (1..4).map { "/covers/$it.png" }
            val ids = covers.map { cover ->
                animes.insertAnime(anime(cover).copy(coverUrl = cover)).toInt()
            }
            val noCover = animes.insertAnime(anime("No cover")).toInt()
            folders.create("First")
            folders.create("Second")
            val (first, second) = db.favoriteFolderDao().getFolderIds()
            folders.addAnimes(first, ids.toSet() + noCover)
            folders.addAnimes(second, setOf(ids.first()))
            folders.reorderAnimes(first, listOf(noCover) + ids.reversed())

            val previews = folders.previews.first().groupBy({ it.folderId }, { it.coverUrl })
            assertEquals(listOf(covers[3], covers[2], covers[1]), previews[first])
            assertEquals(listOf(covers[0]), previews[second])
        }
    }

    @Test
    fun jsonAndZipRestorePreserveCoversAndRemapOrderedMembers() = runBlocking {
        val workspace = File(app.cacheDir, "favorite-folder-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getCacheDir(): File = workspace
            override fun getFilesDir(): File = workspace
        }
        val sourceName = "favorite-source-${UUID.randomUUID()}"
        try {
            Room.databaseBuilder(context, AnimeDatabase::class.java, sourceName).build().withDatabase { source ->
                val animeDao = source.animeDao()
                val folderDao = source.favoriteFolderDao()
                val first = animeDao.insertAnime(anime("First", bangumiId = 101)).toInt()
                val second = animeDao.insertAnime(anime("Second", bangumiId = 102)).toInt()
                val folders = FavoriteFolderRepository(source) {}
                folders.create("Collection")
                folders.create("Empty")
                val (collection, empty) = folderDao.getFolderIds()
                folders.addAnimes(collection, setOf(first, second))
                folders.reorderAnimes(collection, listOf(second, first))
                folders.reorderFolders(listOf(empty, collection))
                folders.setCover(collection, "Y292ZXI=")

                for (strategy in 0..1) {
                    val file = BackupManager.backup(context, strategy, source)
                    Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java).build().withDatabase { target ->
                        val targetAnimes = target.animeDao()
                        val targetFolders = target.favoriteFolderDao()
                        targetAnimes.insertAnime(anime("Discard on overwrite"))

                        val result = BackupManager.restore(context, strategy, 0, file, target)
                        assertEquals(2, result.insertedCount)
                        assertEquals(listOf(empty, collection), targetFolders.getFolderIds())
                        assertEquals("Y292ZXI=", targetFolders.observeCover(collection).first())
                        assertTrue(targetFolders.getAnimeIds(empty).isEmpty())
                        val restored = targetFolders.observeAnimes(collection).first()
                        assertEquals(listOf("Second", "First"), restored.map { it.title })
                        assertNotEquals(first, restored.last().id)
                        assertTrue(restored.all { it.isFavorite })

                        val merged = BackupManager.restore(context, strategy, 1, file, target)
                        assertEquals(2, merged.skippedCount)
                        assertEquals(listOf(empty, collection), targetFolders.getFolderIds())
                        assertEquals(restored.map { it.id }, targetFolders.getAnimeIds(collection))
                    }
                }
            }
        } finally {
            app.deleteDatabase(sourceName)
            workspace.deleteRecursively()
        }
    }

    private inline fun AnimeDatabase.withDatabase(block: (AnimeDatabase) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun anime(title: String, favorite: Boolean = true, bangumiId: Int? = null) = Anime(
        title = title,
        totalEpisodes = 12,
        watchedEpisodes = 0,
        status = AnimeStatus.PLANNED,
        rating = null,
        notes = "",
        isFavorite = favorite,
        bangumiId = bangumiId
    )
}
