package com.aiexile.animetrack.data.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// ========== 反馈接口 DTO ==========
// 契约文档见 specs/feedback-chat/SPEC.md「四、后端接口契约」

/** 反馈消息角色：用户 / 助手（AI 或管理员回复）/ 系统提示 */
const val FEEDBACK_ROLE_USER = "user"
const val FEEDBACK_ROLE_ASSISTANT = "assistant"
const val FEEDBACK_ROLE_SYSTEM = "system"

/** 会话状态：进行中（可追加）/ 已回复待确认（可追加）/ 已结束（不可追加） */
const val FEEDBACK_STATUS_OPEN = "open"
const val FEEDBACK_STATUS_PENDING = "pending"
const val FEEDBACK_STATUS_CLOSED = "closed"

/** 服务端错误码 */
const val FEEDBACK_CODE_RATE_LIMITED = "RATE_LIMITED"
const val FEEDBACK_CODE_SENSITIVE = "SENSITIVE_CONTENT"
const val FEEDBACK_CODE_SESSION_CLOSED = "SESSION_CLOSED"
const val FEEDBACK_CODE_SESSION_NOT_FOUND = "SESSION_NOT_FOUND"

/** 附件类型 */
const val FEEDBACK_ATTACHMENT_TYPE_IMAGE = "image"
const val FEEDBACK_ATTACHMENT_TYPE_FILE = "file"
const val FEEDBACK_ATTACHMENT_TYPE_LOG = "log"

/** 发送反馈请求体：sessionId 为空 = 新建会话；非空 = 在该会话内追加 */
data class FeedbackSendRequest(
    val sessionId: String? = null,
    val content: String,
    /** 已上传附件引用（无附件时为 null，Gson 不序列化） */
    val attachments: List<FeedbackAttachmentRef>? = null
)

/** 附件引用：附件先经 /feedback/attachments 上传，拿 id 后随消息提交 */
data class FeedbackAttachmentRef(
    val id: String,
    /** image / file / log */
    val type: String
)

/** 上传附件响应 */
data class FeedbackAttachmentUploaded(
    val id: String,
    val url: String? = null,
    @SerializedName(value = "file_name", alternate = ["fileName"])
    val fileName: String? = null,
    @SerializedName(value = "file_size", alternate = ["fileSize"])
    val sizeBytes: Long = 0,
    @SerializedName(value = "mime_type", alternate = ["mimeType"])
    val mimeType: String? = null
)

data class FeedbackUploadResponse(
    val success: Boolean,
    val attachment: FeedbackAttachmentUploaded? = null,
    /** 错误码：FILE_TOO_LARGE / TYPE_NOT_ALLOWED / RATE_LIMITED */
    val code: String? = null,
    val message: String? = null
)

/** 消息附件（服务端返回完整信息，url 为绝对地址可直接加载） */
data class FeedbackMessageAttachment(
    val id: String,
    /** image / file / log */
    val type: String,
    val url: String? = null,
    @SerializedName(value = "file_name", alternate = ["fileName"])
    val fileName: String? = null,
    @SerializedName(value = "file_size", alternate = ["fileSize", "sizeBytes"])
    val sizeBytes: Long = 0,
    @SerializedName(value = "mime_type", alternate = ["mimeType"])
    val mimeType: String? = null
)

/** 单条反馈消息 */
data class FeedbackMessage(
    val id: Long,
    /** user / assistant / system */
    val role: String,
    val content: String,
    /** 消息附件（无附件时为空列表） */
    val attachments: List<FeedbackMessageAttachment> = emptyList(),
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null
)

/** 发送反馈响应：reply 为 null 表示回复异步生成，客户端展示占位提示 */
data class FeedbackSendResponse(
    val success: Boolean,
    @SerializedName(value = "sessionId", alternate = ["session_id"])
    val sessionId: String? = null,
    @SerializedName(value = "userMessage", alternate = ["user_message"])
    val userMessage: FeedbackMessage? = null,
    val reply: FeedbackMessage? = null,
    /** 错误码：SENSITIVE_CONTENT / SESSION_CLOSED / CONTENT_TOO_LONG / RATE_LIMITED */
    val code: String? = null,
    /** 限流场景下的重试等待秒数（429 时返回） */
    @SerializedName(value = "retryAfter", alternate = ["retry_after"])
    val retryAfter: Int? = null,
    val message: String? = null
)

/** 反馈会话摘要（历史列表项） */
data class FeedbackSession(
    @SerializedName(value = "sessionId", alternate = ["session_id"])
    val sessionId: String,
    val title: String? = null,
    @SerializedName(value = "lastMessage", alternate = ["last_message"])
    val lastMessage: String? = null,
    @SerializedName(value = "messageCount", alternate = ["message_count"])
    val messageCount: Int = 0,
    /** open（可追加）/ closed（已关闭） */
    val status: String = "open",
    /** 有未读的管理员回复（最新消息是 assistant 且晚于上次已读时间） */
    @SerializedName(value = "hasNewReply", alternate = ["has_new_reply"])
    val hasNewReply: Boolean = false,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null,
    @SerializedName(value = "updated_at", alternate = ["updatedAt"])
    val updatedAt: String? = null
)

data class FeedbackSessionsResponse(
    val success: Boolean,
    val sessions: List<FeedbackSession> = emptyList(),
    @SerializedName(value = "hasMore", alternate = ["has_more"])
    val hasMore: Boolean = false,
    val message: String? = null
)

/** 会话详情响应 */
data class FeedbackSessionDetailResponse(
    val success: Boolean,
    val session: FeedbackSession? = null,
    val messages: List<FeedbackMessage> = emptyList(),
    val message: String? = null
)

/** 已读标记 / 未读检查响应 */
data class FeedbackUnreadResponse(
    val success: Boolean,
    val hasNew: Boolean = false
)

/**
 * 反馈 API：与用户体系同源同鉴权（Bearer Token，401 自动刷新由 UserAuthInterceptor 处理）。
 * 后端实现契约见 specs/feedback-chat/SPEC.md。
 */
interface FeedbackApiService {

    /** 上传附件（multipart/form-data）：file=二进制，kind=image|file|log */
    @Multipart
    @POST("feedback/attachments")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part,
        @Part("kind") kind: RequestBody
    ): FeedbackUploadResponse

    /** 发送反馈（新建会话或追加） */
    @POST("feedback/messages")
    suspend fun sendMessage(
        @Body request: FeedbackSendRequest
    ): FeedbackSendResponse

    /** 会话历史列表（分页，按 updatedAt 倒序） */
    @GET("feedback/sessions")
    suspend fun getSessions(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): FeedbackSessionsResponse

    /** 会话详情（消息记录，按 id 正序） */
    @GET("feedback/sessions/{sessionId}")
    suspend fun getSessionDetail(
        @Path("sessionId") sessionId: String
    ): FeedbackSessionDetailResponse

    /** 标记反馈已读（进入任一反馈界面时调用，清除红点） */
    @POST("feedback/read")
    suspend fun markRead(): FeedbackUnreadResponse

    /** 是否有未读的管理员回复（红点/主页提示用） */
    @GET("feedback/unread")
    suspend fun getUnread(): FeedbackUnreadResponse
}
