package com.aiexile.animetrack.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.auth.UserAuthManager
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.FEEDBACK_ATTACHMENT_TYPE_LOG
import com.aiexile.animetrack.data.network.FeedbackMessage
import com.aiexile.animetrack.data.network.FEEDBACK_ROLE_SYSTEM
import com.aiexile.animetrack.data.network.FEEDBACK_ROLE_USER
import com.aiexile.animetrack.data.remote.AdminFeedbackRepository
import com.aiexile.animetrack.data.remote.AdminIdentityResult
import com.aiexile.animetrack.data.remote.FeedbackRepository
import com.aiexile.animetrack.data.remote.FeedbackSendResult
import com.aiexile.animetrack.data.remote.PendingAttachment
import com.aiexile.animetrack.data.sse.SseEventTypes
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** 反馈聊天主页状态 */
data class FeedbackChatUiState(
    val messages: List<FeedbackChatMessage> = emptyList(),
    val sessionId: String? = null,
    val isSending: Boolean = false,
    val attachments: List<PendingAttachment> = emptyList()
)

/**
 * 解析「附带 App 日志」占位附件（uriString 为空）：导出实际日志文件并替换占位，
 * 保证日志包含到发送时刻为止的最新内容。
 * @return 解析后的附件列表 与 是否导出失败（失败时占位被移除，反馈照常发送）
 */
internal suspend fun resolveLogPlaceholder(
    attachments: List<PendingAttachment>
): Pair<List<PendingAttachment>, Boolean> {
    val placeholder = attachments.firstOrNull {
        it.kind == FEEDBACK_ATTACHMENT_TYPE_LOG && it.uriString.isBlank()
    } ?: return attachments to false
    val logFile = AppLogManager.exportFeedbackLog()
    return if (logFile != null && logFile.length() > 0) {
        attachments.map { if (it == placeholder) it.copy(uriString = logFile.absolutePath) else it } to false
    } else {
        attachments.filterNot { it == placeholder } to true
    }
}

/**
 * 反馈聊天主页 ViewModel：新建会话发送消息，并保持与当前会话的实时通信——
 * 管理员回复经 SSE（feedback_reply，sessionId 匹配）实时拉取并展示在主页，
 * 用户无需前往历史页即可收到回复。消息仅保留在内存中，历史记录由服务端存储。
 */
