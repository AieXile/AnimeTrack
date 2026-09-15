package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.model.AnimeStatus
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class UserAuthRegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val code: String
)

data class UserAuthLoginRequest(
    val username: String,
    val password: String,
    /** 设备唯一标识（多端登录会话管理，同一设备重登时替换旧会话） */
    val deviceId: String? = null,
    /** 设备名称（如 "Google Pixel 7"） */
    val deviceName: String? = null,
    /** 登录平台：android / ios / web */
    val platform: String? = null,
    /** 登录超限后用户选择下线的会话 ID 列表（重试登录时携带） */
    val kickDeviceIds: List<String>? = null
)

data class UserAuthRegisterResponse(
    val success: Boolean,
    val message: String?
)

data class UserAuthUser(
    val id: Int,
    val username: String,
    val email: String?,
    @SerializedName("email_verified")
    val emailVerified: Int? = null,
    val avatar: String? = null,
    @SerializedName("created_at")
    val createdAt: String?
)

data class UserAuthLoginResponse(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val user: UserAuthUser?,
    val message: String?,
    /** 存量用户未绑定邮箱时为 true，此时使用 bindToken 跳转绑定页 */
    @SerializedName("requireEmailBind")
    val requireEmailBind: Boolean? = null,
    val bindToken: String? = null,
    /** 登录设备已达上限时为 true，devices 为需选择下线的设备列表 */
    @SerializedName("deviceLimitReached")
    val deviceLimitReached: Boolean? = null,
    val devices: List<DeviceSession>? = null,
    val limits: DeviceLimits? = null
)

data class UserAuthProfileResponse(
    val success: Boolean,
    val user: UserAuthUser?,
    val message: String?
)

data class UserAuthRefreshRequest(
    val refreshToken: String,
    /** 设备信息（可选）：存量会话刷新时由服务端补齐，用于多端登录设备展示 */
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: String? = null
)

data class UserAuthRefreshResponse(
    val success: Boolean,
    val accessToken: String?,
    val message: String?,
    /** true = 会话被主动撤销（该设备已下线），客户端需给出明确被踢提示 */
    val kicked: Boolean? = null
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
    val newPassword: String,
    /** 邮箱验证码 */
    val code: String
)

// ========== 邮箱验证码 ==========

/** 验证码用途常量（与服务端 verificationCodeService 保持一致） */
object EmailCodePurpose {
    const val REGISTER = "register"
    const val BIND = "bind"
    const val CHANGE_PASSWORD = "change_password"
    const val CHANGE_EMAIL = "change_email"
    const val RESET_PASSWORD = "reset_password"
}

data class SendCodeRequest(
    val email: String?,
    val purpose: String
)

data class SendCodeResponse(
    val success: Boolean,
    val message: String?
)

// ========== 绑定邮箱 ==========

data class BindEmailRequest(
    val email: String,
    val code: String,
    /** 设备信息（多端登录会话管理，与服务端 login 一致） */
    val deviceId: String? = null,
    val deviceName: String? = null,
    val platform: String? = null,
    val kickDeviceIds: List<String>? = null
)

data class BindEmailResponse(
    val success: Boolean,
    val message: String?,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserAuthUser? = null,
    /** 登录设备已达上限时为 true，devices 为需选择下线的设备列表 */
    @SerializedName("deviceLimitReached")
    val deviceLimitReached: Boolean? = null,
    val devices: List<DeviceSession>? = null,
    val limits: DeviceLimits? = null
)

// ========== 多端登录设备管理 ==========

/** 登录设备会话（服务端 refresh_tokens 行） */
data class DeviceSession(
    val sessionId: String,
    val deviceName: String? = null,
    /** android / ios / web */
    val platform: String? = null,
    val createdAt: String? = null,
    @SerializedName("lastUsedAt")
    val lastUsedAt: String? = null,
    /** 是否为当前设备 */
    @SerializedName("isCurrent")
    val isCurrent: Boolean = false
)

/** 各平台同时登录上限（手机端 / 网页端） */
data class DeviceLimits(
    val mobile: Int = 3,
    val web: Int = 1
)

