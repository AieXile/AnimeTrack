package com.aiexile.animetrack.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.aiexile.animetrack.domain.playback.SubtitleSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** 同目录外挂字幕（仅本地 SAF 目录，不访问网络）。 */
data class LocalSubtitle(
    val uri: String,
    val label: String,
    val language: String?,
    val mimeType: String
)

internal object LocalSubtitleLocator {
    data class SubtitleFile(val name: String, val uri: String, val mimeType: String)

    /**
     * 在一次播放准备中，同一来源的同一目录只查一次。
     * 超时或无权限只跳过该目录，不影响其他目录的字幕和视频起播。
     */
    suspend fun findAll(
        videos: List<LocalMediaEntity>,
        directoryTimeoutMs: Long,
        readDirectory: suspend (sourceId: Long, parentId: String) -> List<SubtitleFile>
    ): Map<Long, List<LocalSubtitle>> = buildMap {
        val context = currentCoroutineContext()
        val directories = videos.filter { !it.parentDocumentId.isNullOrBlank() }
            .groupBy { it.sourceId to checkNotNull(it.parentDocumentId) }
        for ((directory, entries) in directories) {
            val (sourceId, parentId) = directory
            val files = withTimeoutOrNull(directoryTimeoutMs) { readDirectory(sourceId, parentId) }.orEmpty()
            if (files.isEmpty()) continue
            for (video in entries) {
                context.ensureActive()
                val subtitles = matchVideo(files, video.fileName.substringBeforeLast('.').trim())
                if (subtitles.isNotEmpty()) put(video.id, subtitles)
            }
        }
    }

    suspend fun readDirectory(context: Context, treeUri: Uri, parentId: String): List<SubtitleFile> {
        val scanContext = currentCoroutineContext()
        return try {
            val resolver = context.contentResolver
            if (resolver.persistedUriPermissions.none { it.uri == treeUri && it.isReadPermission }) {
                return emptyList()
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.queryLocalDocuments(childrenUri, PROJECTION)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        scanContext.ensureActive()
                        val name = cursor.getString(nameColumn).orEmpty()
                        val mime = SubtitleSpec.mimeTypeForFileName(name) ?: continue
                        val id = cursor.getString(idColumn) ?: continue
                        add(SubtitleFile(name, DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(), mime))
                    }
                }
            }.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            scanContext.ensureActive()
            emptyList()
        }
    }

    private fun matchVideo(files: List<SubtitleFile>, videoBase: String): List<LocalSubtitle> = files
        .mapNotNull { file ->
            val score = SubtitleSpec.matchScore(file.name, videoBase)
            if (score == SubtitleSpec.MATCH_NONE) null else score to file
        }
        .sortedBy { it.first }
        .map { (_, file) -> toSubtitle(file, videoBase) }

    private fun toSubtitle(file: SubtitleFile, videoBase: String): LocalSubtitle {
        val subtitleBase = file.name.substringBeforeLast('.')
        val suffix = subtitleBase
            .removePrefix(subtitleBase.take(videoBase.length))
            .trim('[', ']', '.', '_', '-', ' ')
        return LocalSubtitle(
            uri = file.uri,
            label = suffix.ifBlank { "外挂字幕" },
            language = SubtitleSpec.guessLanguage(suffix),
            mimeType = file.mimeType
        )
    }

    private val PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
}