class FeedbackViewModel(
    private val repository: FeedbackRepository,
    private val adminRepository: AdminFeedbackRepository,
    userAuthManager: UserAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackChatUiState())
    val uiState: StateFlow<FeedbackChatUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)

    /** 是否已登录 AnimeTrack 账号（反馈需登录） */
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow<String?>(null)

    /** 已登录用户的用户名（未登录或无用户名为 null），用于问候语展示 */
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _hasNewReplies = MutableStateFlow(false)

    /** 是否有未读的管理员回复（历史入口红点用；当前会话回复已在主页展示时随 markRead 清除） */
    val hasNewReplies: StateFlow<Boolean> = _hasNewReplies.asStateFlow()

    private val _isAdmin = MutableStateFlow(false)

    /** 当前用户是否管理员（服务端白名单识别，控制「反馈管理」入口显隐） */
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    init {
        viewModelScope.launch {
            userAuthManager.isLoggedIn.collect { loggedIn ->
                _isLoggedIn.value = loggedIn
                if (loggedIn) {
                    refreshNewReplies()
                    refreshAdminIdentity()
                } else {
                    _hasNewReplies.value = false
                    _isAdmin.value = false
                }
            }
        }
        viewModelScope.launch {
            userAuthManager.username.collect { name ->
                _username.value = name?.takeIf { it.isNotBlank() }
            }
        }
        // SSE 管理员回复事件：前台在线时实时处理
        // - 回复主页当前会话：拉取服务端最新消息实时展示在主页（用户无需前往历史页），并标记已读
        // - 回复其他（历史）会话：仅刷新红点
        viewModelScope.launch {
            AppContainer.getSseClient().events
                .filter { it.type == SseEventTypes.FEEDBACK_REPLY }
                .collect { event ->
                    if (event.sessionId != null && event.sessionId == _uiState.value.sessionId) {
                        refreshCurrentSessionMessages()
                    } else {
                        refreshNewReplies()
                    }
                }
        }
    }

    /**
     * SSE 通知管理员回复了主页当前会话：拉取服务端最新消息替换主页列表
     * （发送中的本地乐观消息保留在末尾，避免闪烁），随后标记已读并同步红点。
     */
    private fun refreshCurrentSessionMessages() {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            val detail = repository.getSessionDetail(sessionId) ?: return@launch
            val serverMessages = detail.second.map { it.toChatMessage() }
            val state = _uiState.value
            val pendingLocal = state.messages.filter { it.id < 0 && state.isSending }
            _uiState.value = state.copy(messages = serverMessages + pendingLocal)
            // 主页已展示最新回复：标记已读并刷新红点（与历史/详情页的查看行为一致）
            repository.markRead()
            refreshNewReplies()
        }
    }

    /** 查询当前登录用户是否在管理员白名单（仅登录时调用一次） */
    private fun refreshAdminIdentity() {
        viewModelScope.launch {
            _isAdmin.value = adminRepository.getIdentity() is AdminIdentityResult.Admin
        }
    }

    /** 刷新未读回复状态（页面重新可见时调用，如从历史界面返回后红点应及时熄灭） */
    fun refreshNewReplies() {
        viewModelScope.launch {
            // 当前会话的回复已在主页展示并 markRead（见 refreshCurrentSessionMessages），
            // 此处查询仍以服务端为准（覆盖其他历史会话的未读）
            _hasNewReplies.value = repository.hasNewReplies()
        }
    }

    /** 一次性发送错误（UI 消费后调用 consumeError()） */
    private val _sendError = MutableStateFlow<FeedbackSendError?>(null)
    val sendError: StateFlow<FeedbackSendError?> = _sendError.asStateFlow()

    fun consumeError() {
        _sendError.value = null
    }

    /** 附件增删（发送时统一上传） */
    fun addAttachment(attachment: PendingAttachment) {
        _uiState.value = _uiState.value.copy(attachments = _uiState.value.attachments + attachment)
    }

    fun removeAttachment(attachment: PendingAttachment) {
        _uiState.value = _uiState.value.copy(
            attachments = _uiState.value.attachments.filterNot { it == attachment }
        )
    }

    /**
     * 发送反馈（新建会话或延续当前界面内的会话）。
     * 「附带 App 日志」为占位附件（uriString 为空），发送时才导出实际日志文件，
     * 保证日志包含到发送时刻为止的最新内容；导出失败降级为不带日志继续发送。
     */
    fun send(content: String) {
        val state = _uiState.value
        if (state.isSending) return

        viewModelScope.launch {
            // 1. 日志占位附件 → 实际导出文件（导出失败降级移除，反馈优先送达）
            val (attachments, logExportFailed) = resolveLogPlaceholder(state.attachments)

            // 2. 空内容校验（日志附件可能在第 1 步被降级移除）
            if (content.isBlank() && attachments.isEmpty()) {
                _sendError.value = FeedbackSendError.Empty
                return@launch
            }

            // 本地乐观上屏（时间由服务端确认后仍以此展示）
            val localId = -(state.messages.size + 1).toLong()
            _uiState.value = state.copy(
                messages = state.messages + FeedbackChatMessage(
                    id = localId,
                    role = FEEDBACK_ROLE_USER,
                    content = content.trim()
                ),
                isSending = true
            )

            when (val result = repository.send(content, state.sessionId, attachments)) {
                is FeedbackSendResult.Success -> {
                    val messages = _uiState.value.messages
                    val confirmed = result.userMessage?.let { serverMsg ->
                        if (messages.any { it.id == serverMsg.id }) {
                            // SSE 重拉已包含服务端确认消息：仅移除本地乐观占位，避免重复
                            messages.filterNot { it.id == localId }
                        } else {
                            // 服务端确认消息替换本地乐观消息
                            messages.map { if (it.id == localId) serverMsg.toChatMessage() else it }
                        }
                    } ?: messages
                    val withReply = result.reply?.let { r ->
                        // 自动回执也可能已被 SSE 重拉包含（同样按 id 去重）
                        if (confirmed.any { it.id == r.id }) confirmed
                        else confirmed + r.toChatMessage()
                    } ?: confirmed
                    val withPlaceholder = if (result.reply == null && state.sessionId == null) {
                        // 新会话且服务端暂无回复：插入收到提示
                        withReply + FeedbackChatMessage(
                            id = localId - 1,
                            role = FEEDBACK_ROLE_SYSTEM,
                            content = "", // UI 层渲染占位文案
                            createdAt = null
                        )
                    } else {
                        withReply
                    }
                    _uiState.value = _uiState.value.copy(
                        messages = withPlaceholder,
                        sessionId = result.sessionId,
                        isSending = false,
                        attachments = emptyList()
                    )
                }
                is FeedbackSendResult.RateLimited ->
                    _sendError.value = FeedbackSendError.RateLimited(result.retryAfterSeconds)
                FeedbackSendResult.SensitiveContent -> _sendError.value = FeedbackSendError.Sensitive
                FeedbackSendResult.SessionClosed -> _sendError.value = FeedbackSendError.SessionClosed
                FeedbackSendResult.SessionResolved -> _sendError.value = FeedbackSendError.SessionResolved
                FeedbackSendResult.SessionNotFound -> _sendError.value = FeedbackSendError.SessionNotFound
                FeedbackSendResult.AttachmentUploadFailed ->
                    _sendError.value = FeedbackSendError.AttachmentFailed
                FeedbackSendResult.AuthExpired -> _sendError.value = FeedbackSendError.AuthExpired
                is FeedbackSendResult.Error -> _sendError.value = FeedbackSendError.Network
            }
            // 失败时移除乐观消息
            if (_sendError.value != null) {
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages.filterNot { it.id == localId },
                    isSending = false
                )
            } else if (logExportFailed) {
                // 发送成功但日志导出失败：降级提示（置于失败清理之后，避免误触发清理）
                _sendError.value = FeedbackSendError.LogGenerateFailed
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedbackViewModel(
                AppContainer.getFeedbackRepository(),
                AppContainer.getAdminFeedbackRepository(),
                AppContainer.getUserAuthManager()
            ) as T
        }
    }
}

/** 服务端消息 → 聊天 UI 模型 */
fun FeedbackMessage.toChatMessage(): FeedbackChatMessage = FeedbackChatMessage(
    id = id,
    role = role,
    content = content,
    attachments = attachments,
    createdAt = createdAt
)