/** 各平台当前登录设备数 */
data class DeviceCounts(
    val mobile: Int = 0,
    val web: Int = 0
)

data class DevicesResponse(
    val success: Boolean,
    val devices: List<DeviceSession> = emptyList(),
    val counts: DeviceCounts? = null,
    val limits: DeviceLimits? = null,
    val message: String? = null
)

data class RevokeDeviceRequest(
    val sessionId: String
)

// ========== 更换邮箱 ==========

data class ChangeEmailRequest(
    val password: String,
    val newEmail: String,
    val code: String
)

data class ChangeEmailResponse(
    val success: Boolean,
    val message: String?,
    val email: String? = null
)

// ========== 忘记密码 ==========

data class ForgotPasswordRequest(
    val email: String,
    val code: String,
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
    val finishDate: String? = null,
    /**
     * 旧版本客户端上传时使用的本地自增 id。服务端 upsert 成功后按
     * (anime_id + anime_title) 双条件删除该旧记录，完成向稳定远程 ID 的迁移。
     */
    val legacyAnimeId: String? = null
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

    // ========== 邮箱验证码 ==========

    /** 发送邮箱验证码（register/bind/reset_password 公开；change_password/change_email 需登录态） */
    @POST("auth/send-code")
    suspend fun sendCode(
        @Body request: SendCodeRequest
    ): SendCodeResponse

    // ========== 绑定邮箱（bindToken 鉴权） ==========

    @POST("user/bind-email")
    suspend fun bindEmail(
        @Header("Authorization") authorization: String,
        @Body request: BindEmailRequest
    ): BindEmailResponse

    // ========== 更换邮箱 ==========

    @POST("user/change-email")
    suspend fun changeEmail(
        @Body request: ChangeEmailRequest
    ): ChangeEmailResponse

    // ========== 忘记密码 ==========

    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
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

    // ========== 多端登录设备管理 ==========

    /** 查询当前登录设备列表（含各平台计数与上限） */
    @GET("user/devices")
    suspend fun getDevices(): DevicesResponse

    /** 下线指定登录设备（撤销其会话，即时生效） */
    @POST("user/devices/revoke")
    suspend fun revokeDevice(
        @Body request: RevokeDeviceRequest
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

    /** 获取公告详情（含投票选项与当前用户已选状态，需登录） */
    @GET("announcements/{id}")
    suspend fun getAnnouncementDetail(@Path("id") id: Int): AnnouncementDetailResponse

    /** 提交投票（需登录，同一用户重复提交会覆盖原选择） */
    @POST("announcements/{id}/respond")
    suspend fun submitVote(
        @Path("id") id: Int,
        @Body body: VoteRequest
    ): VoteResponse
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

// ========== 服务器错误信息解析 ==========

/**
 * 从 HttpException 错误响应中解析服务端返回的 message 字段
 * （如 send-code 的 429 "发送过于频繁，请 60 秒后再试"），失败时返回 null
 */
fun HttpException.serverMessage(): String? = try {
    val body = response()?.errorBody()?.string()
    body?.let {
        runCatching {
            val json = com.google.gson.JsonParser.parseString(it).asJsonObject
            if (json.has("message")) json.get("message").asString else null
        }.getOrNull()
    }
} catch (_: Exception) {
    null
}

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

// ========== 公告投票 ==========

/** 公告投票选项 */
data class AnnouncementOption(
    val id: Int,
    val text: String,
    val count: Int = 0,
    @SerializedName("sort_order")
    val sortOrder: Int = 0
)

/** 公告详情（在列表公告基础上增加投票选项与当前用户已选状态） */
data class AnnouncementDetail(
    val id: Int,
    val title: String,
    val content: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    @SerializedName("created_at")
    val createdAt: String?,
    val options: List<AnnouncementOption> = emptyList(),
    /** 当前用户已选的选项 ID，未投票为 null */
    val selectedOptionId: Int? = null
)

data class AnnouncementDetailResponse(
    val success: Boolean,
    val announcement: AnnouncementDetail? = null
)

/** 提交投票请求体 */
data class VoteRequest(
    val optionId: Int
)

data class VoteResponse(
    val success: Boolean,
    val message: String? = null
)
