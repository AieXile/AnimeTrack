package com.aiexile.animetrack.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.aiexile.animetrack.domain.playback.SubtitleSpec

/** 同目录外挂字幕（仅本地 SAF 目录，不访问网络）。 */
data class LocalSubtitle(
    val uri: String,
    val label: String,
    val language: String?,
    val mimeType: String
)

object LocalSubtitleLocator {
    private data class SubtitleFile(val name: String, val uri: Uri, val mimeType: String)

    /**
     * 在视频所在目录查找外挂字幕：
     * 文件名必须与视频同名（允许语言/版本后缀），避免把同目录其他集字幕挂上。
     */
    fun find(context: Context, treeUri: Uri, video: LocalMediaEntity): List<LocalSubtitle> {
        val parentId = video.parentDocumentId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val permission = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!permission) return emptyList()

        val videoBase = video.fileName.substringBeforeLast('.').trim()
        if (videoBase.isBlank()) return emptyList()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        val files = runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameColumn).orEmpty()
                        val mime = SubtitleSpec.mimeTypeForFileName(name) ?: continue
                        val id = cursor.getString(idColumn) ?: continue
                        add(
                            SubtitleFile(
                                name = name,
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                                mimeType = mime
                            )
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()

        return files
            .mapNotNull { file ->
                val score = SubtitleSpec.matchScore(file.name, videoBase)
                if (score == SubtitleSpec.MATCH_NONE) null else score to file
            }
            .sortedBy { it.first }
            .map { (_, file) -> toSubtitle(file, videoBase) }
    }

    private fun toSubtitle(file: SubtitleFile, videoBase: String): LocalSubtitle {
        val subtitleBase = file.name.substringBeforeLast('.')
        val suffix = subtitleBase
            .removePrefix(subtitleBase.take(videoBase.length))
            .trim('[', ']', '.', '_', '-', ' ')
        return LocalSubtitle(
            uri = file.uri.toString(),
            label = suffix.ifBlank { "外挂字幕" },
            language = SubtitleSpec.guessLanguage(suffix),
            mimeType = file.mimeType
        )
    }
}