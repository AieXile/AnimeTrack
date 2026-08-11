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
    val voteError: String? = null,
    /**
     * 存在未读公告但被更新弹窗阻塞，等待更新弹窗关闭后由 HomeViewModel 释放显示。
     * 仅在自动拉取（[fetchAnnouncements] 默认）场景下使用，实现"更新日志优先、公告随后"的串行显示。
     */
    val pendingShow: Boolean = false,
    /** 本地已读公告 ID 集合，用于历史列表区分已读/未读样式 */
    val readAnnouncementIds: Set<Int> = emptySet()
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
     * 拉取公告列表，若有未读公告则标记为待显示。
     *
     * @param immediate 是否立即显示弹窗。
     * - false（默认，自动拉取场景）：不直接显示，仅设置 [AnnouncementUiState.pendingShow]，
     *   由 HomeViewModel 协调在更新弹窗关闭后再显示，实现"更新日志优先、公告随后"的串行显示。
     * - true（手动打开场景）：拉取完成后直接显示弹窗，不等待。
     */
    fun fetchAnnouncements(immediate: Boolean = false) {
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
                val hasUnread = unread.isNotEmpty()
                // 优先定位到最新一条未读公告；若全部已读则指向最新一条
                val firstUnreadIndex = sorted.indexOfFirst { it.id !in readIds }.let { if (it < 0) 0 else it }

                _uiState.update {
                    it.copy(
                        announcements = sorted,
                        currentIndex = firstUnreadIndex,
                        isLoading = false,
                        showDialog = immediate && hasUnread,
                        pendingShow = !immediate && hasUnread,
                        readAnnouncementIds = readIds,
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

    /**
     * 释放被更新弹窗阻塞的待显示公告。
     * 由 HomeViewModel 在更新弹窗关闭后调用，实现"更新日志优先、公告随后"的串行显示。
     */
    fun releasePendingShow() {
        if (_uiState.value.pendingShow) {
            _uiState.update { it.copy(showDialog = true, pendingShow = false) }
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
     * 关闭弹窗，仅标记当前查看的这条公告为已读。
     * 使用 NonCancellable 确保写入完成，防止用户快速关闭 App 导致写入丢失。
     * 不再一次性标记全部公告已读，未读的旧公告会在下次启动时按"最新未读优先"逐条提示。
     */
    fun dismiss() {
        val currentId = _uiState.value.currentAnnouncement?.id
        viewModelScope.launch {
            if (currentId != null) {
                withContext(NonCancellable) {
                    settingsRepository.markAnnouncementAsRead(currentId)
                }
            }
            _uiState.update {
                it.copy(
                    showDialog = false,
                    readAnnouncementIds = if (currentId != null)
                        it.readAnnouncementIds + currentId else it.readAnnouncementIds
                )
            }
        }
    }

    /** 手动打开公告弹窗（查看过往公告），立即显示不等待更新弹窗 */
    fun open() {
        if (_uiState.value.announcements.isEmpty()) {
            fetchAnnouncements(immediate = true)
        } else {
            viewModelScope.launch {
                // 重新读取已读集合，优先定位到最新一条未读公告
                val readIds = settingsRepository.getReadAnnouncementIds()
                val list = _uiState.value.announcements
                val firstUnreadIndex = list.indexOfFirst { it.id !in readIds }.let { if (it < 0) 0 else it }
                _uiState.update {
                    it.copy(
                        showDialog = true,
                        currentIndex = firstUnreadIndex,
                        readAnnouncementIds = readIds,
                        pendingShow = false,
                        currentDetail = null,
                        voteError = null
                    )
                }
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
