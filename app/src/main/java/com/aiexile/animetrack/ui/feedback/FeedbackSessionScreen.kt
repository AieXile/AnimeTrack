package com.aiexile.animetrack.ui.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_CLOSED
import com.aiexile.animetrack.data.network.FeedbackSession
import com.aiexile.animetrack.data.remote.FeedbackRepository
import com.aiexile.animetrack.data.remote.FeedbackSendResult
import com.aiexile.animetrack.data.remote.PendingAttachment
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource

/** 会话详情状态 */
data class FeedbackSessionUiState(
    val session: FeedbackSession? = null,
    val messages: List<FeedbackChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSending: Boolean = false,
    val attachments: List<PendingAttachment> = emptyList()
)

/**
 * 会话详情 ViewModel：加载只读消息记录 + 追加发送。
 * 追加与主页共用发送接口，仅固定 sessionId。
 */
class FeedbackSessionViewModel(
    private val repository: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackSessionUiState())
    val uiState: StateFlow<FeedbackSessionUiState> = _uiState.asStateFlow()

    private val _sendError = MutableStateFlow<FeedbackSendError?>(null)
    val sendError: StateFlow<FeedbackSendError?> = _sendError.asStateFlow()

    private var sessionId: String = ""

    fun consumeError() {
        _sendError.value = null
    }

    fun load(id: String) {
        if (sessionId == id && _uiState.value.messages.isNotEmpty()) return
        sessionId = id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadFailed = false)
            val detail = repository.getSessionDetail(id)
            _uiState.value = if (detail == null) {
                _uiState.value.copy(isLoading = false, loadFailed = true)
            } else {
                // 已查看会话内容，清除红点/主页提示
                repository.markRead()
                _uiState.value.copy(
                    session = detail.first,
                    messages = detail.second.map { it.toChatMessage() },
                    isLoading = false,
                    loadFailed = false
                )
            }
        }
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

    /** 追加发送到当前会话 */
    fun append(content: String) {
        val state = _uiState.value
        if (state.isSending || state.session?.status == FEEDBACK_STATUS_CLOSED) return

        viewModelScope.launch {
            // 日志占位附件 → 实际导出文件（导出失败降级移除，反馈优先送达）
            val (attachments, logExportFailed) = resolveLogPlaceholder(state.attachments)
            if (content.isBlank() && attachments.isEmpty()) {
                _sendError.value = FeedbackSendError.Empty
                return@launch
            }

            val localId = -(state.messages.size + 1).toLong()
            _uiState.value = state.copy(
                messages = state.messages + FeedbackChatMessage(
                    id = localId,
                    role = com.aiexile.animetrack.data.network.FEEDBACK_ROLE_USER,
                    content = content.trim()
                ),
                isSending = true
            )

            when (val result = repository.send(content, sessionId, attachments)) {
                is FeedbackSendResult.Success -> {
                    val messages = _uiState.value.messages
                    val confirmed = result.userMessage?.let { serverMsg ->
                        messages.map { if (it.id == localId) serverMsg.toChatMessage() else it }
                    } ?: messages
                    val withReply = result.reply?.let { confirmed + it.toChatMessage() } ?: confirmed
                    _uiState.value = _uiState.value.copy(
                        messages = withReply,
                        session = _uiState.value.session?.copy(status = com.aiexile.animetrack.data.network.FEEDBACK_STATUS_OPEN),
                        isSending = false,
                        attachments = emptyList()
                    )
                }
                is FeedbackSendResult.RateLimited ->
                    _sendError.value = FeedbackSendError.RateLimited(result.retryAfterSeconds)
                FeedbackSendResult.SensitiveContent -> _sendError.value = FeedbackSendError.Sensitive
                FeedbackSendResult.SessionClosed -> {
                    _sendError.value = FeedbackSendError.SessionClosed
                    _uiState.value = _uiState.value.copy(
                        session = _uiState.value.session?.copy(status = FEEDBACK_STATUS_CLOSED)
                    )
                }
                FeedbackSendResult.SessionNotFound -> _sendError.value = FeedbackSendError.SessionNotFound
                FeedbackSendResult.AttachmentUploadFailed ->
                    _sendError.value = FeedbackSendError.AttachmentFailed
                FeedbackSendResult.AuthExpired -> _sendError.value = FeedbackSendError.AuthExpired
                is FeedbackSendResult.Error -> _sendError.value = FeedbackSendError.Network
            }
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
            return FeedbackSessionViewModel(AppContainer.getFeedbackRepository()) as T
        }
    }
}

