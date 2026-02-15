package com.animetrack.data.network

import retrofit2.http.Body
import retrofit2.http.POST

data class BangumiSearchRequest(
    val keyword: String,
    val type: List<Int>? = null,
    val limit: Int = 25,
    val offset: Int = 0
)

data class BangumiSearchResponse(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val data: List<BangumiSubject>
)

data class BangumiRating(
    val score: Double = 0.0,
    val total: Int = 0,
    val rank: Int = 0
)

data class BangumiSubject(
    val id: Int,
    val name: String,
    val name_cn: String?,
    val image: String?,
    val eps: Int?,
    val rating: BangumiRating?
) {
    val displayName: String
        get() = if (!name_cn.isNullOrBlank()) name_cn else name
    
    val coverUrl: String?
        get() = image
    
    val score: Double?
        get() = rating?.score
}

interface BangumiApiService {
    @POST("search/subjects")
    suspend fun searchSubjects(
        @Body request: BangumiSearchRequest
    ): BangumiSearchResponse
}
