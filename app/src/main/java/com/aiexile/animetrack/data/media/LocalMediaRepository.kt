package com.aiexile.animetrack.data.media

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.VideoFormats
import com.aiexile.animetrack.domain.playback.EpisodeFileNameParser
import com.aiexile.animetrack.util.normalizeTitleForMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class LocalMediaRepository internal constructor(
    private val context: Context,
    private val dao: LocalMediaDao,
    private val treeReader: LocalMediaTreeReader,
    private val webDavTreeReader: LocalMediaTreeReader? = null
) {
    constructor(context: Context, dao: LocalMediaDao) : this(
        context, dao, SafLocalMediaTreeReader(context.contentResolver)
    )

    internal constructor(context: Context, dao: LocalMediaDao, settingsRepository: SettingsRepository) : this(
        context,
        dao,
        SafLocalMediaTreeReader(context.contentResolver),
        WebDavLocalMediaTreeReader(settingsRepository)
    )

    private fun readerFor(treeUri: Uri): LocalMediaTreeReader =
        if (treeUri.scheme.equals("webdav", ignoreCase = true)) {
            webDavTreeReader ?: error("WebDAV 媒体库读取器未配置")
        } else {
            treeReader
        }

    data class ScanProgress(val scannedFiles: Int, val videoFiles: Int)

    data class ScanResult(
        val scannedFiles: Int,
        val videoFiles: Int,
        val missingFiles: Int,
        val parsedFiles: Int = 0,
        val writtenFiles: Int = 0
    )

    /** 可选的基准采样，普通扫描不为每个阶段额外读取时钟。 */
    internal class ScanTimings {
        var traversalNs = 0L
        var parsingNs = 0L
        var databaseNs = 0L
    }

    private val activeScans = ConcurrentHashMap.newKeySet<Long>()
    private val webDavSourceLock = Mutex()

    fun observeSources(): Flow<List<LocalMediaSourceEntity>> = dao.observeSources()

    fun pageAnimeGroups(sourceId: Long) = Pager(libraryPagingConfig) { dao.pageAnimeGroups(sourceId) }.flow

    internal fun observeLibraryGroups(webDavUri: String?) = dao.observeLibraryGroups(webDavUri)

    internal fun observeCardMedia(group: LocalMediaAnimeGroup) =
        dao.observeCardMedia(group.sourceId, group.archiveKey, group.reviewKey, group.animeId)

    internal fun observeVisibleMedia(ids: Set<Long>): Flow<List<LocalMediaEntity>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return combine(ids.chunked(500).map(dao::observeVisibleMediaByIds)) { batches -> batches.flatMap { it } }
    }

    fun pageUnclassifiedMedia(sourceId: Long) = Pager(libraryPagingConfig) { dao.pageUnclassifiedMedia(sourceId) }.flow

    fun pageAnimeMedia(sourceId: Long, animeId: Int) = Pager(libraryPagingConfig) { dao.pageAnimeMedia(sourceId, animeId) }.flow

    fun pageArchiveMedia(sourceId: Long, archiveKey: String?, reviewKey: String?) =
        Pager(libraryPagingConfig) { dao.pageArchiveMedia(sourceId, archiveKey, reviewKey) }.flow

    internal fun pageGroupMedia(group: LocalMediaAnimeGroup) = if (group.archiveKey != null || group.reviewKey != null) {
        pageArchiveMedia(group.sourceId, group.archiveKey, group.reviewKey)
    } else {
        pageAnimeMedia(group.sourceId, group.animeId)
    }

    suspend fun getArchive(sourceId: Long, key: String) = dao.getArchive(sourceId, key)

    internal suspend fun getScrapeQueue(sourceId: Long) = dao.getScrapeQueue(sourceId)

    internal fun observeScrapeGroupCount(sourceId: Long) = dao.observeScrapeGroupCount(sourceId)

    internal suspend fun archivesForEvidence(sourceId: Long, groupKey: String) = dao.findArchivesForEvidence(sourceId, groupKey)

    internal suspend fun manualArchivesForEvidence(sourceId: Long, groupKey: String) = dao.findManualArchivesForEvidence(sourceId, groupKey)

    internal suspend fun commitScrape(
        expected: List<LocalMediaEntity>, archive: LocalMediaArchiveEntity?, state: String?, animeId: Int? = null,
        episodeMapping: Map<Long, Pair<Int, Int?>> = emptyMap()
    ) = dao.commitScrape(expected, archive, state, animeId, episodeMapping, System.currentTimeMillis())

    internal suspend fun saveManualArchive(
        sourceId: Long, mediaId: Long?, archive: LocalMediaArchiveEntity?, animeId: Int?,
        card: LocalMediaAnimeGroup? = null
    ) = dao.saveManualArchive(sourceId, mediaId, archive, animeId, System.currentTimeMillis(), card)

    internal suspend fun confirmCardMatch(card: LocalMediaAnimeGroup) = dao.confirmCardMatch(card, System.currentTimeMillis())

    internal suspend fun getPlayableGroup(selected: LocalMediaEntity): List<LocalMediaEntity> =
        selected.archiveKey?.let { dao.getPlayableArchive(selected.sourceId, it) }
            ?: selected.matchedAnimeId?.let { dao.getMedia(selected.sourceId) }
            ?: listOf(selected)

    fun pageHiddenMedia(sourceId: Long) = Pager(libraryPagingConfig) { dao.pageHiddenMedia(sourceId) }.flow

    fun observeHiddenCount(sourceId: Long): Flow<Int> = dao.observeHiddenCount(sourceId)

    suspend fun setHidden(sourceId: Long, mediaId: Long, hidden: Boolean) {
        check(dao.setHidden(sourceId, mediaId, hidden, System.currentTimeMillis()) == 1) { "本地媒体已移除" }
    }

    private val libraryPagingConfig = PagingConfig(
        pageSize = 50, initialLoadSize = 100, prefetchDistance = 20, maxSize = 250, jumpThreshold = 150
    )

    suspend fun getSource(sourceId: Long): LocalMediaSourceEntity? = dao.getSource(sourceId)

    internal suspend fun refreshMissingGrants(sources: List<LocalMediaSourceEntity>) = withContext(Dispatchers.IO) {
        for (source in sources) {
            val reader = readerFor(Uri.parse(source.treeUri))
            if (source.permissionState == LocalMediaPermissionState.GRANTED &&
                !reader.hasPersistedReadPermission(Uri.parse(source.treeUri))
            ) {
                dao.markPermissionRevoked(source.id)
            }
        }
    }

    /** 先比较既有 URI，选错目录不能持久化权限或改变来源。 */
    internal suspend fun reauthorizeSource(sourceId: Long, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val source = checkNotNull(dao.getSource(sourceId)) { "本地来源已移除" }
        if (Uri.parse(source.treeUri) != treeUri) return@withContext false
        readerFor(treeUri).takePersistedReadPermission(treeUri)
        dao.refreshSourceGrant(sourceId, source.displayName, System.currentTimeMillis())
        true
    }

    /** 详情页播放入口：只返回已确认关联且仍在盘上的剧集。 */
    fun observeConfirmedEpisodesForAnime(animeId: Int, activeWebDavUri: String? = null): Flow<List<LocalMediaEntity>> =
        dao.observeConfirmedEpisodesForAnime(animeId, activeWebDavUri)

    fun observeCompletedEpisodeNumbers(animeId: Int): Flow<List<Int>> =
        dao.observeCompletedEpisodeNumbers(animeId)

    suspend fun addOrGetSource(treeUri: Uri, displayName: String): LocalMediaSourceEntity {
        val uriString = treeUri.toString()
        val source = dao.getSourceByTreeUri(uriString) ?: run {
            dao.insertSource(LocalMediaSourceEntity(treeUri = uriString, displayName = displayName))
            checkNotNull(dao.getSourceByTreeUri(uriString)) { "无法添加本地文件夹" }
        }
        dao.refreshSourceGrant(source.id, displayName, System.currentTimeMillis())
        return checkNotNull(dao.getSource(source.id)) { "本地来源已移除" }
    }

    suspend fun syncPlayerWebDavSource(): LocalMediaSourceEntity? = webDavSourceLock.withLock {
        val reader = webDavTreeReader as? WebDavLocalMediaTreeReader ?: return@withLock null
        val activeUri = reader.currentConfig()?.let(WebDavLocalMediaSource::uri)
        dao.deleteInactiveWebDavSources(WebDavLocalMediaSource.URI_PREFIX, activeUri)
        activeUri ?: return@withLock null
        dao.getSourceByTreeUri(activeUri) ?: run {
            dao.insertSource(LocalMediaSourceEntity(treeUri = activeUri, displayName = "WebDAV"))
            checkNotNull(dao.getSourceByTreeUri(activeUri)) { "无法添加 WebDAV 媒体库" }
        }
    }

    suspend fun deleteSource(source: LocalMediaSourceEntity) = dao.deleteSource(source)

    suspend fun scanSource(
        sourceId: Long,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): ScanResult = scanSource(sourceId, null, onProgress = onProgress)

    internal suspend fun scanSource(
        sourceId: Long,
        timings: ScanTimings?,
        reidentify: Boolean = false,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): ScanResult {
        if (!activeScans.add(sourceId)) throw LocalMediaScanBusyException()
        try {
            return withContext(Dispatchers.IO) { runScan(sourceId, timings, onProgress, reidentify) }
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                stopScanPreservingFailure(sourceId, LocalMediaScanState.CANCELLED, e)
            }
            throw e
        } catch (e: Exception) {
            stopScanPreservingFailure(sourceId, LocalMediaScanState.FAILED, e)
            throw e
        } finally {
            activeScans.remove(sourceId)
        }
    }

    private suspend fun stopScanPreservingFailure(sourceId: Long, state: String, cause: Exception) {
        val failed = state == LocalMediaScanState.FAILED
        val revoked = failed && cause is SecurityException
        val error = when {
            revoked -> "本地文件夹授权已失效"
            failed -> cause.message ?: "扫描失败"
            else -> null
        }
        try {
            dao.stopScan(sourceId, state, error, revoked, System.currentTimeMillis())
        } catch (cleanupFailure: Exception) {
            if (cleanupFailure !== cause) cause.addSuppressed(cleanupFailure)
        }
    }

    private suspend fun runScan(
        sourceId: Long,
        timings: ScanTimings?,
        onProgress: suspend (ScanProgress) -> Unit,
        reidentify: Boolean
    ): ScanResult {
        val source = measured(timings, { checkNotNull(dao.getSource(sourceId)) { "本地来源已移除" } }) { databaseNs += it }
        measured(timings, {
            check(dao.beginScan(sourceId, System.currentTimeMillis()) == 1) { "本地来源已移除" }
        }) { databaseNs += it }
        val treeUri = Uri.parse(source.treeUri)
        val reader = readerFor(treeUri)
        if (!reader.hasPersistedReadPermission(treeUri)) {
            throw SecurityException("本地文件夹授权已失效")
        }

        val existing = measured(timings, { dao.getScanSnapshot(sourceId) }) { databaseNs += it }
            .associateBy { it.relativePath }
        val seenPaths = HashSet<String>(existing.size)
        val videos = ArrayList<LocalMediaScanFile>(existing.size)
        val sidecars = ArrayList<LocalMediaScanFile>()
        val pending = ArrayList<LocalMediaEntity>(BATCH_SIZE)
        var scannedFiles = 0
        var parsedFiles = 0
        var writtenFiles = 0
        var lastProgressAt = 0L
        val scanContext = currentCoroutineContext()

        suspend fun commitBatch() {
            measured(timings, { dao.commitScanBatch(pending, reidentify) }) { databaseNs += it }
            writtenFiles += pending.size
            pending.clear()
        }

        val beforeTraversal = if (timings != null) SystemClock.elapsedRealtimeNanos() else 0L
        val databaseBeforeTraversal = timings?.databaseNs ?: 0L
        var progressNs = 0L
        reader.readFiles(treeUri) { file ->
            scanContext.ensureActive()
            scannedFiles++
            if (VideoFormats.isVideoFile(file.fileName)) {
                if (!seenPaths.add(file.relativePath)) throw IOException("目录返回了重复的视频路径")
                videos += file
                val old = existing[file.relativePath]
                val unchanged = old != null && file.hasUnchangedContent(old) && old.parserVersion == EpisodeFileNameParser.VERSION
                // 待识别项留到边车读取后统一重判；已经归档的文件仍使用增量扫描。
                if (old?.sidecarFingerprint == null && !(reidentify && old?.canRetryRecognition() == true)) {
                    if (!unchanged) parsedFiles++
                    val update = if (unchanged) buildScanUpdate(sourceId, file, old, true)
                    else measured(timings, { buildScanUpdate(sourceId, file, old, false) }) { parsingNs += it }
                    update?.let(pending::add)
                    if (pending.size >= BATCH_SIZE) commitBatch()
                }
            } else if (LocalMediaSidecarIndex.supports(file.fileName)) {
                sidecars += file
            }
            val now = SystemClock.elapsedRealtime()
            if (lastProgressAt == 0L || now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                measured(timings, { onProgress(ScanProgress(scannedFiles, seenPaths.size)) }) { progressNs += it }
                lastProgressAt = now
            }
        }
        if (timings != null) {
            timings.traversalNs = SystemClock.elapsedRealtimeNanos() - beforeTraversal - timings.parsingNs -
                (timings.databaseNs - databaseBeforeTraversal) - progressNs
        }
        scanContext.ensureActive()
        if (!reader.hasPersistedReadPermission(treeUri)) {
            throw SecurityException("本地文件夹授权已失效")
        }
        if (pending.isNotEmpty()) commitBatch()
        if (sidecars.isNotEmpty() || reidentify || existing.values.any { it.sidecarFingerprint != null }) {
            val sidecarIndex = LocalMediaSidecarIndex(sidecars)
            val rows = dao.getScanSnapshot(sourceId).associateBy { it.relativePath }
            for (file in videos) {
                scanContext.ensureActive()
                val evidence = sidecarIndex.readFor(file.relativePath, file.fileName, reader::readSidecar)
                    .takeUnless { it == LocalMediaSidecarEvidence() }
                val old = rows[file.relativePath]
                val evidenceKey = evidence?.toString()
                val retry = reidentify && old?.canRetryRecognition() == true
                val unchanged = !retry && old != null && file.hasUnchangedContent(old) &&
                    old.parserVersion == EpisodeFileNameParser.VERSION && old.sidecarFingerprint == evidenceKey
                if (!unchanged) parsedFiles++
                val update = measured(timings, {
                    buildScanUpdate(sourceId, file, old, unchanged, evidence, retry)
                }) { parsingNs += it }
                update?.let(pending::add)
                if (pending.size >= BATCH_SIZE) commitBatch()
            }
            if (pending.isNotEmpty()) commitBatch()
        }
        onProgress(ScanProgress(scannedFiles, seenPaths.size))
        val missingIds = existing.values.mapNotNull { old ->
            old.id.takeIf { !old.isMissing && old.relativePath !in seenPaths }
        }
        scanContext.ensureActive()
        val missingCount = measured(timings, {
            dao.finishScan(sourceId, missingIds, seenPaths.size, System.currentTimeMillis())
        }) { databaseNs += it }
        return ScanResult(scannedFiles, seenPaths.size, missingCount, parsedFiles, writtenFiles + missingIds.size)
    }

    private inline fun <T> measured(timings: ScanTimings?, block: () -> T, record: ScanTimings.(Long) -> Unit): T {
        if (timings == null) return block()
        val start = SystemClock.elapsedRealtimeNanos()
        return block().also { timings.record(SystemClock.elapsedRealtimeNanos() - start) }
    }

    private fun buildScanUpdate(
        sourceId: Long,
        file: LocalMediaScanFile,
        old: LocalMediaEntity?,
        unchanged: Boolean,
        evidence: LocalMediaSidecarEvidence? = null,
        reidentify: Boolean = false
    ): LocalMediaEntity? {
        if (old != null && unchanged) {
            if (!old.isMissing && old.documentUri == file.documentUri && old.parentDocumentId == file.parentDocumentId) {
                return null
            }
            return old.copy(
                documentUri = file.documentUri,
                parentDocumentId = file.parentDocumentId,
                isMissing = false,
                updatedAt = System.currentTimeMillis()
            )
        }

        val folder = file.relativePath.substringBeforeLast('/', "")
        val parsed = EpisodeFileNameParser.parse(file.fileName, folder)
        val sidecarYear = evidence?.year
        val year = sidecarYear ?: parsed.year
        val season = evidence?.season ?: parsed.seasonNumber
        val isMovie = evidence?.movie == true || parsed.isMovie
        val name = evidence?.title ?: parsed.parsedTitle
        val names = (listOfNotNull(evidence?.title, evidence?.originalTitle).ifEmpty { parsed.titleCandidates })
            .map { it.replace('\u001f', ' ').trim() }.filter { it.isNotBlank() }.distinctBy(::normalizeTitleForMatch)
        val bangumiHint = evidence?.bangumiId?.takeUnless { evidence.conflict } ?: parsed.bangumiId
        val tmdbHint = evidence?.tmdbId?.takeUnless { evidence.conflict } ?: parsed.tmdbId
        val groupKey = if (names.isEmpty() && bangumiHint == null && tmdbHint == null) null else
            listOf(folder, names.map(::normalizeTitleForMatch).sorted().joinToString("|"), season, year, isMovie, bangumiHint, tmdbHint,
                evidence?.conflict == true, evidence?.offset).joinToString("\u001f")
        val nfoEpisode = evidence?.episode
        val episode = if (nfoEpisode != null) nfoEpisode.toIntOrNull() else parsed.episodeNumber
        val decimal = if (nfoEpisode != null) nfoEpisode.takeIf { '.' in it } else parsed.decimalEpisode
        val isMain = parsed.isMainFeature && season != 0 && decimal == null
        val scrapeState = when {
            groupKey == null -> LocalMediaScrapeState.NONE
            evidence?.conflict == true -> LocalMediaScrapeState.REVIEW
            !parsed.isMainFeature && evidence?.bangumiId == null && evidence?.tmdbId == null -> LocalMediaScrapeState.NONE
            else -> LocalMediaScrapeState.WAITING
        }
        val refreshed = LocalMediaEntity(
            id = old?.id ?: 0L,
            sourceId = sourceId,
            documentUri = file.documentUri,
            relativePath = file.relativePath,
            fileName = file.fileName,
            size = file.size,
            lastModified = file.lastModified,
            parsedTitle = name,
            seasonNumber = season,
            episodeNumber = episode,
            episodeEndNumber = parsed.episodeEndNumber.takeIf { nfoEpisode == null },
            matchState = if (isMain) LocalMediaMatchState.UNMATCHED else LocalMediaMatchState.IGNORED,
            confidence = parsed.confidence,
            episodeType = when {
                !isMain -> LocalEpisodeType.EXTRA
                episode != null -> LocalEpisodeType.MAIN
                else -> LocalEpisodeType.UNKNOWN
            },
            parentDocumentId = file.parentDocumentId,
            parserVersion = EpisodeFileNameParser.VERSION,
            groupKey = groupKey,
            titleEvidence = names.joinToString("\u001f").takeIf(String::isNotBlank),
            baseTitle = if (evidence?.title == null) parsed.baseTitle else evidence.title,
            releaseYear = year,
            decimalEpisode = decimal,
            originalEpisode = parsed.decimalEpisode ?: parsed.episodeNumber?.toString(),
            bangumiHint = bangumiHint,
            tmdbHint = tmdbHint,
            episodeOffset = evidence?.offset?.takeIf {
                nfoEpisode == null && !parsed.explicitNumbering && episode != null && parsed.isMainFeature
            },
            sidecarFingerprint = evidence?.toString(),
            movie = isMovie,
            scrapeState = scrapeState,
            explicitEpisodeNumbers = nfoEpisode != null || parsed.explicitNumbering
        )
        return refreshed.takeUnless {
            !reidentify && old != null && !old.isMissing && it.hasSameScanContent(old) &&
                old.documentUri == file.documentUri && old.parentDocumentId == file.parentDocumentId
        }
    }

    suspend fun getPendingMatches(sourceId: Long): List<LocalMediaEntity> = dao.getPendingMatches(sourceId)

    internal suspend fun applyAutoMatches(items: List<LocalMediaAutoMatch>) {
        if (items.isNotEmpty()) dao.applyAutoMatches(items, System.currentTimeMillis())
    }

    suspend fun getMediaForSource(sourceId: Long): List<LocalMediaEntity> = dao.getMedia(sourceId)

    suspend fun getMediaById(mediaId: Long): LocalMediaEntity? = dao.getMediaById(mediaId)

    internal suspend fun findTrackedAnimeIdForArchive(mediaId: Long): Int? =
        dao.findTrackedAnimeIdsForArchive(mediaId).singleOrNull()

    /** 记录单集完成；重复完成时更新完成时间。 */
    suspend fun recordEpisodeCompleted(
        animeId: Int,
        seasonNumber: Int?,
        episodeNumber: Int
    ) = dao.markEpisodeCompleted(
        LocalEpisodeProgressEntity(
            animeId = animeId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            completedAt = System.currentTimeMillis()
        )
    )

    suspend fun getCompletedEpisodes(animeId: Int): Set<Int> =
        dao.getCompletedEpisodes(animeId).toSet()

    internal suspend fun findLocalSubtitles(
        videos: List<LocalMediaEntity>,
        directoryTimeoutMs: Long
    ): Map<Long, List<LocalSubtitle>> {
        val sourceUris = mutableMapOf<Long, Uri?>()
        return LocalSubtitleLocator.findAll(videos, directoryTimeoutMs) { sourceId, parentId ->
            val treeUri = if (sourceId in sourceUris) sourceUris[sourceId] else {
                dao.getSource(sourceId)?.let { Uri.parse(it.treeUri) }.also { sourceUris[sourceId] = it }
            }
            when {
                treeUri?.scheme.equals("webdav", ignoreCase = true) ->
                    (webDavTreeReader as? WebDavLocalMediaTreeReader)?.readSubtitleDirectory(parentId).orEmpty()
                treeUri != null -> LocalSubtitleLocator.readDirectory(context, treeUri, parentId)
                else -> emptyList()
            }
        }
    }

    suspend fun updateUserMatch(mediaId: Long, animeId: Int?) {
        dao.updateMatch(
            mediaId = mediaId,
            animeId = animeId,
            confidence = if (animeId == null) 0f else 1f,
            matchState = LocalMediaMatchState.CONFIRMED,
            isUserOverride = true,
            updatedAt = System.currentTimeMillis()
        )
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val PROGRESS_INTERVAL_MS = 250L
    }
}

