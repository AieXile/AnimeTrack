package com.aiexile.animetrack.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// ===== 二维码登录 =====

data class BilibiliQrCodeResponse(
    val code: Int = -1,
    val message: String = "",
    val data: BilibiliQrCodeData? = null
)

data class BilibiliQrCodeData(
    val url: String = "",
    @SerializedName("qrcode_key")
    val qrcodeKey: String = ""
)

data class BilibiliQrCodePollResponse(
    val code: Int = -1,
    val message: String = "",
    val data: BilibiliQrCodePollData? = null
)

data class BilibiliQrCodePollData(
    val url: String = "",
    @SerializedName("refresh_token")
    val refreshToken: String = "",
    val timestamp: Long = 0,
    val code: Int = 0,
    val message: String = ""
)

// ===== Nav / Wbi 密钥 =====

data class BilibiliNavResponse(
    val code: Int = -1,
    val message: String = "",
    val data: BilibiliNavData? = null
)

data class BilibiliNavData(
    @SerializedName("wbi_img")
    val wbiImg: BilibiliWbiImg? = null,
    val mid: Long = 0,
    val uname: String = "",
    val face: String = ""
)

data class BilibiliWbiImg(
    @SerializedName("img_url")
    val imgUrl: String = "",
    @SerializedName("sub_url")
    val subUrl: String = ""
)

// ===== 追番列表 =====

data class BilibiliFollowResponse(
    val code: Int = -1,
    val message: String = "",
    val data: BilibiliFollowData? = null
)

data class BilibiliFollowData(
    val total: Int = 0,
    val pn: Int = 1,
    val ps: Int = 30,
    val list: List<BilibiliFollowItem>? = null
)

data class BilibiliFollowItem(
    @SerializedName("season_id")
    val seasonId: Long = 0,
    @SerializedName("media_id")
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerializedName("square_cover")
    val squareCover: String = "",
    val evaluate: String = "",
    val summary: String = "",
    @SerializedName("season_type_name")
    val seasonTypeName: String = "",
    @SerializedName("new_ep")
    val newEp: BilibiliNewEpInfo? = null,
    val progress: String = "",
    @SerializedName("is_finish")
    val isFinish: Int = 0,
    @SerializedName("follow_status")
    val followStatus: Int = 0,
    val total: Int = 0,
    @SerializedName("total_count")
    val totalCount: Int = 0,
    @SerializedName("formal_ep_count")
    val formalEpCount: Int = 0,
    @SerializedName("renewal_time")
    val renewalTime: String = "",
    val rating: BilibiliFollowRating? = null,
    val publish: BilibiliPublishInfo? = null
)

data class BilibiliNewEpInfo(
    val cover: String = "",
    val id: Long = 0,
    @SerializedName("index_show")
    val indexShow: String = ""
)

data class BilibiliFollowRating(
    val score: Float = 0f,
    val count: Int = 0
)

data class BilibiliPublishInfo(
    @SerializedName("pub_time")
    val pubTime: String = "",
    @SerializedName("release_date")
    val releaseDate: String = ""
)

// ===== Retrofit 接口 =====

// 登录专用接口（使用独立的干净 OkHttpClient，无 Cookie/Referer）
interface BilibiliLoginApiService {

    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): BilibiliQrCodeResponse

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(
        @Query("qrcode_key") qrCodeKey: String,
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): Response<BilibiliQrCodePollResponse>
}

// 已认证接口（使用带 Cookie/Referer 的 OkHttpClient）
interface BilibiliApiService {

    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): BilibiliNavResponse

    @GET("x/space/bangumi/follow/list")
    suspend fun getMyFollowBangumi(
        @Query("vmid") vmid: Long,
        @Query("type") type: Int = 1,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30
    ): BilibiliFollowResponse
}