/**
 * 会话详情页：只读展示当时的对话记录（无输入框），
 * 底部「追加反馈」按钮——点击展开输入框，可基于该会话再次提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSessionScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    val viewModel: FeedbackSessionViewModel = viewModel(factory = FeedbackSessionViewModel.Factory())
    val uiState by viewModel.uiState.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val context = LocalContext.current

    var appendInputVisible by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    LaunchedEffect(sessionId) {
        viewModel.load(sessionId)
    }

    // 错误提示（一次性消费）
    LaunchedEffect(sendError) {
        sendError?.let { error ->
            val message = when (error) {
                is FeedbackSendError.RateLimited ->
                    context.getString(R.string.feedback_error_rate_limited, error.retryAfterSeconds)
                FeedbackSendError.HourlyLimit ->
                    context.getString(R.string.feedback_error_hourly_limit)
                FeedbackSendError.Sensitive ->
                    context.getString(R.string.feedback_error_sensitive)
                FeedbackSendError.SessionClosed ->
                    context.getString(R.string.feedback_error_session_closed)
                FeedbackSendError.SessionNotFound ->
                    context.getString(R.string.feedback_error_session_not_found)
                FeedbackSendError.AttachmentFailed ->
                    context.getString(R.string.feedback_error_attachment)
                FeedbackSendError.LogGenerateFailed ->
                    context.getString(R.string.feedback_log_generate_failed)
                FeedbackSendError.AuthExpired ->
                    context.getString(R.string.feedback_error_auth_expired)
                FeedbackSendError.Network ->
                    context.getString(R.string.feedback_error_network)
                FeedbackSendError.Empty ->
                    context.getString(R.string.feedback_error_empty)
                FeedbackSendError.TooLong ->
                    context.getString(R.string.feedback_error_too_long, FeedbackRepository.MAX_CONTENT_LENGTH)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    val isClosed = uiState.session?.status == FEEDBACK_STATUS_CLOSED

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.session?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.feedback_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.sym_arrow_back),
                            contentDescription = stringResource(R.string.feedback_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // 详情页默认无输入框：底部为「追加反馈」按钮，点击后展开输入区
            if (!uiState.isLoading && !uiState.loadFailed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        )
                        .padding(horizontal = 16.dp)
                ) {
                    AnimatedVisibility(
                        visible = appendInputVisible && !isClosed,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        FeedbackChatInput(
                            value = input,
                            onValueChange = { input = it },
                            attachments = uiState.attachments,
                            onAttachmentsChange = { newList ->
                                // 与 VM 状态做差量同步：新增/移除分别提交
                                val current = uiState.attachments
                                newList.filterNot { current.contains(it) }.forEach { viewModel.addAttachment(it) }
                                current.filterNot { newList.contains(it) }.forEach { viewModel.removeAttachment(it) }
                            },
                            isSending = uiState.isSending,
                            enabled = true,
                            onSend = {
                                viewModel.append(input)
                                input = ""
                            },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    if (!appendInputVisible || isClosed) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .padding(vertical = 12.dp)
                                .clip(SquircleShape(16.dp))
                                .background(
                                    if (isClosed) MaterialTheme.colorScheme.surfaceContainerHighest
                                    else MaterialTheme.colorScheme.primary
                                )
                                .clickable(enabled = !isClosed) { appendInputVisible = true }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isClosed) stringResource(R.string.feedback_append_closed)
                                else stringResource(R.string.feedback_append),
                                color = if (isClosed) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> Text(
                    text = stringResource(R.string.common_loading),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
                uiState.loadFailed -> Text(
                    text = stringResource(R.string.feedback_session_load_failed),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        FeedbackMessageBubble(message)
                    }
                    if (uiState.isSending) {
                        item(key = "sending") {
                            Text(
                                text = stringResource(R.string.common_loading),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
