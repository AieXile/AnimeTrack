package com.aiexile.animetrack.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.FEEDBACK_ATTACHMENT_TYPE_IMAGE
import com.aiexile.animetrack.data.network.FEEDBACK_CODE_RATE_LIMITED
import com.aiexile.animetrack.data.network.FEEDBACK_CODE_SENSITIVE
import com.aiexile.animetrack.data.network.FEEDBACK_CODE_SESSION_CLOSED
import com.aiexile.animetrack.data.network.FEEDBACK_CODE_SESSION_NOT_FOUND
import com.aiexile.animetrack.data.network.FeedbackApiService
import com.aiexile.animetrack.data.network.FeedbackAttachmentRef
import com.aiexile.animetrack.data.network.FeedbackMessage
import com.aiexile.animetrack.data.network.FeedbackSendRequest
import com.aiexile.animetrack.data.network.FeedbackSendResponse
import com.aiexile.animetrack.data.network.FeedbackSession
import com.aiexile.animetrack.data.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

private val Context.feedbackRateLimitDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "feedback_rate_limit"
)

/** 客户端风控结果 */
sealed class FeedbackSendResult {
    /** 发送成功：reply 为 null 表示服务端异步生成回复 */
    data class Success(
        val sessionId: String,
        val userMessage: FeedbackMessage?,
        val reply: FeedbackMessage?
    ) : FeedbackSendResult()

    /** 需再等 retryAfterSeconds 秒才能发送 */
    data class RateLimited(val retryAfterSeconds: Int) : FeedbackSendResult()

    /** 命中敏感词（客户端预检或服务端拦截） */
    data object SensitiveContent : FeedbackSendResult()

    /** 会话已关闭，不能追加 */
    data object SessionClosed : FeedbackSendResult()

    /** 会话不存在（可能已被删除） */
    data object SessionNotFound : FeedbackSendResult()

    /** 附件上传失败 */
    data object AttachmentUploadFailed : FeedbackSendResult()

    /** 登录已过期（access/refresh token 均失效），需重新登录 */
    data object AuthExpired : FeedbackSendResult()

    /** 网络/服务异常 */
    data class Error(val throwable: Throwable? = null) : FeedbackSendResult()
}

/** 待上传附件（本地选择，发送时先上传再随消息提交引用） */
data class PendingAttachment(
    /** content:// Uri 字符串 */
    val uriString: String,
    val fileName: String,
    val mimeType: String?,
    /** image / file / log */
    val kind: String
)

data class FeedbackSessionsPage(
    val sessions: List<FeedbackSession>,
    val hasMore: Boolean
)

/**
 * 反馈仓库：API 封装 + 客户端风控（发送节流 + 敏感词预检）。
 * 限流时间戳持久化于 DataStore，App 重启不重置；服务端仍为最终兜底。
 */
class FeedbackRepository(private val context: Context) {

    companion object {
        /** 应用日志输出标签 */
        private const val LOG_TAG = "Feedback"

        /** 两次发送最小间隔 */
        private const val MIN_SEND_INTERVAL_MS = 15_000L

        /** 滚动 1 小时窗口内最大发送条数 */
        private const val HOURLY_LIMIT = 10

        private const val WINDOW_MS = 60 * 60 * 1000L

        private val SEND_TIMESTAMPS_KEY = stringSetPreferencesKey("send_timestamps")

        /** 单条内容长度上限（与后端契约 CONTENT_TOO_LONG 对齐） */
        const val MAX_CONTENT_LENGTH = 2000

        /** 单条消息最多附件数 */
        const val MAX_ATTACHMENTS_PER_MESSAGE = 5

        /** 图片大小上限 10MB */
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

        /** 文件/日志大小上限 20MB */
        const val MAX_FILE_BYTES = 20L * 1024 * 1024

        /** MB 字节数（提示文案换算用） */
        const val MB_PER_BYTE = 1024L * 1024

        /** 查询本地附件元信息：返回 (文件名, 字节数，未知为 -1)，无法读取时 null */
        fun readAttachmentMeta(context: Context, uri: Uri): Pair<String, Long>? {
            return try {
                var name = "file"
                var size = -1L
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.let { name = it }
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
                name to size
            } catch (_: Exception) {
                null
            }
        }
    }

    private val api: FeedbackApiService by lazy { RetrofitClient.feedbackApi }

    // ===== 客户端限流 =====

