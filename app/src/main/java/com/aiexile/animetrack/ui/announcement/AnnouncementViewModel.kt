package com.aiexile.animetrack.ui.announcement

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.network.Announcement
import com.aiexile.animetrack.data.network.RetrofitClient
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
    val error: String? = null
) {
    val currentAnnouncement: Announcement?
        get() = announcements.getOrNull(currentIndex)

    val hasMultiple: Boolean
        get() = announcements.size > 1
}

class AnnouncementViewModel(
    private val settingsRepository: SettingsRepository
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
                        error = null
                    )
                }
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
                state.copy(currentIndex = index, showHistoryList = false)
            } else state
        }
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
        }
    }
}
