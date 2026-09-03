package com.aiexile.animetrack.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_CLOSED
import com.aiexile.animetrack.data.network.FEEDBACK_STATUS_PENDING
import com.aiexile.animetrack.data.remote.FeedbackRepository
import com.aiexile.animetrack.data.remote.FeedbackSessionsPage
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource

/** 反馈历史列表状态 */
data class FeedbackHistoryUiState(
    val sessions: List<com.aiexile.animetrack.data.network.FeedbackSession> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val loadFailed: Boolean = false,
    val page: Int = 0
)

/** 反馈历史列表 ViewModel：分页加载会话列表 */
class FeedbackHistoryViewModel(
    private val repository: FeedbackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackHistoryUiState())
    val uiState: StateFlow<FeedbackHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = FeedbackHistoryUiState(isLoading = true)
        loadPage(1)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        loadPage(state.page + 1)
    }

    private fun loadPage(page: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadFailed = false)
            val result: FeedbackSessionsPage? = repository.getSessions(page)
            _uiState.value = if (result == null) {
                _uiState.value.copy(isLoading = false, loadFailed = page == 1)
            } else {
                // 首页加载成功即视为已查看反馈（清除红点/主页提示）
                if (page == 1) repository.markRead()
                _uiState.value.copy(
                    sessions = if (page == 1) result.sessions
                    else _uiState.value.sessions + result.sessions,
                    hasMore = result.hasMore,
                    page = page,
                    isLoading = false,
                    loadFailed = false
                )
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedbackHistoryViewModel(AppContainer.getFeedbackRepository()) as T
        }
    }
}

/**
 * 反馈历史列表页：按会话展示历史反馈（摘要 + 时间 + 状态），点击进入只读会话详情。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackHistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val viewModel: FeedbackHistoryViewModel = viewModel(factory = FeedbackHistoryViewModel.Factory())
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
                        text = stringResource(R.string.feedback_history),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.loadFailed -> HistoryStatusText(
                    text = stringResource(R.string.feedback_history_load_failed),
                    onClick = { viewModel.refresh() }
                )
                uiState.sessions.isEmpty() && !uiState.isLoading -> HistoryStatusText(
                    text = stringResource(R.string.feedback_history_empty)
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.sessions, key = { it.sessionId }) { session ->
                        FeedbackSessionCard(
                            title = session.title?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.settings_feedback),
                            lastMessage = session.lastMessage.orEmpty(),
                            time = formatFeedbackTime(session.updatedAt ?: session.createdAt),
                            status = session.status,
                            hasNewReply = session.hasNewReply,
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStatusText(text: String, onClick: (() -> Unit)? = null) {
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

/** 历史列表项卡片：标题（首条摘要）+ 最新消息 + 时间 + 状态（三态），有未读回复时标注 */
@Composable
private fun FeedbackSessionCard(
    title: String,
    lastMessage: String,
    time: String,
    status: String,
    hasNewReply: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .clip(SquircleShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasNewReply) {
                // 未读回复红点：标题前提示该会话有新回复
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.padding(start = 6.dp))
            if (hasNewReply) {
                // 「新回复」胶囊标签（与状态标签同规格，error 配色突出）
                Text(
                    text = stringResource(R.string.feedback_new_reply),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(SquircleShape(7.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            val (labelRes, labelColor, labelBg) = when (status) {
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
                    .clip(SquircleShape(7.dp))
                    .background(labelBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        if (lastMessage.isNotBlank()) {
            Text(
                text = lastMessage,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (time.isNotBlank()) {
            Text(
                text = time,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
