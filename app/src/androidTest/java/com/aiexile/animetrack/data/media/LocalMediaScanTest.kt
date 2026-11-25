package com.aiexile.animetrack.data.media

import android.net.Uri
import androidx.paging.PagingSource
import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.TmdbGenre
import com.aiexile.animetrack.data.network.TmdbTvDetail
import com.aiexile.animetrack.data.network.TmdbTvShow
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.playback.LocalMediaAutoMatchUseCase
import com.aiexile.animetrack.domain.playback.LocalPlaybackQueue
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import java.util.UUID
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
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
    fun aggregateLibraryUsesVisibleFilesFromDeviceAndActiveWebDavSource() = runBlocking {
        val activeUri = "webdav://player-library/https/active"
        val active = dao.insertSource(LocalMediaSourceEntity(treeUri = activeUri, displayName = "Active"))
        val stale = dao.insertSource(LocalMediaSourceEntity(treeUri = "webdav://player-library/https/stale", displayName = "Stale"))
        val archiveKey = "bgm:42"
        for (id in listOf(sourceId, active, stale)) {
            dao.putArchive(LocalMediaArchiveEntity(id, archiveKey, "Series", bangumiId = 42))
            val media = LocalMediaEntity(
                sourceId = id, documentUri = "content://test/video", relativePath = "01.mkv",
                fileName = "01.mkv", archiveKey = archiveKey
            )
            dao.insertMedia(listOf(media, media.copy(relativePath = "02.mkv", isHidden = true)))
        }

        val groups = dao.observeLibraryGroups(activeUri).first()
        assertEquals(listOf(sourceId, active), groups.map { it.group.sourceId })
        assertEquals(listOf(false, true), groups.map { it.webDav })
        assertTrue(groups.all { it.group.videoCount == 1 && it.bangumiId == 42 })
        assertEquals(listOf(sourceId), dao.observeLibraryGroups(null).first().map { it.group.sourceId })
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

    @Test
    fun libraryPagesPartitionPresentFilesAndKeepGroupsDistinctAcrossPages() = runBlocking {
        val animeDao = database.animeDao()
        val first = Anime(id = 10, title = "Same title", coverUrl = "https://example.test/cover.jpg", totalEpisodes = 12,
            watchedEpisodes = 0, status = AnimeStatus.PLANNED, rating = null, notes = "")
        val second = first.copy(id = 11, coverUrl = null)
        animeDao.insertAnimes(listOf(first, second))
        val otherSourceId = repository.addOrGetSource(Uri.parse("content://test/tree/other"), "Other source").id
        val sample = LocalMediaEntity(
            sourceId = sourceId, documentUri = "content://test/document/1", relativePath = "a-episode-10.mkv",
            fileName = "a-episode-10.mkv", matchedAnimeId = first.id, matchState = LocalMediaMatchState.CONFIRMED,
            episodeNumber = 10
        )
        dao.insertMedia(listOf(
            sample.copy(id = 1),
            sample.copy(id = 2, relativePath = "b-episode-2.mkv", episodeNumber = 2, matchState = LocalMediaMatchState.AUTO_MATCHED),
            sample.copy(id = 3, relativePath = "c-episode-2.mkv", episodeNumber = 2),
            sample.copy(id = 4, relativePath = "d-missing.mkv", isMissing = true),
            sample.copy(id = 5, relativePath = "e-same-title.mkv", matchedAnimeId = second.id),
            sample.copy(id = 6, relativePath = "f-cleared.mkv", matchedAnimeId = null, isUserOverride = true),
            sample.copy(id = 7, relativePath = "g-dangling.mkv", matchedAnimeId = 99),
            sample.copy(id = 8, relativePath = "h-preview.mkv", matchState = LocalMediaMatchState.IGNORED),
            sample.copy(id = 9, sourceId = otherSourceId),
            sample.copy(id = 10, relativePath = "aa-extra.mkv", episodeType = LocalEpisodeType.EXTRA, episodeNumber = 1),
            sample.copy(id = 11, relativePath = "i-unknown.mkv", episodeNumber = null),
            sample.copy(id = 12, relativePath = "j-season-2.mkv", seasonNumber = 2, episodeNumber = 1)
        ))

        val groups = dao.pageAnimeGroups(sourceId).readPages()
        assertEquals(listOf(first.id, second.id), groups.map { it.animeId })
        assertEquals(listOf(6, 1), groups.map { it.videoCount })
        assertEquals(listOf(1, 0), groups.map { it.pendingCount })
        assertEquals(first.coverUrl, groups.first().coverUrl)
        assertEquals(1, dao.pageAnimeGroups(otherSourceId).readPages().single().videoCount)
        assertEquals(listOf(2L, 3L, 1L, 11L, 12L, 10L), dao.pageAnimeMedia(sourceId, first.id).readPages().map { it.id })
        assertEquals(listOf(5L), dao.pageAnimeMedia(sourceId, second.id).readPages().map { it.id })
        assertEquals(listOf(6L, 7L, 8L), dao.pageUnclassifiedMedia(sourceId).readPages().map { it.id })

        repository.updateUserMatch(2, first.id)
        assertEquals(0, dao.pageAnimeGroups(sourceId).readPages().first().pendingCount)
        repository.updateUserMatch(2, null)
        assertEquals(5, dao.pageAnimeGroups(sourceId).readPages().first().videoCount)
        animeDao.deleteAnime(second)
        assertEquals(listOf(first.id), dao.pageAnimeGroups(sourceId).readPages().map { it.animeId })
        assertTrue(dao.pageAnimeMedia(sourceId, second.id).readPages().isEmpty())
        assertEquals(listOf(2L, 5L, 6L, 7L, 8L), dao.pageUnclassifiedMedia(sourceId).readPages().map { it.id })
    }

    @Test
    fun hiddenFilesStayHiddenAcrossScansAndCanBeRestoredInTheirSource() = runBlocking {
        scan(file(1), file(2))
        val (bound, unmatched) = dao.getMedia(sourceId)
        val other = repository.addOrGetSource(Uri.parse("content://test/tree/other"), "Other").id
        dao.insertMedia(listOf(unmatched.copy(id = 0, sourceId = other)))
        val anime = Anime(id = 55, title = "Series", totalEpisodes = 12, watchedEpisodes = 0,
            status = AnimeStatus.PLANNED, rating = null, notes = "")
        database.animeDao().insertAnime(anime)
        repository.updateUserMatch(bound.id, anime.id)

        // 模拟扫描先建立旧快照，再在批次提交前记录隐藏操作。
        reader.visit = { emit ->
            emit(file(1).copy(size = 501))
            emit(file(2).copy(size = 502))
            repository.setHidden(sourceId, bound.id, true)
            repository.setHidden(sourceId, unmatched.id, true)
        }
        repository.scanSource(sourceId)
        assertEquals(2, dao.observeHiddenCount(sourceId).first())
        assertEquals(2, dao.pageHiddenMedia(sourceId).readPages().size)
        assertTrue(dao.pageAnimeGroups(sourceId).readPages().isEmpty())
        assertTrue(dao.pageAnimeMedia(sourceId, anime.id).readPages().isEmpty())
        assertTrue(dao.pageUnclassifiedMedia(sourceId).readPages().isEmpty())
        assertTrue(dao.observeConfirmedEpisodesForAnime(anime.id).first().isEmpty())
        assertTrue(dao.getPendingMatches(sourceId).isEmpty())
        repository.applyAutoMatches(listOf(match(unmatched)))
        assertNull(dao.getMediaById(unmatched.id)?.matchedAnimeId)
        val otherGroup = dao.pageAnimeGroups(other).readPages().single()
        assertTrue(otherGroup.waiting)
        assertEquals(1, otherGroup.videoCount)
        assertEquals(0, dao.setHidden(other, bound.id, false, 0))

        scan()
        assertEquals(0, dao.observeHiddenCount(sourceId).first())
        scan(file(1), file(2))
        assertEquals(listOf(bound.id, unmatched.id), dao.pageHiddenMedia(sourceId).readPages().map { it.id })
        repository.setHidden(sourceId, bound.id, false)
        assertEquals(listOf(bound.id), dao.observeConfirmedEpisodesForAnime(anime.id).first().map { it.id })
        assertEquals(1, dao.observeHiddenCount(sourceId).first())
    }

    @Test
    fun rescanReturnsPreviouslyArchivedRecordingsToUnclassified() = runBlocking {
        val seaside = folderFile("杂项/手机拍摄_海边 [720p][x264].mp4")
        val recording = folderFile("杂项/屏幕录制 [480p][x264].mp4")
        val episode = folderFile("Series/Series - 01.mkv")
        scan(seaside, recording, episode)
        val previous = dao.getMedia(sourceId).associateBy { it.relativePath }
        val oldRecordings = listOf(seaside, recording).map { file ->
            previous.getValue(file.relativePath).copy(parserVersion = 1, groupKey = "legacy:${file.fileName}",
                archiveKey = "bgm:12", scrapeState = LocalMediaScrapeState.IDENTIFIED, matchState = LocalMediaMatchState.AUTO_MATCHED)
        }
        val validEpisode = previous.getValue(episode.relativePath).copy(parserVersion = 1,
            archiveKey = "bgm:42", scrapeState = LocalMediaScrapeState.IDENTIFIED, matchState = LocalMediaMatchState.AUTO_MATCHED)
        dao.putArchive(LocalMediaArchiveEntity(sourceId, "bgm:12", "错误归档", bangumiId = 12))
        dao.putArchive(LocalMediaArchiveEntity(sourceId, "bgm:42", "Series", bangumiId = 42))
        dao.updateMedia(oldRecordings + validEpisode)
        assertTrue(dao.pageAnimeGroups(sourceId).readPages().any { it.archiveKey == "bgm:12" })

        scan(seaside, recording, episode)
        val loose = dao.pageUnclassifiedMedia(sourceId).readPages()
        assertEquals(oldRecordings.map { it.id }.toSet(), loose.map { it.id }.toSet())
        assertTrue(loose.all { it.archiveKey == null && it.episodeNumber == null && it.scrapeState == LocalMediaScrapeState.NONE })
        assertTrue(dao.getScrapeQueue(sourceId).isEmpty())
        assertEquals("bgm:42", dao.pageAnimeGroups(sourceId).readPages().single().archiveKey)
        assertEquals(0, scan(seaside, recording, episode).parsedFiles)
    }

    @Test
    fun emptyTrackingLibraryArchivesAndPlaysAWholeSeries() = runBlocking {
        val inputs = (1..3).map { folderFile("Anime/Season 2/Series 第二季 第0${it}集.mkv") }
        scan(*inputs.toTypedArray())
        var searches = 0
        val useCase = matcher { query ->
            searches++
            if (query == "Series 第二季") listOf(subject(42, "Series 第二季"), subject(41, "Series")) else emptyList()
        }
        assertTrue(useCase.run(sourceId, online = false))
        assertEquals(0, searches)
        assertTrue(dao.pageAnimeGroups(sourceId).readPages().single().waiting)
        assertFalse(useCase.run(sourceId))
        val group = dao.pageAnimeGroups(sourceId).readPages().single()
        assertEquals("bgm:42", group.archiveKey)
        assertFalse(group.waiting)
        assertEquals(3, group.videoCount)
        val items = dao.pageArchiveMedia(sourceId, group.archiveKey, null).readPages()
        assertEquals(listOf(1, 2, 3), LocalPlaybackQueue.build(items.first(), items).map { it.episodeNumber })
        assertTrue(items.all { it.matchedAnimeId == null && !it.isUserOverride })
        assertEquals(0, database.animeDao().getAllAnimes().first().size)
        assertTrue(searches in 1..3)
        assertFalse(useCase.run(sourceId))
        assertEquals(0, scan(*inputs.toTypedArray()).parsedFiles)
    }

    @Test
    fun explicitSidecarArchivesOfflineAndScopesToItsDirectory() = runBlocking {
        val ini = folderFile("Series/bangumi.ini")
        val show = folderFile("Series/tvshow.nfo")
        val episode = folderFile("Series/Season 2/01.nfo")
        val video = folderFile("Series/Season 2/01.mkv")
        val sibling = folderFile("Unrelated/01.mkv")
        reader.sidecars = mapOf(ini.relativePath to "[Bangumi]\nid=42\noffset=-12".toByteArray(),
            show.relativePath to "<tvshow><title>作品</title><tmdbid>5</tmdbid></tvshow>".toByteArray(),
            episode.relativePath to "<episodedetails><season>2</season><episode>3</episode></episodedetails>".toByteArray())
        scan(video, sibling, episode, show, ini)
        val rows = dao.getMedia(sourceId).associateBy { it.relativePath }
        val named = rows.getValue(video.relativePath)
        assertEquals(42, named.bangumiHint)
        assertEquals(5, named.tmdbHint)
        assertEquals("作品", named.parsedTitle)
        assertEquals(2, named.seasonNumber)
        assertEquals(3, named.episodeNumber)
        assertNull(named.episodeOffset)
        assertNull(rows.getValue(sibling.relativePath).bangumiHint)
        assertTrue(matcher { error("offline stage must not search") }.run(sourceId, online = false))
        assertEquals("bgm:42", dao.getMediaById(named.id)?.archiveKey)
        assertEquals(0, scan(video, sibling, episode, show, ini).parsedFiles)
        reader.sidecars = reader.sidecars + (episode.relativePath to
            "<episodedetails><season>2</season><episode>4</episode></episodedetails>".toByteArray())
        scan(video, sibling, episode, show, ini)
        assertEquals(4, dao.getMediaById(named.id)?.episodeNumber)
    }

    @Test
    fun reidentifyOnlyRetriesUnrecognizedFilesAndKeepsSuccessfulRows() = runBlocking {
        val known = folderFile("Known/Known - 01.mkv")
        val review = folderFile("Review/Review - 01.mkv")
        val waiting = folderFile("Waiting/Waiting - 01.mkv")
        val cleared = folderFile("Cleared/Cleared - 01.mkv")
        scan(known, review, waiting, cleared)
        val rows = dao.getMedia(sourceId).associateBy { it.relativePath }
        val hit = rows.getValue(known.relativePath)
        val clearedId = rows.getValue(cleared.relativePath).id
        repository.commitScrape(listOf(hit), LocalMediaArchiveEntity(sourceId, "bgm:7", "Known"), LocalMediaScrapeState.IDENTIFIED)
        repository.commitScrape(listOf(rows.getValue(review.relativePath)), null, LocalMediaScrapeState.REVIEW)
        repository.updateUserMatch(clearedId, null)
        val identified = dao.getMediaById(hit.id)
        val manual = dao.getMediaById(clearedId)

        val result = repository.scanSource(sourceId, null, reidentify = true)
        assertEquals(2, result.parsedFiles)
        assertEquals(2, result.writtenFiles)
        assertEquals(identified, dao.getMediaById(hit.id))
        assertEquals(manual, dao.getMediaById(clearedId))
        assertEquals(setOf(review.relativePath, waiting.relativePath), dao.getScrapeQueue(sourceId).map { it.relativePath }.toSet())
        val searched = mutableSetOf<String>()
        matcher { searched += it; emptyList() }.run(sourceId)
        assertEquals(setOf("Review", "Waiting"), searched)
    }

    @Test
    fun cardActionsApplyAcrossEvidenceGroupsWithoutChangingOtherSources() = runBlocking {
        val first = folderFile("One/Series S01E01.mkv")
        val second = folderFile("Two/Series S02E02.mkv")
        val review = folderFile("Other/Another - 01.mkv")
        scan(first, second, review)
        val grouped = dao.getMedia(sourceId).filter { it.relativePath != review.relativePath }
        val tracked = Anime(id = 78, title = "Series", totalEpisodes = 12, watchedEpisodes = 0,
            status = AnimeStatus.PLANNED, rating = null, notes = "")
        database.animeDao().insertAnime(tracked)
        val archive = LocalMediaArchiveEntity(sourceId, "bgm:7", "Series", bangumiId = 7)
        repository.commitScrape(grouped, archive, LocalMediaScrapeState.IDENTIFIED, tracked.id)
        val otherId = repository.addOrGetSource(Uri.parse("content://test/tree/other-card"), "Other source").id
        dao.putArchive(archive.copy(sourceId = otherId))
        dao.insertMedia(listOf(grouped.first().copy(id = 0L, sourceId = otherId,
            archiveKey = archive.key, matchState = LocalMediaMatchState.AUTO_MATCHED)))
        val card = dao.pageAnimeGroups(sourceId).readPages().single { it.archiveKey == archive.key }
        assertEquals(2, card.pendingCount)
        repository.confirmCardMatch(card)
        assertEquals(2, dao.observeConfirmedEpisodesForAnime(tracked.id).first().size)
        assertEquals(0, dao.pageAnimeGroups(sourceId).readPages().single { it.archiveKey == archive.key }.pendingCount)
        val changedArchive = archive.copy(key = "bgm:8", title = "Corrected", bangumiId = 8)
        repository.saveManualArchive(sourceId, null, changedArchive, null, card = card)
        val changed = dao.pageAnimeGroups(sourceId).readPages().single { it.archiveKey == changedArchive.key }
        assertEquals(2, changed.videoCount)
        repository.saveManualArchive(sourceId, null, null, null, card = changed)
        repository.commitScrape(grouped, archive, LocalMediaScrapeState.IDENTIFIED)
        repository.scanSource(sourceId, null, reidentify = true)
        val unclassified = dao.pageUnclassifiedMedia(sourceId).readPages()
        assertEquals(grouped.map { it.id }.toSet(), unclassified.map { it.id }.toSet())
        assertTrue(unclassified.all { it.archiveKey == null && it.matchedAnimeId == null && it.isUserOverride && !it.groupOverride })
        assertEquals(archive.key, dao.pageAnimeGroups(otherId).readPages().single().archiveKey)
        assertEquals(review.relativePath, dao.getScrapeQueue(sourceId).single().relativePath)
    }

    @Test
    fun confirmedArchiveAppearsInDetailWhenMatchingAnimeIsAdded() = runBlocking {
        scan(file(1))
        val local = dao.getMedia(sourceId).single()
        val archive = LocalMediaArchiveEntity(sourceId, "bgm:7", "Series", bangumiId = 7)
        repository.commitScrape(listOf(local), archive, LocalMediaScrapeState.IDENTIFIED)
        val card = dao.pageAnimeGroups(sourceId).readPages().single()
        repository.confirmCardMatch(card)
        val remoteUri = "${WebDavLocalMediaSource.URI_PREFIX}/https/current"
        val remoteId = dao.insertSource(LocalMediaSourceEntity(treeUri = remoteUri, displayName = "WebDAV"))
        val oldRemoteId = dao.insertSource(
            LocalMediaSourceEntity(treeUri = "${WebDavLocalMediaSource.URI_PREFIX}/https/old", displayName = "Old WebDAV")
        )
        for (id in listOf(remoteId, oldRemoteId)) {
            dao.putArchive(archive.copy(sourceId = id))
            dao.insertMedia(listOf(local.copy(id = 0, sourceId = id, documentUri = "https://dav.example/Series/01.mkv",
                archiveKey = archive.key, matchState = LocalMediaMatchState.CONFIRMED)))
        }
        val tracked = Anime(id = 78, title = "Tracking title", totalEpisodes = 12, watchedEpisodes = 0,
            status = AnimeStatus.PLANNED, rating = null, notes = "", bangumiId = 7)
        assertTrue(dao.observeConfirmedEpisodesForAnime(tracked.id, remoteUri).first().isEmpty())
        database.animeDao().insertAnime(tracked)

        val visible = dao.observeConfirmedEpisodesForAnime(tracked.id, remoteUri).first()
        assertEquals(setOf(sourceId, remoteId), visible.map { it.sourceId }.toSet())
        assertTrue(visible.all { it.matchedAnimeId == null })
        assertEquals(listOf(local.id), dao.observeConfirmedEpisodesForAnime(tracked.id).first().map { it.id })

        repository.setHidden(sourceId, local.id, true)
        assertEquals(listOf(remoteId), dao.observeConfirmedEpisodesForAnime(tracked.id, remoteUri).first().map { it.sourceId })
    }

    @Test
    fun confirmedTmdbArchiveRespectsSeasonAndDoesNotExposeUnconfirmedFiles() = runBlocking {
        val first = folderFile("Series/Series S01E01.mkv")
        val second = folderFile("Series/Series S02E01.mkv")
        val pending = folderFile("Series/Series S02E02.mkv")
        scan(first, second, pending)
        val files = dao.getMedia(sourceId).associateBy { it.relativePath }
        val archive = LocalMediaArchiveEntity(sourceId, "tmdb:tv:7", "Series", tmdbId = 7)
        for (file in listOf(first, second)) {
            repository.saveManualArchive(sourceId, files.getValue(file.relativePath).id, archive, null)
        }
        repository.commitScrape(listOf(files.getValue(pending.relativePath)), archive, LocalMediaScrapeState.IDENTIFIED)
        val tracked = Anime(id = 78, title = "Series", totalEpisodes = 12, watchedEpisodes = 0,
            status = AnimeStatus.PLANNED, rating = null, notes = "", tmdbId = 7, seasonNumber = 2)
        database.animeDao().insertAnime(tracked)

        assertEquals(listOf(second.relativePath), dao.observeConfirmedEpisodesForAnime(tracked.id).first().map { it.relativePath })
    }

    @Test
    fun clearingPendingGroupsUnclassifiesFilesAndBlocksOldScrapeResults() = runBlocking {
        val waiting = folderFile("Waiting/Waiting - 01.mkv")
        val review = folderFile("Review/Review - 01.mkv")
        scan(waiting, review)
        val initial = dao.getMedia(sourceId)
        repository.commitScrape(initial.filter { it.relativePath == review.relativePath }, null, LocalMediaScrapeState.REVIEW)
        val groups = dao.pageAnimeGroups(sourceId).readPages()
        assertEquals(setOf(true, false), groups.map { it.waiting }.toSet())
        assertTrue(groups.all { it.reviewKey != null && it.archiveKey == null })
        for (card in groups) repository.saveManualArchive(sourceId, null, null, null, card = card)

        repository.commitScrape(initial, LocalMediaArchiveEntity(sourceId, "bgm:1", "Stale"), LocalMediaScrapeState.IDENTIFIED)
        repository.scanSource(sourceId, null, reidentify = true)
        assertTrue(dao.pageAnimeGroups(sourceId).readPages().isEmpty())
        assertTrue(dao.getScrapeQueue(sourceId).isEmpty())
        val unclassified = dao.pageUnclassifiedMedia(sourceId).readPages()
        assertEquals(initial.map { it.id }.toSet(), unclassified.map { it.id }.toSet())
        assertTrue(unclassified.all { it.scrapeState == LocalMediaScrapeState.NONE && it.isUserOverride })
    }

    @Test
    fun temporaryNetworkFailureAndManualGroupCorrectionAreSeparate() = runBlocking {
        val first = folderFile("Series/Series - 01.mkv")
        val second = folderFile("Series/Series - 02.mkv")
        scan(first, second)
        assertTrue(matcher { throw IOException("offline") }.run(sourceId))
        assertTrue(dao.pageAnimeGroups(sourceId).readPages().single().waiting)
        val matcher = matcher { listOf(subject(1, "Series", "2001-01-01"), subject(2, "Series", "2024-01-01")) }
        assertFalse(matcher.run(sourceId))
        val review = dao.pageAnimeGroups(sourceId).readPages().single()
        assertFalse(review.waiting)
        assertEquals(2, review.videoCount)
        val oldFiles = dao.getMedia(sourceId)
        val archive = LocalMediaArchiveEntity(sourceId, "bgm:2", "Series", bangumiId = 2)
        repository.saveManualArchive(sourceId, null, archive, null, card = review)
        assertEquals("bgm:2", dao.pageAnimeGroups(sourceId).readPages().single().archiveKey)
        reader.visit = { emit -> emit(first); emit(second) }
        repository.scanSource(sourceId, null, reidentify = true)
        repository.commitScrape(oldFiles, LocalMediaArchiveEntity(sourceId, "bgm:1", "Wrong"), LocalMediaScrapeState.IDENTIFIED)
        val protected = dao.getMedia(sourceId)
        assertEquals(oldFiles.map { it.id }, protected.map { it.id })
        assertTrue(protected.all { it.archiveKey == "bgm:2" && it.isUserOverride && it.matchedAnimeId == null })
        val third = folderFile("Series/Series - 03.mkv")
        scan(first, second, third)
        assertFalse(matcher.run(sourceId, online = false))
        assertEquals("bgm:2", dao.getMedia(sourceId).single { it.relativePath == third.relativePath }.archiveKey)
        val current = dao.pageAnimeGroups(sourceId).readPages().single()
        repository.saveManualArchive(sourceId, null, archive.copy(key = "bgm:3", bangumiId = 3), null, card = current)
        assertEquals("bgm:3", dao.pageAnimeGroups(sourceId).readPages().single().archiveKey)
    }

    @Test
    fun bangumiRateLimitStillAllowsVerifiedTmdbMatch() = runBlocking {
        scan(folderFile("Series/Series - 01.mkv"))
        val matcher = matcher(
            searchTmdb = {
                listOf(TmdbTvShow(7, "Series", overview = null, posterPath = null, firstAirDate = null, voteAverage = null))
            },
            getTmdb = { id ->
                TmdbTvDetail(id, "Series", null, null, null, null, 12, 1, null, listOf(TmdbGenre(16, "Animation")))
            }
        ) {
            throw HttpException(Response.error<Unit>(429, "".toResponseBody()))
        }

        assertFalse(matcher.run(sourceId))
        assertEquals("tmdb:tv:7", dao.getMedia(sourceId).single().archiveKey)
    }

    @Test
    fun temporaryFailuresDoNotStarveGroupsBeyondTheRequestBudget() = runBlocking {
        val titles = (0 until 36).map { "Example ${'A' + it / 26}${'A' + it % 26}" }
        scan(*titles.map { folderFile("$it/$it - 01.mkv") }.toTypedArray())
        val target = dao.getScrapeQueue(sourceId).last()
        val title = checkNotNull(target.parsedTitle)
        val matcher = matcher { query ->
            if (query.equals(title, ignoreCase = true)) listOf(subject(99, title)) else throw IOException("temporary error")
        }

        assertTrue(matcher.run(sourceId))
        assertNull(dao.getMediaById(target.id)?.archiveKey)
        assertTrue(matcher.run(sourceId))
        assertEquals("bgm:99", dao.getMediaById(target.id)?.archiveKey)
        assertEquals(titles.size - 1, dao.observeScrapeGroupCount(sourceId).first())
    }

    @Test
    fun migration31PreservesFileIdAndManualState() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val filename = "media-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(filename)
        path.parentFile?.mkdirs()
        val json = instrumentation.context.assets.open("com.aiexile.animetrack.data.AnimeDatabase/31.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = json.getJSONArray("entities")
            repeat(entities.length()) { i ->
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                repeat(indices.length()) { index ->
                    db.execSQL(indices.getJSONObject(index).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                }
            }
            val setup = json.getJSONArray("setupQueries")
            repeat(setup.length()) { db.execSQL(setup.getString(it)) }
            db.execSQL("INSERT INTO local_source (id,treeUri,displayName,videoCount,scanState,permissionState,createdAt,updatedAt) VALUES (1,'content://test/tree/old','Old',1,'COMPLETED','GRANTED',0,0)")
            db.execSQL("INSERT INTO local_media (id,sourceId,documentUri,relativePath,fileName,size,lastModified,matchState,matchedAnimeId,confidence,isUserOverride,isMissing,isHidden,updatedAt) VALUES (50,1,'content://test/old','01.mkv','01.mkv',10,1,'CONFIRMED',7,1,1,0,1,0)")
            db.version = 31
        }
        val migrated = Room.databaseBuilder(context, AnimeDatabase::class.java, filename)
            .addMigrations(AnimeDatabase.MIGRATION_31_32, AnimeDatabase.MIGRATION_32_33).build()
        try {
            val item = checkNotNull(migrated.localMediaDao().getMediaById(50))
            assertEquals(7, item.matchedAnimeId)
            assertTrue(item.isHidden && item.isUserOverride)
            assertEquals(LocalMediaMatchState.CONFIRMED, item.matchState)
            assertEquals(0, item.parserVersion)
            assertNull(item.archiveKey)
        } finally {
            migrated.close()
            context.deleteDatabase(filename)
        }
    }

    private fun matcher(
        searchTmdb: suspend (String) -> List<TmdbTvShow> = { emptyList() },
        getTmdb: suspend (Int) -> TmdbTvDetail = { error("Unexpected TMDB detail request: $it") },
        search: suspend (String) -> List<BangumiSubject>
    ): LocalMediaAutoMatchUseCase {
        val base = AppContainer.getAnimeRepository()
        val fake = object : AnimeRepository by base {
            override fun getAllAnimes() = flowOf(emptyList<Anime>())
            override suspend fun searchBangumi(query: String) = search(query)
            override suspend fun searchTmdbTvShows(query: String) = searchTmdb(query)
            override suspend fun getTmdbTvDetail(tmdbId: Int) = getTmdb(tmdbId)
            override suspend fun insertAnime(anime: Anime): Long = error("Scraping must not write subscriptions")
        }
        return LocalMediaAutoMatchUseCase(repository, fake, episodes = { emptyList() }, relations = { emptyList() })
    }

    private fun folderFile(path: String) = LocalMediaScanFile(path, path.substringAfterLast('/'), "content://test/$path", 10, 20,
        path.substringBeforeLast('/', "root"))

    private fun subject(id: Int, title: String, date: String? = null) = BangumiSubject(
        id, title, null, com.aiexile.animetrack.data.network.BangumiImages(null, null, "https://example.invalid/$id", null, null),
        12, 12, null, date = date
    )

    private suspend fun <T : Any> PagingSource<Int, T>.readPages(): List<T> {
        return try {
            buildList {
                var page = load(PagingSource.LoadParams.Refresh(null, 1, true)) as PagingSource.LoadResult.Page<Int, T>
                while (true) {
                    addAll(page.data)
                    val next = page.nextKey ?: break
                    page = load(PagingSource.LoadParams.Append(next, 1, true)) as PagingSource.LoadResult.Page<Int, T>
                }
            }
        } finally {
            invalidate()
        }
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

        var sidecars: Map<String, ByteArray> = emptyMap()

        override suspend fun readSidecar(file: LocalMediaScanFile): ByteArray? = sidecars[file.relativePath]

        override suspend fun readFiles(treeUri: Uri, onFile: suspend (LocalMediaScanFile) -> Unit) = visit(onFile)
    }
}
