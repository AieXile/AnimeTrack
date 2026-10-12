package com.aiexile.animetrack.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// ========== 管理员反馈接口 DTO ==========
// 契约见服务端 server.js「反馈管理接口（管理员）」：
// 鉴权双通道——X-Admin-Key（网页后台）或 Bearer token + 服务端 ADMIN_USER_IDS 白名单（App）。
// 与用户端同源同鉴权（Bearer + 401 自动刷新 + URL 重写），复用 userAuthRetrofit 链路。

/** 当前登录用户身份（GET auth/identity）：App 端据此显隐「反馈管理」入口 */
data class IdentityResponse(
    val success: Boolean = false,
    val userId: Long? = null,
    val username: String? = null,
    /** 是否在服务端管理员白名单内 */
    val isAdmin: Boolean = false
)

/** 管理端反馈会话摘要（列表项） */
data class AdminFeedbackSession(
    @SerializedName(value = "sessionId", alternate = ["session_id"])
    val sessionId: String,
    val title: String? = null,
    /** 反馈提交者的用户名 */
    val username: String? = null,
    @SerializedName(value = "lastMessage", alternate = ["last_message"])
    val lastMessage: String? = null,
    @SerializedName(value = "messageCount", alternate = ["message_count"])
    val messageCount: Int = 0,
    /** open / pending / resolved / closed */
    val status: String = "open",
    @SerializedName(value = "createdAt", alternate = ["created_at"])
    val createdAt: String? = null,
    @SerializedName(value = "updatedAt", alternate = ["updated_at"])
    val updatedAt: String? = null
)

data class AdminFeedbackSessionsResponse(
    val success: Boolean = false,
    val sessions: List<AdminFeedbackSession> = emptyList(),
    val hasMore: Boolean = false,
    val total: Int = 0,
    val message: String? = null
)

/** 管理端会话详情（比用户端多用户信息字段） */
data class AdminFeedbackSessionInfo(
    @SerializedName(value = "sessionId", alternate = ["session_id"])
    val sessionId: String,
    val title: String? = null,
    val status: String = "open",
    val username: String? = null,
    val email: String? = null,
    /** 用户贡献等级 */
    val level: Int = 0,
    @SerializedName(value = "createdAt", alternate = ["created_at"])
    val createdAt: String? = null,
    @SerializedName(value = "updatedAt", alternate = ["updated_at"])
    val updatedAt: String? = null
)

data class AdminFeedbackSessionDetailResponse(
    val success: Boolean = false,
    val session: AdminFeedbackSessionInfo? = null,
    /** 消息结构同用户端 FeedbackMessage */
    val messages: List<FeedbackMessage> = emptyList(),
    val message: String? = null
)

/** 管理员回复请求体 */
data class AdminFeedbackReplyRequest(
    val content: String
)

data class AdminFeedbackReplyResponse(
    val success: Boolean = false,
    /** 回复消息（结构同用户端 FeedbackMessage） */
    val reply: FeedbackMessage? = null,
    /** 错误码：SESSION_CLOSED / SESSION_RESOLVED / SESSION_NOT_FOUND / INVALID_INPUT / CONTENT_TOO_LONG */
    val code: String? = null,
    val message: String? = null
)

/** 会话状态流转请求体 */
data class AdminFeedbackStatusRequest(
    val status: String
)

data class AdminFeedbackStatusResponse(
    val success: Boolean = false,
    @SerializedName(value = "sessionId", alternate = ["session_id"])
    val sessionId: String? = null,
    val status: String? = null,
    /** 错误码：INVALID_STATUS / SESSION_CLOSED / SESSION_RESOLVED / SESSION_NOT_FOUND */
    val code: String? = null,
    val message: String? = null
)

/** 授予/调整用户贡献等级请求体：delta 非零整数，绝对值不超过 999 */
data class AdminFeedbackLevelRequest(
    val delta: Int
)

data class AdminFeedbackLevelResponse(
    val success: Boolean = false,
    /** 调整后的用户等级 */
    val level: Int = 0,
    /** 错误码：INVALID_INPUT / SESSION_NOT_FOUND */
    val code: String? = null,
    val message: String? = null
)

/**
 * 管理员反馈 API：会话列表/详情/回复/状态流转/授等级 + 身份识别。
 * 全部经 Bearer token 鉴权，服务端校验 ADMIN_USER_IDS 白名单。
 */
interface AdminFeedbackApiService {

    /** 当前用户身份（是否在管理员白名单，白名单由服务端配置） */
    @GET("auth/identity")
    suspend fun getIdentity(): IdentityResponse

    /** 反馈会话列表（status 不传查全部；分页按 updatedAt 倒序） */
    @GET("feedback/admin/sessions")
    suspend fun getSessions(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): AdminFeedbackSessionsResponse

    /** 会话详情（含用户名/邮箱/等级，消息按 id 正序） */
    @GET("feedback/admin/sessions/{sessionId}")
    suspend fun getSessionDetail(
        @Path("sessionId") sessionId: String
    ): AdminFeedbackSessionDetailResponse

    /** 回复反馈（追加 assistant 消息，会话自动转为 pending，并 SSE 通知用户） */
    @POST("feedback/admin/sessions/{sessionId}/reply")
    suspend fun reply(
        @Path("sessionId") sessionId: String,
        @Body request: AdminFeedbackReplyRequest
    ): AdminFeedbackReplyResponse

    /** 修改会话状态（open / pending / closed / resolved；closed / resolved 为终态不可再变更） */
    @PUT("feedback/admin/sessions/{sessionId}/status")
    suspend fun updateStatus(
        @Path("sessionId") sessionId: String,
        @Body request: AdminFeedbackStatusRequest
    ): AdminFeedbackStatusResponse

    /** 授予/调整用户贡献等级（自动插入回执消息并 SSE 通知用户） */
    @POST("feedback/admin/sessions/{sessionId}/level")
    suspend fun grantLevel(
        @Path("sessionId") sessionId: String,
        @Body request: AdminFeedbackLevelRequest
    ): AdminFeedbackLevelResponse
}
