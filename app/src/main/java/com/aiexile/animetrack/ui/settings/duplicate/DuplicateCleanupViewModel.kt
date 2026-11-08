package com.aiexile.animetrack.ui.settings.duplicate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.sync.DuplicateTitleDetector
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 同标题重复卡清理：扫描 → 确认 → 身份移交后本地删除。
 *
 * 所有数据访问都在 ViewModel 里，Compose 层只负责展示与勾选。
 */
class DuplicateCleanupViewModel(
    private val repository: AnimeRepository
) : ViewModel() {

    data class GroupUi(
        val key: String,
        val title: String,
        val totalEpisodes: Int,
        val items: List<Anime>,
        val keeperId: Int,
        val deleteIds: Set<Int>
    )

    data class CleanResult(
        val cleanedGroups: Int,
        val deletedCards: Int,
        val remainingGroups: Int,
        /** 中途失败：已完成的删除保留，提示用户重试 */
        val failed: Boolean = false
    )

    data class UiState(
        val isLoading: Boolean = false,
        val groups: List<GroupUi> = emptyList(),
        val isCleaning: Boolean = false,
        val result: CleanResult? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun scan() {
        val current = _state.value
        if (current.isLoading || current.isCleaning) return
        _state.update { it.copy(isLoading = true, result = null) }
        viewModelScope.launch {
            val groups = loadGroups()
            _state.update { it.copy(isLoading = false, groups = groups) }
        }
    }

    fun setKeeper(groupKey: String, animeId: Int) {
        _state.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.key != groupKey) group
                    else group.copy(keeperId = animeId, deleteIds = group.deleteIds - animeId)
                }
            )
        }
    }

    fun toggleDelete(groupKey: String, animeId: Int) {
        _state.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    if (group.key != groupKey) group
                    else if (group.keeperId == animeId) group
                    else group.copy(
                        deleteIds = if (animeId in group.deleteIds) {
                            group.deleteIds - animeId
                        } else {
                            group.deleteIds + animeId
                        }
                    )
                }
            )
        }
    }

    /** 确认清理：先移交被删卡同步身份，再只删本地行，最后反馈清了几组、剩几组 */
    fun confirm() {
        val snapshot = _state.value
        if (snapshot.isCleaning) return
        val selected = snapshot.groups.filter { it.deleteIds.isNotEmpty() }
        if (selected.isEmpty()) return

        _state.update { it.copy(isCleaning = true) }
        viewModelScope.launch {
            var cleanedGroups = 0
            var deletedCards = 0
            var failed = false
            try {
                selected.forEach { group ->
                    var keeper = group.items.firstOrNull { it.id == group.keeperId } ?: return@forEach
                    val duplicates = group.items.filter { it.id in group.deleteIds && it.id != keeper.id }
                    if (duplicates.isEmpty()) return@forEach
                    duplicates.forEach { duplicate ->
                        repository.handoffDuplicateIdentity(keeper, duplicate)
                        // 一轮可能清多张：身份补写后重新读取，避免后续循环用旧 keeper 覆盖已落的同步 ID
                        keeper = repository.getAnimeById(keeper.id) ?: keeper
                        repository.deleteDuplicateAnimeLocally(duplicate)
                    }
                    cleanedGroups++
                    deletedCards += duplicates.size
                }
            } catch (e: Exception) {
                // 中途失败：保留已完成部分，重新扫描后提示用户重试，避免按钮永久禁用
                failed = true
                Log.e("DuplicateCleanup", "清理同标题重复卡失败", e)
            }
            val remaining = runCatching { loadGroups() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    isCleaning = false,
                    groups = remaining,
                    result = CleanResult(
                        cleanedGroups = cleanedGroups,
                        deletedCards = deletedCards,
                        remainingGroups = remaining.size,
                        failed = failed
                    )
                )
            }
        }
    }

    private suspend fun loadGroups(): List<GroupUi> {
        val animes = repository.getAllAnimes().first()
        return DuplicateTitleDetector.findGroups(animes).map { group ->
            val keeper = DuplicateTitleDetector.defaultKeeper(group.animes)
            GroupUi(
                key = group.key,
                title = group.title,
                totalEpisodes = group.totalEpisodes,
                items = group.animes,
                keeperId = keeper.id,
                deleteIds = DuplicateTitleDetector.conservativeDeletions(group)
            )
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DuplicateCleanupViewModel(AppContainer.getAnimeRepository()) as T
        }
    }
}
