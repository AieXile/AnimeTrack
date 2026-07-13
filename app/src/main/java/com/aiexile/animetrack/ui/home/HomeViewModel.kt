package com.aiexile.animetrack.ui.home

import android.util.Log
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.network.TmdbTvDetail
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.data.sync.BilibiliSyncManager
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.SearchUseCase
import com.aiexile.animetrack.domain.UpdateCheckUseCase
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.util.computeIsFinished
import com.aiexile.animetrack.util.getCurrentWeekday
import com.aiexile.animetrack.util.isAirDateInFuture
import com.aiexile.animetrack.util.resolveSearchError
import com.aiexile.animetrack.ui.components.AddAnimeFormState
import com.aiexile.animetrack.ui.update.UpdateViewModel
import com.aiexile.animetrack.ui.announcement.AnnouncementViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.Calendar

sealed class AutoSyncState {
    object Idle : AutoSyncState()
    object Syncing : AutoSyncState()
    data class Completed(val count: Int) : AutoSyncState()
    data class Failed(val message: String) : AutoSyncState()
}

data class HomeUiState(
    val selectedFilter: AnimeFilter = AnimeFilter.ALL,
    val isLoading: Boolean = true,
    val isBottomSheetVisible: Boolean = false,
    val formState: AddAnimeFormState = AddAnimeFormState(),
    val formError: String? = null,
    val newlyAddedAnimeId: Long? = null,
    val shouldScrollToTop: Boolean = false,
    val selectedAnimeId: Long? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val hasSearched: Boolean = false,
    val searchSource: SearchSource = SearchSource.BANGUMI,
    val showCompletedToast: Boolean = false,
    val showDuplicateToast: Boolean = false,
    val showFormDialog: Boolean = false,
    val highlightedAnimeIds: Set<Long> = emptySet(),
    val todayUpdatePinnedIds: Set<Long> = emptySet(),
    val localSearchQuery: String = "",
    val isLocalSearchActive: Boolean = false
)

enum class AnimeFilter(val displayName: String) {
    ALL("全部"),
    WATCHING("正在观看"),
    COMPLETED("已看完"),
    PLANNED("计划观看"),
    DROPPED("已弃番"),
    HIGH_RATED("高分推荐")
}

