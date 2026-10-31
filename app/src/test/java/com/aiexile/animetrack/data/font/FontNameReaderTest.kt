package com.aiexile.animetrack.data.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FontNameReaderTest {

    @Test
    fun `prefers typographic family and windows english record`() {
        val bytes = buildNameTable(
            listOf(
                Record(platformId = 3, languageId = 0x0409, nameId = 1, text = "Legacy Family"),
                Record(platformId = 3, languageId = 0x0409, nameId = 2, text = "Regular"),
                Record(platformId = 3, languageId = 0x0409, nameId = 16, text = "Typographic Family"),
                Record(platformId = 3, languageId = 0x0409, nameId = 17, text = "Display")
            )
        )

        val result = FontNameReader.parseNameTable(bytes)

        assertEquals("Typographic Family", result?.family)
        assertEquals("Display", result?.subfamily)
    }

    @Test
    fun `falls back to name id 1 when typographic family missing`() {
        val bytes = buildNameTable(
            listOf(
                Record(platformId = 1, languageId = 0, nameId = 1, text = "Mac Family"),
                Record(platformId = 3, languageId = 0x0409, nameId = 1, text = "Windows Family"),
                Record(platformId = 3, languageId = 0x0409, nameId = 2, text = "Italic")
            )
        )

        val result = FontNameReader.parseNameTable(bytes)

        // Windows 英文记录优先于 Mac 记录
        assertEquals("Windows Family", result?.family)
        assertEquals("Italic", result?.subfamily)
    }

    @Test
    fun `returns null when table is truncated`() {
        assertNull(FontNameReader.parseNameTable(byteArrayOf(0, 0, 1)))
        assertNull(FontNameReader.parseNameTable(ByteArray(6)))
    }

    @Test
    fun `cmap format 4 detects covered code point`() {
        val cmap = buildCmapWithFormat4(startCode = 0x0041, endCode = 0x4E2D, idDelta = 1 - 0x0041)

        assertTrue(FontNameReader.parseCmap(cmap, 0x0041))
        assertTrue(FontNameReader.parseCmap(cmap, 0x4E2D))
        assertFalse(FontNameReader.parseCmap(cmap, 0x4E2E))
        assertFalse(FontNameReader.parseCmap(cmap, 0x0030))
    }

    @Test
    fun `cmap format 12 detects covered code point`() {
        val cmap = buildCmapWithFormat12(startCode = 0x4E00, endCode = 0x9FFF, startGlyph = 5)

        assertTrue(FontNameReader.parseCmap(cmap, 0x4E2D))
        assertFalse(FontNameReader.parseCmap(cmap, 0x0041))
    }

    private data class Record(
        val platformId: Int,
        val languageId: Int,
        val nameId: Int,
        val text: String
    )

    /** 构造一份 name 表（format 0，字符串按 UTF-16BE 存储） */
    private fun buildNameTable(records: List<Record>): ByteArray {
        val encoded = records.map { it.text.toByteArray(Charsets.UTF_16BE) }
        val stringOffset = 6 + records.size * 12
        val totalSize = stringOffset + encoded.sumOf { it.size }
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(0.toShort())
        buffer.putShort(records.size.toShort())
        buffer.putShort(stringOffset.toShort())

        var stringCursor = 0
        records.forEachIndexed { index, record ->
            buffer.putShort(record.platformId.toShort())
            buffer.putShort(1.toShort()) // encodingId
            buffer.putShort(record.languageId.toShort())
            buffer.putShort(record.nameId.toShort())
            buffer.putShort(encoded[index].size.toShort())
            buffer.putShort(stringCursor.toShort())
            stringCursor += encoded[index].size
        }
        encoded.forEach { buffer.put(it) }
        return buffer.array()
    }

    /** 构造 cmap：12 字节头 + 1 个 format 4 子表（单段） */
    private fun buildCmapWithFormat4(startCode: Int, endCode: Int, idDelta: Int): ByteArray {
        val subtableSize = 14 + 10
        val buffer = ByteBuffer.allocate(12 + subtableSize).order(ByteOrder.BIG_ENDIAN)
        // cmap header
        buffer.putShort(0.toShort()) // version
        buffer.putShort(1.toShort()) // numTables
        buffer.putShort(3.toShort()) // platformId (Windows)
        buffer.putShort(1.toShort()) // encodingId (Unicode BMP)
        buffer.putInt(12) // subtable offset
        // format 4 header
        buffer.putShort(4.toShort())
        buffer.putShort(subtableSize.toShort())
        buffer.putShort(0.toShort()) // language
        buffer.putShort(2.toShort()) // segCountX2
        buffer.putShort(2.toShort()) // searchRange
        buffer.putShort(0.toShort()) // entrySelector
        buffer.putShort(0.toShort()) // rangeShift
        buffer.putShort(endCode.toShort())
        buffer.putShort(0.toShort()) // reservedPad
        buffer.putShort(startCode.toShort())
        buffer.putShort(idDelta.toShort())
        buffer.putShort(0.toShort()) // idRangeOffset
        return buffer.array()
    }

    /** 构造 cmap：12 字节头 + 1 个 format 12 子表（单组） */
    private fun buildCmapWithFormat12(startCode: Int, endCode: Int, startGlyph: Int): ByteArray {
        val subtableSize = 16 + 12
        val buffer = ByteBuffer.allocate(12 + subtableSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(0.toShort()) // version
        buffer.putShort(1.toShort()) // numTables
        buffer.putShort(3.toShort()) // platformId (Windows)
        buffer.putShort(10.toShort()) // encodingId (Unicode full repertoire)
        buffer.putInt(12) // subtable offset
        // format 12 header
        buffer.putShort(12.toShort())
        buffer.putShort(0.toShort()) // reserved
        buffer.putInt(subtableSize)
        buffer.putInt(0) // language
        buffer.putInt(1) // numGroups
        buffer.putInt(startCode)
        buffer.putInt(endCode)
        buffer.putInt(startGlyph)
        return buffer.array()
    }
}
