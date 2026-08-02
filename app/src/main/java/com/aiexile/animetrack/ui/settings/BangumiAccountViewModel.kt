package com.aiexile.animetrack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.sync.BangumiSyncManager
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 同步操作类型 */
enum class BangumiSyncAction { PULL, PUSH }

/** 单次同步的运行时状态 */
sealed class BangumiSyncState {
    data object Idle : BangumiSyncState()
    data class Syncing(val action: BangumiSyncAction) : BangumiSyncState()
    data class Success(val action: BangumiSyncAction, val count: Int) : BangumiSyncState()
    data class Failed(val action: BangumiSyncAction, val message: String) : BangumiSyncState()
}

data class BangumiAccountUiState(
    val nickname: String? = null,
    val avatar: String? = null,
    val syncState: BangumiSyncState = BangumiSyncState.Idle
)

class BangumiAccountViewModel(
    private val authManager: AuthManager,
    private val syncManager: BangumiSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BangumiAccountUiState())
    val uiState: StateFlow<BangumiAccountUiState> = _uiState.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = authManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // 先用持久化数据填充 UI（避免闪烁），再后台静默刷新
        viewModelScope.launch {
            authManager.userNickname.collect { nickname ->
                _uiState.value = _uiState.value.copy(nickname = nickname)
            }
        }
        viewModelScope.launch {
            authManager.userAvatar.collect { avatar ->
                _uiState.value = _uiState.value.copy(avatar = avatar)
            }
        }
        refreshProfile()
    }

    /** 后台静默拉取最新资料并写回持久化（不显示 loading，不闪烁） */
    fun refreshProfile() {
        viewModelScope.launch {
            if (!authManager.isLoggedIn.first()) return@launch
            try {
                val profile = withContext(Dispatchers.IO) {
                    RetrofitClient.bangumiApi.getMyProfile()
                }
                authManager.saveUserProfile(
                    avatar = profile.avatar?.bestUrl,
                    nickname = profile.nickname,
                    bangumiId = profile.id
                )
            } catch (_: Exception) {
                // 静默失败，保持持久化数据
            }
        }
    }

    /** 全量拉取：远程 → 本地 */
    fun pullFromRemote() {
        if (_uiState.value.syncState is BangumiSyncState.Syncing) return
        _uiState.value = _uiState.value.copy(syncState = BangumiSyncState.Syncing(BangumiSyncAction.PULL))
        viewModelScope.launch {
            // syncRemoteToLocal 内部已切换到 Dispatchers.IO 并吞掉异常
            syncManager.syncRemoteToLocal()
            _uiState.value = _uiState.value.copy(
                syncState = BangumiSyncState.Success(BangumiSyncAction.PULL, 0)
            )
        }
    }

    /** 全量推送：本地 → 远程 */
    fun pushToRemote() {
        if (_uiState.value.syncState is BangumiSyncState.Syncing) return
        _uiState.value = _uiState.value.copy(syncState = BangumiSyncState.Syncing(BangumiSyncAction.PUSH))
        viewModelScope.launch {
            val result = syncManager.syncLocalToRemote()
            _uiState.value = _uiState.value.copy(
                syncState = result.fold(
                    onSuccess = { count ->
                        BangumiSyncState.Success(BangumiSyncAction.PUSH, count)
                    },
                    onFailure = { e ->
                        BangumiSyncState.Failed(BangumiSyncAction.PUSH, e.message ?: "Unknown")
                    }
                )
            )
        }
    }

    /** 重置同步状态为 Idle */
    fun resetSyncState() {
        _uiState.value = _uiState.value.copy(syncState = BangumiSyncState.Idle)
    }

    /** 退出登录 */
    fun logout() {
        viewModelScope.launch {
            authManager.logout()
            _uiState.value = _uiState.value.copy(syncState = BangumiSyncState.Idle)
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BangumiAccountViewModel(
                AppContainer.getAuthManager(),
                AppContainer.getSyncManager()
            ) as T
        }
    }
}
