package com.aiexile.animetrack.data.websource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.google.gson.JsonParser
import com.aiexile.animetrack.model.WebSourceRule
import org.junit.Test

class WebSourceRuleCodecTest {

    private val kazumiRule = """
        {
            "api": "5",
            "type": "anime",
            "name": "AGE",
            "version": "1.6",
            "useWebview": true,
            "useNativePlayer": true,
            "baseURL": "https://www.agedm.io/",
            "searchURL": "https://www.agedm.io/search?query=@keyword",
            "searchList": "//div[2]/div/section/div/div/div/div",
            "searchName": "//div/div[2]/h5/a",
            "searchResult": "//div/div[2]/h5/a",
            "chapterRoads": "//div[2]/div/section/div/div[2]/div[2]/div[2]/div",
            "chapterResult": "//ul/li/a"
        }
    """.trimIndent()

    @Test
    fun `decodes a single kazumi rule`() {
        val decoded = WebSourceRuleCodec.decode(kazumiRule).getOrThrow()
        assertEquals(1, decoded.rules.size)
        val rule = decoded.rules.single()
        assertEquals("AGE", rule.name)
        assertEquals("https://www.agedm.io/", rule.baseUrl)
        assertEquals("https://www.agedm.io/search?query=@keyword", rule.searchUrl)
        assertTrue(rule.isUsable)
    }

    @Test
    fun `decodes array and skips metadata entries`() {
        val raw = """
            [
              $kazumiRule,
              {"name": "indexEntry", "version": "1.0", "lastUpdate": 123}
            ]
        """.trimIndent()
        val decoded = WebSourceRuleCodec.decode(raw).getOrThrow()
        assertEquals(1, decoded.rules.size)
        assertEquals(listOf("indexEntry"), decoded.skipped)
    }

    @Test
    fun `decodes map keyed rules`() {
        val raw = """{"AGE": $kazumiRule}"""
        val decoded = WebSourceRuleCodec.decode(raw).getOrThrow()
        assertEquals("AGE", decoded.rules.single().name)
    }

    @Test
    fun `rejects rule without keyword placeholder`() {
        val raw = kazumiRule.replace("@keyword", "keyword")
        assertTrue(WebSourceRuleCodec.decode(raw).isFailure)
    }

    @Test
    fun `encode round trips`() {
        val rule = WebSourceRuleCodec.decode(kazumiRule).getOrThrow().rules.single()
        val encoded = WebSourceRuleCodec.encode(listOf(rule))
        val decoded = WebSourceRuleCodec.decode(encoded).getOrThrow()
        assertEquals(rule, decoded.rules.single())
    }

    @Test
    fun `规则配置在保存后保留且默认值统一`() {
        val original = WebSourceRuleCodec.decode(kazumiRule).getOrThrow().rules.single()
        assertEquals(WebSourceRule.DEFAULT_USER_AGENT, original.effectiveUserAgent)
        assertEquals(original.baseUrl, original.effectiveReferer)
        val custom = original.copy(userAgent = "RuleUA", referer = "https://referer.example/")
        val restored = WebSourceRuleCodec.decode(WebSourceRuleCodec.encode(listOf(custom)))
            .getOrThrow().rules.single()
        assertEquals("RuleUA", restored.effectiveUserAgent)
        assertEquals("https://referer.example/", restored.effectiveReferer)
    }

    @Test
    fun `不支持的开关给出具体原因而非导入为 GET 规则`() {
        val options = mapOf(
            "usePost" to (true to WebSourceRuleCodec.IssueReason.POST),
            "useNativePlayer" to (false to WebSourceRuleCodec.IssueReason.WEB_PLAYER_ONLY),
            "useLegacyParser" to (true to WebSourceRuleCodec.IssueReason.LEGACY_PARSER),
            "useWebview" to (false to WebSourceRuleCodec.IssueReason.NON_WEBVIEW_PARSER),
            "antiCrawlerEnabled" to (true to WebSourceRuleCodec.IssueReason.ANTI_CRAWLER),
            "deprecated" to (true to WebSourceRuleCodec.IssueReason.DEPRECATED)
        )
        options.forEach { (key, option) ->
            val obj = JsonParser.parseString(kazumiRule).asJsonObject
            obj.addProperty(key, option.first)
            val error = WebSourceRuleCodec.decode(obj.toString()).exceptionOrNull()
                as WebSourceRuleCodec.NoUsableRulesException
            assertTrue(key, error.issues.any { it.reason == option.second && it.ruleName == "AGE" })
        }
    }

