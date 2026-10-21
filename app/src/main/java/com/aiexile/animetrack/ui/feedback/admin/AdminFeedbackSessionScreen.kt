package com.aiexile.animetrack.ui.feedback.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.AdminFeedbackSessionInfo
import com.aiexile.animetrack.data.network.FEEDBACK_ROLE_ASSISTANT
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_CLOSED
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_OPEN
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_PENDING
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_RESOLVED
import com.aiexile.animetrack.data.remote.AdminOperationResult
import com.aiexile.animetrack.data.remote.AdminReplyResult
import com.aiexile.animetrack.data.remote.AdminFeedbackRepository
import com.aiexile.animetrack.data.remote.FeedbackRepository
import com.aiexile.animetrack.data.sse.SseEventTypes
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.dialogBlurBehind
import com.aiexile.animetrack.ui.feedback.FeedbackChatMessage
import com.aiexile.animetrack.ui.feedback.FeedbackMessageBubble
import com.aiexile.animetrack.ui.feedback.FeedbackSendButton
import com.aiexile.animetrack.ui.feedback.toChatMessage
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** 「已解决」状态标签绿色（与列表页一致） */
private val ResolvedGreen = Color(0xFF4CAF50)

/** 管理员会话详情状态 */
data class AdminFeedbackSessionUiState(
    val session: AdminFeedbackSessionInfo? = null,
    val messages: List<FeedbackChatMessage> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSending: Boolean = false
)

/** 一次性操作结果提示（UI 消费后调 consumeNotice） */
data class AdminNotice(val messageRes: Int, val arg: Any? = null)

/**
 * 管理员会话详情 ViewModel：查看用户反馈对话、回复、状态流转、授等级。
 * SSE 监听该会话的用户新消息（feedback_message），停留页面时实时追加。
 */
