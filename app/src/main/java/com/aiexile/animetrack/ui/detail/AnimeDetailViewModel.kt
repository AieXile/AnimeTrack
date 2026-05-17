package com.aiexile.animetrack.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CoverSearchState(
    val isVisible: Boolean = false,
    val query: String = "",
    val results: List<BangumiSubject> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

data class EditState(
    val isEditing: Boolean = false,
    val title: String = "",
    val coverUrl: String? = null,
    val totalEpisodes: Int = 0,
    val airDate: String? = null,
    val airWeekday: Int? = null,
    val bangumiId: Int? = null,
    val summary: String? = null,
    val isEditingTitle: Boolean = false,
    val localCoverUri: String? = null
)

data class AnimeDetailUiState(
    val anime: Anime? = null,
    val isLoading: Boolean = true,
    val isFetchingDetail: Boolean = false,
    val error: String? = null,
    val notesText: String = "",
    val isEditingNotes: Boolean = false,
    val coverSearch: CoverSearchState = CoverSearchState(),
    val airStatusText: String? = null,
    val showCompletedToast: Boolean = false,
    val editState: EditState = EditState()
)

class AnimeDetailViewModel(
    private val repository: AnimeRepository,
    private val settingsRepository: SettingsRepository,
    private val animeId: Int
) : ViewModel() {

    companion object {
        private const val TAG = "AnimeDetailViewModel"
    }

    val animeFlow: StateFlow<Anime?> = repository.observeAnimeById(animeId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isFetchingDetail = MutableStateFlow(false)

    private val _notesText = MutableStateFlow<String?>(null)

    private val _isEditingNotes = MutableStateFlow(false)

    private val _coverSearch = MutableStateFlow(CoverSearchState())

    private val _showCompletedToast = MutableStateFlow(false)

    private val _editState = MutableStateFlow(EditState())

    private data class UiExtras(
        val isFetchingDetail: Boolean = false,
        val notesText: String = "",
        val isEditingNotes: Boolean = false,
        val coverSearch: CoverSearchState = CoverSearchState(),
        val showCompletedToast: Boolean = false,
        val editState: EditState = EditState()
    )

    private val uiExtras: StateFlow<UiExtras> = combine(
        combine(_isFetchingDetail, _notesText, _isEditingNotes) { fetching, notes, editing ->
            Triple(fetching, notes, editing)
        },
        combine(_coverSearch, _showCompletedToast, _editState) { search, toast, edit ->
            Triple(search, toast, edit)
        }
    ) { (fetching, notes, editing), (search, toast, edit) ->
        UiExtras(
            isFetchingDetail = fetching,
            notesText = notes ?: "",
            isEditingNotes = editing,
            coverSearch = search,
            showCompletedToast = toast,
            editState = edit
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiExtras()
    )

    val uiState: StateFlow<AnimeDetailUiState> = combine(
        animeFlow,
        uiExtras
    ) { anime, extras ->
        val resolvedNotes = if (_notesText.value != null) _notesText.value!! else anime?.notes ?: ""
        AnimeDetailUiState(
            anime = anime,
            isLoading = anime == null && !extras.isFetchingDetail,
            isFetchingDetail = extras.isFetchingDetail,
            error = if (anime == null && !extras.isFetchingDetail) "未找到该番剧" else null,
            notesText = resolvedNotes,
            isEditingNotes = extras.isEditingNotes,
            coverSearch = extras.coverSearch,
            airStatusText = anime?.let { computeAirStatus(it) },
            showCompletedToast = extras.showCompletedToast,
            editState = extras.editState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnimeDetailUiState()
    )

    val autoCompleteEnabled: StateFlow<Boolean> = settingsRepository.autoCompleteEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    val completedToastEnabled: StateFlow<Boolean> = settingsRepository.completedToastEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    private var hasFetchedDetail = false

    init {
        viewModelScope.launch {
            animeFlow.collect { anime ->
                if (anime != null && !hasFetchedDetail) {
                    if (anime.bangumiId != null) {
                        val needsDetail = anime.summary.isNullOrBlank()
                            || anime.airDate == null
                            || anime.airWeekday == null
                        if (needsDetail) {
                            hasFetchedDetail = true
                            fetchDetailFromApi(anime)
                        } else {
                            refreshFinishStatus(anime)
                        }
                    }
                }
            }
        }
    }

    private fun fetchDetailFromApi(anime: Anime) {
        viewModelScope.launch {
            _isFetchingDetail.value = true

            try {
                val bangumiId = anime.bangumiId ?: return@launch
                Log.d(TAG, "Fetching detail from API for bangumiId: $bangumiId")

                val detail = RetrofitClient.bangumiApi.getSubjectDetail(bangumiId)

                val detailTotalEpisodes = detail.eps ?: detail.totalEpisodes
                val newTotalEpisodes = if (detailTotalEpisodes != null && detailTotalEpisodes > 0)
                    detailTotalEpisodes else anime.totalEpisodes
                val newWatchedEpisodes = if (
                    anime.status == AnimeStatus.COMPLETED
                    && anime.watchedEpisodes == 0
                    && newTotalEpisodes > 0
                ) newTotalEpisodes else anime.watchedEpisodes

                val isFinished = computeIsFinished(
                    airDate = detail.date,
                    totalEpisodes = newTotalEpisodes,
                    localStatus = anime.status
                )

                val updatedAnime = anime.copy(
                    summary = detail.summary?.cleanSummary() ?: anime.summary,
                    airDate = detail.date ?: anime.airDate,
                    airWeekday = detail.airWeekday ?: anime.airWeekday,
                    rating = detail.score?.toFloat() ?: anime.rating,
                    totalEpisodes = newTotalEpisodes,
                    watchedEpisodes = newWatchedEpisodes,
                    isFinished = isFinished
                )

                repository.updateAnime(updatedAnime)

                Log.d(TAG, "Detail fetched and updated: summary=${detail.summary?.take(50)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch detail", e)
            } finally {
                _isFetchingDetail.value = false
            }
        }
    }

    private fun refreshFinishStatus(anime: Anime) {
        val isFinished = computeIsFinished(
            airDate = anime.airDate,
            totalEpisodes = anime.totalEpisodes,
            localStatus = anime.status
        )
        if (isFinished != anime.isFinished) {
            viewModelScope.launch {
                repository.updateAnime(anime.copy(isFinished = isFinished))
            }
        }
    }

    private fun computeIsFinished(
        airDate: String?,
        totalEpisodes: Int,
        localStatus: AnimeStatus
    ): Boolean {
        if (localStatus == AnimeStatus.COMPLETED) return true

        if (airDate == null || totalEpisodes <= 0) return false

        return try {
            val startDate = LocalDate.parse(airDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()
            val diffWeeks = ChronoUnit.WEEKS.between(startDate, today)
            diffWeeks > (totalEpisodes + 1)
        } catch (e: Exception) {
            false
        }
    }

    private fun computeAirStatus(anime: Anime): String? {
        val airDate = anime.airDate ?: return null
        val totalEpisodes = anime.totalEpisodes

        return try {
            val startDate = LocalDate.parse(airDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()

            if (today.isBefore(startDate)) {
                "${airDate} 开播"
            } else if (anime.isFinished || anime.status == AnimeStatus.COMPLETED) {
                "全 ${totalEpisodes} 集 / 已完结"
            } else {
                val diffWeeks = ChronoUnit.WEEKS.between(startDate, today)
                if (diffWeeks > (totalEpisodes + 1)) {
                    "全 ${totalEpisodes} 集 / 已完结"
                } else {
                    val weekdayName = anime.airWeekday?.toWeekdayName()
                    if (weekdayName != null) {
                        "每周${weekdayName}更新"
                    } else {
                        "更新中"
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun adjustWatchedEpisodes(delta: Int) {
        val anime = animeFlow.value ?: return
        val newCount = (anime.watchedEpisodes + delta).coerceIn(0, anime.totalEpisodes)
        if (newCount == anime.watchedEpisodes) return
        applyWatchedEpisodesUpdate(anime, newCount)
    }

    fun updateWatchedEpisodes(newCount: Int) {
        val anime = animeFlow.value ?: return
        val validCount = newCount.coerceIn(0, anime.totalEpisodes)
        if (validCount == anime.watchedEpisodes) return
        applyWatchedEpisodesUpdate(anime, validCount)
    }

    private fun applyWatchedEpisodesUpdate(anime: Anime, newCount: Int) {
        val autoComplete = autoCompleteEnabled.value

        var updatedAnime = anime.copy(watchedEpisodes = newCount)

        if (autoComplete) {
            if (newCount == anime.totalEpisodes && anime.totalEpisodes > 0 && anime.status != AnimeStatus.COMPLETED) {
                updatedAnime = updatedAnime.copy(
                    status = AnimeStatus.COMPLETED,
                    isFinished = true,
                    finishDate = System.currentTimeMillis()
                )
                if (completedToastEnabled.value) {
                    _showCompletedToast.value = true
                }
            } else if (newCount < anime.totalEpisodes && anime.status == AnimeStatus.COMPLETED) {
                updatedAnime = updatedAnime.copy(
                    status = AnimeStatus.WATCHING,
                    isFinished = false,
                    finishDate = null
                )
            }
        }

        viewModelScope.launch { repository.updateAnime(updatedAnime) }
    }

    fun dismissCompletedToast() {
        _showCompletedToast.value = false
    }

    fun incrementEpisode() {
        val anime = animeFlow.value ?: return
        if (anime.watchedEpisodes < anime.totalEpisodes) {
            updateWatchedEpisodes(anime.watchedEpisodes + 1)
        }
    }

    fun decrementEpisode() {
        val anime = animeFlow.value ?: return
        if (anime.watchedEpisodes > 0) {
            updateWatchedEpisodes(anime.watchedEpisodes - 1)
        }
    }

    fun updateNotes(notes: String) {
        _notesText.value = notes
    }

    fun saveNotes() {
        val anime = animeFlow.value ?: return
        val notes = _notesText.value ?: anime.notes

        viewModelScope.launch {
            val updatedAnime = anime.copy(notes = notes)
            repository.updateAnime(updatedAnime)
            _isEditingNotes.value = false
            _notesText.value = null
        }
    }

    fun setEditingNotes(editing: Boolean) {
        _isEditingNotes.value = editing
    }

    fun enterEditMode() {
        val anime = animeFlow.value ?: return
        _editState.value = EditState(
            isEditing = true,
            title = anime.title,
            coverUrl = anime.coverUrl,
            totalEpisodes = anime.totalEpisodes,
            airDate = anime.airDate,
            airWeekday = anime.airWeekday,
            bangumiId = anime.bangumiId,
            summary = anime.summary
        )
    }

    fun exitEditMode() {
        _editState.value = EditState()
    }

    fun hasUnsavedChanges(): Boolean {
        val anime = animeFlow.value ?: return false
        val edit = _editState.value
        if (!edit.isEditing) return false
        return edit.title != anime.title
            || edit.coverUrl != anime.coverUrl
            || edit.localCoverUri != null
            || edit.totalEpisodes != anime.totalEpisodes
            || edit.airDate != anime.airDate
            || edit.airWeekday != anime.airWeekday
            || edit.bangumiId != anime.bangumiId
            || edit.summary != anime.summary
    }

    fun updateEditTitle(title: String) {
        _editState.value = _editState.value.copy(title = title)
    }

    fun updateEditCoverUrl(url: String?) {
        _editState.value = _editState.value.copy(coverUrl = url, localCoverUri = null)
    }

    fun updateEditLocalCoverUri(uri: String?) {
        _editState.value = _editState.value.copy(localCoverUri = uri)
    }

    fun setEditingTitle(editing: Boolean) {
        _editState.value = _editState.value.copy(isEditingTitle = editing)
    }

    fun saveEditChanges() {
        val anime = animeFlow.value ?: return
        val edit = _editState.value
        if (!edit.isEditing) return

        viewModelScope.launch {
            val updatedAnime = anime.copy(
                title = edit.title,
                coverUrl = edit.localCoverUri ?: edit.coverUrl,
                totalEpisodes = edit.totalEpisodes,
                airDate = edit.airDate,
                airWeekday = edit.airWeekday,
                bangumiId = edit.bangumiId,
                summary = edit.summary ?: anime.summary
            )
            repository.updateAnime(updatedAnime)
            _editState.value = EditState()
        }
    }

    fun updateStatus(newStatus: AnimeStatus) {
        val anime = animeFlow.value ?: return

        viewModelScope.launch {
            val isFinished = newStatus == AnimeStatus.COMPLETED || anime.isFinished
            val updatedAnime = anime.copy(
                status = newStatus,
                isFinished = isFinished,
                finishDate = if (newStatus == AnimeStatus.COMPLETED && anime.finishDate == null) {
                    System.currentTimeMillis()
                } else {
                    anime.finishDate
                }
            )
            repository.updateAnime(updatedAnime)
        }
    }

    fun updateFinishDate(finishDate: Long?) {
        val anime = animeFlow.value ?: return

        viewModelScope.launch {
            val updatedAnime = anime.copy(finishDate = finishDate)
            repository.updateAnime(updatedAnime)
        }
    }

    fun showCoverSearch() {
        val anime = animeFlow.value ?: return
        _coverSearch.value = CoverSearchState(
            isVisible = true,
            query = anime.title
        )
    }

    fun hideCoverSearch() {
        _coverSearch.value = CoverSearchState(
            isVisible = false,
            results = emptyList(),
            error = null,
            isSearching = false
        )
    }

    fun updateCoverSearchQuery(query: String) {
        _coverSearch.value = _coverSearch.value.copy(query = query)
    }

    fun searchCover() {
        val query = _coverSearch.value.query.trim()
        if (query.isBlank()) {
            _coverSearch.value = _coverSearch.value.copy(
                results = emptyList(),
                error = null
            )
            return
        }

        viewModelScope.launch {
            _coverSearch.value = _coverSearch.value.copy(
                isSearching = true,
                error = null
            )

            try {
                val results = repository.searchBangumi(query)
                _coverSearch.value = _coverSearch.value.copy(
                    results = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _coverSearch.value = _coverSearch.value.copy(
                    isSearching = false,
                    error = "搜索失败: ${e.message}",
                    results = emptyList()
                )
            }
        }
    }

    fun selectCoverResult(subject: BangumiSubject) {
        val newCoverUrl = subject.coverUrl ?: return

        if (_editState.value.isEditing) {
            viewModelScope.launch {
                val detail = tryFetchDetail(subject.id)
                val detailEps = detail?.eps ?: detail?.totalEpisodes ?: subject.episodeCount
                val finalEps = if (detailEps != null && detailEps > 0) detailEps else _editState.value.totalEpisodes
                val newSummary = detail?.summary?.cleanSummary() ?: subject.summary ?: _editState.value.summary
                _editState.value = _editState.value.copy(
                    title = subject.displayName,
                    coverUrl = newCoverUrl,
                    localCoverUri = null,
                    totalEpisodes = finalEps,
                    airDate = detail?.date ?: _editState.value.airDate,
                    airWeekday = detail?.airWeekday ?: _editState.value.airWeekday,
                    bangumiId = subject.id,
                    summary = newSummary
                )
            }
        } else {
            val anime = animeFlow.value ?: return
            viewModelScope.launch {
                val detail = tryFetchDetail(subject.id)
                val summary = subject.summary
                    ?: detail?.summary?.cleanSummary()
                val airDate = detail?.date ?: anime.airDate
                val airWeekday = detail?.airWeekday ?: anime.airWeekday

                val isFinished = computeIsFinished(
                    airDate = airDate,
                    totalEpisodes = anime.totalEpisodes,
                    localStatus = anime.status
                )

                val updatedAnime = anime.copy(
                    coverUrl = newCoverUrl,
                    summary = summary ?: anime.summary,
                    airDate = airDate,
                    airWeekday = airWeekday,
                    isFinished = isFinished
                )
                repository.updateAnime(updatedAnime)
            }
        }
        _coverSearch.value = CoverSearchState()
    }

    private suspend fun tryFetchDetail(bangumiId: Int): com.aiexile.animetrack.data.network.BangumiSubjectDetail? {
        return try {
            RetrofitClient.bangumiApi.getSubjectDetail(bangumiId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch detail for bangumiId: $bangumiId", e)
            null
        }
    }

    private suspend fun tryFetchSummary(bangumiId: Int): String? {
        return try {
            RetrofitClient.bangumiApi.getSubjectDetail(bangumiId).summary?.cleanSummary()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch summary for bangumiId: $bangumiId", e)
            null
        }
    }

    class Factory(
        private val animeId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = AppContainer.getAnimeRepository()
            val settingsRepository = AppContainer.getSettingsRepository()
            return AnimeDetailViewModel(repository, settingsRepository, animeId) as T
        }
    }
}

private fun String.cleanSummary(): String {
    return trim().replace(Regex("\n{3,}"), "\n\n")
}

private fun Int.toWeekdayName(): String {
    return when (this) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> ""
    }
}
