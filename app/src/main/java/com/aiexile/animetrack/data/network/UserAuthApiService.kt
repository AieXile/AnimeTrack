package com.aiexile.animetrack.data.network

import com.aiexile.animetrack.model.AnimeStatus
import com.google.gson.annotations.JsonAdapter
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
    val createdAt: String?,
    /** 贡献等级（服务端下发，默认 0；旧版本服务端不返回时为 null） */
    val level: Int? = null
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
    val platform: String? = null,
    /**
     * 新客户端声明支持 refresh token 轮换；旧客户端不发送该字段，
     * 服务端据此保持兼容不轮换，避免旧版因未保存新 token 被误判重放而登出。
     */
    val supportsRefreshTokenRotation: Boolean = true
)

data class UserAuthRefreshResponse(
    val success: Boolean,
    val accessToken: String?,
    /** 轮换后的新 refresh token；旧 token 已作废，客户端必须保存 */
    val refreshToken: String? = null,
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
    const val DELETE_ACCOUNT = "delete_account"
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

// ========== 注销账号 ==========

/** 注销验证：已绑定邮箱用验证码，否则回退密码（二选一） */
data class DeleteAccountRequest(
    val code: String? = null,
    val password: String? = null
)

data class DeleteAccountResponse(
    val success: Boolean,
    /** 物理清除到期时间（服务端时间） */
    val purgeAt: String? = null,
    val message: String? = null
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
    val legacyAnimeId: String? = null,
    /**
     * 行键迁移时被本行取代的旧行键列表（旧 bangumiId / remoteSyncId）。
     * 服务端 upsert 后删除旧行并记录迁移映射：拦截其他设备旧副本以旧键重传
     * （乒乓复活），下发列表时携带映射，让各设备合并换键而非插入新卡。
     */
    val supersededAnimeIds: List<String>? = null
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
    val finishDate: String? = null,
    /** 行键迁移映射（服务端下发）：本行取代的旧行键，客户端合并时据此换键合并 */
    val supersededAnimeIds: List<String>? = null,
    /** 服务端权威订阅 ID（v2 下发；v1 兼容响应也会带上） */
    val subscriptionId: String? = null,
    /** 服务端写入版本号 */
    val revision: Int? = null,
    /** 服务端最后写入时间（ISO 8601） */
    val updatedAt: String? = null,
    /** 客户端最后有效修改时间（ISO 8601，LWW 依据） */
    val clientUpdatedAt: String? = null
)

data class SubscriptionsResponse(
    val success: Boolean,
    val subscriptions: List<Subscription>? = null,
    val message: String? = null
)

// ========== 番剧订阅 v2（服务端订阅身份） ==========

data class SubscriptionV2Identity(
    val bangumiId: Int? = null,
    val remoteSyncId: String? = null
)

data class SubscriptionV2SupersededKey(
    /** bangumi / remote_sync */
    val type: String,
    val value: String
)

/**
 * 服务端下发的带类型别名：bangumi / remote_sync / legacy_local。
 *
 * 兼容旧版服务端：部分线上版本下发的是原始键字符串数组（如 `["3","uuid"]`），
 * 由 [SubscriptionV2AliasAdapter] 推断类型；无法识别的值解析为 [TYPE_UNKNOWN]。
 */
@JsonAdapter(SubscriptionV2AliasAdapter::class)
data class SubscriptionV2Alias(
    val type: String,
    val value: String
) {
    companion object {
        const val TYPE_BANGUMI = "bangumi"
        const val TYPE_REMOTE_SYNC = "remote_sync"
        const val TYPE_LEGACY_LOCAL = "legacy_local"

        /** 旧格式里无法安全推断的值：调用方按未知类型跳过，绝不能拿去匹配本地卡片 */
        const val TYPE_UNKNOWN = ""
    }
}

data class SubscriptionV2Data(
    val animeTitle: String,
    val animeImage: String? = null,
    val airDate: String? = null,
    val isAiring: Boolean? = null,
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

data class SubscriptionV2UpsertRequest(
    val subscriptionId: String? = null,
    /** add = 用户显式添加（可绕过墓碑）；sync = 常规同步 */
    val intent: String? = null,
    val identity: SubscriptionV2Identity,
    /** 旧本地自增 id 等一次性键，服务端不会写入 alias 表 */
    val legacyKeys: List<String>? = null,
    /** 客户端明确上报的已换绑旧键（旧 bangumiId / remoteSyncId） */
    val supersededKeys: List<SubscriptionV2SupersededKey>? = null,
    /** 本地最后有效修改时间（ISO 8601）；服务端据此做 LWW */
    val clientUpdatedAt: String? = null,
    val progressReset: Boolean? = null,
    val legacyTitleConvergence: Boolean? = null,
    /** 客户端上次看到的 revision，仅用于服务端诊断，不参与拒绝写入 */
    val baseRevision: Int? = null,
    val data: SubscriptionV2Data
)

data class SubscriptionV2DeleteRequest(
    val subscriptionId: String? = null,
    val identity: SubscriptionV2Identity? = null,
    val legacyKeys: List<String>? = null,
    val supersededKeys: List<SubscriptionV2SupersededKey>? = null
)

data class SubscriptionV2MergedItem(
    val mergedSubscriptionId: String,
    /** 被合并行的旧键，客户端用它清理本地重复卡片 */
    val aliases: List<SubscriptionV2Alias>? = null
)

data class SubscriptionV2ConflictKey(
    val type: String,
    val value: String
)

data class SubscriptionV2ConflictExisting(
    val type: String,
    val subscriptionId: String
)

data class SubscriptionV2Conflict(
    val key: SubscriptionV2ConflictKey,
    val existing: SubscriptionV2ConflictExisting
)

data class SubscriptionV2UpsertResponse(
    val success: Boolean,
    val subscriptionId: String? = null,
    val revision: Int? = null,
    /** 服务端诊断：客户端 baseRevision 是否与写入前的 revision 一致 */
    val revisionMatched: Boolean? = null,
    val applied: Boolean? = null,
    val ignored: Boolean? = null,
    val canonical: Subscription? = null,
    val merged: List<SubscriptionV2MergedItem>? = null,
    val conflicts: List<SubscriptionV2Conflict>? = null,
    val message: String? = null
)

data class SubscriptionV2DeleteResponse(
    val success: Boolean,
    val removed: Boolean? = null,
    val subscriptionId: String? = null,
    val message: String? = null
)

data class SubscriptionV2Tombstone(
    val subscriptionId: String,
    /** 带类型的别名；legacy_local 只在标题一致时采信 */
    val aliases: List<SubscriptionV2Alias>? = null,
    val deletedAt: String? = null,
    /** 删除快照里的标题，用于旧本地 id 的二次确认 */
    val animeTitle: String? = null
)

data class SubscriptionsV2Response(
    val success: Boolean,
    val apiVersion: Int? = null,
    val subscriptions: List<Subscription>? = null,
    val tombstones: List<SubscriptionV2Tombstone>? = null,
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

    // ========== 注销账号（宽限期软删除） ==========

    /** 发起注销（需登录态；服务端撤销全部会话，所有设备被下线） */
    @POST("user/delete-account")
    suspend fun deleteAccount(
        @Body request: DeleteAccountRequest
    ): DeleteAccountResponse

    /** 恢复账号（注销宽限期内，密码验证通过即恢复并完成登录） */
    @POST("auth/restore-account")
    suspend fun restoreAccount(
        @Body request: UserAuthLoginRequest
    ): UserAuthLoginResponse

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

    @GET("v2/subscriptions/list")
    suspend fun getSubscriptionsV2(): SubscriptionsV2Response

    @POST("v2/subscriptions/upsert")
    suspend fun upsertSubscriptionV2(
        @Body request: SubscriptionV2UpsertRequest
    ): SubscriptionV2UpsertResponse

    @POST("v2/subscriptions/delete")
    suspend fun deleteSubscriptionV2(
        @Body request: SubscriptionV2DeleteRequest
    ): SubscriptionV2DeleteResponse

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

/**
 * 从 HttpException 错误响应中一次性解析完整 JSON 对象。
 * 注意：OkHttp 的 errorBody 只能消费一次，需要同时取多个字段
 * （如 code + purgeAt + message）时必须用本函数一次解析后再取值，
 * 不能连续调用 serverMessage / serverErrorCode / serverField（第二次读取会得到空）。
 */
fun HttpException.serverErrorJson(): com.google.gson.JsonObject? = try {
    val body = response()?.errorBody()?.string()
    body?.let {
        runCatching {
            com.google.gson.JsonParser.parseString(it).asJsonObject
        }.getOrNull()
    }
} catch (_: Exception) {
    null
}

/**
 * 从 HttpException 错误响应中解析服务端返回的业务 code 字段
 * （如登录时 403 的 DELETION_PENDING / DELETION_EXPIRED），失败时返回 null
 */
fun HttpException.serverErrorCode(): String? = try {
    val body = response()?.errorBody()?.string()
    body?.let {
        runCatching {
            val json = com.google.gson.JsonParser.parseString(it).asJsonObject
            if (json.has("code")) json.get("code").asString else null
        }.getOrNull()
    }
} catch (_: Exception) {
    null
}

/** 从 HttpException 错误响应中解析服务端返回的任意字符串字段（如 purgeAt） */
fun HttpException.serverField(name: String): String? = try {
    val body = response()?.errorBody()?.string()
    body?.let {
        runCatching {
            val json = com.google.gson.JsonParser.parseString(it).asJsonObject
            if (json.has(name) && !json.get(name).isJsonNull) json.get(name).asString else null
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
    val createdAt: String?,
    /** 该公告的投票选项数量（列表接口返回，未登录时也能据此判断是否展示投票入口） */
    @SerializedName("option_count")
    val optionCount: Int = 0
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
