package com.aiexile.animetrack.data.websource

import com.aiexile.animetrack.model.WebSourceApiRequest
import com.aiexile.animetrack.model.WebSourceChapterApi
import com.aiexile.animetrack.model.WebSourceEntry
import com.aiexile.animetrack.model.WebSourceEpisodePage
import com.aiexile.animetrack.model.WebSourceRule
import com.aiexile.animetrack.model.WebSourceSearchApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class WebSourceApiParserTest {

    private fun apiRule(
        searchApi: WebSourceSearchApi,
        chapterApi: WebSourceChapterApi
    ) = WebSourceRule(
        name = "api",
        baseUrl = "https://site.example/",
        searchMode = WebSourceRule.MODE_API,
        chapterMode = WebSourceRule.MODE_API,
        searchApi = searchApi,
        chapterApi = chapterApi
    )

    private val searchApi = WebSourceSearchApi(
        request = WebSourceApiRequest(
            url = "https://api.example/list",
            query = mapOf("wd" to "@keyword", "page" to "1")
        ),
        listPath = "$.info.rows[*]",
        namePath = "$.vod_name",
        sourcePath = "$.vod_id"
    )

    @Test
    fun `search call renders keyword into query`() {
        val rule = apiRule(searchApi, chapterApi())

        val call = WebSourceApiParser.buildSearchCall(rule, "葬送的芙莉莲")

        assertEquals("GET", call.method)
        assertEquals("https://api.example/list", call.url)
        assertEquals("葬送的芙莉莲", call.query["wd"])
        assertEquals("1", call.query["page"])
    }

    @Test
    fun `search parses list nodes and keeps raw source id`() {
        val rule = apiRule(searchApi, chapterApi())
        val raw = """
            {"info":{"rows":[
              {"vod_id":25524,"vod_name":"葬送的芙莉莲"},
              {"vod_id":26879,"vod_name":"葬送的芙莉莲 第二季"},
              {"vod_id":1,"vod_name":""}
            ]}}
        """.trimIndent()

        val entries = WebSourceApiParser.parseSearch(raw, rule)

        assertEquals(
            listOf(
                WebSourceEntry("葬送的芙莉莲", "25524"),
                WebSourceEntry("葬送的芙莉莲 第二季", "26879")
            ),
            entries
        )
    }

    @Test
    fun `source template builds detail url and encodes source`() {
        val rule = apiRule(searchApi.copy(sourceTemplate = "https://site.example/anime/@source.html"), chapterApi())
        val raw = """{"info":{"rows":[{"vod_id":"a b","vod_name":"A"}]}}"""

        val entries = WebSourceApiParser.parseSearch(raw, rule)

        assertEquals("https://site.example/anime/a%20b.html", entries.single().url)
    }

    @Test
    fun `delimited chapters expand separators and episode page query`() {
        val chapter = chapterApi(
            format = WebSourceChapterApi.FORMAT_DELIMITED,
            roadNamesPath = "$.from",
            roadEpisodesPath = "$.urls"
        )
        val rule = apiRule(searchApi, chapter)
        val raw = """
            {"from":"cht${'$'}${'$'}${'$'}chs","urls":"01${'$'}https://m.example/a.mp4#02${'$'}https://m.example/b.mp4${'$'}${'$'}${'$'}01${'$'}https://m.example/c.mp4"}
        """.trimIndent()

        val episodes = WebSourceApiParser.parseEpisodes(raw, rule, "25524")

        assertEquals(listOf("01", "02", "01"), episodes.map { it.name })
        assertEquals(listOf(0, 0, 1), episodes.map { it.channelIndex })
        assertEquals(
            "https://pl.example/atom.php?key=0&url=https%3A%2F%2Fm.example%2Fa.mp4",
            episodes.first().url
        )
    }

    @Test
    fun `single delimited road has no channel index`() {
        val chapter = chapterApi(
            format = WebSourceChapterApi.FORMAT_DELIMITED,
            roadNamesPath = "$.from",
            roadEpisodesPath = "$.urls"
        )
        val rule = apiRule(searchApi, chapter)

        val episodes = WebSourceApiParser.parseEpisodes(
            """{"from":"main","urls":"01${'$'}https://m.example/a.mp4"}""",
            rule,
            "1"
        )

        assertEquals(1, episodes.size)
        assertNull(episodes.single().channelIndex)
    }

    @Test
    fun `delimited entry without name falls back to episode number`() {
        val chapter = chapterApi(
            format = WebSourceChapterApi.FORMAT_DELIMITED,
            roadNamesPath = "$.from",
            roadEpisodesPath = "$.urls",
            episodePage = null
        )
        val rule = apiRule(searchApi, chapter)

        val episodes = WebSourceApiParser.parseEpisodes(
            """{"from":"main","urls":"${'$'}https://m.example/a.mp4#x${'$'}https://m.example/b.mp4"}""",
            rule,
            "1"
        )

        assertEquals(listOf("第1集", "x"), episodes.map { it.name })
        assertEquals("https://m.example/a.mp4", episodes.first().url)
    }

    @Test
    fun `nested chapters build episode page from path template`() {
        val chapter = WebSourceChapterApi(
            request = WebSourceApiRequest(url = "https://api.example/detail", query = mapOf("id" to "@source")),
            format = WebSourceChapterApi.FORMAT_NESTED,
            roadsPath = "$.sources[*]",
            roadNamePath = "$.name",
            episodesPath = "$.episodes[*]",
            episodeNamePath = "$.title",
            episodeUrlPath = "$.id",
            episodePage = WebSourceEpisodePage(url = "https://site.example/anime/@source/play/@episodeUrl")
        )
        val rule = apiRule(searchApi, chapter)
        val raw = """
            {"sources":[{"name":"line","episodes":[
              {"title":"第1话","id":7},
              {"title":"","id":8}
            ]}]}
        """.trimIndent()

        val episodes = WebSourceApiParser.parseEpisodes(raw, rule, "88")

        assertEquals(
            listOf(
                "https://site.example/anime/88/play/7",
                "https://site.example/anime/88/play/8"
            ),
            episodes.map { it.url }
        )
        assertEquals(listOf("第1话", "第2集"), episodes.map { it.name })
    }

    @Test
    fun `chapter variables can be reused in episode page`() {
        val chapter = WebSourceChapterApi(
            request = WebSourceApiRequest(url = "https://api.example/detail/@source"),
            format = WebSourceChapterApi.FORMAT_NESTED,
            roadsPath = "$.data",
            episodesPath = "$.episodes[*]",
            episodeNamePath = "$.name",
            episodeUrlPath = "$.order",
            variables = mapOf("videoId" to "$.data.id"),
            episodePage = WebSourceEpisodePage(url = "https://site.example/v/@videoId/e/@episodeUrl")
        )
        val rule = apiRule(searchApi, chapter)
        val raw = """{"data":{"id":"v1","episodes":[{"name":"E1","order":3}]}}"""

        val episodes = WebSourceApiParser.parseEpisodes(raw, rule, "ignored")

        assertEquals("https://site.example/v/v1/e/3", episodes.single().url)
    }

    @Test
    fun `post json body keeps non-string values and substitutes variables`() {
        val api = searchApi.copy(
            request = WebSourceApiRequest(
                method = "POST",
                url = "https://api.example/rpc/search",
                bodyType = WebSourceApiRequest.BODY_JSON,
                body = """{"search_term":"@keyword","page":1,"filters":{"enabled":true}}"""
            )
        )
        val rule = apiRule(api, chapterApi())

        val call = WebSourceApiParser.buildSearchCall(rule, "测试")

        assertEquals("POST", call.method)
        assertEquals(
            """{"search_term":"测试","page":1,"filters":{"enabled":true}}""",
            call.body
        )
        assertEquals("application/json; charset=utf-8", call.contentType)
    }

    @Test
    fun `post form body is url encoded`() {
        val keyword = "芙莉莲"
        val api = searchApi.copy(
            request = WebSourceApiRequest(
                method = "POST",
                url = "https://api.example/suggest",
                bodyType = WebSourceApiRequest.BODY_FORM,
                body = """{"wd":"@keyword","mid":1}"""
            )
        )
        val rule = apiRule(api, chapterApi())

        val call = WebSourceApiParser.buildSearchCall(rule, keyword)

        assertEquals(
            "wd=${URLEncoder.encode(keyword, "UTF-8")}&mid=1",
            call.body
        )
        assertEquals("application/x-www-form-urlencoded; charset=utf-8", call.contentType)
    }

    @Test
    fun `get request never sends body`() {
        val api = searchApi.copy(
            request = WebSourceApiRequest(
                url = "https://api.example/list",
                bodyType = WebSourceApiRequest.BODY_JSON,
                body = """{"wd":"@keyword"}"""
            )
        )
        val rule = apiRule(api, chapterApi())

        val call = WebSourceApiParser.buildSearchCall(rule, "x")

        assertNull(call.body)
        assertNull(call.contentType)
    }

    @Test
    fun `unsupported method and missing variable are reported`() {
        val deleteRule = apiRule(
            searchApi.copy(request = WebSourceApiRequest(method = "DELETE", url = "https://api.example/list")),
            chapterApi()
        )
        assertTrue(runCatching { WebSourceApiParser.buildSearchCall(deleteRule, "x") }.isFailure)

        val missingVariable = apiRule(
            searchApi.copy(request = WebSourceApiRequest(url = "https://api.example/list", query = mapOf("wd" to "@nope"))),
            chapterApi()
        )
        assertTrue(runCatching { WebSourceApiParser.buildSearchCall(missingVariable, "x") }.isFailure)
    }

    @Test
    fun `invalid json response is reported`() {
        val rule = apiRule(searchApi, chapterApi())
        assertTrue(runCatching { WebSourceApiParser.parseSearch("<html>", rule) }.isFailure)
    }

    private fun chapterApi(
        format: String = WebSourceChapterApi.FORMAT_DELIMITED,
        roadNamesPath: String = "$.from",
        roadEpisodesPath: String = "$.urls",
        episodePage: WebSourceEpisodePage? = WebSourceEpisodePage(
            url = "https://pl.example/atom.php",
            query = mapOf("key" to "0", "url" to "@episodeUrl")
        )
    ) = WebSourceChapterApi(
        request = WebSourceApiRequest(url = "https://api.example/detail", query = mapOf("id" to "@source")),
        format = format,
        roadNamesPath = roadNamesPath,
        roadEpisodesPath = roadEpisodesPath,
        episodePage = episodePage
    )
}