class AdminFeedbackSessionViewModel(
    private val repository: AdminFeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminFeedbackSessionUiState())
    val uiState: StateFlow<AdminFeedbackSessionUiState> = _uiState.asStateFlow()

    private val _notice = MutableStateFlow<AdminNotice?>(null)
    val notice: StateFlow<AdminNotice?> = _notice.asStateFlow()

    private var sessionId: String = ""

    init {
        // 用户在该会话追加新消息：停留详情页时实时刷新对话
        viewModelScope.launch {
            AppContainer.getSseClient().events
                .filter { it.type == SseEventTypes.FEEDBACK_MESSAGE && it.sessionId == sessionId }
                .collect { refreshFromServer() }
        }
    }

    fun consumeNotice() {
        _notice.value = null
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
                _uiState.value.copy(
                    session = detail.first,
                    messages = detail.second.map { it.toChatMessage() },
                    isLoading = false,
                    loadFailed = false
                )
            }
        }
    }

    /** SSE 通知后静默重拉：发送中的本地乐观消息（负 id）保留在末尾 */
    private fun refreshFromServer() {
        if (sessionId.isBlank() || _uiState.value.isLoading) return
        viewModelScope.launch {
            val detail = repository.getSessionDetail(sessionId) ?: return@launch
            val serverMessages = detail.second.map { it.toChatMessage() }
            val pendingLocal = _uiState.value.messages
                .filter { it.id < 0 && _uiState.value.isSending }
            _uiState.value = _uiState.value.copy(
                session = detail.first,
                messages = serverMessages + pendingLocal
            )
        }
    }

    /** 回复反馈：本地乐观上屏 → 服务端确认替换 */
    fun reply(content: String) {
        val state = _uiState.value
        val status = state.session?.status
        if (state.isSending || status == FEEDBACK_STATUS_CLOSED || status == FEEDBACK_STATUS_RESOLVED) return

        viewModelScope.launch {
            if (content.isBlank()) {
                _notice.value = AdminNotice(R.string.feedback_error_empty)
                return@launch
            }
            if (content.length > FeedbackRepository.MAX_CONTENT_LENGTH) {
                _notice.value = AdminNotice(
                    R.string.feedback_error_too_long, FeedbackRepository.MAX_CONTENT_LENGTH
                )
                return@launch
            }

            val localId = -(state.messages.size + 1).toLong()
            _uiState.value = state.copy(
                messages = state.messages + FeedbackChatMessage(
                    id = localId,
                    // 管理员视角下 assistant 是「自己」，直接以 assistant 上屏保证立即右置
                    role = FEEDBACK_ROLE_ASSISTANT,
                    content = content.trim()
                ),
                isSending = true
            )

            when (val result = repository.reply(sessionId, content)) {
                is AdminReplyResult.Success -> {
                    val messages = _uiState.value.messages
                    _uiState.value = _uiState.value.copy(
                        messages = messages.map {
                            if (it.id == localId) result.reply.toChatMessage() else it
                        },
                        // 回复后服务端自动转 pending
                        session = _uiState.value.session?.copy(status = FEEDBACK_STATUS_PENDING),
                        isSending = false
                    )
                }
                AdminReplyResult.SessionClosed -> {
                    _notice.value = AdminNotice(R.string.feedback_error_session_closed)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == localId },
                        session = _uiState.value.session?.copy(status = FEEDBACK_STATUS_CLOSED),
                        isSending = false
                    )
                }
                AdminReplyResult.SessionResolved -> {
                    _notice.value = AdminNotice(R.string.feedback_error_session_resolved)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == localId },
                        session = _uiState.value.session?.copy(status = FEEDBACK_STATUS_RESOLVED),
                        isSending = false
                    )
                }
                AdminReplyResult.SessionNotFound -> {
                    _notice.value = AdminNotice(R.string.feedback_error_session_not_found)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == localId },
                        isSending = false
                    )
                }
                AdminReplyResult.AuthExpired -> {
                    _notice.value = AdminNotice(R.string.feedback_error_auth_expired)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == localId },
                        isSending = false
                    )
                }
                AdminReplyResult.NoPermission -> {
                    _notice.value = AdminNotice(R.string.admin_feedback_no_permission)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == localId },
                        isSending = false
                    )
                }
                is AdminReplyResult.Error -> {
                    _notice.value = AdminNotice(R.string.feedback_error_network)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == localId },
                        isSending = false
                    )
                }
            }
        }
    }

    /** 状态流转（open / pending / closed / resolved；终态不可再变更） */
    fun updateStatus(status: String) {
        viewModelScope.launch {
            when (val result = repository.updateStatus(sessionId, status)) {
                is AdminOperationResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        session = _uiState.value.session?.copy(status = status)
                    )
                    _notice.value = AdminNotice(R.string.admin_feedback_status_updated)
                }
                AdminOperationResult.SessionClosed,
                AdminOperationResult.SessionResolved -> {
                    // 终态不可变更（可能已被其他端处理），重拉同步真实状态
                    _notice.value = AdminNotice(R.string.admin_feedback_status_terminal)
                    refreshFromServer()
                }
                AdminOperationResult.SessionNotFound ->
                    _notice.value = AdminNotice(R.string.feedback_error_session_not_found)
                AdminOperationResult.InvalidInput ->
                    _notice.value = AdminNotice(R.string.admin_feedback_invalid_input)
                AdminOperationResult.AuthExpired ->
                    _notice.value = AdminNotice(R.string.feedback_error_auth_expired)
                AdminOperationResult.NoPermission ->
                    _notice.value = AdminNotice(R.string.admin_feedback_no_permission)
                is AdminOperationResult.Error ->
                    _notice.value = AdminNotice(R.string.feedback_error_network)
            }
        }
    }

    /** 授予/调整用户贡献等级：服务端插入自动回执并 SSE 通知用户，成功后重拉对话 */
    fun grantLevel(delta: Int) {
        viewModelScope.launch {
            when (val result = repository.grantLevel(sessionId, delta)) {
                is AdminOperationResult.Success -> {
                    _notice.value = AdminNotice(
                        R.string.admin_feedback_level_granted, result.newLevel ?: 0
                    )
                    refreshFromServer()
                }
                AdminOperationResult.SessionNotFound ->
                    _notice.value = AdminNotice(R.string.feedback_error_session_not_found)
                AdminOperationResult.InvalidInput ->
                    _notice.value = AdminNotice(R.string.admin_feedback_invalid_input)
                AdminOperationResult.AuthExpired ->
                    _notice.value = AdminNotice(R.string.feedback_error_auth_expired)
                AdminOperationResult.NoPermission ->
                    _notice.value = AdminNotice(R.string.admin_feedback_no_permission)
                is AdminOperationResult.Error ->
                    _notice.value = AdminNotice(R.string.feedback_error_network)
                AdminOperationResult.SessionClosed,
                AdminOperationResult.SessionResolved -> { /* 授等级不校验会话状态，理论上不出现 */ }
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminFeedbackSessionViewModel(AppContainer.getAdminFeedbackRepository()) as T
        }
    }
}

