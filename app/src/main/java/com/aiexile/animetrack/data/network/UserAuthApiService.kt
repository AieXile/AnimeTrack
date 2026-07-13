package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.model.AnimeStatus
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class UserAuthRegisterRequest(
    val username: String,
    val password: String,
    val email: String
)

data class UserAuthLoginRequest(
    val username: String,
    val password: String
)

data class UserAuthRegisterResponse(
    val success: Boolean,
    val message: String?
)

data class UserAuthUser(
    val id: Int,
    val username: String,
    val email: String?,
    val avatar: String? = null,
    @SerializedName("created_at")
    val createdAt: String?
)

data class UserAuthLoginResponse(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val user: UserAuthUser?,
    val message: String?
)

data class UserAuthProfileResponse(
    val success: Boolean,
    val user: UserAuthUser?,
    val message: String?
)

data class UserAuthRefreshRequest(
    val refreshToken: String
)

data class UserAuthRefreshResponse(
    val success: Boolean,
    val accessToken: String?,
    val message: String?
)

data class UserAuthLogoutRequest(
    val refreshToken: String
)

data class UserAuthLogoutResponse(
    val success: Boolean,
    val message: String?
)

// ========== 修改密码 ==========

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

// ========== 上传头像 ==========

data class UploadAvatarResponse(
    val success: Boolean,
    val message: String?,
    val avatar: String? = null
)

// ========== 推送设置 ==========

data class PushSettings(
    val pushEnabled: Boolean,
    val dailyPushEnabled: Boolean,
    val preferredHour: Int,
    val preferredMinute: Int
)

data class UpdatePushSettingsRequest(
    val pushEnabled: Boolean,
    val dailyPushEnabled: Boolean,
    val preferredHour: Int,
    val preferredMinute: Int
)

data class PushSettingsResponse(
    val success: Boolean,
    val settings: PushSettings? = null,
    val message: String? = null
)

// ========== 极光推送设备ID ==========

data class RegistrationIdRequest(
    val registrationId: String
)

// ========== 番剧订阅 ==========

data class SubscribeRequest(
    val animeId: String,
    val animeTitle: String,
    val animeImage: String? = null,
    val airDate: String? = null,
    val isAiring: Int,
    val weekday: Int? = null,
    val totalEpisodes: Int = 0,
    val watchedEpisodes: Int = 0,
    val currentEpisodes: Int = 0,
    val status: String? = null,
    val rating: Float? = null,
    val notes: String? = null,
    val startDate: String? = null,
    val finishDate: String? = null
)

data class RemoveSubscribeRequest(
    val animeId: String
)

data class Subscription(
    val animeId: String,
    val animeTitle: String,
    val animeImage: String? = null,
    val airDate: String? = null,
    val isAiring: Boolean,
    val weekday: Int? = null,
    val subscribedAt: String? = null,
    val totalEpisodes: Int? = null,
    val watchedEpisodes: Int? = null,
    val currentEpisodes: Int? = null,
    val status: String? = null,
    val rating: Float? = null,
    val notes: String? = null,
    val startDate: String? = null,
    val finishDate: String? = null
)

data class SubscriptionsResponse(
    val success: Boolean,
    val subscriptions: List<Subscription>? = null,
    val message: String? = null
)

interface UserAuthApiService {

    @POST("auth/register")
    suspend fun register(
        @Body request: UserAuthRegisterRequest
    ): UserAuthRegisterResponse

    @POST("auth/login")
    suspend fun login(
        @Body request: UserAuthLoginRequest
    ): UserAuthLoginResponse

    @GET("user/profile")
    suspend fun getProfile(
        @Header("Authorization") authorization: String
    ): UserAuthProfileResponse

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: UserAuthRefreshRequest
    ): UserAuthRefreshResponse

    @POST("auth/logout")
    suspend fun logout(
        @Body request: UserAuthLogoutRequest
    ): UserAuthLogoutResponse

    @POST("user/change-password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): UserAuthLogoutResponse

    @Multipart
    @POST("user/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): UploadAvatarResponse

    // ========== 推送设置 ==========

    @GET("user/push-settings")
    suspend fun getPushSettings(): PushSettingsResponse

    @POST("user/push-settings")
    suspend fun updatePushSettings(
        @Body request: UpdatePushSettingsRequest
    ): UserAuthLogoutResponse

    // ========== 极光推送设备ID ==========

    @POST("user/registration-id")
    suspend fun reportRegistrationId(
        @Body request: RegistrationIdRequest
    ): UserAuthLogoutResponse

    // ========== 番剧订阅 ==========

    @POST("subscriptions/add")
    suspend fun addSubscription(
        @Body request: SubscribeRequest
    ): UserAuthLogoutResponse

    @POST("subscriptions/remove")
    suspend fun removeSubscription(
        @Body request: RemoveSubscribeRequest
    ): UserAuthLogoutResponse

    @GET("subscriptions/list")
    suspend fun getSubscriptions(): SubscriptionsResponse

    // ========== 用户活跃上报 ==========

    @POST("user/activity")
    suspend fun reportActivity(
        @Body body: EmptyRequestBody
    ): ActivityReportResponse

    // ========== 公告 ==========

    @GET("announcements")
    suspend fun getAnnouncements(): AnnouncementsResponse
}

// ========== 订阅字段转换辅助函数 ==========

/** AnimeStatus 枚举 → 后端 status 字符串（watching/completed/planning/dropped） */
fun AnimeStatus.toApiString(): String = when (this) {
    AnimeStatus.WATCHING -> "watching"
    AnimeStatus.COMPLETED -> "completed"
    AnimeStatus.PLANNED -> "planning"
    AnimeStatus.DROPPED -> "dropped"
}

/** 后端 status 字符串 → AnimeStatus 枚举，无法识别时回退到 PLANNED */
fun parseAnimeStatus(status: String?): AnimeStatus = when (status?.lowercase()) {
    "watching" -> AnimeStatus.WATCHING
    "completed" -> AnimeStatus.COMPLETED
    "planning" -> AnimeStatus.PLANNED
    "dropped" -> AnimeStatus.DROPPED
    else -> AnimeStatus.PLANNED
}

// ========== 活跃上报 ==========

/** 空请求体，Gson 序列化为 {} */
class EmptyRequestBody

data class ActivityReportResponse(
    val success: Boolean
)

// ========== 公告 ==========

data class Announcement(
    val id: Int,
    val title: String,
    val content: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("created_at")
    val createdAt: String?
)

data class AnnouncementsResponse(
    val success: Boolean,
    val announcements: List<Announcement> = emptyList()
)