class HomeViewModel(
    private val repository: AnimeRepository,
    private val settingsRepository: SettingsRepository,
    val updateViewModel: UpdateViewModel,
    val announcementViewModel: AnnouncementViewModel,
    private val searchUseCase: SearchUseCase,
    private val updateCheckUseCase: UpdateCheckUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "AnimeTrack"
    }
    
    val gridState = LazyGridState()
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var greetingHasAnimated = false
    private var lastAnimatedGreeting = ""
    private val randomGreetingPrefix = listOf("Hi", "Hey", "Hello").random()

    fun resolveGreetingText(customGreeting: String): String {
        return if (customGreeting.isNotEmpty()) customGreeting else randomGreetingPrefix
    }

    fun shouldAnimateGreeting(text: String): Boolean {
        return !greetingHasAnimated || text != lastAnimatedGreeting
    }

    fun onGreetingAnimated(text: String) {
        greetingHasAnimated = true
        lastAnimatedGreeting = text
    }
    
    val animeList: StateFlow<List<Anime>> = repository.getAllAnimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val todayUpdateCount: StateFlow<Int> = animeList.map { animes ->
        val todayWeekday = getCurrentWeekday()
        animes.count { it.airWeekday == todayWeekday && !it.isFinished }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    /** 影响列表排序/分组的筛选参数，用于派生列表的去重防抖 */
    private data class ListParams(
        val filter: AnimeFilter,
        val searchQuery: String,
        val pinnedIds: Set<Long>
    )

    private val listParams: StateFlow<ListParams> = _uiState.map {
        ListParams(it.selectedFilter, it.localSearchQuery, it.todayUpdatePinnedIds)
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ListParams(AnimeFilter.ALL, "", emptySet())
        )

    /**
     * 派生的首页列表：仅在数据或筛选参数变化时重新排序/分组，
     * 配合 distinctUntilChanged 避免 Compose 每帧重复计算。
     */
    val filteredAnimeListItems: StateFlow<List<AnimeListItem>> =
        combine(animeList, listParams) { animes, params ->
            val filtered = getFilteredAnimeList(animes, params.filter, params.searchQuery, params.pinnedIds)
            SeriesMatcher.groupAnimeList(filtered)
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    // 系列堆叠开关：hoist 到 ViewModel，避免 Composable 重建时 collectAsState 初始值闪烁
    // 导致 displayList item 数量变化（true=堆叠少项 / false=拆分多项），从而引发滚动位置漂移
    val seriesStackEnabled: StateFlow<Boolean> = settingsRepository.seriesStackEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    private val _autoSyncState = MutableStateFlow<AutoSyncState>(AutoSyncState.Idle)
    val autoSyncState: StateFlow<AutoSyncState> = _autoSyncState.asStateFlow()

    private var autoSyncTriggered = false

    fun highlightTodayUpdates() {
        val todayWeekday = getCurrentWeekday()
        val todayAnimeIds = animeList.value
            .filter { it.airWeekday == todayWeekday && !it.isFinished }
            .map { it.id.toLong() }
            .toSet()
        if (todayAnimeIds.isEmpty()) return
        _uiState.update {
            it.copy(
                highlightedAnimeIds = todayAnimeIds,
                todayUpdatePinnedIds = todayAnimeIds
            )
        }
    }

    fun clearHighlight() {
        if (_uiState.value.highlightedAnimeIds.isNotEmpty()) {
            _uiState.update { it.copy(highlightedAnimeIds = emptySet()) }
        }
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }
    
    init {
        viewModelScope.launch {
            // 仅需在首次拿到数据后关闭 loading，无需常驻收集
            val animes = animeList.first()
            Log.d(TAG, "Initial data loaded, anime count: ${animes.size}")
            _uiState.update { it.copy(isLoading = false) }
        }
        // App 启动时重新识别 seriesKey 并持久化（仅一次）
        viewModelScope.launch {
            repository.reassignSeriesKeys()
        }
        updateViewModel.checkForUpdate()
        announcementViewModel.fetchAnnouncements()
        checkAiringAnimeUpdates()
        viewModelScope.launch {
            val syncManager = AppContainer.getSyncManager()
            syncManager.syncRemoteToLocal()
        }
        triggerAutoSync()
    }

    fun triggerAutoSync() {
        if (autoSyncTriggered) return
        autoSyncTriggered = true
        viewModelScope.launch {
            try {
                val bilibiliAuthManager = AppContainer.getBilibiliAuthManager()
                val isLoggedIn = bilibiliAuthManager.isLoggedIn.first()
                if (!isLoggedIn) return@launch

                val autoSyncEnabled = bilibiliAuthManager.bilibiliAutoSync.first()
                if (!autoSyncEnabled) return@launch

                val lastSyncTime = bilibiliAuthManager.lastSyncTime.first()
                val oneHourAgo = System.currentTimeMillis() - 3600000
                if (lastSyncTime > oneHourAgo) return@launch

                _autoSyncState.value = AutoSyncState.Syncing

                val bilibiliSyncManager = AppContainer.getBilibiliSyncManager()
                val result = bilibiliSyncManager.fetchAndSyncFiltered()

                _autoSyncState.value = if (result.isSuccess) {
                    AutoSyncState.Completed(result.getOrDefault(0))
                } else {
                    Log.e(TAG, "Auto sync failed: ${result.exceptionOrNull()?.message}")
                    AutoSyncState.Failed(result.exceptionOrNull()?.message ?: "同步失败")
                }

                // 3 秒后自动消失
                delay(3000)
                _autoSyncState.value = AutoSyncState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Auto sync error", e)
                _autoSyncState.value = AutoSyncState.Failed(e.message ?: "同步失败")
                delay(3000)
                _autoSyncState.value = AutoSyncState.Idle
            }
        }
    }

    fun checkAiringAnimeUpdates() {
        viewModelScope.launch {
            updateCheckUseCase.checkAiringAnimeUpdates()
        }
    }
    
    fun setFilter(filter: AnimeFilter) {
        _uiState.update { 
            it.copy(
                selectedFilter = filter,
                selectedAnimeId = null
            )
        }
    }
    
    fun showBottomSheet() {
        Log.d(TAG, "Showing bottom sheet")
        _uiState.update { 
            it.copy(
                isBottomSheetVisible = true,
                showFormDialog = false,
                formState = AddAnimeFormState(),
                formError = null,
                selectedAnimeId = null,
                searchResults = emptyList(),
                searchQuery = "",
                hasSearched = false,
                searchError = null
            )
        }
    }
    
    fun hideBottomSheet() {
        Log.d(TAG, "Hiding bottom sheet")
        _uiState.update { 
            it.copy(
                isBottomSheetVisible = false,
                showFormDialog = false,
                formState = AddAnimeFormState(),
                formError = null
            )
        }
    }
    
    fun selectAnime(animeId: Long?) {
        _uiState.update { it.copy(selectedAnimeId = animeId) }
    }
    
    fun clearSelection() {
        _uiState.update { it.copy(selectedAnimeId = null) }
    }
    
    fun updateAnimeStatus(anime: Anime, newStatus: AnimeStatus) {
        viewModelScope.launch {
            val updatedAnime = if (newStatus == AnimeStatus.COMPLETED) {
                val newWatchedEpisodes = if (anime.totalEpisodes > 0) {
                    anime.totalEpisodes
                } else {
                    anime.watchedEpisodes
                }
                anime.copy(
                    status = newStatus,
                    isFinished = true,
                    watchedEpisodes = newWatchedEpisodes,
                    finishDate = if (anime.finishDate == null) System.currentTimeMillis() else anime.finishDate
                )
            } else {
                anime.copy(
                    status = newStatus,
                    isFinished = false
                )
            }
            repository.updateAnime(updatedAnime)
            Log.d(TAG, "Updated anime status: ${anime.title} -> $newStatus")

            if (anime.bangumiId != null) {
                val syncManager = AppContainer.getSyncManager()
                if (newStatus == AnimeStatus.COMPLETED && anime.totalEpisodes > 0) {
                    syncManager.pushProgressThenStatus(anime.bangumiId, anime.totalEpisodes, newStatus)
                } else {
                    syncManager.pushStatusToRemote(anime.bangumiId, newStatus)
                }
            }

            if (newStatus == AnimeStatus.COMPLETED) {
                val showToast = settingsRepository.completedToastEnabled.first()
                if (showToast) {
                    _uiState.update { it.copy(showCompletedToast = true) }
                }
            }
            clearSelection()
        }
    }

    fun dismissCompletedToast() {
        _uiState.update { it.copy(showCompletedToast = false) }
    }

    fun dismissDuplicateToast() {
        _uiState.update { it.copy(showDuplicateToast = false) }
    }

    fun clearAnimeUpdateFlag(animeId: Int) {
        viewModelScope.launch {
            repository.clearNewUpdate(animeId)
        }
    }

    fun hideFormDialog() {
        _uiState.update { it.copy(showFormDialog = false) }
    }

    fun showManualAddDialog() {
        _uiState.update {
            it.copy(
                formState = AddAnimeFormState(),
                formError = null,
                showFormDialog = true
            )
        }
    }
    
    fun deleteAnime(anime: Anime) {
        viewModelScope.launch {
            repository.deleteAnime(anime)
            Log.d(TAG, "Deleted anime: ${anime.title}")
            clearSelection()
        }
    }
    
    fun updateFormState(formState: AddAnimeFormState) {
        val error = validateForm(formState)
        _uiState.update { 
            it.copy(
                formState = formState,
                formError = error
            )
        }
    }
    
    private fun validateForm(formState: AddAnimeFormState): String? {
        return when {
            formState.title.isBlank() -> "请输入番剧名称"
            formState.watchedEpisodes > formState.totalEpisodes -> "已看集数不能超过总集数"
            else -> null
        }
    }
    
    fun saveAnime() {
        val formState = _uiState.value.formState
        
        Log.d(TAG, "saveAnime called with title: ${formState.title}")
        
        val error = validateForm(formState)
        if (error != null) {
            Log.d(TAG, "Validation failed: $error")
            _uiState.update { it.copy(formError = error) }
            return
        }
        
        viewModelScope.launch {
            if (formState.bangumiId != null) {
                val existing = repository.getAnimeByBangumiId(formState.bangumiId)
                if (existing != null) {
                    Log.d(TAG, "Duplicate bangumiId: ${formState.bangumiId}")
                    _uiState.update { it.copy(showDuplicateToast = true) }
                    return@launch
                }
            }

            if (formState.tmdbId != null) {
                val existing = repository.getAnimeByTmdbId(formState.tmdbId)
                if (existing != null) {
                    Log.d(TAG, "Duplicate tmdbId: ${formState.tmdbId}")
                    _uiState.update { it.copy(showDuplicateToast = true) }
                    return@launch
                }
            }

            val anime = Anime(
                title = formState.title.trim(),
                totalEpisodes = formState.totalEpisodes,
                watchedEpisodes = formState.watchedEpisodes,
                status = formState.status,
                rating = formState.rating,
                notes = formState.notes.trim(),
                startDate = formState.startDate,
                finishDate = formState.finishDate,
                coverUrl = formState.coverUrl,
                summary = formState.summary,
                bangumiId = formState.bangumiId,
                tmdbId = formState.tmdbId,
                airDate = formState.airDate,
                airWeekday = formState.airWeekday,
                currentEpisodes = formState.currentEpisodes,
                isFinished = computeIsFinished(formState.airDate, formState.totalEpisodes, formState.status)
            )
            
            Log.d(TAG, "Inserting anime: $anime")
            
            try {
                val id = repository.insertAnime(anime)
                Log.d(TAG, "Anime inserted successfully with id: $id")
                
                _uiState.update { 
                    it.copy(
                        isBottomSheetVisible = false,
                        showFormDialog = false,
                        formState = AddAnimeFormState(),
                        formError = null,
                        newlyAddedAnimeId = id,
                        shouldScrollToTop = true
                    )
                }

                repository.downloadCoverAsync(
                    animeId = id.toInt(),
                    coverUrl = anime.coverUrl,
                    bangumiId = anime.bangumiId,
                    tmdbId = anime.tmdbId
                )

                if (anime.bangumiId != null && (anime.airWeekday == null || anime.airDate == null || anime.summary == null)) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val detail = repository.fetchBangumiDetail(anime.bangumiId) ?: return@launch
                            val dbAnime = repository.getAnimeById(id.toInt()) ?: return@launch
                            val updatedAirDate = dbAnime.airDate ?: detail.date
                            val updatedAirWeekday = dbAnime.airWeekday ?: detail.airWeekday
                            val updated = dbAnime.copy(
                                airWeekday = updatedAirWeekday,
                                airDate = updatedAirDate,
                                summary = dbAnime.summary ?: detail.summary?.cleanSummary(),
                                isFinished = computeIsFinished(updatedAirDate, dbAnime.totalEpisodes, dbAnime.status)
                            )
                            repository.updateAnimeInternal(updated)
                            Log.d(TAG, "Backfilled detail for animeId=$id")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to backfill detail for animeId=$id", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert anime", e)
            }
        }
    }
    
    fun onScrollCompleted() {
        _uiState.update { it.copy(shouldScrollToTop = false) }
    }
    
    fun onHighlightCompleted() {
        _uiState.update { it.copy(newlyAddedAnimeId = null) }
    }
    
    fun getFilteredAnimeList(animeList: List<Anime>, filter: AnimeFilter, searchQuery: String = "", pinnedIds: Set<Long> = emptySet()): List<Anime> {
        Log.d(TAG, "getFilteredAnimeList: ${animeList.size} items, filter: $filter, searchQuery: $searchQuery")
        
        val filtered = when (filter) {
            AnimeFilter.ALL -> animeList
            AnimeFilter.WATCHING -> animeList.filter { it.status == AnimeStatus.WATCHING }
            AnimeFilter.COMPLETED -> animeList.filter { it.status == AnimeStatus.COMPLETED }
            AnimeFilter.PLANNED -> animeList.filter { it.status == AnimeStatus.PLANNED }
            AnimeFilter.DROPPED -> animeList.filter { it.status == AnimeStatus.DROPPED }
            AnimeFilter.HIGH_RATED -> animeList.filter { (it.rating ?: 0f) >= 4.5f }
        }
        
        val sorted = when (filter) {
            AnimeFilter.ALL -> {
                filtered.sortedWith(
                    compareBy<Anime> { it.status != AnimeStatus.WATCHING }
                        .thenByDescending { anime ->
                            when (anime.status) {
                                AnimeStatus.COMPLETED -> anime.finishDate ?: Long.MIN_VALUE
                                else -> anime.startDate ?: Long.MIN_VALUE
                            }
                        }
                )
            }
            AnimeFilter.COMPLETED -> {
                filtered.sortedByDescending { it.finishDate ?: Long.MIN_VALUE }
            }
            else -> {
                filtered.sortedByDescending { it.startDate ?: Long.MIN_VALUE }
            }
        }

        return if (searchQuery.isNotBlank()) {
            sorted.filter { it.title.contains(searchQuery, ignoreCase = true) }
        } else if (pinnedIds.isNotEmpty()) {
            val pinned = sorted.filter { it.id.toLong() in pinnedIds }
            val unpinned = sorted.filter { it.id.toLong() !in pinnedIds }
            pinned + unpinned
        } else {
            sorted
        }
    }

    fun updateLocalSearchQuery(query: String) {
        _uiState.update { it.copy(localSearchQuery = query) }
    }
    
    fun startLocalSearch() {
        _uiState.update { it.copy(isLocalSearchActive = true) }
    }
    
    fun clearLocalSearch() {
        _uiState.update { it.copy(localSearchQuery = "", isLocalSearchActive = false) }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchSource(source: SearchSource) {
        _uiState.update { it.copy(searchSource = source) }
    }

    fun searchAnime() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null) }
            return
        }

        val source = _uiState.value.searchSource
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = true) }

            try {
                val results = searchUseCase.search(query, source)
                Log.d(TAG, "Search results: ${results.size} items (source=$source)")
                _uiState.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = "搜索失败: ${resolveSearchError(e)}",
                        searchResults = emptyList()
                    )
                }
            }
        }
    }
    
    fun selectSearchResult(result: SearchResult) {
        val totalEpisodes = result.episodeCount ?: 12
        val summary = result.summary?.cleanSummary()

        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    title = result.title,
                    totalEpisodes = totalEpisodes,
                    coverUrl = result.coverUrl,
                    rating = result.rating,
                    summary = summary,
                    bangumiId = if (result.source == SearchSource.BANGUMI) result.sourceId else null,
                    tmdbId = if (result.source == SearchSource.TMDB) result.sourceId else null
                ),
                showFormDialog = true
            )
        }

        viewModelScope.launch {
            try {
                when (result.source) {
                    SearchSource.BANGUMI -> {
                        val detail = repository.fetchBangumiDetail(result.sourceId) ?: return@launch
                        val apiEps = detail.eps
                        val apiTotalEps = detail.totalEpisodes

                        val mainEps = if (apiEps != null && apiEps > 0) apiEps else 0
                        val allEps = if (apiTotalEps != null && apiTotalEps > 0) apiTotalEps else 0

                        val finalTotal = when {
                            mainEps > 0 -> mainEps
                            allEps > 0 -> allEps
                            else -> 0
                        }

                        _uiState.update { state ->
                            val updatedAirDate = detail.date ?: state.formState.airDate
                            val isNotYetAired = isAirDateInFuture(updatedAirDate)
                            state.copy(
                                formState = state.formState.copy(
                                    totalEpisodes = if (finalTotal > 0) finalTotal else state.formState.totalEpisodes,
                                    currentEpisodes = if (finalTotal > 0) 0 else if (mainEps > 0) 0 else state.formState.currentEpisodes,
                                    airDate = updatedAirDate,
                                    airWeekday = detail.airWeekday ?: state.formState.airWeekday,
                                    summary = detail.summary?.cleanSummary()
                                        ?: state.formState.summary,
                                    status = if (isNotYetAired) AnimeStatus.PLANNED else state.formState.status
                                )
                            )
                        }
                    }
                    SearchSource.TMDB -> {
                        val detail = repository.getTmdbTvDetail(result.sourceId)
                        val eps = detail.numberOfEpisodes
                        val finalTotal = if (eps != null && eps > 0) eps else 0

                        _uiState.update { state ->
                            val updatedAirDate = detail.firstAirDate ?: state.formState.airDate
                            val isNotYetAired = isAirDateInFuture(updatedAirDate)
                            state.copy(
                                formState = state.formState.copy(
                                    totalEpisodes = if (finalTotal > 0) finalTotal else state.formState.totalEpisodes,
                                    airDate = updatedAirDate,
                                    summary = detail.overview?.cleanSummary()
                                        ?: state.formState.summary,
                                    status = if (isNotYetAired) AnimeStatus.PLANNED else state.formState.status
                                )
                            )
                        }
                    }
                    SearchSource.ALL -> { /* ALL 模式下结果已标记具体来源，不会走到这里 */ }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch detail for ${result.source}Id: ${result.sourceId}", e)
            }
        }
    }
    
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = AppContainer.getAnimeRepository()
            val settingsRepository = AppContainer.getSettingsRepository()
            val updateRepository = UpdateRepository()
            val updateViewModel = UpdateViewModel(updateRepository, settingsRepository)
            val announcementViewModel = AnnouncementViewModel(settingsRepository)
            val searchUseCase = SearchUseCase(repository)
            val updateCheckUseCase = UpdateCheckUseCase(repository)
            return HomeViewModel(repository, settingsRepository, updateViewModel, announcementViewModel, searchUseCase, updateCheckUseCase) as T
        }
    }
}
