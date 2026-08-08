package com.aiexile.animetrack.ui.announcement

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.auth.UserAuthManager
import com.aiexile.animetrack.data.network.Announcement
import com.aiexile.animetrack.data.network.AnnouncementDetail
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.network.VoteRequest
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnnouncementUiState(
    val announcements: List<Announcement> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val showDialog: Boolean = false,
    val showHistoryList: Boolean = false,
    val error: String? = null,
    /** 当前公告的投票详情（需登录，未登录或拉取失败时为 null） */
    val currentDetail: AnnouncementDetail? = null,
    val isDetailLoading: Boolean = false,
    val isVoting: Boolean = false,
    val voteError: String? = null
) {
    val currentAnnouncement: Announcement?
        get() = announcements.getOrNull(currentIndex)

    val hasMultiple: Boolean
        get() = announcements.size > 1
}

class AnnouncementViewModel(
    private val settingsRepository: SettingsRepository,
    private val userAuthManager: UserAuthManager
) : ViewModel() {

    companion object {
        private const val TAG = "AnnouncementVM"
    }

    private val _uiState = MutableStateFlow(AnnouncementUiState())
    val uiState: StateFlow<AnnouncementUiState> = _uiState.asStateFlow()

    /**
     * 拉取公告列表，若有未读公告则自动弹出弹窗。
     */
    fun fetchAnnouncements() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val response = RetrofitClient.userAuthApi.getAnnouncements()
                if (!response.success) {
                    _uiState.update { it.copy(isLoading = false, error = "获取公告失败") }
                    return@launch
                }
                // 按创建时间倒序排列
                val sorted = response.announcements.sortedByDescending { it.createdAt ?: it.id.toString() }
                val readIds = settingsRepository.getReadAnnouncementIds()
                val unread = sorted.filter { it.id !in readIds }

                _uiState.update {
                    it.copy(
                        announcements = sorted,
                        currentIndex = 0,
                        isLoading = false,
                        showDialog = unread.isNotEmpty(),
                        error = null,
                        currentDetail = null,
                        voteError = null
                    )
                }
                // 拉取当前公告的投票详情
                loadCurrentDetail()
            } catch (e: Exception) {
                Log.e(TAG, "Fetch announcements failed: ${e.message}")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** 选择指定索引的公告（用于历史公告查看） */
    fun selectAnnouncement(index: Int) {
        _uiState.update { state ->
            if (index in state.announcements.indices) {
                state.copy(
                    currentIndex = index,
                    showHistoryList = false,
                    currentDetail = null,
                    voteError = null
                )
            } else state
        }
        // 切换公告后拉取新公告的投票详情
        loadCurrentDetail()
    }

    /** 显示历史公告列表 */
    fun showHistoryList() {
        _uiState.update { it.copy(showHistoryList = true) }
    }

    /** 从历史公告列表返回到内容视图 */
    fun backFromHistory() {
        _uiState.update { it.copy(showHistoryList = false) }
    }

    /**
     * 关闭弹窗并标记所有当前公告为已读。
     * 使用 NonCancellable 确保写入完成，防止用户快速关闭 App 导致写入丢失，
     * 避免下次冷启动因未读公告再次弹出弹窗。
     */
    fun dismiss() {
        val allIds = _uiState.value.announcements.map { it.id }
        viewModelScope.launch {
            withContext(NonCancellable) {
                if (allIds.isNotEmpty()) {
                    settingsRepository.markAllAnnouncementsAsRead(allIds)
                }
            }
            _uiState.update { it.copy(showDialog = false) }
        }
    }

    /** 手动打开公告弹窗（查看过往公告） */
    fun open() {
        if (_uiState.value.announcements.isEmpty()) {
            fetchAnnouncements()
        } else {
            _uiState.update { it.copy(showDialog = true, currentIndex = 0) }
            if (_uiState.value.currentDetail == null) {
                loadCurrentDetail()
            }
        }
    }

    /**
     * 拉取当前公告的投票详情（含选项与已选状态）。
     * 仅在用户已登录时请求，未登录则不展示投票区。
     */
    private fun loadCurrentDetail() {
        val announcement = _uiState.value.currentAnnouncement ?: return
        // 未登录时不拉取详情（投票功能需要登录）
        if (userAuthManager.getCachedAccessToken() == null) return
        _uiState.update { it.copy(isDetailLoading = true, voteError = null) }
        viewModelScope.launch {
            try {
                val response = RetrofitClient.userAuthApi.getAnnouncementDetail(announcement.id)
                if (response.success) {
                    _uiState.update { it.copy(isDetailLoading = false, currentDetail = response.announcement) }
                } else {
                    _uiState.update { it.copy(isDetailLoading = false, currentDetail = null) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch announcement detail failed: ${e.message}")
                _uiState.update { it.copy(isDetailLoading = false, currentDetail = null) }
            }
        }
    }

    /**
     * 提交投票。提交成功后刷新该公告详情以获取最新票数。
     * 同一用户重复提交会覆盖原选择。
     * 投票期间保持 isVoting=true 直至详情刷新完成，避免界面闪回未投票态。
     */
    fun submitVote(optionId: Int) {
        val announcement = _uiState.value.currentAnnouncement ?: return
        if (userAuthManager.getCachedAccessToken() == null) return
        _uiState.update { it.copy(isVoting = true, voteError = null) }
        viewModelScope.launch {
            try {
                val response = RetrofitClient.userAuthApi.submitVote(
                    announcement.id,
                    VoteRequest(optionId)
                )
                if (response.success) {
                    // 投票成功后刷新详情，获取最新票数与已选状态
                    refreshCurrentDetail()
                } else {
                    _uiState.update {
                        it.copy(isVoting = false, voteError = response.message ?: "投票失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Submit vote failed: ${e.message}")
                _uiState.update { it.copy(isVoting = false, voteError = e.message) }
            }
        }
    }

    /** 重新拉取当前公告详情（投票后刷新票数），完成后清除 isVoting */
    private fun refreshCurrentDetail() {
        val announcement = _uiState.value.currentAnnouncement ?: run {
            _uiState.update { it.copy(isVoting = false) }
            return
        }
        viewModelScope.launch {
            try {
                val response = RetrofitClient.userAuthApi.getAnnouncementDetail(announcement.id)
                if (response.success) {
                    _uiState.update {
                        it.copy(currentDetail = response.announcement, isVoting = false)
                    }
                } else {
                    _uiState.update { it.copy(isVoting = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh announcement detail failed: ${e.message}")
                _uiState.update { it.copy(isVoting = false) }
            }
        }
    }

    /** 清除投票错误提示 */
    fun clearVoteError() {
        _uiState.update { it.copy(voteError = null) }
    }
}
