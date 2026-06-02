package com.aiexile.animetrack.ui.home

import android.util.Log
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.components.AddAnimeFormState
import com.aiexile.animetrack.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import java.util.Calendar

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
    val searchResults: List<BangumiSubject> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val hasSearched: Boolean = false,
    val showCompletedToast: Boolean = false,
    val showDuplicateToast: Boolean = false,
    val showFormDialog: Boolean = false,
    val highlightedAnimeIds: Set<Long> = emptySet()
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
    val updateViewModel: UpdateViewModel
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
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val todayWeekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let {
        when (it) {
            Calendar.SUNDAY -> 7
            else -> it - 1
        }
    }

    val todayUpdateCount: StateFlow<Int> = animeList.map { animes ->
        animes.count { it.airWeekday == todayWeekday && !it.isFinished }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = 0
    )

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    fun highlightTodayUpdates() {
        val todayAnimeIds = animeList.value
            .filter { it.airWeekday == todayWeekday && !it.isFinished }
            .map { it.id.toLong() }
            .toSet()
        if (todayAnimeIds.isEmpty()) return
        _uiState.update { it.copy(highlightedAnimeIds = todayAnimeIds) }
        viewModelScope.launch {
            delay(250)
            _uiState.update { it.copy(highlightedAnimeIds = emptySet()) }
        }
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }
    
    init {
        viewModelScope.launch {
            animeList.collect { animes ->
                Log.d(TAG, "Database changed, anime count: ${animes.size}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        updateViewModel.checkForUpdate()
        checkAiringAnimeUpdates()
        viewModelScope.launch {
            val syncManager = AppContainer.getSyncManager()
            syncManager.syncRemoteToLocal()
        }
    }

    fun checkAiringAnimeUpdates() {
        viewModelScope.launch {
            try {
                val airingAnimes = repository.getAiringAnimesWithBangumiId()
                if (airingAnimes.isEmpty()) return@launch

                Log.d(TAG, "Checking updates for ${airingAnimes.size} airing animes")

                val deferredResults = airingAnimes.map { anime ->
                    async {
                        try {
                            val bangumiId = anime.bangumiId ?: return@async null
                            val detail = RetrofitClient.bangumiApi.getSubjectDetail(bangumiId)

                            val remoteEps = detail.eps ?: 0
                            val remoteTotal = detail.totalEpisodes ?: 0

                            val resolvedTotal = when {
                                remoteEps > 0 -> remoteEps
                                remoteTotal > 0 -> remoteTotal
                                else -> anime.totalEpisodes
                            }

                            if (remoteEps > anime.currentEpisodes) {
                                Log.d(TAG, "New episode found: ${anime.title} local=${anime.currentEpisodes} remote=$remoteEps")
                                val updatedAnime = anime.copy(
                                    currentEpisodes = remoteEps,
                                    hasNewUpdate = true
                                )
                                repository.updateAnime(updatedAnime)
                                return@async anime.title to remoteEps
                            } else if (resolvedTotal > 0 && anime.totalEpisodes == 0) {
                                val updatedAnime = anime.copy(
                                    totalEpisodes = resolvedTotal,
                                    hasNewUpdate = false
                                )
                                repository.updateAnime(updatedAnime)
                            }
                            null
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to check update for: ${anime.title}", e)
                            null
                        }
                    }
                }

                val results = deferredResults.awaitAll().filterNotNull()
                if (results.isNotEmpty()) {
                    results.forEach { (title, eps) ->
                        Log.d(TAG, "Update detected: $title -> $eps episodes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkAiringAnimeUpdates failed", e)
            }
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
                airDate = formState.airDate,
                airWeekday = formState.airWeekday,
                currentEpisodes = formState.currentEpisodes
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
                    bangumiId = anime.bangumiId
                )

                if (anime.bangumiId != null && (anime.airWeekday == null || anime.airDate == null || anime.summary == null)) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val detail = RetrofitClient.bangumiApi.getSubjectDetail(anime.bangumiId)
                            val dbAnime = repository.getAnimeById(id.toInt()) ?: return@launch
                            val updated = dbAnime.copy(
                                airWeekday = dbAnime.airWeekday ?: detail.airWeekday,
                                airDate = dbAnime.airDate ?: detail.date,
                                summary = dbAnime.summary ?: detail.summary?.trim()?.replace(Regex("\n{3,}"), "\n\n")
                            )
                            repository.updateAnime(updated)
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
    
    fun getFilteredAnimeList(animeList: List<Anime>, filter: AnimeFilter): List<Anime> {
        Log.d(TAG, "getFilteredAnimeList: ${animeList.size} items, filter: $filter")
        
        val filtered = when (filter) {
            AnimeFilter.ALL -> animeList
            AnimeFilter.WATCHING -> animeList.filter { it.status == AnimeStatus.WATCHING }
            AnimeFilter.COMPLETED -> animeList.filter { it.status == AnimeStatus.COMPLETED }
            AnimeFilter.PLANNED -> animeList.filter { it.status == AnimeStatus.PLANNED }
            AnimeFilter.DROPPED -> animeList.filter { it.status == AnimeStatus.DROPPED }
            AnimeFilter.HIGH_RATED -> animeList.filter { (it.rating ?: 0f) >= 4.5f }
        }
        
        return when (filter) {
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
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
    
    fun searchAnime() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = true) }
            
            try {
                val results = repository.searchBangumi(query)
                Log.d(TAG, "Search results: ${results.size} items")
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
                        searchError = "搜索失败: ${e.message}",
                        searchResults = emptyList()
                    )
                }
            }
        }
    }
    
    fun selectSearchResult(subject: BangumiSubject) {
        val rating = subject.score?.toFloat()
        val totalEpisodes = subject.episodeCount ?: 0
        val summary = subject.summary?.trim()?.replace(Regex("\n{3,}"), "\n\n")

        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    title = subject.displayName,
                    totalEpisodes = totalEpisodes,
                    coverUrl = subject.coverUrl,
                    rating = rating,
                    summary = summary,
                    bangumiId = subject.id
                ),
                showFormDialog = true
            )
        }

        viewModelScope.launch {
            try {
                val detail = RetrofitClient.bangumiApi.getSubjectDetail(subject.id)
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
                    state.copy(
                        formState = state.formState.copy(
                            totalEpisodes = if (finalTotal > 0) finalTotal else state.formState.totalEpisodes,
                            currentEpisodes = if (finalTotal > 0) 0 else if (mainEps > 0) 0 else state.formState.currentEpisodes,
                            airDate = detail.date ?: state.formState.airDate,
                            airWeekday = detail.airWeekday ?: state.formState.airWeekday,
                            summary = detail.summary?.trim()?.replace(Regex("\n{3,}"), "\n\n")
                                ?: state.formState.summary
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch detail for bangumiId: ${subject.id}", e)
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
            return HomeViewModel(repository, settingsRepository, updateViewModel) as T
        }
    }
}
