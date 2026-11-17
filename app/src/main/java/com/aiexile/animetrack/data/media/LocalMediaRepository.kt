package com.aiexile.animetrack.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.aiexile.animetrack.data.player.VideoFormats
import com.aiexile.animetrack.domain.media.EpisodeFileNameParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class LocalMediaRepository(
    private val context: Context,
    private val dao: LocalMediaDao
) {
    data class ScanProgress(
        val scannedFiles: Int,
        val videoFiles: Int,
        val currentPath: String = ""
    )

    data class ScanResult(
        val scannedFiles: Int,
        val videoFiles: Int,
        val missingFiles: Int
    )

    fun observeSources(): Flow<List<LocalMediaSourceEntity>> = dao.observeSources()

    fun observeMedia(sourceId: Long): Flow<List<LocalMediaEntity>> = dao.observeMedia(sourceId)

    suspend fun addOrGetSource(treeUri: Uri, displayName: String): LocalMediaSourceEntity {
        val uriString = treeUri.toString()
        dao.getSourceByTreeUri(uriString)?.let { existing ->
            val updated = existing.copy(
                displayName = displayName,
                permissionState = LocalMediaPermissionState.GRANTED,
                updatedAt = System.currentTimeMillis()
            )
            dao.updateSource(updated)
            return updated
        }

        val source = LocalMediaSourceEntity(
            treeUri = uriString,
            displayName = displayName
        )
        val id = dao.insertSource(source)
        return dao.getSource(id.takeIf { it > 0 } ?: source.id)
            ?: dao.getSourceByTreeUri(uriString)
            ?: source.copy(id = id)
    }

    suspend fun deleteSource(source: LocalMediaSourceEntity) = dao.deleteSource(source)

    suspend fun scanSource(
        source: LocalMediaSourceEntity,
        onProgress: suspend (ScanProgress) -> Unit = {}
    ): ScanResult {
        val treeUri = Uri.parse(source.treeUri)
        if (!hasPersistedReadPermission(treeUri)) {
            dao.updateSource(
                source.copy(
                    permissionState = LocalMediaPermissionState.REVOKED,
                    scanState = LocalMediaScanState.FAILED,
                    scanError = "本地文件夹授权已失效",
                    updatedAt = System.currentTimeMillis()
                )
            )
            throw SecurityException("本地文件夹授权已失效")
        }

        val scanStartedAt = System.currentTimeMillis()
        dao.updateSource(
            source.copy(
                permissionState = LocalMediaPermissionState.GRANTED,
                scanState = LocalMediaScanState.SCANNING,
                scanError = null,
                updatedAt = scanStartedAt
            )
        )
        dao.markAllMissing(source.id, scanStartedAt)

        return try {
            val existing = dao.getMedia(source.id).associateBy { it.relativePath }
            val pending = mutableListOf<LocalMediaEntity>()
            var scannedFiles = 0
            var videoFiles = 0
            val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

            walkDirectory(
                treeUri = treeUri,
                documentId = rootDocumentId,
                relativeDirectory = "",
                existing = existing,
                onFile = { relativePath, fileName, documentUri, size, lastModified, parentDocumentId ->
                    currentCoroutineContext().ensureActive()
                    scannedFiles++
                    if (VideoFormats.isVideoFile(fileName)) {
                        videoFiles++
                        val parsed = EpisodeFileNameParser.parse(fileName, relativePath.substringBeforeLast('/', ""))
                        val old = existing[relativePath]
                        pending += LocalMediaEntity(
                            id = old?.id ?: 0,
                            sourceId = source.id,
                            documentUri = documentUri.toString(),
                            relativePath = relativePath,
                            fileName = fileName,
                            size = size,
                            lastModified = lastModified,
                            parsedTitle = parsed.parsedTitle,
                            seasonNumber = parsed.seasonNumber,
                            episodeNumber = parsed.episodeNumber,
                            episodeEndNumber = parsed.episodeEndNumber,
                            matchState = when {
                                !parsed.isMainFeature -> LocalMediaMatchState.IGNORED
                                old?.isUserOverride == true -> old.matchState
                                else -> LocalMediaMatchState.UNMATCHED
                            },
                            matchedAnimeId = old?.matchedAnimeId?.takeIf { old.isUserOverride },
                            confidence = if (old?.isUserOverride == true) old.confidence else parsed.confidence,
                            isUserOverride = old?.isUserOverride == true,
                            isMissing = false,
                            episodeType = when {
                                !parsed.isMainFeature -> LocalEpisodeType.EXTRA
                                parsed.episodeNumber != null -> LocalEpisodeType.MAIN
                                else -> LocalEpisodeType.UNKNOWN
                            },
                            parentDocumentId = parentDocumentId,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    if (pending.size >= BATCH_SIZE) {
                        dao.upsertMedia(pending.toList())
                        pending.clear()
                    }
                    onProgress(ScanProgress(scannedFiles, videoFiles, relativePath))
                }
            )
            if (pending.isNotEmpty()) dao.upsertMedia(pending)

            val finishedAt = System.currentTimeMillis()
            val missingCount = dao.getMedia(source.id).count { it.isMissing }
            val updatedSource = source.copy(
                lastScanAt = finishedAt,
                videoCount = videoFiles,
                scanState = LocalMediaScanState.COMPLETED,
                scanError = null,
                permissionState = LocalMediaPermissionState.GRANTED,
                updatedAt = finishedAt
            )
            dao.updateSource(updatedSource)
            ScanResult(scannedFiles, videoFiles, missingCount)
        } catch (e: Exception) {
            val failedAt = System.currentTimeMillis()
            dao.updateSource(
                source.copy(
                    scanState = LocalMediaScanState.FAILED,
                    scanError = e.message ?: "扫描失败",
                    permissionState = if (e is SecurityException) {
                        LocalMediaPermissionState.REVOKED
                    } else {
                        LocalMediaPermissionState.GRANTED
                    },
                    updatedAt = failedAt
                )
            )
            throw e
        }
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

    suspend fun updateMatch(
        mediaId: Long,
        animeId: Int?,
        confidence: Float,
        confirmed: Boolean,
        userOverride: Boolean
    ) {
        dao.updateMatch(
            mediaId = mediaId,
            animeId = animeId,
            confidence = confidence,
            matchState = if (confirmed) LocalMediaMatchState.CONFIRMED else LocalMediaMatchState.AUTO_MATCHED,
            isUserOverride = userOverride,
            updatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun walkDirectory(
        treeUri: Uri,
        documentId: String,
        relativeDirectory: String,
        existing: Map<String, LocalMediaEntity>,
        onFile: suspend (String, String, Uri, Long, Long, String) -> Unit
    ) {
        currentCoroutineContext().ensureActive()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val childId = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn).orEmpty()
                if (name.isBlank()) continue
                val mime = cursor.getString(mimeColumn).orEmpty()
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val childRelative = if (relativeDirectory.isBlank()) {
                        name
                    } else {
                        "$relativeDirectory/$name"
                    }
                    walkDirectory(treeUri, childId, childRelative, existing, onFile)
                } else {
                    val relativePath = if (relativeDirectory.isBlank()) {
                        name
                    } else {
                        "$relativeDirectory/$name"
                    }
                    val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        cursor.getLong(sizeColumn)
                    } else {
                        0L
                    }
                    val modified = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                        cursor.getLong(modifiedColumn)
                    } else {
                        0L
                    }
                    onFile(relativePath, name, childUri, size, modified, documentId)
                }
            }
        }
    }

    private fun hasPersistedReadPermission(treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }

    private companion object {
        const val BATCH_SIZE = 100
    }
}