internal class LocalMediaScanBusyException : IllegalStateException("该文件夹正在扫描")

internal fun LocalMediaEntity.hasSameScanContent(other: LocalMediaEntity, ignoreParserVersion: Boolean = false): Boolean =
    fileName == other.fileName && size == other.size && lastModified == other.lastModified &&
        parsedTitle == other.parsedTitle && seasonNumber == other.seasonNumber &&
        (ignoreParserVersion || parserVersion == other.parserVersion) && groupKey == other.groupKey && titleEvidence == other.titleEvidence &&
        originalEpisode == other.originalEpisode && sidecarFingerprint == other.sidecarFingerprint &&
        explicitEpisodeNumbers == other.explicitEpisodeNumbers &&
        episodeType == other.episodeType && (parserVersion != 0 ||
            (episodeNumber == other.episodeNumber && episodeEndNumber == other.episodeEndNumber))

/** 重识别不重置成功结果、手动解除或本来就不参与刮削的普通视频。 */
internal fun LocalMediaEntity.canRetryRecognition(): Boolean =
    !isUserOverride && !isHidden && archiveKey == null && matchedAnimeId == null && groupKey != null &&
        (scrapeState == LocalMediaScrapeState.WAITING || scrapeState == LocalMediaScrapeState.REVIEW)
