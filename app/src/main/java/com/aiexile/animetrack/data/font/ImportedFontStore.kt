package com.aiexile.animetrack.data.font

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aiexile.animetrack.data.log.AppLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 用户从文件导入的字体：统一保存在应用私有目录 `files/fonts`，与系统字体一起
 * 展示在「本机字体」选择页。
 *
 * 导入字体以**文件名**作为列表名称，这样同一家族的不同文件、以及同内容改名导入时
 * 都能直观体现用户当前使用的名字；系统字体仍按 TTF 家族名展示。
 */
object ImportedFontStore {

    private const val TAG = "ImportedFont"
    private const val DIR_NAME = "fonts"
    private val SUPPORTED_EXTENSIONS = listOf("ttf", "otf", "ttc")

    /**
     * 列出已导入的字体：一个文件对应一个列表项。
     *
     * 名称解析失败的文件也按文件名展示：旧版本导入时不做校验，升级后不能
     * 让这些文件凭空消失；Typeface 能否加载由实际字体内容决定。
     */
    fun list(context: Context): List<DeviceFontFamily> {
        val dir = fontsDir(context)
        val files = dir.listFiles()?.filter { it.isFile && it.canRead() && isFontFile(it.name) }
            ?: return emptyList()
        return files
            .map(::familyOfFile)
            .sortedBy { it.name.lowercase() }
    }

    /**
     * 复制所选文件到私有目录并解析家族名。
     *
     * - 内容相同（SHA-256）的字体不重复保存；若用户导入了新文件名，直接把旧文件改名；
     * - 同名但内容不同视为覆盖更新；
     * - 名称表解析失败时用文件名兜底，与旧版导入行为一致。
     */
    suspend fun import(context: Context, uri: Uri): DeviceFontFamily? = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val dir = fontsDir(context).apply { if (!exists()) mkdirs() }
            val preferredFile = File(dir, resolveImportFileName(context, uri))
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                AppLogManager.e(TAG, "导入失败：无法打开文件流 uri=$uri")
                return@withContext null
            }

            val temp = File(dir, ".import_${System.nanoTime()}.tmp")
            tempFile = temp
            input.use { stream ->
                temp.outputStream().use { output -> stream.copyTo(output) }
            }
            if (temp.length() <= 0L) {
                AppLogManager.e(TAG, "导入失败：文件为空 uri=$uri")
                return@withContext null
            }

            val hash = sha256(temp)
            if (hash == null) {
                AppLogManager.e(TAG, "导入失败：无法计算文件哈希 uri=$uri")
                return@withContext null
            }

            val sameContentFile = findSameContent(dir, temp.length(), hash)
            val targetFile = if (sameContentFile != null) {
                // 已有相同内容：复用，不再复制；文件名不同时直接改成新导入的名称
                val target = resolveTargetForExisting(dir, preferredFile, sameContentFile, hash)
                temp.delete()
                tempFile = null
                if (sameContentFile != target) {
                    if (target.exists()) {
                        // 目标名本身也是同一内容的副本：保留新名称，删除多余副本
                        sameContentFile.delete()
                    } else if (!sameContentFile.renameTo(target)) {
                        AppLogManager.e(TAG, "导入失败：字体改名失败 ${sameContentFile.absolutePath} -> ${target.name}")
                        return@withContext null
                    }
                }
                target
            } else {
                // 同名覆盖旧内容；先删旧文件再移动临时文件
                if (preferredFile.exists()) preferredFile.delete()
                if (!temp.renameTo(preferredFile)) {
                    AppLogManager.e(TAG, "导入失败：临时文件重命名失败 ${temp.absolutePath}")
                    return@withContext null
                }
                tempFile = null
                preferredFile
            }

            familyOfFile(targetFile)
        } catch (e: Exception) {
            AppLogManager.e(TAG, "导入字体失败 uri=$uri", e)
            null
        } finally {
            tempFile?.delete()
        }
    }

    /** 删除导入字体对应的私有文件；文件已不存在也视为成功 */
    suspend fun delete(context: Context, family: DeviceFontFamily): Boolean = withContext(Dispatchers.IO) {
        val files = family.faces.map { File(it.filePath) }.distinct()
        files.all { !it.exists() || it.delete() }
    }

    private fun familyOfFile(file: File): DeviceFontFamily {
        val faceCount = runCatching { FontNameReader.faceCount(file) }.getOrDefault(1)
        val faces = mutableListOf<DeviceFontFace>()
        repeat(faceCount) { index ->
            val names = FontNameReader.read(file, index) ?: return@repeat
            faces += DeviceFontFace(
                filePath = file.absolutePath,
                ttcIndex = index,
                weight = weightFromSubfamily(names.subfamily),
                italic = isItalicSubfamily(names.subfamily)
            )
        }
        if (faces.isEmpty()) {
            AppLogManager.d(TAG, "名称表解析失败，按文件名展示: ${file.name}")
            faces += DeviceFontFace(
                filePath = file.absolutePath,
                ttcIndex = 0,
                weight = 400,
                italic = false
            )
        }
        return DeviceFontFamily(
            name = file.nameWithoutExtension,
            faces = faces.distinctBy { it.filePath to it.ttcIndex },
            isImported = true,
            contentTag = contentTag(file)
        )
    }

    /**
     * 相同内容已有文件时决定最终文件名：
     * - 目标名空闲 → 直接用新名字；
     * - 目标名就是同一份内容 → 直接复用；
     * - 目标名被其他内容占用 → 生成 `名称 (1).ttf` 避免覆盖。
     */
    private fun resolveTargetForExisting(
        dir: File,
        preferredFile: File,
        sameContentFile: File,
        hash: String
    ): File {
        if (!preferredFile.exists() || preferredFile == sameContentFile) return preferredFile
        if (sha256(preferredFile) == hash) return preferredFile
        return uniqueFile(dir, preferredFile)
    }

    private fun uniqueFile(dir: File, preferredFile: File): File {
        val base = preferredFile.nameWithoutExtension
        val extension = preferredFile.extension
        var index = 1
        while (true) {
            val candidate = File(
                dir,
                if (extension.isBlank()) "$base ($index)" else "$base ($index).$extension"
            )
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun findSameContent(dir: File, length: Long, hash: String): File? =
        dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.canRead() && isFontFile(it.name) && it.length() == length }
            ?.firstOrNull { sha256(it) == hash }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                append(HEX[(byte.toInt() shr 4) and 0x0F])
                append(HEX[byte.toInt() and 0x0F])
            }
        }
    }.getOrNull()

    private fun contentTag(file: File): String = "${file.length()}-${file.lastModified()}"

    private fun fontsDir(context: Context) = File(context.filesDir, DIR_NAME)

    /**
     * 从 ContentResolver 查原始文件名；去掉路径分隔符并保证扩展名合法。
     * 部分文件管理器不返回扩展名，此时按 .ttf 命名，最终以字体解析结果为准。
     */
    private fun resolveImportFileName(context: Context, uri: Uri): String {
        val displayName = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()?.trim().orEmpty()

        val safeName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .take(120)
            .ifBlank { "imported_font" }
        val extension = safeName.substringAfterLast('.', "").lowercase()
        return if (extension in SUPPORTED_EXTENSIONS) {
            safeName
        } else {
            "$safeName.ttf"
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
