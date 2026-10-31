package com.aiexile.animetrack.data.font

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 字体名：从 TTF/OTF/TTC 的 `name` 表读取的家族名与子家族名。
 */
data class FontNameTable(val family: String, val subfamily: String)

/**
 * 轻量字体名称读取器。
 *
 * Android 只提供 `SystemFonts#getAvailableFonts`（返回 Font 对象），没有公开的
 * 「字体家族名」API；这里直接解析 OpenType `name` 表：
 * - 只按需读取文件中的 `name` 表区间，不解码整个字体（CJK TTC 常有 30MB+）；
 * - 支持 TTC：按 [ttcIndex] 解析对应 face 的 offset；
 * - 优先取 Typographic Family（nameID 16/17），回退 Family/Subfamily（1/2）；
 * - 优先 Windows 英文（platform 3 / language 0x409），再回退 Unicode、Mac。
 *
 * 解析失败（文件格式异常/越界）返回 null，由调用方回退到文件名。
 */
object FontNameReader {

    private const val NAME_ID_FAMILY = 1
    private const val NAME_ID_SUBFAMILY = 2
    private const val NAME_ID_TYPO_FAMILY = 16
    private const val NAME_ID_TYPO_SUBFAMILY = 17

    /** 名称表通常很小；设上限防止损坏文件导致超大分配 */
    private const val MAX_NAME_TABLE_SIZE = 512 * 1024

    /** CJK 字体的 cmap 可能较大（format 12），上限 16MB */
    private const val MAX_CMAP_TABLE_SIZE = 16 * 1024 * 1024

    /** TTC 最多解析的 face 数上限，防止损坏文件给出离谱值 */
    private const val MAX_TTC_FACES = 64

