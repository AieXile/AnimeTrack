package com.aiexile.animetrack.data.remote

import com.aiexile.animetrack.data.network.RetrofitClient
import kotlin.random.Random

/** 彩蛋展示用的动漫语录 */
data class AnimeQuote(val text: String, val from: String)

/**
 * 随机动漫语录获取：一言 API（动画分类）优先，请求失败回退内置语录池。
 * 用于关于页应用图标彩蛋。
 */
class HitokotoRepository {

    /** 网络失败时的内置语录兜底 */
    private val fallbackQuotes = listOf(
        AnimeQuote("真相只有一个！", "名侦探柯南"),
        AnimeQuote("我是将来要成为海贼王的男人！", "海贼王"),
        AnimeQuote("我们的征途是星辰大海。", "银河英雄传说"),
        AnimeQuote("不相信自己的人，连努力的价值都没有。", "火影忍者"),
        AnimeQuote("人没有牺牲就什么都得不到，为了得到什么，就需要付出同等的代价。", "钢之炼金术师")
    )

    /** 立即返回内置语录（无网络等待），用于彩蛋即时展示 */
    fun getFallbackQuote(): AnimeQuote = fallbackQuotes[Random.nextInt(fallbackQuotes.size)]

    suspend fun getRandomAnimeQuote(): AnimeQuote {
        return try {
            val res = RetrofitClient.hitokotoApi.getQuote()
            AnimeQuote(
                text = res.hitokoto,
                from = listOfNotNull(res.from, res.from_who).joinToString(" · ")
            )
        } catch (_: Exception) {
            fallbackQuotes[Random.nextInt(fallbackQuotes.size)]
        }
    }
}
