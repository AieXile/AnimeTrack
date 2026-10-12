package com.aiexile.animetrack.data.remote

import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.AdminFeedbackLevelRequest
import com.aiexile.animetrack.data.network.AdminFeedbackLevelResponse
import com.aiexile.animetrack.data.network.AdminFeedbackReplyRequest
import com.aiexile.animetrack.data.network.AdminFeedbackReplyResponse
import com.aiexile.animetrack.data.network.AdminFeedbackSession
import com.aiexile.animetrack.data.network.AdminFeedbackSessionDetailResponse
import com.aiexile.animetrack.data.network.AdminFeedbackSessionInfo
import com.aiexile.animetrack.data.network.AdminFeedbackSessionsResponse
import com.aiexile.animetrack.data.network.AdminFeedbackStatusRequest
import com.aiexile.animetrack.data.network.AdminFeedbackStatusResponse
import com.aiexile.animetrack.data.network.AdminFeedbackApiService
import com.aiexile.animetrack.data.network.FEEDBACK_CODE_SESSION_CLOSED
import com.aiexile.animetrack.data.network.FEEDBACK_CODE_SESSION_NOT_FOUND
import com.aiexile.animetrack.data.network.FEEDBACK_CODE_SESSION_RESOLVED
import com.aiexile.animetrack.data.network.FeedbackMessage
import com.aiexile.animetrack.data.network.RetrofitClient
import com.google.gson.Gson
import retrofit2.HttpException

/** 管理员身份识别结果 */
sealed class AdminIdentityResult {
    /** 管理员（服务端白名单内） */
    data object Admin : AdminIdentityResult()

    /** 普通用户（入口不显示） */
    data object NotAdmin : AdminIdentityResult()

    /** 查询失败（网络/登录态失效，按非管理员处理） */
    data object Error : AdminIdentityResult()
}

/** 管理员回复结果 */
sealed class AdminReplyResult {
    /** 回复成功：reply 为服务端确认的消息 */
    data class Success(val reply: FeedbackMessage) : AdminReplyResult()
    data object SessionClosed : AdminReplyResult()
    data object SessionResolved : AdminReplyResult()
    data object SessionNotFound : AdminReplyResult()
    data object AuthExpired : AdminReplyResult()
    data object NoPermission : AdminReplyResult()
    data class Error(val throwable: Throwable? = null) : AdminReplyResult()
}

/** 状态流转 / 授等级共用结果 */
sealed class AdminOperationResult {
    /** 操作成功（授等级成功时携带新等级） */
    data class Success(val newLevel: Int? = null) : AdminOperationResult()
    data object SessionClosed : AdminOperationResult()
    data object SessionResolved : AdminOperationResult()
    data object SessionNotFound : AdminOperationResult()
    data object InvalidInput : AdminOperationResult()
    data object AuthExpired : AdminOperationResult()
    data object NoPermission : AdminOperationResult()
    data class Error(val throwable: Throwable? = null) : AdminOperationResult()
}

data class AdminFeedbackSessionsPage(
    val sessions: List<AdminFeedbackSession>,
    val hasMore: Boolean,
    val total: Int
)

/**
 * 管理员反馈仓库：封装管理端会话列表/详情/回复/状态流转/授等级。
 * 鉴权由服务端 ADMIN_USER_IDS 白名单兜底（401/403 映射为 NoPermission/AuthExpired）。
 */
class AdminFeedbackRepository {

    companion object {
        private const val LOG_TAG = "AdminFeedback"

        /** 单次授等级调整上限（与服务端校验对齐） */
        const val MAX_LEVEL_DELTA = 999
    }

    private val api: AdminFeedbackApiService = RetrofitClient.adminFeedbackApi

    /** 当前登录用户是否管理员（轻量接口，调用方自行缓存结果） */
    suspend fun getIdentity(): AdminIdentityResult = try {
        val response = api.getIdentity()
        if (response.success) {
            if (response.isAdmin) AdminIdentityResult.Admin else AdminIdentityResult.NotAdmin
        } else {
            AdminIdentityResult.Error
        }
    } catch (e: HttpException) {
        if (e.code() == 401 || e.code() == 403) AdminIdentityResult.Error else AdminIdentityResult.Error
    } catch (_: Exception) {
        AdminIdentityResult.Error
    }

    /** 反馈会话列表（status 为 null 查全部；分页按 updatedAt 倒序），失败返回 null */
    suspend fun getSessions(
        status: String?,
        page: Int = 1,
        pageSize: Int = 20
    ): AdminFeedbackSessionsPage? = try {
        val response: AdminFeedbackSessionsResponse = api.getSessions(status, page, pageSize)
        if (response.success) {
            AdminFeedbackSessionsPage(response.sessions, response.hasMore, response.total)
        } else {
            null
        }
    } catch (e: Exception) {
        AppLogManager.w(LOG_TAG, "管理端获取反馈列表失败: page=$page status=$status", e)
        null
    }

