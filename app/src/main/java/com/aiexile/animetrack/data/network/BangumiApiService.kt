package com.aiexile.animetrack.data.network

import com.google.gson.annotations.SerializedName
import kotlin.jvm.Transient
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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
    
    @Transient
    @Volatile
    private var episodeCountTextCache: String? = null

    val episodeCountText: String
        get() {
            episodeCountTextCache?.let { return it }
            val text = computeEpisodeCountText()
            episodeCountTextCache = text
            return text
        }

    private fun computeEpisodeCountText(): String {
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

    /**
     * 从 infobox 提取「播放结束」日期，并归一化为 yyyy-MM-dd。
     *
     * 仅番剧完结后 Bangumi 才会填充此字段；连载中番剧返回 null。
     * 解析失败也返回 null（保守视为未完结，由 computeIsFinished 回退到日期估算）。
     */
    fun parseEndDate(): String? {
        val item = infobox?.find { it.key == "播放结束" } ?: return null
        val value = item.value ?: return null
        val text = when (value) {
            is String -> value
            is Map<*, *> -> value["v"] as? String
            else -> null
        } ?: return null
        return normalizeCnDate(text)
    }

    /**
     * 将 Bangumi infobox 中的中文日期（如 "2026年6月24日"）归一化为 yyyy-MM-dd。
     * 解析失败返回 null。
     */
    private fun normalizeCnDate(text: String): String? {
        val match = Regex("""(\d{4})年(\d{1,2})月(\d{1,2})日""").find(text.trim()) ?: return null
        val (year, month, day) = match.destructured
        return "%04d-%02d-%02d".format(year.toInt(), month.toInt(), day.toInt())
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

data class BangumiTokenResponse(
    val access_token: String,
    val token_type: String?,
    val expires_in: Int?,
    val refresh_token: String?,
    val scope: String?,
    val user_id: Int?
)

data class BangumiUserProfile(
    val id: Int,
    val url: String?,
    val username: String?,
    val nickname: String?,
    val avatar: BangumiUserAvatar?,
    val sign: String?
)

data class BangumiUserAvatar(
    val large: String?,
    val medium: String?,
    val small: String?
) {
    val bestUrl: String? get() = large ?: medium ?: small
}

data class BangumiCollectionSubject(
    val id: Int,
    val name: String,
    @SerializedName("name_cn")
    val nameCn: String?,
    val date: String?,
    val eps: Int?,
    @SerializedName("total_episodes")
    val totalEpisodes: Int?,
    val images: BangumiImages?,
    val rating: BangumiRating?,
    val summary: String?,
    @SerializedName("air_weekday")
    val airWeekday: Int?
) {
    val displayName: String
        get() = if (!nameCn.isNullOrBlank()) nameCn else name

    val coverUrl: String?
        get() = images?.large ?: images?.common ?: images?.medium

    val resolvedEps: Int
        get() = when {
            eps != null && eps > 0 -> eps
            totalEpisodes != null && totalEpisodes > 0 -> totalEpisodes
            else -> 0
        }
}

data class BangumiCollectionItem(
    @SerializedName("subject_id")
    val subjectId: Int,
    @SerializedName("ep_status")
    val epStatus: Int,
    val type: Int,
    val rate: Int,
    val comment: String?,
    val subject: BangumiCollectionSubject?
)

data class BangumiCollectionResponse(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val data: List<BangumiCollectionItem>
)

data class CollectionStatusBody(
    val type: Int,
    val rate: Int? = null,
    val comment: String? = null,
    @SerializedName("private")
    val isPrivate: Boolean? = null
)

data class BangumiEpisode(
    val id: Int,
    val type: Int,
    val sort: Double,
    val ep: Double?
)

data class BangumiEpisodeResponse(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val data: List<BangumiEpisode>
)

data class EpisodeCollectionBody(
    @SerializedName("episode_id")
    val episodeId: List<Int>,
    val type: Int
)

interface BangumiApiService {
    @POST("search/subjects")
    suspend fun searchSubjects(
        @Body request: BangumiSearchRequest
    ): BangumiSearchResponse
    
    @GET("subjects/{id}")
    suspend fun getSubjectDetail(
        @Path("id") id: Int
    ): BangumiSubjectDetail

    @FormUrlEncoded
    @Headers("User-Agent: AieXile/AnimeTrack/1.0 (https://github.com/AieXile)")
    @POST("oauth/access_token")
    suspend fun getAccessToken(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String
    ): BangumiTokenResponse

    /**
     * 使用 refresh_token 刷新 access_token。
     * Bangumi OAuth 端点与授权码换 token 相同：POST /oauth/access_token，
     * 仅 grant_type 与参数不同。刷新成功会返回新的 access_token 与（可能更新的）refresh_token。
     */
    @FormUrlEncoded
    @Headers("User-Agent: AieXile/AnimeTrack/1.0 (https://github.com/AieXile)")
    @POST("oauth/access_token")
    suspend fun refreshAccessToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("redirect_uri") redirectUri: String
    ): BangumiTokenResponse

    @Headers("User-Agent: AieXile/AnimeTrack/1.0 (https://github.com/AieXile)")
    @GET("me")
    suspend fun getMyProfile(): BangumiUserProfile

    @Headers("User-Agent: AieXile/AnimeTrack/1.0 (https://github.com/AieXile)")
    @GET("users/{username}/collections")
    suspend fun getUserCollections(
        @Path("username") username: String,
        @Query("subject_type") subjectType: Int = 2,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): BangumiCollectionResponse

    @Headers("User-Agent: AieXile/AnimeTrack/1.0 (https://github.com/AieXile)")
    @POST("users/-/collections/{subject_id}")
    suspend fun updateCollectionStatus(
        @Path("subject_id") subjectId: Int,
        @Body body: CollectionStatusBody
    )

    @Headers("User-Agent: AieXile/AnimeTrack/1.0 (https://github.com/AieXile)")
    @GET("episodes")
    suspend fun getEpisodes(
        @Query("subject_id") subjectId: Int,
        @Query("type") type: Int = 0,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0
    ): BangumiEpisodeResponse

    @Headers("User-Agent: AieXile/AnimeTrack/1.0 (https://github.com/AieXile)")
    @PATCH("users/-/collections/{subject_id}/episodes")
    suspend fun markEpisodesWatched(
        @Path("subject_id") subjectId: Int,
        @Body body: EpisodeCollectionBody
    )
}
