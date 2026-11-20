package com.aiexile.animetrack.data.media

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal data class LocalMediaScanFile(
    val relativePath: String,
    val fileName: String,
    val documentUri: String,
    val size: Long,
    val lastModified: Long,
    val parentDocumentId: String
) {
    fun hasUnchangedContent(old: LocalMediaEntity): Boolean =
        relativePath == old.relativePath && fileName == old.fileName &&
            size > 0 && lastModified > 0 && size == old.size && lastModified == old.lastModified &&
            old.episodeType != null // 迁移来的记录可能尚未分类。
}

internal interface LocalMediaTreeReader {
    fun hasPersistedReadPermission(treeUri: Uri): Boolean

    /** 从系统选择器返回的授权持久化，测试可替换访问边界。 */
    fun takePersistedReadPermission(treeUri: Uri)

    /** 只有目录完整遍历后才正常返回；不完整或不可读的结果必须抛出。 */
    suspend fun readFiles(treeUri: Uri, onFile: suspend (LocalMediaScanFile) -> Unit)
}

internal class SafLocalMediaTreeReader(private val resolver: ContentResolver) : LocalMediaTreeReader {
    private data class Directory(val documentId: String, val relativePath: String)

    override fun hasPersistedReadPermission(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    override fun takePersistedReadPermission(treeUri: Uri) {
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override suspend fun readFiles(treeUri: Uri, onFile: suspend (LocalMediaScanFile) -> Unit) {
        val pending = ArrayDeque<Directory>()
        pending.addLast(Directory(DocumentsContract.getTreeDocumentId(treeUri), ""))
        val visited = mutableSetOf<String>()
        val scanContext = currentCoroutineContext()
        while (pending.isNotEmpty()) {
            scanContext.ensureActive()
            val directory = pending.removeLast()
            if (!visited.add(directory.documentId)) throw IOException("文件夹目录结构异常")
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directory.documentId)
            queryChildren(childrenUri).use { cursor ->
                cursor.requireComplete()
                val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndex(Document.COLUMN_SIZE)
                val modifiedColumn = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    scanContext.ensureActive()
                    val id = cursor.getString(idColumn)?.takeIf { it.isNotBlank() }
                        ?: throw IOException("目录返回了缺少标识的条目")
                    val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() && '/' !in it }
                        ?: throw IOException("目录返回了无效的文件名")
                    val mime = cursor.getString(mimeColumn)?.takeIf { it.isNotBlank() }
                        ?: throw IOException("目录返回了缺少类型的条目")
                    val path = if (directory.relativePath.isEmpty()) name else "${directory.relativePath}/$name"
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending.addLast(Directory(id, path))
                    } else {
                        onFile(
                            LocalMediaScanFile(
                                relativePath = path,
                                fileName = name,
                                documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                                size = cursor.nonNegativeLong(sizeColumn),
                                lastModified = cursor.nonNegativeLong(modifiedColumn),
                                parentDocumentId = directory.documentId
                            )
                        )
                    }
                }
                cursor.requireComplete()
            }
        }
    }

    private suspend fun queryChildren(uri: Uri): Cursor = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        val cursor = try {
            resolver.query(uri, PROJECTION, null, null, null, signal)
                ?: throw IOException("无法读取文件夹内容")
        } catch (e: Exception) {
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }
        continuation.resume(cursor) { _, result, _ -> result.close() }
    }

    private fun Cursor.requireComplete() {
        val status = extras
        if (status.getBoolean(DocumentsContract.EXTRA_LOADING) ||
            !status.getString(DocumentsContract.EXTRA_ERROR).isNullOrBlank()
        ) {
            throw IOException("文件夹未返回完整内容，请稍后重试")
        }
    }

    private fun Cursor.nonNegativeLong(column: Int): Long =
        if (column < 0 || isNull(column)) 0L else getLong(column).coerceAtLeast(0L)

    private companion object {
        val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )
    }
}