    /** 会话详情：返回 (会话信息, 消息列表)，失败返回 null */
    suspend fun getSessionDetail(
        sessionId: String
    ): Pair<AdminFeedbackSessionInfo, List<FeedbackMessage>>? = try {
        val response: AdminFeedbackSessionDetailResponse = api.getSessionDetail(sessionId)
        if (response.success && response.session != null) {
            response.session to response.messages
        } else {
            null
        }
    } catch (e: Exception) {
        AppLogManager.w(LOG_TAG, "管理端获取会话详情失败: $sessionId", e)
        null
    }

    /** 回复反馈（追加 assistant 消息，服务端自动转 pending 并 SSE 通知用户） */
    suspend fun reply(sessionId: String, content: String): AdminReplyResult = try {
        val response: AdminFeedbackReplyResponse = api.reply(
            sessionId, AdminFeedbackReplyRequest(content.trim())
        )
        if (response.success && response.reply != null) {
            AdminReplyResult.Success(response.reply)
        } else {
            AdminReplyResult.Error()
        }
    } catch (e: HttpException) {
        mapReplyException(e)
    } catch (e: Exception) {
        AppLogManager.w(LOG_TAG, "管理端回复失败: $sessionId", e)
        AdminReplyResult.Error(e)
    }

    /** 修改会话状态（closed / resolved 为终态不可再变更） */
    suspend fun updateStatus(sessionId: String, status: String): AdminOperationResult = try {
        val response: AdminFeedbackStatusResponse = api.updateStatus(
            sessionId, AdminFeedbackStatusRequest(status)
        )
        if (response.success) AdminOperationResult.Success() else AdminOperationResult.Error()
    } catch (e: HttpException) {
        mapStatusException(e)
    } catch (e: Exception) {
        AppLogManager.w(LOG_TAG, "管理端修改状态失败: $sessionId -> $status", e)
        AdminOperationResult.Error(e)
    }

    /** 授予/调整用户贡献等级（自动插入回执消息并 SSE 通知用户） */
    suspend fun grantLevel(sessionId: String, delta: Int): AdminOperationResult = try {
        val response: AdminFeedbackLevelResponse = api.grantLevel(
            sessionId, AdminFeedbackLevelRequest(delta)
        )
        if (response.success) {
            AdminOperationResult.Success(newLevel = response.level)
        } else {
            AdminOperationResult.Error()
        }
    } catch (e: HttpException) {
        mapStatusException(e)
    } catch (e: Exception) {
        AppLogManager.w(LOG_TAG, "管理端授等级失败: $sessionId delta=$delta", e)
        AdminOperationResult.Error(e)
    }

    /** 解析回复接口的 HTTP 4xx（错误码在响应 body 中） */
    private fun mapReplyException(e: HttpException): AdminReplyResult = try {
        val body = e.response()?.errorBody()?.string()
        val error = body?.takeIf { it.isNotBlank() }
            ?.let { Gson().fromJson(it, AdminFeedbackReplyResponse::class.java) }
        when (error?.code) {
            FEEDBACK_CODE_SESSION_CLOSED -> AdminReplyResult.SessionClosed
            FEEDBACK_CODE_SESSION_RESOLVED -> AdminReplyResult.SessionResolved
            FEEDBACK_CODE_SESSION_NOT_FOUND -> AdminReplyResult.SessionNotFound
            else -> when (e.code()) {
                401 -> AdminReplyResult.AuthExpired
                403 -> AdminReplyResult.NoPermission
                404 -> AdminReplyResult.SessionNotFound
                else -> AdminReplyResult.Error(e)
            }
        }
    } catch (_: Exception) {
        AdminReplyResult.Error(e)
    }

    /** 解析状态/授等级接口的 HTTP 4xx（状态与授等级共用一套错误码映射） */
    private fun mapStatusException(e: HttpException): AdminOperationResult = try {
        val body = e.response()?.errorBody()?.string()
        val error = body?.takeIf { it.isNotBlank() }
            ?.let { Gson().fromJson(it, AdminFeedbackStatusResponse::class.java) }
        when (error?.code) {
            FEEDBACK_CODE_SESSION_CLOSED -> AdminOperationResult.SessionClosed
            FEEDBACK_CODE_SESSION_RESOLVED -> AdminOperationResult.SessionResolved
            FEEDBACK_CODE_SESSION_NOT_FOUND -> AdminOperationResult.SessionNotFound
            "INVALID_INPUT", "INVALID_STATUS" -> AdminOperationResult.InvalidInput
            else -> when (e.code()) {
                401 -> AdminOperationResult.AuthExpired
                403 -> AdminOperationResult.NoPermission
                404 -> AdminOperationResult.SessionNotFound
                else -> AdminOperationResult.Error(e)
            }
        }
    } catch (_: Exception) {
        AdminOperationResult.Error(e)
    }
}