    /**
     * 发送前风控检查：读取 DataStore 中最近发送时间戳（滚动窗口内），
     * 返回 null 表示放行，否则返回需等待的秒数。
     */
    private suspend fun checkRateLimit(nowMs: Long): Int? {
        val prefs = context.feedbackRateLimitDataStore.data.first()
        val timestamps = prefs[SEND_TIMESTAMPS_KEY]
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
            .filter { nowMs - it < WINDOW_MS }
        val last = timestamps.maxOrNull()
        if (last != null) {
            val elapsed = nowMs - last
            if (elapsed < MIN_SEND_INTERVAL_MS) {
                return ((MIN_SEND_INTERVAL_MS - elapsed) / 1000L).toInt() + 1
            }
        }
        if (timestamps.size >= HOURLY_LIMIT) {
            // 距窗口内最早一条滑出还需多久
            val earliest = timestamps.min()
            val waitMs = WINDOW_MS - (nowMs - earliest)
            return ((waitMs / 1000L).toInt()).coerceAtLeast(1)
        }
        return null
    }

    /** 发送成功后记录时间戳（同时清理窗口外旧数据） */
    private suspend fun recordSend(nowMs: Long) {
        context.feedbackRateLimitDataStore.edit { prefs ->
            val kept = prefs[SEND_TIMESTAMPS_KEY]
                ?.mapNotNull { it.toLongOrNull() }
                .orEmpty()
                .filter { nowMs - it < WINDOW_MS }
            prefs[SEND_TIMESTAMPS_KEY] = (kept + nowMs).map { it.toString() }.toSet()
        }
    }

    // ===== 敏感词预检（起步方案：内置基础词库，服务端为最终兜底） =====

    private val sensitiveWords = setOf(
        // 政治类
        "法轮功", "达赖", "台独", "港独", "疆独", "藏独", "六四", "习近平下台",
        // 色情类
        "嫖娼", "卖淫", "约炮", "援交", "裸聊", "淫秽",
        // 赌毒
        "赌博", "博彩", "网赌", "冰毒", "摇头丸", "贩毒", "大麻",
        // 违规服务
        "办证", "代开发票", "外挂", "刷单", "洗钱",
        // 辱骂
        "傻逼", "煞笔", "妈的", "他妈的", "狗日", "杂种", "婊子", "贱人", "去死"
    )

    /** 命中敏感词时返回首个命中词，否则 null */
    fun findSensitiveWord(content: String): String? {
        val normalized = content.replace(Regex("[\\s\\p{Punct}*·•]"), "")
        return sensitiveWords.firstOrNull { normalized.contains(it, ignoreCase = true) }
    }

    // ===== 对外操作 =====

    /** 标记反馈已读（进入任一反馈界面时调用，失败静默不影响主流程） */
    suspend fun markRead() {
        try {
            api.markRead()
        } catch (_: Exception) {
        }
    }

    /** 是否有未读回复（设置页红点/主页提示用，失败按无未读处理） */
    suspend fun hasNewReplies(): Boolean = try {
        api.getUnread().hasNew
    } catch (_: Exception) {
        false
    }

    /**
     * 发送反馈：本地限流 → 敏感词预检 → 附件上传 → 调用服务端。
     * 服务端 429/400 错误码映射为对应结果类型。
     * 结果统一写入应用日志（不含反馈内容，仅记录结果类型与附件数）。
     */
    suspend fun send(
        content: String,
        sessionId: String? = null,
        attachments: List<PendingAttachment> = emptyList()
    ): FeedbackSendResult {
        val result = doSend(content, sessionId, attachments)
        AppLogManager.i(
            LOG_TAG,
            "反馈发送结果: ${result::class.simpleName}, 附件=${attachments.size}, 会话=${sessionId ?: "新建"}"
        )
        return result
    }

    private suspend fun doSend(
        content: String,
        sessionId: String?,
        attachments: List<PendingAttachment>
    ): FeedbackSendResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return FeedbackSendResult.Error()
        if (trimmed.length > MAX_CONTENT_LENGTH) return FeedbackSendResult.Error()

        // 1. 客户端限流
        val now = System.currentTimeMillis()
        checkRateLimit(now)?.let { return FeedbackSendResult.RateLimited(it) }

        // 2. 敏感词预检
        if (findSensitiveWord(trimmed) != null) return FeedbackSendResult.SensitiveContent

        // 3. 附件逐个上传（任一失败整体失败，保留本地附件供重试）
        val refs = mutableListOf<FeedbackAttachmentRef>()
        for (attachment in attachments) {
            val ref = uploadAttachment(attachment)
            if (ref == null) return FeedbackSendResult.AttachmentUploadFailed
            refs += ref
        }