    @Test
    fun `混合规则合集保留可用项并报告不支持项`() {
        val unsupported = JsonParser.parseString(kazumiRule).asJsonObject.apply {
            addProperty("name", "POST source")
            addProperty("usePost", true)
        }
        val decoded = WebSourceRuleCodec.decode("[$kazumiRule,$unsupported]").getOrThrow()
        assertEquals(listOf("AGE"), decoded.rules.map { it.name })
        assertEquals(listOf("POST source"), decoded.skipped)
        assertEquals(WebSourceRuleCodec.IssueReason.POST, decoded.issues.single().reason)
    }

    @Test
    fun `布尔字符串可读取而无效开关不能静默忽略`() {
        val obj = JsonParser.parseString(kazumiRule).asJsonObject
        obj.addProperty("usePost", "false")
        assertTrue(WebSourceRuleCodec.decode(obj.toString()).isSuccess)
        obj.addProperty("usePost", "unknown")
        val error = WebSourceRuleCodec.decode(obj.toString()).exceptionOrNull()
            as WebSourceRuleCodec.NoUsableRulesException
        assertEquals(WebSourceRuleCodec.IssueReason.INVALID_OPTION, error.issues.single().reason)
    }

    @Test
    fun `广告过滤未实现时明确警告但允许播放`() {
        val obj = JsonParser.parseString(kazumiRule).asJsonObject
        obj.addProperty("adBlocker", true)
        val decoded = WebSourceRuleCodec.decode(obj.toString()).getOrThrow()
        assertEquals(1, decoded.rules.size)
        assertTrue(decoded.skipped.isEmpty())
        assertEquals(WebSourceRuleCodec.IssueReason.AD_BLOCKING_IGNORED, decoded.issues.single().reason)
    }

    @Test
    fun `被拒绝的规则不显示已导入的广告过滤提示`() {
        val obj = JsonParser.parseString(kazumiRule).asJsonObject
        obj.addProperty("usePost", true)
        obj.addProperty("adBlocker", true)
        val error = WebSourceRuleCodec.decode(obj.toString()).exceptionOrNull()
            as WebSourceRuleCodec.NoUsableRulesException
        assertEquals(listOf(WebSourceRuleCodec.IssueReason.POST), error.issues.map { it.reason })
    }

    @Test
    fun `重复规则报告名称且保留第一条`() {
        val decoded = WebSourceRuleCodec.decode("[$kazumiRule,$kazumiRule]").getOrThrow()
        assertEquals(1, decoded.rules.size)
        assertEquals(listOf("AGE"), decoded.skipped)
        assertEquals(WebSourceRuleCodec.IssueReason.DUPLICATE, decoded.issues.single().reason)
    }

    @Test
    fun `非法请求头在导入阶段拒绝`() {
        listOf("userAgent" to "UA\r\nCookie: sid=1", "referer" to "javascript:alert(1)").forEach { (key, value) ->
            val obj = JsonParser.parseString(kazumiRule).asJsonObject
            obj.addProperty(key, value)
            val error = WebSourceRuleCodec.decode(obj.toString()).exceptionOrNull()
                as WebSourceRuleCodec.NoUsableRulesException
            assertEquals(WebSourceRuleCodec.IssueReason.INVALID_HEADERS, error.issues.single().reason)
        }
    }

