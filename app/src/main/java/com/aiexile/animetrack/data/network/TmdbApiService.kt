package com.aiexile.animetrack.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TmdbSearchResponse(
    val page: Int,
    val results: List<TmdbTvShow>,
    @SerializedName("total_pages")
    val totalPages: Int,
    @SerializedName("total_results")
    val totalResults: Int
)

data class TmdbTvShow(
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Float?
) {
    val coverUrl: String?
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

    val episodeCountText: String
        get() {
            if (!firstAirDate.isNullOrBlank()) {
                return firstAirDate.replace("-", "/")
            }
            return "未知"
        }
}

data class TmdbTvDetail(
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("overview")
    val overview: String?,
    @SerializedName("poster_path")
    val posterPath: String?,
    @SerializedName("first_air_date")
    val firstAirDate: String?,
    @SerializedName("vote_average")
    val voteAverage: Float?,
    @SerializedName("number_of_episodes")
    val numberOfEpisodes: Int?,
    @SerializedName("number_of_seasons")
    val numberOfSeasons: Int?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("genres")
    val genres: List<TmdbGenre>?
) {
    val coverUrl: String?
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }

    val isFinished: Boolean
        get() = status == "Ended" || status == "Canceled"

    val episodeCountText: String
        get() {
            val epCount = numberOfEpisodes
            val date = firstAirDate
            if (!date.isNullOrBlank()) {
                val isFuture = try {
                    java.time.LocalDate.parse(date)
                        .isAfter(java.time.LocalDate.now())
                } catch (_: Exception) { false }
                if (isFuture) {
                    return date.replace("-", "/") + " 放送"
                }
            }
            return when {
                epCount != null && epCount > 0 -> "全${epCount}话"
                date != null -> "连载中"
                else -> "未定"
            }
        }
}

data class TmdbGenre(
    val id: Int,
    @SerializedName("name")
    val name: String
)

interface TmdbApiService {
    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("language") language: String = "zh-CN",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse

    @GET("tv/{series_id}")
    suspend fun getTvDetail(
        @Path("series_id") seriesId: Int,
        @Query("language") language: String = "zh-CN"
    ): TmdbTvDetail
}
