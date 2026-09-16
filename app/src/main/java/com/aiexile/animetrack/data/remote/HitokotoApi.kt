package com.aiexile.animetrack.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/** 一言 API 响应（v1.hitokoto.cn） */
data class HitokotoResponse(
    val hitokoto: String,
    val from: String?,
    val from_who: String?
)

/** 一言 API：随机语录获取（https://v1.hitokoto.cn） */
interface HitokotoApi {

    /** 获取一条随机语录，c=a 为动画分类 */
    @GET("/")
    suspend fun getQuote(@Query("c") category: String = "a"): HitokotoResponse
}
