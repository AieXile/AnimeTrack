package com.aiexile.animetrack.data.websource

import com.aiexile.animetrack.model.WebSourceRule
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSourceParserTest {

    private val rule = WebSourceRule(
        name = "test",
        baseUrl = "https://site.example/",
        searchUrl = "https://site.example/search?wd=@keyword",
        searchList = "//div[@class='item']",
        searchName = "//h3/a",
        searchResult = "//h3/a",
        chapterRoads = "//div[@class='road']",
        chapterResult = "//ul/li/a"
    )

    @Test
    fun `buildSearchUrl encodes cleaned keyword`() {
        assertEquals(
            "https://site.example/search?wd=%E6%B5%8B%E8%AF%95%E7%95%AA%E5%89%A7",
            WebSourceParser.buildSearchUrl(rule, "剧场版 测试番剧 [BDRip]")
        )
    }

    @Test
    fun `cleanKeyword drops markers and brackets`() {
        assertEquals("测试番剧", WebSourceParser.cleanKeyword("剧场版 测试番剧 [BDRip]"))
    }

    @Test
    fun `parseEntries resolves relative links`() {
        val html = """
            <html><body>
              <div class="item"><h3><a href="/detail/1">测试番剧</a></h3></div>
              <div class="item"><h3><a href="https://other.example/detail/2">测试番剧 第二季</a></h3></div>
            </body></html>
        """.trimIndent()

        val entries = WebSourceParser.parseEntries(html.toByteArray(), rule, rule.searchUrl)

        assertEquals(2, entries.size)
        assertEquals("测试番剧", entries[0].name)
        assertEquals("https://site.example/detail/1", entries[0].url)
        assertEquals("https://other.example/detail/2", entries[1].url)
    }

    @Test
    fun `parseEpisodes groups roads by index`() {
        val html = """
            <html><body>
              <div class="road"><span>线路一</span><ul>
                <li><a href="/play/1-1">第1话</a></li>
                <li><a href="/play/1-2">第2话</a></li>
              </ul></div>
              <div class="road"><span>线路二</span><ul>
                <li><a href="/play/2-1">第1话</a></li>
              </ul></div>
            </body></html>
        """.trimIndent()

        val episodes = WebSourceParser.parseEpisodes(
            html.toByteArray(),
            rule,
            "https://site.example/detail/1"
        )

        assertEquals(3, episodes.size)
        assertEquals(listOf(0, 0, 1), episodes.map { it.channelIndex })
        assertEquals("https://site.example/play/1-1", episodes[0].url)
    }

    @Test
    fun `parseEpisodes handles omitted list item end tags`() {
        val html = """
            <div class="road"><ul>
              <li><a href="/play/1">第1话</a>
              <li><a href="/play/2">第2话 &amp; 特典</a>
            </ul></div>
        """.trimIndent()

        val episodes = WebSourceParser.parseEpisodes(
            html.toByteArray(), rule, "https://site.example/detail/1"
        )

        assertEquals(listOf("第1话", "第2话 & 特典"), episodes.map { it.name })
        assertEquals(listOf("https://site.example/play/1", "https://site.example/play/2"), episodes.map { it.url })
    }

    @Test
    fun `chapterRoads slash slash uses the page as the only road`() {
        val wholePageRule = rule.copy(chapterRoads = "//")
        val html = """
            <html><body>
              <ul><li><a href="/play/1">第1话</a></li><li><a href="/play/2">第2话</a></li></ul>
            </body></html>
        """.trimIndent()

        val episodes = WebSourceParser.parseEpisodes(
            html.toByteArray(),
            wholePageRule,
            "https://site.example/detail/1"
        )

        assertEquals(2, episodes.size)
        assertEquals("https://site.example/play/1", episodes[0].url)
    }

    @Test
    fun `union selectors stay relative to the road container`() {
        // ezdmw 式写法：线路容器自身用 /self::* 判断，再取随后的兄弟节点作为剧集
        val unionRule = rule.copy(
            chapterRoads = "//div[contains(@class,'line_')]",
            chapterResult = "/self::*[contains(@class,'line_a')]/following-sibling::a | " +
                "/self::*[contains(@class,'line_b')]/following-sibling::a"
        )
        val html = """
            <html><body>
              <div class="line_a"></div>
              <a href="/play/a-1">A1</a><a href="/play/a-2">A2</a>
              <div class="line_b"></div>
              <a href="/play/b-1">B1</a>
            </body></html>
        """.trimIndent()

        val episodes = WebSourceParser.parseEpisodes(
            html.toByteArray(),
            unionRule,
            "https://site.example/detail/1"
        )

        assertEquals(listOf("A1", "A2", "B1"), episodes.map { it.name })
    }

    @Test
    fun `css style class substring selector is normalized for xpath`() {
        // TvTFun 规则沿用 CSS 的 @class*= 写法，XPath 1.0 需要 contains()
        val tvRule = rule.copy(
            chapterRoads = "//ol[@class*='tvt-watch-episodes']",
            chapterResult = "//li/a"
        )
        val html = """
            <html><body>
              <ol class="tvt-watch-episodes"><li><a href="/watch/1/1">第 1 集</a></li></ol>
            </body></html>
        """.trimIndent()

        val episodes = WebSourceParser.parseEpisodes(
            html.toByteArray(),
            tvRule,
            "https://site.example/watch/1/1"
        )

        assertEquals(listOf("第 1 集"), episodes.map { it.name })
        assertEquals("https://site.example/watch/1/1", episodes.single().url)
    }

    @Test
    fun `relative links use the fetched page instead of a stale base url`() {
        // 站点换域名后规则里的 baseURL 可能过期，相对链接要以实际页面地址为基准
        val staleRule = rule.copy(baseUrl = "https://old.example/")
        val html = "<html><body><div class='item'><h3><a href='/detail/9'>测试</a></h3></div></body></html>"

        val entries = WebSourceParser.parseEntries(
            html.toByteArray(),
            staleRule,
            "https://new.example/search?wd=x"
        )

        assertEquals("https://new.example/detail/9", entries.single().url)
    }
}
