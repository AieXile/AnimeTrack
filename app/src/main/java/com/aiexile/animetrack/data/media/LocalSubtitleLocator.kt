package com.aiexile.animetrack.data.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale

/** 同目录外挂字幕（仅本地 SAF 目录，不访问网络）。 */
data class LocalSubtitle(
    val uri: String,
    val label: String,
    val language: String?,
    val mimeType: String
)

object LocalSubtitleLocator {
    private val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt")

    private data class SubtitleFile(val name: String, val uri: Uri)

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
                        if (!isSubtitle(name)) continue
                        val id = cursor.getString(idColumn) ?: continue
                        add(SubtitleFile(name, DocumentsContract.buildDocumentUriUsingTree(treeUri, id)))
                    }
                }
            }
        }.getOrNull().orEmpty()

        return files
            .filter { belongsTo(it.name, videoBase) }
            .sortedBy { if (it.name.substringBeforeLast('.').equals(videoBase, true)) 0 else 1 }
            .map { toSubtitle(it, videoBase) }
    }

    private fun isSubtitle(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in subtitleExtensions

    private fun belongsTo(subtitleName: String, videoBase: String): Boolean {
        val subtitleBase = subtitleName.substringBeforeLast('.').trim()
        if (subtitleBase.equals(videoBase, ignoreCase = true)) return true
        val separators = charArrayOf('.', '_', '-', ' ', '[')
        return subtitleBase.length > videoBase.length &&
            subtitleBase.startsWith(videoBase, ignoreCase = true) &&
            subtitleBase[videoBase.length] in separators
    }

    private fun toSubtitle(file: SubtitleFile, videoBase: String): LocalSubtitle {
        val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val subtitleBase = file.name.substringBeforeLast('.')
        val suffix = subtitleBase
            .removePrefix(subtitleBase.take(videoBase.length))
            .trim('[', ']', '.', '_', '-', ' ')
        val language = when {
            suffix.containsAny("chs", "zh-cn", "zh_cn", "sc", "简") -> "zh"
            suffix.containsAny("cht", "tc", "繁") -> "zh-Hant"
            suffix.containsAny("jpn", "japanese", "jp", "ja", "日") -> "ja"
            suffix.containsAny("eng", "english", "en", "英") -> "en"
            else -> null
        }
        return LocalSubtitle(
            uri = file.uri.toString(),
            label = suffix.ifBlank { "外挂字幕" },
            language = language,
            mimeType = when (extension) {
                "srt" -> "application/x-subrip"
                "ass", "ssa" -> "text/x-ssa"
                "vtt" -> "text/vtt"
                else -> "application/x-subrip"
            }
        )
    }

    private fun String.containsAny(vararg tokens: String): Boolean =
        tokens.any { this.contains(it, ignoreCase = true) }
}