/**
 * 管理员会话详情页：用户反馈对话（管理员视角气泡）+ 回复输入框 +
 * 状态流转与授等级操作菜单。closed / resolved 终态时禁用回复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFeedbackSessionScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    val viewModel: AdminFeedbackSessionViewModel = viewModel(
        factory = AdminFeedbackSessionViewModel.Factory()
    )
    val uiState by viewModel.uiState.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val context = LocalContext.current

    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var levelDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.load(sessionId)
    }

    // 一次性操作结果提示
    LaunchedEffect(notice) {
        notice?.let { n ->
            val message = if (n.arg != null) {
                context.getString(n.messageRes, n.arg)
            } else {
                context.getString(n.messageRes)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    // 新消息到达自动滚到底部
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    val session = uiState.session
    val isTerminal = session?.status == FEEDBACK_STATUS_CLOSED ||
        session?.status == FEEDBACK_STATUS_RESOLVED

    AppScaffold(
        title = session?.username
            ?: stringResource(R.string.admin_feedback_unknown_user),
        onBack = onBack,
        titleContent = {
            Column {
                Text(
                    text = session?.username
                        ?: stringResource(R.string.admin_feedback_unknown_user),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lv.${session?.level ?: 0}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(statusLabel(session?.status)),
                        fontSize = 12.sp,
                        color = statusColor(session?.status)
                    )
                }
            }
        },
        actions = {
            if (session != null) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.MORE_VERT),
                        contentDescription = stringResource(R.string.admin_feedback_actions)
                    )
                }
                // ===== 操作菜单：状态流转 + 授予等级 =====
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!isTerminal) {
                        if (session.status != FEEDBACK_STATUS_PENDING) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.admin_feedback_mark_pending)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.updateStatus(FEEDBACK_STATUS_PENDING)
                                }
                            )
                        }
                        if (session.status != FEEDBACK_STATUS_RESOLVED) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.admin_feedback_mark_resolved)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.updateStatus(FEEDBACK_STATUS_RESOLVED)
                                }
                            )
                        }
                        if (session.status != FEEDBACK_STATUS_CLOSED) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.admin_feedback_mark_closed)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.updateStatus(FEEDBACK_STATUS_CLOSED)
                                }
                            )
                        }
                        if (session.status != FEEDBACK_STATUS_OPEN) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.admin_feedback_mark_open)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.updateStatus(FEEDBACK_STATUS_OPEN)
                                }
                            )
                        }
                    } else {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.admin_feedback_status_terminal_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { menuOpen = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.admin_feedback_grant_level)) },
                        onClick = {
                            menuOpen = false
                            levelDialogOpen = true
                        }
                    )
                }
            }
        }
    ) { chrome ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
                // 玻璃档把导航栏内边距留在视口内，输入区不被系统手势条遮挡
                .padding(bottom = chrome.content.calculateBottomPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.common_loading),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    uiState.loadFailed -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.feedback_session_load_failed),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            // 玻璃档在原有 12dp 内边距上叠加顶栏空间，消息可滚到顶栏下方
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 12.dp + chrome.content.calculateTopPadding(),
                                bottom = 12.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(uiState.messages, key = { it.id }) { message ->
                                FeedbackMessageBubble(message, viewerIsAdmin = true)
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

                        // ===== 回复输入区（终态禁用） =====
                        if (isTerminal) {
                            Text(
                                text = if (session?.status == FEEDBACK_STATUS_RESOLVED) {
                                    stringResource(R.string.feedback_append_resolved)
                                } else {
                                    stringResource(R.string.feedback_append_closed)
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 14.dp)
                            )
                        } else {
                            AdminReplyInput(
                                value = input,
                                onValueChange = { input = it },
                                isSending = uiState.isSending,
                                onSend = {
                                    viewModel.reply(input)
                                    input = ""
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .imePadding()
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== 授予等级对话框 =====
    if (levelDialogOpen) {
        AdminGrantLevelDialog(
            onDismiss = { levelDialogOpen = false },
            onConfirm = { delta ->
                levelDialogOpen = false
                viewModel.grantLevel(delta)
            }
        )
    }
}

/** 管理员回复输入框：大圆角容器 + 多行输入 + 圆形发送按钮（样式与用户端聊天一致） */
@Composable
private fun AdminReplyInput(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(R.string.admin_feedback_reply_hint),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 4,
            shape = SquircleShape(24.dp)
        )
        Spacer(modifier = Modifier.padding(start = 8.dp))
        FeedbackSendButton(
            enabled = value.isNotBlank() && !isSending,
            onClick = onSend
        )
    }
}

/** 授予贡献等级对话框：输入 delta（正=提升，负=调整，非零且 |delta| ≤ 999） */
@Composable
private fun AdminGrantLevelDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogBlurBehind(),
        title = { Text(stringResource(R.string.admin_feedback_grant_level)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.admin_feedback_grant_level_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '-' } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    text.toIntOrNull()?.takeIf { it != 0 && kotlin.math.abs(it) <= AdminFeedbackRepository.MAX_LEVEL_DELTA }
                        ?.let(onConfirm)
                }
            ) {
                Text(stringResource(R.string.admin_feedback_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.admin_feedback_cancel))
            }
        }
    )
}

/** 状态文案资源映射 */
private fun statusLabel(status: String?): Int = when (status) {
    FEEDBACK_STATUS_PENDING -> R.string.feedback_status_pending
    FEEDBACK_STATUS_RESOLVED -> R.string.feedback_status_resolved
    FEEDBACK_STATUS_CLOSED -> R.string.feedback_status_closed
    else -> R.string.feedback_status_open
}

/** 状态标签颜色（与列表页四态配色一致） */
@Composable
private fun statusColor(status: String?): Color = when (status) {
    FEEDBACK_STATUS_PENDING -> MaterialTheme.colorScheme.tertiary
    FEEDBACK_STATUS_RESOLVED -> ResolvedGreen
    FEEDBACK_STATUS_CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
}