        // 4. 服务端
        return try {
            val response = api.sendMessage(
                FeedbackSendRequest(
                    sessionId = sessionId,
                    content = trimmed,
                    attachments = refs.ifEmpty { null }
                )
            )
            if (response.success) {
                recordSend(System.currentTimeMillis())
                val sid = response.sessionId ?: sessionId
                if (sid == null) FeedbackSendResult.Error()
                else FeedbackSendResult.Success(sid, response.userMessage, response.reply)
            } else {
                when (response.code) {
                    FEEDBACK_CODE_RATE_LIMITED ->
                        FeedbackSendResult.RateLimited(response.retryAfter ?: 30)
                    FEEDBACK_CODE_SENSITIVE -> FeedbackSendResult.SensitiveContent
                    FEEDBACK_CODE_SESSION_CLOSED -> FeedbackSendResult.SessionClosed
                    else -> FeedbackSendResult.Error()
                }
            }
        } catch (e: HttpException) {
            mapHttpException(e)
        } catch (e: IOException) {
            FeedbackSendResult.Error(e)
        } catch (e: Exception) {
            FeedbackSendResult.Error(e)
        }
    }

    /**
     * 解析服务端 HTTP 4xx 错误响应（错误码在 body 中，如 400 SENSITIVE_CONTENT / 429 RATE_LIMITED），
     * 映射为对应的业务结果；401/403 表示登录态失效。
     */
    private fun mapHttpException(e: HttpException): FeedbackSendResult = try {
        val body = e.response()?.errorBody()?.string()
        val error = body?.takeIf { it.isNotBlank() }
            ?.let { Gson().fromJson(it, FeedbackSendResponse::class.java) }
        when (error?.code) {
            FEEDBACK_CODE_RATE_LIMITED ->
                FeedbackSendResult.RateLimited(error.retryAfter ?: 30)
            FEEDBACK_CODE_SENSITIVE -> FeedbackSendResult.SensitiveContent
            FEEDBACK_CODE_SESSION_CLOSED -> FeedbackSendResult.SessionClosed
            FEEDBACK_CODE_SESSION_NOT_FOUND -> FeedbackSendResult.SessionNotFound
            else -> when (e.code()) {
                // 401 未登录 / 403 token 过期且刷新失败：登录已过期
                401, 403 -> FeedbackSendResult.AuthExpired
                // 404 会话不存在
                404 -> FeedbackSendResult.SessionNotFound
                else -> FeedbackSendResult.Error(e)
            }
        }
    } catch (_: Exception) {
        FeedbackSendResult.Error(e)
    }

    /** 上传单个附件，失败返回 null。支持 content:// URI 与本地文件路径（日志导出产物） */
    private suspend fun uploadAttachment(attachment: PendingAttachment): FeedbackAttachmentRef? {
        return try {
            val bytes = if (attachment.uriString.startsWith("content:")) {
                context.contentResolver.openInputStream(Uri.parse(attachment.uriString))
                    ?.use { it.readBytes() }
            } else {
                val file = java.io.File(attachment.uriString)
                if (file.exists()) file.readBytes() else null
            } ?: run {
                AppLogManager.w(LOG_TAG, "附件读取失败: ${attachment.fileName}")
                return null
            }
            val mediaType = (attachment.mimeType ?: "application/octet-stream").toMediaTypeOrNull()
            val filePart = MultipartBody.Part.createFormData(
                "file", attachment.fileName, bytes.toRequestBody(mediaType)
            )
            val kindPart = attachment.kind.toRequestBody("text/plain".toMediaTypeOrNull())
            val response = api.uploadAttachment(filePart, kindPart)
            if (response.success && response.attachment != null) {
                FeedbackAttachmentRef(response.attachment.id, attachment.kind)
            } else {
                AppLogManager.w(LOG_TAG, "附件上传被拒: ${attachment.fileName}, kind=${attachment.kind}")
                null
            }
        } catch (e: Exception) {
            AppLogManager.w(LOG_TAG, "附件上传异常: ${attachment.fileName}", e)
            null
        }
    }

    /** 会话历史列表（分页） */
    suspend fun getSessions(page: Int = 1, pageSize: Int = 20): FeedbackSessionsPage? = try {
        val response = api.getSessions(page, pageSize)
        if (response.success) FeedbackSessionsPage(response.sessions, response.hasMore) else null
    } catch (_: Exception) {
        null
    }

    /** 会话详情：返回 (会话信息, 消息列表)，失败返回 null */
    suspend fun getSessionDetail(sessionId: String): Pair<FeedbackSession, List<FeedbackMessage>>? = try {
        val response = api.getSessionDetail(sessionId)
        if (response.success && response.session != null) {
            response.session to response.messages
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}
