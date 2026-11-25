package com.aiexile.animetrack.data

import com.aiexile.animetrack.data.BangumiSeriesResolver.SubjectRelation
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.home.AnimeListItem
import com.aiexile.animetrack.ui.home.SeriesMatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesStackRecognitionTest {
    @Test
    fun `JOJO 主线各段和旧版动画在离线时也能堆叠`() {
        val titles = listOf(
            "JOJO的奇妙冒险", "JOJO的奇妙冒险 星尘斗士", "JOJO的奇妙冒险 星尘斗士 埃及篇",
            "JOJO的奇妙冒险 不灭钻石", "JOJO的奇妙冒险 黄金之风", "JOJO的奇妙冒险 石之海",
            "JOJO的奇妙冒险 石之海 第2部分", "JOJO的奇妙冒险 石之海 第3部分",
            "飙马野郎 JOJO的奇妙冒险 第一赛段", "飙马野郎 JOJO的奇妙冒险 第二&第三赛段"
        )
        val bangumiIds = listOf(43558, 85631, 113292, 150490, 235128, 332261, 375913, 395782, 551918, 639938)
        val mainline = titles.mapIndexed { index, title -> anime(index + 1, title, bangumiIds[index]) }
        val oldOva = anime(11, "ジョジョの奇妙な冒険", 38326)
        val unrelated = anime(12, "JOJO的奇妙冒险 黄金赞歌", 999999)
        val relations = mapOf(
            43558 to listOf(edge(85631, titles[1], "续集")),
            85631 to listOf(edge(38326, oldOva.title, "不同演绎"), edge(999999, unrelated.title, "联动")),
            551918 to listOf(edge(43558, titles[0], "不同世界观"), edge(639938, titles[9], "续集"))
        )
        val library = mainline + oldOva + unrelated
        val stacked = recognize(library, relations)
        val series = stacked.filterIsInstance<AnimeListItem.Series>().single()
        assertEquals(titles[0], series.baseTitle)
        assertEquals(library.dropLast(1).map { it.id }, series.animeList.map { it.id })
        assertEquals((1..10).toList() + 99, series.animeList.map { it.seasonNumber })
        assertEquals(unrelated, stacked.filterIsInstance<AnimeListItem.Single>().single().anime)

        val titleOnly = mainline.map { it.copy(bangumiId = null) }
        val offline = recognize(titleOnly).single() as AnimeListItem.Series
        assertEquals((1..10).toList(), offline.animeList.map { it.seasonNumber })
    }

    @Test
    fun `元气少女缘结神圆圈后缀只在已知标题中代表第二季`() {
        val first = anime(1, "元气少女缘结神", 43525)
        val second = anime(2, "元气少女缘结神◎", 109724)
        val other = anime(3, "白箱", 110467)
        val stacked = recognize(listOf(first, second, other), mapOf(43525 to emptyList(), 109724 to emptyList()))
        val series = stacked.filterIsInstance<AnimeListItem.Series>().single()
        assertEquals(listOf(1, 2), series.animeList.map { it.id })
        assertEquals(listOf(1, 2), series.animeList.map { it.seasonNumber })
        assertEquals(other, stacked.filterIsInstance<AnimeListItem.Single>().single().anime)
        assertEquals(listOf(1, 2), (recognize(listOf(
            first.copy(bangumiId = null), second.copy(title = "神様はじめました◎", bangumiId = null)
        )).single() as AnimeListItem.Series).animeList.map { it.seasonNumber })
        assertEquals(2, recognize(listOf(anime(4, "其他动画"), anime(5, "其他动画◎"))).size)
    }

    @Test
    fun `超过三百个库内条目仍分批查完并允许重试失败请求`() = runBlocking {
        val requests = mutableListOf<Int>()
        val publications = mutableListOf<Int>()
        val sync = BangumiSeriesRelationSync(batchSize = 100) { id ->
            requests.add(id)
            if (id == 301 && requests.count { it == id } == 1) null else emptyList()
        }
        val ids = (1..301).toSet()
        sync.sync(ids) { publications.add(it.size) }
        assertEquals((1..301).toList(), requests)
        assertEquals(listOf(100, 200, 300), publications)
        sync.sync(ids) { publications.add(it.size) }
        assertEquals((1..301).toList() + 301, requests)
        assertEquals(listOf(100, 200, 300, 301), publications)
    }

    @Test
    fun `补查库外的续集和分支但不追查联动或原作`() = runBlocking {
        val library = listOf(anime(1, "曙光", 101), anime(2, "终焉", 104))
        val requests = mutableListOf<Int>()
        val graph = mapOf(
            101 to listOf(edge(102, "夜幕", "续集"), edge(105, "联动作品", "联动")),
            102 to listOf(edge(103, "分支桥梁", "分支故事"), edge(106, "原作", "书籍", false)),
            103 to listOf(edge(104, "终焉", "续集"))
        )
        val sync = BangumiSeriesRelationSync { id -> requests.add(id); graph[id].orEmpty() }
        var result = emptyList<AnimeListItem>()
        sync.sync(setOf(101, 104)) { result = recognize(library, it) }
        assertEquals(listOf(101, 104, 102, 103), requests)
        assertEquals(listOf(1, 2), (result.single() as AnimeListItem.Series).animeList.map { it.id })
        sync.sync(setOf(101, 104)) { error("缓存命中不应重复发布") }
        assertEquals(4, requests.size)
    }

    @Test
    fun `库外请求达到上限不会影响库内条目且下轮可从缓存继续`() = runBlocking {
        val requests = mutableListOf<Int>()
        val graph = mapOf(1 to listOf(edge(3, "中间一", "续集")), 3 to listOf(edge(4, "中间二", "续集")))
        val sync = BangumiSeriesRelationSync(maxOutsideRequests = 1) { id ->
            requests.add(id)
            graph[id].orEmpty()
        }
        sync.sync(setOf(1, 2, 0)) {}
        assertEquals(listOf(1, 2, 3), requests)
        sync.sync(setOf(1, 2, 0)) {}
        assertEquals(listOf(1, 2, 3, 4), requests)
    }

    @Test
    fun `单向续集能推导季序且缺失关系的明确第三季仍可加入`() {
        val library = listOf(anime(1, "航海日志", 101), anime(2, "新大陆", 102), anime(3, "航海日志 第三季", 103))
        val relations = mapOf(101 to listOf(edge(102, "新大陆", "续集")), 103 to emptyList())
        val series = recognize(library, relations).single() as AnimeListItem.Series
        assertEquals(library.map { it.id }, series.animeList.map { it.id })
        assertEquals(listOf(1, 2, null), series.animeList.map { it.seasonNumber })
        assertEquals(3, SeriesMatcher.extractSeasonNumber(series.animeList.last().title))
    }

    @Test
    fun `标题回退不会合并有联动关系的相似题名`() {
        val library = listOf(anime(1, "航海日志", 101), anime(2, "航海日志 第二季", 102))
        val result = recognize(library, mapOf(102 to listOf(edge(101, "航海日志", "联动"))))
        assertEquals(2, result.size)
        assertTrue(result.all { it is AnimeListItem.Single })
        assertNull((result.first() as AnimeListItem.Single).anime.seriesKey)
    }

    @Test
    fun `同名的独立关系组不会串组且歧义季号不附加`() {
        val library = listOf(
            anime(1, "航海日志", 101), anime(2, "新大陆", 102),
            anime(3, "航海日志", 103), anime(4, "远洋传说", 104),
            anime(5, "航海日志 第三季", 105)
        )
        val relations = mapOf(101 to listOf(edge(102, "新大陆", "续集")), 103 to listOf(edge(104, "远洋传说", "续集")))
        val stacked = recognize(library, relations)
        val groups = stacked.filterIsInstance<AnimeListItem.Series>()
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), groups.map { group -> group.animeList.map { it.id } })
        assertEquals(listOf("航海日志", "航海日志"), groups.map { it.baseTitle })
        assertEquals(5, stacked.filterIsInstance<AnimeListItem.Single>().single().anime.id)
    }

    @Test
    fun `有主标题时补入不同分隔符的副标题而不是只有共同前几个字`() {
        val titles = listOf(
            "星海纪行", "星海纪行 第一季", "星海纪行 特别篇", "星海纪行：失落的航线",
            "星海纪行《归途》", "星海纪行 银色回声", "星海纪行 - 终点站",
            "星海纪行者", "星海纪行：", "星海纪行-者"
        )
        val stacked = recognize(titles.mapIndexed { index, title -> anime(index + 1, title) })
        val series = stacked.filterIsInstance<AnimeListItem.Series>().single()
        assertEquals("星海纪行", series.baseTitle)
        assertEquals(titles.take(7), series.animeList.map { it.title })
        assertEquals(titles.drop(7), stacked.filterIsInstance<AnimeListItem.Single>().map { it.anime.title })
    }

    @Test
    fun `未成组的完整片名也能和副标题组成一叠`() {
        val library = listOf(anime(1, "星海纪行：失落的航线"), anime(2, "星海纪行"))
        val series = recognize(library).single() as AnimeListItem.Series
        assertEquals("星海纪行", series.baseTitle)
        assertEquals(listOf(1, 2), series.animeList.map { it.id })
    }

    @Test
    fun `没有主标题时可用关系链确认的系列名补副标题`() {
        val library = listOf(
            anime(1, "潮汐之夜", 102), anime(2, "陆地尽头", 103), anime(3, "星海纪行：失落的航线", 104)
        )
        val relations = mapOf(
            101 to listOf(edge(102, "潮汐之夜", "续集")),
            102 to listOf(edge(101, "星海纪行", "前传"), edge(103, "陆地尽头", "续集"))
        )
        val series = recognize(library, relations).single() as AnimeListItem.Series
        assertEquals("星海纪行", series.baseTitle)
        assertEquals(listOf(1, 2, 3), series.animeList.map { it.id })
    }

    @Test
    fun `没有锚点时不会仅凭几个副标题的共同前缀建组`() {
        val library = listOf(anime(1, "星海纪行：失落的航线"), anime(2, "星海纪行《归途》"))
        assertTrue(recognize(library).all { it is AnimeListItem.Single })
    }

    @Test
    fun `多个完整主标题可匹配时不猜副标题归属`() {
        val library = listOf(
            anime(1, "星海纪行"), anime(2, "星海纪行 外星回声"), anime(3, "星海纪行 外星回声：归途")
        )
        val stacked = recognize(library)
        assertEquals(3, stacked.filterIsInstance<AnimeListItem.Single>().single().anime.id)
        assertEquals(listOf(1, 2), stacked.filterIsInstance<AnimeListItem.Series>().single().animeList.map { it.id })
        assertEquals(3, recognize(library.reversed()).filterIsInstance<AnimeListItem.Single>().single().anime.id)
    }

    @Test
    fun `副标题命中前缀也不能绕过联动或不同世界观`() {
        val library = listOf(
            anime(1, "星海纪行", 101), anime(2, "星海纪行：归途", 102), anime(3, "星海纪行《回声》", 103),
            anime(4, "星海纪行：星港之夜", 104), anime(5, "星海纪行 海面之下", 105)
        )
        val relations = mapOf(
            101 to listOf(edge(102, library[1].title, "续集")),
            103 to listOf(edge(102, library[1].title, "联动")),
            104 to listOf(edge(101, library[0].title, "不同世界观"))
        )
        val stacked = recognize(library, relations)
        assertEquals(listOf(1, 2, 5), stacked.filterIsInstance<AnimeListItem.Series>().single().animeList.map { it.id })
        assertEquals(listOf(3, 4), stacked.filterIsInstance<AnimeListItem.Single>().map { it.anime.id })
    }

    private fun recognize(library: List<Anime>, relations: Map<Int, List<SubjectRelation>> = emptyMap()): List<AnimeListItem> =
        SeriesMatcher.groupAnimeList(SeriesMatcher.assignSeriesKeys(library, BangumiSeriesResolver.resolve(library, relations), relations))

    private fun edge(id: Int, title: String, relation: String, isAnime: Boolean = true) =
        SubjectRelation(id, title, relation, isAnime)

    private fun anime(id: Int, title: String, bangumiId: Int? = null) = Anime(
        id = id, title = title, bangumiId = bangumiId, totalEpisodes = 12,
        watchedEpisodes = 0, status = AnimeStatus.PLANNED, rating = null, notes = ""
    )
}
