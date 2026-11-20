package com.aiexile.animetrack.data.media

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.aiexile.animetrack.data.player.VideoFormats
import com.aiexile.animetrack.domain.playback.EpisodeFileNameParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class LocalMediaRepository internal constructor(
    private val context: Context,
    private val dao: LocalMediaDao,
    private val treeReader: LocalMediaTreeReader
) {
    constructor(context: Context, dao: LocalMediaDao) : this(
        context, dao, SafLocalMediaTreeReader(context.contentResolver)
    )

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

    fun observeSources(): Flow<List<LocalMediaSourceEntity>> = dao.observeSources()

    fun pagePresentMedia(sourceId: Long) = Pager(
        config = PagingConfig(pageSize = 50, initialLoadSize = 100, prefetchDistance = 20, maxSize = 250, jumpThreshold = 150),
        pagingSourceFactory = { dao.pagePresentMedia(sourceId) }
    ).flow

    suspend fun getSource(sourceId: Long): LocalMediaSourceEntity? = dao.getSource(sourceId)

    internal suspend fun refreshMissingGrants(sources: List<LocalMediaSourceEntity>) = withContext(Dispatchers.IO) {
        for (source in sources) {
            if (source.permissionState == LocalMediaPermissionState.GRANTED &&
                !treeReader.hasPersistedReadPermission(Uri.parse(source.treeUri))
            ) {
                dao.markPermissionRevoked(source.id)
            }
        }
    }

    /** 先比较既有 URI，选错目录不能持久化权限或改变来源。 */
    internal suspend fun reauthorizeSource(sourceId: Long, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val source = checkNotNull(dao.getSource(sourceId)) { "本地来源已移除" }
        if (Uri.parse(source.treeUri) != treeUri) return@withContext false
        treeReader.takePersistedReadPermission(treeUri)
        dao.refreshSourceGrant(sourceId, source.displayName, System.currentTimeMillis())
        true
    }

    /** 详情页播放入口：只返回已确认关联且仍在盘上的剧集。 */
    fun observeConfirmedEpisodesForAnime(animeId: Int): Flow<List<LocalMediaEntity>> =
        dao.observeConfirmedEpisodesForAnime(animeId)

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

    suspend fun deleteSource(source: LocalMediaSourceEntity) = dao.deleteSource(source)

    suspend fun scanSource(
        sourceId: Long,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): ScanResult = scanSource(sourceId, null, onProgress)

    internal suspend fun scanSource(
        sourceId: Long,
        timings: ScanTimings?,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): ScanResult {
        if (!activeScans.add(sourceId)) throw LocalMediaScanBusyException()
        try {
            return withContext(Dispatchers.IO) { runScan(sourceId, timings, onProgress) }
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
        onProgress: suspend (ScanProgress) -> Unit
    ): ScanResult {
        val source = measured(timings, { checkNotNull(dao.getSource(sourceId)) { "本地来源已移除" } }) { databaseNs += it }
        measured(timings, {
            check(dao.beginScan(sourceId, System.currentTimeMillis()) == 1) { "本地来源已移除" }
        }) { databaseNs += it }
        val treeUri = Uri.parse(source.treeUri)
        if (!treeReader.hasPersistedReadPermission(treeUri)) {
            throw SecurityException("本地文件夹授权已失效")
        }

        val existing = measured(timings, { dao.getScanSnapshot(sourceId) }) { databaseNs += it }
            .associateBy { it.relativePath }
        val seenPaths = HashSet<String>(existing.size)
        val pending = ArrayList<LocalMediaEntity>(BATCH_SIZE)
        var scannedFiles = 0
        var parsedFiles = 0
        var writtenFiles = 0
        var lastProgressAt = 0L
        val scanContext = currentCoroutineContext()

        suspend fun commitBatch() {
            measured(timings, { dao.commitScanBatch(pending) }) { databaseNs += it }
            writtenFiles += pending.size
            pending.clear()
        }

        val beforeTraversal = if (timings != null) SystemClock.elapsedRealtimeNanos() else 0L
        val databaseBeforeTraversal = timings?.databaseNs ?: 0L
        var progressNs = 0L
        treeReader.readFiles(treeUri) { file ->
            scanContext.ensureActive()
            scannedFiles++
            if (VideoFormats.isVideoFile(file.fileName)) {
                if (!seenPaths.add(file.relativePath)) throw IOException("目录返回了重复的视频路径")
                val old = existing[file.relativePath]
                val unchanged = old != null && file.hasUnchangedContent(old)
                if (!unchanged) parsedFiles++
                val update = if (unchanged) buildScanUpdate(sourceId, file, old, true)
                else measured(timings, { buildScanUpdate(sourceId, file, old, false) }) { parsingNs += it }
                update?.let(pending::add)
                if (pending.size >= BATCH_SIZE) commitBatch()
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
        if (!treeReader.hasPersistedReadPermission(treeUri)) {
            throw SecurityException("本地文件夹授权已失效")
        }
        if (pending.isNotEmpty()) commitBatch()
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
        unchanged: Boolean
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

        val parsed = EpisodeFileNameParser.parse(file.fileName, file.relativePath.substringBeforeLast('/', ""))
        val refreshed = LocalMediaEntity(
            id = old?.id ?: 0L,
            sourceId = sourceId,
            documentUri = file.documentUri,
            relativePath = file.relativePath,
            fileName = file.fileName,
            size = file.size,
            lastModified = file.lastModified,
            parsedTitle = parsed.parsedTitle,
            seasonNumber = parsed.seasonNumber,
            episodeNumber = parsed.episodeNumber,
            episodeEndNumber = parsed.episodeEndNumber,
            matchState = if (parsed.isMainFeature) LocalMediaMatchState.UNMATCHED else LocalMediaMatchState.IGNORED,
            confidence = parsed.confidence,
            episodeType = when {
                !parsed.isMainFeature -> LocalEpisodeType.EXTRA
                parsed.episodeNumber != null -> LocalEpisodeType.MAIN
                else -> LocalEpisodeType.UNKNOWN
            },
            parentDocumentId = file.parentDocumentId
        )
        return refreshed.takeUnless {
            old != null && !old.isMissing && it.hasSameScanContent(old) &&
                old.documentUri == file.documentUri && old.parentDocumentId == file.parentDocumentId
        }
    }

    suspend fun getPendingMatches(sourceId: Long): List<LocalMediaEntity> = dao.getPendingMatches(sourceId)

    internal suspend fun applyAutoMatches(items: List<LocalMediaAutoMatch>) {
        if (items.isNotEmpty()) dao.applyAutoMatches(items, System.currentTimeMillis())
    }

    suspend fun getMediaForSource(sourceId: Long): List<LocalMediaEntity> = dao.getMedia(sourceId)

    suspend fun getMediaById(mediaId: Long): LocalMediaEntity? = dao.getMediaById(mediaId)

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

    suspend fun findLocalSubtitles(media: LocalMediaEntity): List<LocalSubtitle> {
        val source = dao.getSource(media.sourceId) ?: return emptyList()
        return LocalSubtitleLocator.find(context, Uri.parse(source.treeUri), media)
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

internal fun LocalMediaEntity.hasSameScanContent(other: LocalMediaEntity): Boolean =
    fileName == other.fileName && size == other.size && lastModified == other.lastModified &&
        parsedTitle == other.parsedTitle && seasonNumber == other.seasonNumber &&
        episodeNumber == other.episodeNumber && episodeEndNumber == other.episodeEndNumber &&
        episodeType == other.episodeType
