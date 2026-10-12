package com.aiexile.animetrack.ui.feedback.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.AdminFeedbackSession
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_CLOSED
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_OPEN
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_PENDING
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_RESOLVED
import com.aiexile.animetrack.data.remote.AdminFeedbackRepository
import com.aiexile.animetrack.data.sse.SseEventTypes
import com.aiexile.animetrack.data.sse.SseEvent
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.feedback.formatFeedbackTime
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** 「已解决」状态标签绿色（与用户端历史列表一致） */
private val ResolvedGreen = Color(0xFF4CAF50)

/** 管理员反馈列表状态 */
data class AdminFeedbackUiState(
    val sessions: List<AdminFeedbackSession> = emptyList(),
    /** 当前筛选状态（null = 全部） */
    val statusFilter: String? = null,
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val page: Int = 0,
    val total: Int = 0
)

/**
 * 管理员反馈列表 ViewModel：全部用户反馈会话，按状态筛选分页加载。
 * SSE 监听用户新反馈事件（feedback_message）：前台停留时静默刷新当前筛选页。
 */
class AdminFeedbackViewModel(
    private val repository: AdminFeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminFeedbackUiState())
    val uiState: StateFlow<AdminFeedbackUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // 用户发新反馈时实时刷新（当前会话外的新反馈也刷新列表摘要）
        viewModelScope.launch {
            AppContainer.getSseClient().events
                .filter { it.type == SseEventTypes.FEEDBACK_MESSAGE }
                .collect { onNewFeedbackMessage(it) }
        }
    }

    fun refresh() {
        loadPage(1)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        loadPage(state.page + 1)
    }

    /** 切换状态筛选并重载首页 */
    fun setStatusFilter(status: String?) {
        if (_uiState.value.statusFilter == status && _uiState.value.sessions.isNotEmpty()) return
        _uiState.value = AdminFeedbackUiState(statusFilter = status, isLoading = true)
        loadPage(1)
    }

    private fun loadPage(page: Int) {
        val status = _uiState.value.statusFilter
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadFailed = false)
            val result = repository.getSessions(status, page)
            _uiState.value = if (result == null) {
                _uiState.value.copy(isLoading = false, loadFailed = page == 1)
            } else {
                _uiState.value.copy(
                    sessions = if (page == 1) result.sessions
                    else _uiState.value.sessions + result.sessions,
                    hasMore = result.hasMore,
                    page = page,
                    total = result.total,
                    isLoading = false,
                    loadFailed = false
                )
            }
        }
    }

    /** 收到用户新反馈：刷新列表（新反馈/追问会话状态为 open，仅「全部」与「进行中」筛选下刷新） */
    private fun onNewFeedbackMessage(event: SseEvent) {
        if (_uiState.value.isLoading) return
        val filter = _uiState.value.statusFilter
        if (filter == null || filter == FEEDBACK_STATUS_OPEN) {
            refresh()
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminFeedbackViewModel(AppContainer.getAdminFeedbackRepository()) as T
        }
    }
}

/**
 * 管理员反馈中心列表页：状态筛选 chips + 会话列表（用户名/摘要/状态/时间），
 * 点击进入会话详情回复。仅管理员可见（入口由 FeedbackScreen 控制）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFeedbackScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val viewModel: AdminFeedbackViewModel = viewModel(factory = AdminFeedbackViewModel.Factory())
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // 滚动接近底部时加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            uiState.hasMore && !uiState.isLoading &&
                last >= uiState.sessions.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.admin_feedback_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                            contentDescription = stringResource(R.string.feedback_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ===== 状态筛选 chips =====
            AdminStatusFilterRow(
                selected = uiState.statusFilter,
                onSelect = { viewModel.setStatusFilter(it) }
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.loadFailed -> AdminStatusText(
                        text = stringResource(R.string.feedback_history_load_failed),
                        onClick = { viewModel.refresh() }
                    )
                    uiState.sessions.isEmpty() && !uiState.isLoading -> AdminStatusText(
                        text = stringResource(R.string.admin_feedback_empty)
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.sessions, key = { it.sessionId }) { session ->
                            AdminSessionCard(
                                session = session,
                                onClick = { onOpenSession(session.sessionId) }
                            )
                        }
                        if (uiState.isLoading) {
                            item(key = "loading") {
                                Text(
                                    text = stringResource(R.string.common_loading),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 状态筛选行：全部 / 进行中 / 已回复 / 已解决 / 已结束 */
@Composable
private fun AdminStatusFilterRow(
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val filters = listOf(
        null to R.string.admin_feedback_filter_all,
        "open" to R.string.feedback_status_open,
        "pending" to R.string.feedback_status_pending,
        "resolved" to R.string.feedback_status_resolved,
        "closed" to R.string.feedback_status_closed
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters.size) { index ->
            val (status, labelRes) = filters[index]
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(stringResource(labelRes), fontSize = 13.sp) },
                shape = SquircleShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun AdminStatusText(text: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 管理端列表项卡片：用户名 + 状态 / 标题摘要 / 最新消息 / 时间 */
@Composable
private fun AdminSessionCard(
    session: AdminFeedbackSession,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
            .clip(SquircleShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 用户标识：头像圆点 + 用户名
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = session.username ?: stringResource(R.string.admin_feedback_unknown_user),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .weight(1f)
            )
            val (labelRes, labelColor, labelBg) = when (session.status) {
                FEEDBACK_STATUS_CLOSED -> Triple(
                    R.string.feedback_status_closed,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHighest
                )
                FEEDBACK_STATUS_PENDING -> Triple(
                    R.string.feedback_status_pending,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                )
                FEEDBACK_STATUS_RESOLVED -> Triple(
                    R.string.feedback_status_resolved,
                    ResolvedGreen,
                    ResolvedGreen.copy(alpha = 0.12f)
                )
                else -> Triple(
                    R.string.feedback_status_open,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }
            Text(
                text = stringResource(labelRes),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(SquircleShape(7.dp))
                    .background(labelBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        val title = session.title?.takeIf { it.isNotBlank() }
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (!session.lastMessage.isNullOrBlank()) {
            Text(
                text = session.lastMessage!!,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatFeedbackTime(session.updatedAt ?: session.createdAt),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.admin_feedback_message_count, session.messageCount),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