    private val apiRule = """
        {
            "api": "9",
            "name": "giriGiriLove",
            "version": "2.7",
            "useWebview": true,
            "useNativePlayer": true,
            "usePost": false,
            "useLegacyParser": false,
            "adBlocker": false,
            "baseURL": "https://ani.girigirilove.com",
            "searchMode": "api",
            "chapterMode": "api",
            "searchApiConfig": {
                "request": {
                    "method": "GET",
                    "url": "https://m3u8.girigirilove.com/api.php/Vod/get_list",
                    "query": {"vod_name": "@keyword", "offset": "0", "limit": "24"}
                },
                "listPath": "${'$'}.info.rows[*]",
                "namePath": "${'$'}.vod_name",
                "sourcePath": "${'$'}.vod_id",
                "sourceTemplate": ""
            },
            "chapterApiConfig": {
                "request": {
                    "method": "GET",
                    "url": "https://m3u8.girigirilove.com/api.php/Vod/get_detail",
                    "query": {"vod_id": "@source"}
                },
                "format": "delimited",
                "roadNamesPath": "${'$'}.info.vod_play_from",
                "roadEpisodesPath": "${'$'}.info.vod_play_url",
                "roadSeparator": "${'$'}${'$'}${'$'}",
                "episodeSeparator": "#",
                "fieldSeparator": "${'$'}",
                "episodePage": {
                    "url": "https://pl.girigirilove.com/addons/aplyer/atom.php",
                    "query": {"key": "0", "url": "@episodeUrl"}
                }
            },
            "searchURL": "",
            "searchList": "",
            "searchName": "",
            "searchResult": "",
            "chapterRoads": "",
            "chapterResult": "",
            "referer": ""
        }
    """.trimIndent()

    @Test
    fun `解析 api 规则的分流模式与接口配置`() {
        val rule = WebSourceRuleCodec.decode(apiRule).getOrThrow().rules.single()

        assertEquals("giriGiriLove", rule.name)
        assertTrue(rule.isApiSearch)
        assertTrue(rule.isApiChapter)
        assertTrue(rule.isUsable)
        assertEquals("$.info.rows[*]", rule.searchApi?.listPath)
        val chapter = rule.chapterApi
        assertNotNull(chapter)
        assertEquals("delimited", chapter?.format)
        assertEquals("$$$", chapter?.roadSeparator)
        assertEquals("https://pl.girigirilove.com/addons/aplyer/atom.php", chapter?.episodePage?.url)
        assertEquals("@episodeUrl", chapter?.episodePage?.query?.get("url"))
    }

    @Test
    fun `api 规则不受 usePost 开关影响`() {
        val obj = JsonParser.parseString(apiRule).asJsonObject
        obj.addProperty("usePost", true)

        val decoded = WebSourceRuleCodec.decode(obj.toString()).getOrThrow()

        assertEquals(1, decoded.rules.size)
        assertTrue(decoded.skipped.isEmpty())
    }

    @Test
    fun `api 规则编码后可往返`() {
        val rule = WebSourceRuleCodec.decode(apiRule).getOrThrow().rules.single()

        val restored = WebSourceRuleCodec.decode(WebSourceRuleCodec.encode(listOf(rule)))
            .getOrThrow().rules.single()

        assertEquals(rule, restored)
    }

    @Test
    fun `非法 jsonpath 与缺失模板变量的规则计入不完整`() {
        val badPath = JsonParser.parseString(apiRule).asJsonObject
        badPath.getAsJsonObject("searchApiConfig").addProperty("listPath", "info.rows")

        val badTemplate = JsonParser.parseString(apiRule).asJsonObject
        badTemplate.getAsJsonObject("searchApiConfig").addProperty("sourceTemplate", "https://x.example/@id")

        listOf(badPath, badTemplate).forEach { obj ->
            val error = WebSourceRuleCodec.decode(obj.toString()).exceptionOrNull()
                as WebSourceRuleCodec.NoUsableRulesException
            assertEquals(WebSourceRuleCodec.IssueReason.INCOMPLETE, error.issues.single().reason)
        }
    }

    @Test
    fun `嵌套防爬开关被拒绝而关闭时正常导入`() {
        val obj = JsonParser.parseString(apiRule).asJsonObject
        obj.add("antiCrawlerConfig", JsonParser.parseString("""{"enabled":true}"""))
        val error = WebSourceRuleCodec.decode(obj.toString()).exceptionOrNull()
            as WebSourceRuleCodec.NoUsableRulesException
        assertEquals(WebSourceRuleCodec.IssueReason.ANTI_CRAWLER, error.issues.single().reason)

        obj.getAsJsonObject("antiCrawlerConfig").addProperty("enabled", false)
        assertTrue(WebSourceRuleCodec.decode(obj.toString()).isSuccess)
    }
}

