package com.aiexile.animetrack.data.network

import com.google.gson.annotations.SerializedName
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

data class BangumiImages(
    val large: String?,
    val common: String?,
    val medium: String?,
    val small: String?,
    val grid: String?
)

data class BangumiSubject(
    val id: Int,
    val name: String,
    @SerializedName("name_cn")
    val name_cn: String?,
    val images: BangumiImages?,
    val eps: Int?,
    @SerializedName("total_episodes")
    val total_episodes: Int?,
    val rating: BangumiRating?
) {
    val displayName: String
        get() = if (!name_cn.isNullOrBlank()) name_cn else name
    
    val coverUrl: String?
        get() = images?.large ?: images?.common ?: images?.medium
    
    val score: Double?
        get() = rating?.score
    
    val episodeCount: Int?
        get() = when {
            total_episodes != null && total_episodes > 0 -> total_episodes
            eps != null && eps > 0 -> eps
            else -> null
        }
    
    val episodeCountText: String
        get() = episodeCount?.let { "${it}集" } ?: "未定"
}

interface BangumiApiService {
    @POST("search/subjects")
    suspend fun searchSubjects(
        @Body request: BangumiSearchRequest
    ): BangumiSearchResponse
}
