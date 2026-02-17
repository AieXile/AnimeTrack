package com.aiexile.animetrack.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.network.BangumiSearchRequest
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.components.AddAnimeFormState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val searchError: String? = null
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
    private val repository: AnimeRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "AnimeTrack"
    }
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    val animeList: StateFlow<List<Anime>> = repository.getAllAnimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
    
    init {
        viewModelScope.launch {
            animeList.collect { animes ->
                Log.d(TAG, "Database changed, anime count: ${animes.size}")
                _uiState.update { it.copy(isLoading = false) }
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
                formState = AddAnimeFormState(),
                formError = null,
                selectedAnimeId = null
            )
        }
    }
    
    fun hideBottomSheet() {
        Log.d(TAG, "Hiding bottom sheet")
        _uiState.update { 
            it.copy(
                isBottomSheetVisible = false,
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
            val updatedAnime = anime.copy(status = newStatus)
            repository.updateAnime(updatedAnime)
            Log.d(TAG, "Updated anime status: ${anime.title} -> $newStatus")
            clearSelection()
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
            val anime = Anime(
                title = formState.title.trim(),
                totalEpisodes = formState.totalEpisodes,
                watchedEpisodes = formState.watchedEpisodes,
                status = formState.status,
                rating = formState.rating,
                notes = formState.notes.trim(),
                startDate = formState.startDate,
                finishDate = formState.finishDate,
                coverUrl = formState.coverUrl
            )
            
            Log.d(TAG, "Inserting anime: $anime")
            
            try {
                val id = repository.insertAnime(anime)
                Log.d(TAG, "Anime inserted successfully with id: $id")
                
                _uiState.update { 
                    it.copy(
                        isBottomSheetVisible = false,
                        formState = AddAnimeFormState(),
                        formError = null,
                        newlyAddedAnimeId = id,
                        shouldScrollToTop = true
                    )
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
        
        return if (filter == AnimeFilter.ALL) {
            filtered.sortedWith(
                compareBy<Anime> { it.status != AnimeStatus.WATCHING }
                    .thenByDescending { it.startDate ?: Long.MIN_VALUE }
            )
        } else {
            filtered.sortedByDescending { it.startDate ?: Long.MIN_VALUE }
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
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            
            try {
                val response = RetrofitClient.bangumiApi.searchSubjects(
                    BangumiSearchRequest(
                        keyword = query,
                        type = listOf(2),
                        limit = 25
                    )
                )
                
                Log.d(TAG, "Search results: ${response.data.size} items")
                _uiState.update { 
                    it.copy(
                        searchResults = response.data,
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
        val totalEpisodes = subject.episodeCount ?: 12
        
        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    title = subject.displayName,
                    totalEpisodes = totalEpisodes,
                    coverUrl = subject.coverUrl,
                    rating = rating
                ),
                searchResults = emptyList(),
                searchQuery = ""
            )
        }
    }
    
    fun clearSearchResults() {
        _uiState.update { 
            it.copy(
                searchResults = emptyList(),
                searchQuery = "",
                searchError = null
            )
        }
    }
    
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = AppContainer.getAnimeRepository()
            return HomeViewModel(repository) as T
        }
    }
}