    /**
     * 读取指定字体面的名称。
     *
     * @param file 字体文件
     * @param ttcIndex TTC 内的 face 索引；非 TTC 字体传 0
     */
    fun read(file: File, ttcIndex: Int = 0): FontNameTable? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val faceOffset = resolveFaceOffset(raf, ttcIndex) ?: return@use null
            val nameTable = findTable(raf, faceOffset, "name", MAX_NAME_TABLE_SIZE) ?: return@use null
            parseNameTable(nameTable)
        }
    }.getOrNull()

    /**
     * 字体面是否包含 [codePoint] 的字形。
     *
     * 直接解析 `cmap` 表判断覆盖范围，而不是用 `Paint.hasGlyph`：带系统 fallback 的
     * Typeface 在缺字时会走 fallback，导致所有字体都被误判为支持中文。
     */
    fun supports(file: File, ttcIndex: Int, codePoint: Int): Boolean = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val faceOffset = resolveFaceOffset(raf, ttcIndex) ?: return@use false
            val cmap = findTable(raf, faceOffset, "cmap", MAX_CMAP_TABLE_SIZE) ?: return@use false
            parseCmap(cmap, codePoint)
        }
    }.getOrDefault(false)

    /** 字体文件包含的 face 数：TTC 读取头部计数，其余为 1 */
    fun faceCount(file: File): Int = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            if (readTag(raf, 0L) == TTC_TAG) {
                raf.readUInt32(8L).coerceIn(1L, MAX_TTC_FACES.toLong()).toInt()
            } else {
                1
            }
        }
    }.getOrDefault(1)

    private const val TTC_TAG = "ttcf"

    /**
     * TTC 中 face 的 sfnt 头偏移；非 TTC 且 ttcIndex == 0 时返回 0（文件本身即 sfnt）。
     */
    private fun resolveFaceOffset(raf: RandomAccessFile, ttcIndex: Int): Long? {
        if (readTag(raf, 0L) != TTC_TAG) {
            return if (ttcIndex == 0) 0L else null
        }
        val numFonts = raf.readUInt32(8L)
        if (ttcIndex < 0 || ttcIndex >= numFonts || ttcIndex >= MAX_TTC_FACES) return null
        return raf.readUInt32(12L + 4L * ttcIndex).takeIf { it > 0L }
    }

    /** 找到指定 face 的某个 OpenType 表并读取其字节 */
    private fun findTable(
        raf: RandomAccessFile,
        faceOffset: Long,
        tag: String,
        maxSize: Int
    ): ByteArray? {
        val numTables = raf.readU16(faceOffset + 4L)
        val tableRecordStart = faceOffset + 12L
        repeat(numTables) { index ->
            val record = tableRecordStart + 16L * index
            if (readTag(raf, record) != tag) return@repeat
            val tableOffset = raf.readUInt32(record + 8L)
            val tableLength = raf.readUInt32(record + 12L)
            if (tableOffset <= 0L || tableLength <= 0L || tableLength > maxSize.toLong()) return null
            if (tableOffset + tableLength > raf.length()) return null
            val bytes = ByteArray(tableLength.toInt())
            raf.seek(tableOffset)
            raf.readFully(bytes)
            return bytes
        }
        return null
    }

    /**
     * 解析 name 表字节。内部可见，供单元测试直接构造字节验证。
     */
    internal fun parseNameTable(bytes: ByteArray): FontNameTable? {
        if (bytes.size < 6) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val count = buffer.getU16(2)
        val stringOffset = buffer.getU16(4)
        val recordsEnd = 6 + count * 12
        if (count <= 0 || recordsEnd > bytes.size) return null

        var typoFamily: NameCandidate? = null
        var legacyFamily: NameCandidate? = null
        var typoSubfamily: NameCandidate? = null
        var legacySubfamily: NameCandidate? = null
        repeat(count) { index ->
            val record = 6 + index * 12
            if (record + 12 > bytes.size) return@repeat
            val platformId = buffer.getU16(record)
            val languageId = buffer.getU16(record + 4)
            val nameId = buffer.getU16(record + 6)
            if (nameId != NAME_ID_TYPO_FAMILY && nameId != NAME_ID_FAMILY &&
                nameId != NAME_ID_TYPO_SUBFAMILY && nameId != NAME_ID_SUBFAMILY
            ) {
                return@repeat
            }
            val length = buffer.getU16(record + 8)
            val offset = buffer.getU16(record + 10)
            val start = stringOffset + offset
            if (start < 0 || length <= 0 || start + length > bytes.size) return@repeat
            val text = decode(bytes, start, length, platformId)?.trim()
            if (text.isNullOrBlank()) return@repeat
            val candidate = NameCandidate(text, score(platformId, languageId))
            when (nameId) {
                NAME_ID_TYPO_FAMILY -> if (candidate.isBetterThan(typoFamily)) typoFamily = candidate
                NAME_ID_FAMILY -> if (candidate.isBetterThan(legacyFamily)) legacyFamily = candidate
                NAME_ID_TYPO_SUBFAMILY -> if (candidate.isBetterThan(typoSubfamily)) typoSubfamily = candidate
                NAME_ID_SUBFAMILY -> if (candidate.isBetterThan(legacySubfamily)) legacySubfamily = candidate
            }
        }
        // Typographic Family（16）优先；缺失时回退 Legacy Family（1）
        val familyName = (typoFamily ?: legacyFamily)?.text ?: return null
        val subfamilyName = (typoSubfamily ?: legacySubfamily)?.text.orEmpty()
        return FontNameTable(familyName, subfamilyName)
    }

    private data class NameCandidate(val text: String, val score: Int) {
        fun isBetterThan(other: NameCandidate?): Boolean = other == null || score < other.score
    }

    /** 越小越优先：Windows 英文 > Unicode > 其他 Windows > Mac */
    private fun score(platformId: Int, languageId: Int): Int = when {
        platformId == 3 && languageId == 0x0409 -> 0
        platformId == 0 -> 1
        platformId == 3 -> 2
        platformId == 1 && languageId == 0 -> 3
        else -> 4
    }

    private fun decode(bytes: ByteArray, start: Int, length: Int, platformId: Int): String? = runCatching {
        when (platformId) {
            3, 0 -> String(bytes, start, length, Charsets.UTF_16BE)
            else -> String(bytes, start, length, Charsets.ISO_8859_1)
        }
    }.getOrNull()

    /**
     * 解析 cmap 表，判断是否有 Unicode 子表覆盖 [codePoint]。
     * 支持常见的 format 0 / 4 / 6 / 12。
     */
    internal fun parseCmap(bytes: ByteArray, codePoint: Int): Boolean {
        if (codePoint !in 0..0x10FFFF) return false
        if (bytes.size < 4) return false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val numTables = buffer.getU16(2)
        for (index in 0 until numTables) {
            val record = 4 + index * 8
            if (record + 8 > bytes.size) break
            val platformId = buffer.getU16(record)
            val encodingId = buffer.getU16(record + 2)
            // Unicode 平台（0）或 Windows 的 Unicode BMP/满平面编码（3/1、3/10）
            val isUnicode = platformId == 0 ||
                (platformId == 3 && (encodingId == 1 || encodingId == 10))
            if (!isUnicode) continue
            val offset = buffer.getU32(record + 4).toInt()
            if (offset <= 0 || offset + 2 > bytes.size) continue
            if (subtableCovers(buffer, offset, codePoint)) return true
        }
        return false
    }

    private fun subtableCovers(buffer: ByteBuffer, offset: Int, codePoint: Int): Boolean =
        runCatching {
            when (buffer.getU16(offset)) {
                0 -> format0Covers(buffer, offset, codePoint)
                4 -> format4Covers(buffer, offset, codePoint)
                6 -> format6Covers(buffer, offset, codePoint)
                12 -> format12Covers(buffer, offset, codePoint)
                else -> false
            }
        }.getOrDefault(false)

    private fun format0Covers(buffer: ByteBuffer, offset: Int, codePoint: Int): Boolean {
        if (codePoint > 0xFF) return false
        val index = offset + 6 + codePoint
        if (index >= buffer.capacity()) return false
        return (buffer.get(index).toInt() and 0xFF) != 0
    }

    private fun format4Covers(buffer: ByteBuffer, offset: Int, codePoint: Int): Boolean {
        if (codePoint > 0xFFFF) return false
        val segCountX2 = buffer.getU16(offset + 6)
        if (segCountX2 == 0) return false
        val endCodePos = offset + 14
        val startCodePos = endCodePos + segCountX2 + 2
        val idDeltaPos = startCodePos + segCountX2
        val idRangeOffsetPos = idDeltaPos + segCountX2
        if (idRangeOffsetPos + segCountX2 > buffer.capacity()) return false

        for (index in 0 until segCountX2 / 2) {
            val endCode = buffer.getU16(endCodePos + index * 2)
            if (codePoint > endCode) continue
            val startCode = buffer.getU16(startCodePos + index * 2)
            if (codePoint < startCode) return false

            val delta = buffer.getU16(idDeltaPos + index * 2)
            val rangeOffset = buffer.getU16(idRangeOffsetPos + index * 2)
            if (rangeOffset == 0) {
                return ((codePoint + delta) and 0xFFFF) != 0
            }
            val glyphIndexPos = idRangeOffsetPos + index * 2 + rangeOffset +
                (codePoint - startCode) * 2
            if (glyphIndexPos + 2 > buffer.capacity()) return false
            val glyph = buffer.getU16(glyphIndexPos)
            if (glyph == 0) return false
            return ((glyph + delta) and 0xFFFF) != 0
        }
        return false
    }

    private fun format6Covers(buffer: ByteBuffer, offset: Int, codePoint: Int): Boolean {
        val firstCode = buffer.getU16(offset + 6)
        val entryCount = buffer.getU16(offset + 8)
        if (codePoint < firstCode || codePoint >= firstCode + entryCount) return false
        val index = offset + 10 + (codePoint - firstCode) * 2
        if (index + 2 > buffer.capacity()) return false
        return buffer.getU16(index) != 0
    }

    private fun format12Covers(buffer: ByteBuffer, offset: Int, codePoint: Int): Boolean {
        val numGroups = buffer.getU32(offset + 12)
        if (numGroups <= 0L || numGroups > MAX_CMAP_GROUPS) return false
        val groupsPos = offset + 16
        val end = groupsPos + numGroups * 12
        if (end > buffer.capacity()) return false
        for (index in 0 until numGroups.toInt()) {
            val group = groupsPos + index * 12
            val startChar = buffer.getU32(group)
            val endChar = buffer.getU32(group + 4)
            val startGlyph = buffer.getU32(group + 8)
            if (codePoint.toLong() in startChar..endChar) {
                return startGlyph + (codePoint.toLong() - startChar) != 0L
            }
        }
        return false
    }

    private const val MAX_CMAP_GROUPS = 100_000L

    // ---- RandomAccessFile 读取辅助 ----

    private fun RandomAccessFile.readU16(offset: Long): Int {
        seek(offset)
        val high = read()
        val low = read()
        if (high < 0 || low < 0) throw java.io.EOFException()
        return (high shl 8) or low
    }

    private fun RandomAccessFile.readUInt32(offset: Long): Long {
        seek(offset)
        return readInt().toLong() and 0xFFFFFFFFL
    }

    private fun readTag(raf: RandomAccessFile, offset: Long): String {
        raf.seek(offset)
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return String(bytes, Charsets.ISO_8859_1)
    }
}

/** 无符号 16 位读取 */
private fun ByteBuffer.getU16(index: Int): Int = getShort(index).toInt() and 0xFFFF

/** 无符号 32 位读取 */
private fun ByteBuffer.getU32(index: Int): Long = getInt(index).toLong() and 0xFFFFFFFFL
