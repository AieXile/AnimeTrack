package com.aiexile.animetrack.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class BangumiSearchRequest(
    val keyword: String,
    val sort: String? = "match",
    val filter: BangumiSearchFilter? = null
)

data class BangumiSearchFilter(
    val type: List<Int>? = listOf(2),
    val nsfw: Boolean = false
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
    val rating: BangumiRating?,
    val summary: String? = null,
    val date: String? = null
) {
    val displayName: String
        get() = if (!name_cn.isNullOrBlank()) name_cn else name
    
    val coverUrl: String?
        get() = images?.large ?: images?.common ?: images?.medium
    
    val score: Double?
        get() = rating?.score
    
    val episodeCount: Int?
        get() = when {
            eps != null && eps > 0 -> eps
            total_episodes != null && total_episodes > 0 -> total_episodes
            else -> null
        }
    
    val episodeCountText: String
        get() {
            val epCount = episodeCount
            val airDate = date
            if (!airDate.isNullOrBlank()) {
                val isFuture = try {
                    java.time.LocalDate.parse(airDate)
                        .isAfter(java.time.LocalDate.now())
                } catch (_: Exception) { false }
                if (isFuture) {
                    return airDate.replace("-", "/") + " 放送"
                }
            }
            return when {
                epCount != null && epCount > 0 -> "全${epCount}话"
                airDate != null -> "连载中"
                else -> "未定"
            }
        }
}

data class BangumiInfoboxItem(
    val key: String?,
    val value: Any?
)

data class BangumiSubjectDetail(
    val id: Int,
    val name: String,
    @SerializedName("name_cn")
    val nameCn: String?,
    val summary: String?,
    val date: String?,
    @SerializedName("air_weekday")
    private val _airWeekday: Int?,
    val eps: Int?,
    @SerializedName("total_episodes")
    val totalEpisodes: Int?,
    val rating: BangumiRating?,
    val images: BangumiImages?,
    val infobox: List<BangumiInfoboxItem>? = null
) {
    val score: Double?
        get() = rating?.score

    val airWeekday: Int?
        get() = _airWeekday ?: parseWeekdayFromInfobox()

    private fun parseWeekdayFromInfobox(): Int? {
        val item = infobox?.find { it.key == "放送星期" } ?: return null
        val value = item.value ?: return null
        val text = when (value) {
            is String -> value
            is Map<*, *> -> value["v"] as? String
            else -> null
        } ?: return null
        return weekdayTextToInt(text)
    }

    companion object {
        private val weekdayMap = mapOf(
            "星期一" to 1, "周一" to 1, "一" to 1,
            "星期二" to 2, "周二" to 2, "二" to 2,
            "星期三" to 3, "周三" to 3, "三" to 3,
            "星期四" to 4, "周四" to 4, "四" to 4,
            "星期五" to 5, "周五" to 5, "五" to 5,
            "星期六" to 6, "周六" to 6, "六" to 6,
            "星期日" to 7, "周日" to 7, "日" to 7,
            "星期天" to 7, "周天" to 7, "天" to 7
        )

        fun weekdayTextToInt(text: String): Int? {
            val trimmed = text.trim()
            return weekdayMap[trimmed]
                ?: weekdayMap.entries.find { trimmed.contains(it.key) }?.value
        }
    }
}

interface BangumiApiService {
    @POST("search/subjects")
    suspend fun searchSubjects(
        @Body request: BangumiSearchRequest
    ): BangumiSearchResponse
    
    @GET("subjects/{id}")
    suspend fun getSubjectDetail(
        @Path("id") id: Int
    ): BangumiSubjectDetail
}
