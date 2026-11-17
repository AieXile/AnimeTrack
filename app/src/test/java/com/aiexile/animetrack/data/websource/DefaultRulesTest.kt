package com.aiexile.animetrack.data.websource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 内置规则必须全部是可导入、可跑通搜索与选集链路的完整规则。 */
class DefaultRulesTest {

    private val rulesFile = sequenceOf(
        File("src/main/assets/websource/default_rules.json"),
        File("app/src/main/assets/websource/default_rules.json")
    ).firstOrNull { it.exists() } ?: error("default_rules.json not found")

    @Test
    fun `bundled rules decode and are all usable`() {
        val decoded = WebSourceRuleCodec.decode(rulesFile.readText()).getOrThrow()

        assertTrue("expected several bundled rules", decoded.rules.size >= 8)
        assertTrue("some rules were skipped: ${decoded.skipped}", decoded.skipped.isEmpty())
        assertEquals(
            "rule names must be unique",
            decoded.rules.size,
            decoded.rules.map { it.name }.distinct().size
        )
        decoded.rules.forEach { rule ->
            assertTrue("${rule.name} is not usable", rule.isUsable)
        }
    }
}
