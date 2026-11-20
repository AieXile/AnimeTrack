package com.aiexile.animetrack.data.media

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.data.AnimeDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LocalMediaScanTest {
    private lateinit var database: AnimeDatabase
    private lateinit var dao: LocalMediaDao
    private lateinit var repository: LocalMediaRepository
    private val reader = TestTreeReader()
    private var sourceId = 0L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java).build()
        dao = database.localMediaDao()
        repository = LocalMediaRepository(context, dao, reader)
        sourceId = runBlocking {
            repository.addOrGetSource(Uri.parse("content://test/tree/root"), "Test source").id
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun unchangedScanLeavesMediaIdenticalAndChangedLocatorRetainsIdAndMatch() = runBlocking {
        val initial = file(1)
        scan(initial)
        val stored = dao.getMedia(sourceId).single()
        repository.applyAutoMatches(listOf(match(stored)))
        val matched = dao.getMedia(sourceId).single()
        assertEquals(LocalMediaMatchState.AUTO_MATCHED, matched.matchState)

        scan(initial)
        assertEquals(matched, dao.getMedia(sourceId).single())
        assertTrue(repository.getPendingMatches(sourceId).isEmpty())

        scan(initial.copy(documentUri = "content://test/document/changed", parentDocumentId = "new-parent"))
        val relocated = dao.getMedia(sourceId).single()
        assertEquals(matched.id, relocated.id)
        assertEquals(matched.matchedAnimeId, relocated.matchedAnimeId)
        assertEquals("content://test/document/changed", relocated.documentUri)
        assertEquals("new-parent", relocated.parentDocumentId)
    }

    @Test
    fun changedMetadataRequeuesAutomaticMatchesAndKeepsMediaId() = runBlocking {
        val initial = file(1)
        scan(initial)
        val stored = dao.getMedia(sourceId).single()
        repository.applyAutoMatches(listOf(match(stored)))

        scan(initial.copy(lastModified = 120))
        val pending = repository.getPendingMatches(sourceId).single()
        assertEquals(stored.id, pending.id)
        assertEquals(120L, pending.lastModified)
        assertEquals(LocalMediaMatchState.UNMATCHED, pending.matchState)
        assertNull(pending.matchedAnimeId)
    }

    @Test
    fun completeScanMarksMissingAcrossBatchesAndSamePathReappearanceKeepsId() = runBlocking {
        scan(*(1..505).map(::file).toTypedArray())
        val initial = dao.getMedia(sourceId).associateBy { it.relativePath }
        val deleted = initial.getValue(file(505).relativePath)

        assertEquals(505, scan().missingFiles)
        assertTrue(dao.getMedia(sourceId).all { it.isMissing })
        assertEquals(0, dao.getSource(sourceId)?.videoCount)
        assertEquals(504, scan(file(505)).missingFiles)
        val restored = checkNotNull(dao.getMediaById(deleted.id))
        assertFalse(restored.isMissing)
        assertEquals(deleted.relativePath, restored.relativePath)
    }

    @Test
    fun providerFailureKeepsOldAvailabilityAndCommittedBatches() = runBlocking {
        scan(file(1), file(2))
        val previous = checkNotNull(dao.getSource(sourceId))
        val old = dao.getMedia(sourceId)
        reader.visit = { emit ->
            for (number in 3..102) emit(file(number))
            throw IOException("subdirectory unavailable")
        }

        assertTrue(runCatching { repository.scanSource(sourceId) }.exceptionOrNull() is IOException)
        assertEquals(102, dao.countPresentMedia(sourceId))
        assertEquals(old, dao.getMediaByIds(old.map { it.id }).sortedBy { it.relativePath })
        val failed = checkNotNull(dao.getSource(sourceId))
        assertEquals(LocalMediaScanState.FAILED, failed.scanState)
        assertEquals(previous.lastScanAt, failed.lastScanAt)
        assertEquals(previous.videoCount, failed.videoCount)
    }

    @Test
    fun cancellingTraversalKeepsOldMediaAndAllowsRetry() = runBlocking {
        scan(file(1))
        val old = dao.getMedia(sourceId).single()
        repository.updateUserMatch(old.id, 42)
        val scanning = CompletableDeferred<Unit>()
        reader.visit = { emit ->
            for (number in 2..101) emit(file(number))
            scanning.complete(Unit)
            awaitCancellation()
        }
        val job = launch { repository.scanSource(sourceId) }
        scanning.await()
        assertEquals(old.id, repository.observeConfirmedEpisodesForAnime(42).first().single().id)
        assertTrue(runCatching { repository.scanSource(sourceId) }.exceptionOrNull() is IllegalStateException)
        job.cancelAndJoin()

        assertFalse(checkNotNull(dao.getMediaById(old.id)).isMissing)
        assertEquals(101, dao.countPresentMedia(sourceId))
        assertEquals(LocalMediaScanState.CANCELLED, dao.getSource(sourceId)?.scanState)
        assertEquals(100, scan(file(1)).missingFiles)
        assertEquals(LocalMediaScanState.COMPLETED, dao.getSource(sourceId)?.scanState)
    }

    @Test
    fun removedGrantRecordsRevocationWithoutMarkingMissing() = runBlocking {
        scan(file(1))
        reader.granted = false
        assertTrue(runCatching { repository.scanSource(sourceId) }.exceptionOrNull() is SecurityException)
        assertEquals(LocalMediaPermissionState.REVOKED, dao.getSource(sourceId)?.permissionState)
        assertFalse(dao.getMedia(sourceId).single().isMissing)
        reader.granted = true
        scan(file(1))
        assertEquals(LocalMediaPermissionState.GRANTED, dao.getSource(sourceId)?.permissionState)
    }

    @Test
    fun reauthorizationRejectsWrongOrDeniedGrantAndKeepsOriginalId() = runBlocking {
        scan(file(1))
        val old = dao.getMedia(sourceId).single()
        repository.updateUserMatch(old.id, 42)
        val source = checkNotNull(dao.getSource(sourceId))
        reader.granted = false
        repository.refreshMissingGrants(listOf(source))
        assertEquals(LocalMediaPermissionState.REVOKED, dao.getSource(sourceId)?.permissionState)

        assertFalse(repository.reauthorizeSource(sourceId, Uri.parse("content://test/tree/wrong")))
        assertEquals(0, reader.grantsTaken)
        reader.denyGrant = true
        val original = Uri.parse(source.treeUri)
        assertTrue(runCatching { repository.reauthorizeSource(sourceId, original) }.exceptionOrNull() is SecurityException)
        assertEquals(LocalMediaPermissionState.REVOKED, dao.getSource(sourceId)?.permissionState)

        reader.denyGrant = false
        assertTrue(repository.reauthorizeSource(sourceId, original))
        scan(file(1))
        assertEquals(LocalMediaPermissionState.GRANTED, dao.getSource(sourceId)?.permissionState)
        assertEquals(old.id, repository.observeConfirmedEpisodesForAnime(42).first().single().id)
        assertEquals(1, repository.observeSources().first().size)
    }

    @Test
    fun userBindAndUnbindWinOverScanSnapshotAndStaleAutoMatches() = runBlocking {
        scan(file(1), file(2))
        val (bound, unbound) = dao.getMedia(sourceId)
        reader.visit = { emit ->
            emit(file(1).copy(size = 501))
            emit(file(2).copy(size = 502))
            repository.updateUserMatch(bound.id, 42)
            repository.updateUserMatch(unbound.id, null)
        }
        repository.scanSource(sourceId)
        repository.applyAutoMatches(listOf(match(bound), match(unbound)))

        val (savedBound, savedUnbound) = dao.getMedia(sourceId)
        assertEquals(bound.id, savedBound.id)
        assertEquals(unbound.id, savedUnbound.id)
        assertEquals(501L, savedBound.size)
        assertEquals(502L, savedUnbound.size)
        assertTrue(savedBound.isUserOverride && savedUnbound.isUserOverride)
        assertEquals(42, savedBound.matchedAnimeId)
        assertNull(savedUnbound.matchedAnimeId)
        assertEquals(LocalMediaMatchState.CONFIRMED, savedBound.matchState)
        assertEquals(LocalMediaMatchState.CONFIRMED, savedUnbound.matchState)
    }

    @Test
    fun finalizationRollsBackMissingUpdatesIfSourceCannotBeCompleted() = runBlocking {
        scan(file(1))
        val stored = dao.getMedia(sourceId).single()
        assertTrue(runCatching {
            dao.finishScan(sourceId, listOf(stored.id), 0, System.currentTimeMillis())
        }.isFailure)
        assertFalse(checkNotNull(dao.getMediaById(stored.id)).isMissing)
    }

    private suspend fun scan(vararg files: LocalMediaScanFile): LocalMediaRepository.ScanResult {
        reader.visit = { emit -> files.forEach { emit(it) } }
        return repository.scanSource(sourceId)
    }

    private fun file(number: Int): LocalMediaScanFile {
        val name = "Series - $number.mkv"
        return LocalMediaScanFile(name, name, "content://test/document/$number", 10, 20, "root")
    }

    private fun match(media: LocalMediaEntity) = LocalMediaAutoMatch(
        media.id, checkNotNull(media.parsedTitle), media.seasonNumber, animeId = 5, confidence = 0.98f
    )

    private class TestTreeReader : LocalMediaTreeReader {
        var granted = true
        var visit: suspend (suspend (LocalMediaScanFile) -> Unit) -> Unit = {}

        override fun hasPersistedReadPermission(treeUri: Uri): Boolean = granted

        override fun takePersistedReadPermission(treeUri: Uri) {
            grantsTaken++
            if (denyGrant) throw SecurityException("grant denied")
            granted = true
        }

        var grantsTaken = 0
        var denyGrant = false

        override suspend fun readFiles(treeUri: Uri, onFile: suspend (LocalMediaScanFile) -> Unit) = visit(onFile)
    }
}
