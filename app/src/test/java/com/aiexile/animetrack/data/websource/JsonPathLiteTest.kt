package com.aiexile.animetrack.data.websource

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPathLiteTest {

    private val document = JsonParser.parseString(
        """
        {
          "info": {
            "rows": [
              {"vod_id": 1, "vod_name": "A"},
              {"vod_id": 2, "vod_name": "B"}
            ],
            "first": {"title": "T"},
            "map": {"x": 1, "y": 2}
          },
          "list": [{"id": 7}],
          "plain": "value"
        }
        """.trimIndent()
    )

    @Test
    fun `reads nested fields and wildcard arrays`() {
        val rows = JsonPathLite.read(document, "$.info.rows[*]")

        assertEquals(2, rows.size)
        assertEquals(1, JsonPathLite.readFirst(rows[0], "$.vod_id")?.asInt)
        assertEquals("B", JsonPathLite.readFirst(rows[1], "$.vod_name")?.asString)
    }

    @Test
    fun `root and index selectors`() {
        assertEquals(document, JsonPathLite.readFirst(document, "$"))
        assertEquals(
            "value",
            JsonPathLite.readFirst(document, "$['plain']")?.asString
        )
        assertEquals(
            7,
            JsonPathLite.readFirst(document, "$.list[0].id")?.asInt
        )
    }

    @Test
    fun `wildcard on object yields values`() {
        val values = JsonPathLite.read(document, "$.info.map[*]").map { it.asInt }

        assertEquals(listOf(1, 2), values)
    }

    @Test
    fun `missing fields return empty and do not throw`() {
        assertTrue(JsonPathLite.read(document, "$.nope[*]").isEmpty())
        assertTrue(JsonPathLite.read(document, "$.info.rows[9]").isEmpty())
        assertTrue(JsonPathLite.read(document, "$.plain.child").isEmpty())
    }

    @Test
    fun `rejects expressions outside the supported subset`() {
        listOf("info", "$.info..x", "$.info[?(@.a)]", "$.info[", "$.").forEach { path ->
            assertTrue(path, runCatching { JsonPathLite.validate(path) }.isFailure)
        }
        JsonPathLite.validate("$.info.rows[*].vod-name")
    }
}
