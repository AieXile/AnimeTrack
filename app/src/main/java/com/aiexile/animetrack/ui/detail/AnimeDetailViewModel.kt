package com.aiexile.animetrack.ui.detail

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.SearchUseCase
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.util.ShareCardRenderer
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.util.computeIsFinished
import com.aiexile.animetrack.util.formatAirDate
import com.aiexile.animetrack.util.resolveSearchError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CoverSearchState(
    val isVisible: Boolean = false,
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val source: SearchSource = SearchSource.BANGUMI
)

data class EditState(
    val isEditing: Boolean = false,
    val title: String = "",
    val coverUrl: String? = null,
    val totalEpisodes: Int = 0,
    val airDate: String? = null,
    val airWeekday: Int? = null,
    val bangumiId: Int? = null,
    val tmdbId: Int? = null,
    val summary: String? = null,
    val isEditingTitle: Boolean = false,
    val localCoverUri: String? = null
)

sealed class MatchSearchState {
    object Idle : MatchSearchState()
    object Searching : MatchSearchState()
    data class Results(val results: List<SearchResult>) : MatchSearchState()
    data class Failed(val message: String) : MatchSearchState()
}

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
    private val application: Application,
    private val repository: AnimeRepository,
    private val settingsRepository: SettingsRepository,
    private val animeId: Int,
    private val searchUseCase: SearchUseCase
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

    private val _matchSearchState = MutableStateFlow<MatchSearchState>(MatchSearchState.Idle)
    val matchSearchState: StateFlow<MatchSearchState> = _matchSearchState.asStateFlow()

    private val _matchSearchQuery = MutableStateFlow("")
    val matchSearchQuery: StateFlow<String> = _matchSearchQuery.asStateFlow()

    private val _showMatchDialog = MutableStateFlow(false)
    val showMatchDialog: StateFlow<Boolean> = _showMatchDialog.asStateFlow()

    val missingSearchSource: SearchSource?
        get() {
            val anime = animeFlow.value ?: return null
            return when {
                anime.bangumiId == null -> SearchSource.BANGUMI
                anime.tmdbId == null -> SearchSource.TMDB
                else -> null
            }
        }

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
                        // 仅在尚未获取过简介时才触发获取，避免无网时反复加载
                        val needsDetail = anime.summaryFetched != true
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

                val detail = repository.fetchBangumiDetail(bangumiId)
                if (detail == null) {
                    // 获取失败（网络错误等），不标记 summaryFetched，下次有网时可重试
                    return@launch
                }

                val apiEps = detail.eps
                val apiTotalEps = detail.totalEpisodes

                val mainEps = if (apiEps != null && apiEps > 0) apiEps else 0
                val allEps = if (apiTotalEps != null && apiTotalEps > 0) apiTotalEps else 0

                val finalTotalEpisodes = when {
                    mainEps > 0 -> mainEps
                    allEps > 0 -> allEps
                    else -> anime.totalEpisodes
                }
                val finalCurrentEpisodes = if (mainEps > 0 || allEps > 0) 0 else anime.currentEpisodes

                val newWatchedEpisodes = when {
                    anime.status == AnimeStatus.COMPLETED && anime.watchedEpisodes == 0 && finalTotalEpisodes > 0 -> finalTotalEpisodes
                    finalTotalEpisodes > 0 && anime.watchedEpisodes > finalTotalEpisodes -> finalTotalEpisodes
                    else -> anime.watchedEpisodes
                }

                val isFinished = computeIsFinished(
                    airDate = detail.date,
                    totalEpisodes = finalTotalEpisodes,
                    localStatus = anime.status
                )

                val updatedAnime = anime.copy(
                    summary = detail.summary?.cleanSummary()?.takeIf { it.isNotBlank() } ?: anime.summary,
                    airDate = detail.date ?: anime.airDate,
                    airWeekday = detail.airWeekday ?: anime.airWeekday,
                    rating = detail.score?.toFloat() ?: anime.rating,
                    totalEpisodes = finalTotalEpisodes,
                    currentEpisodes = finalCurrentEpisodes,
                    watchedEpisodes = newWatchedEpisodes,
                    isFinished = isFinished,
                    // 标记已获取过简介，避免无网时反复触发加载
                    summaryFetched = true
                )

                repository.updateAnimeInternal(updatedAnime)

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
                repository.updateAnimeInternal(anime.copy(isFinished = isFinished))
            }
        }
    }

    private fun computeAirStatus(anime: Anime): String? {
        val rawAirDate = anime.airDate ?: return null
        // 统一格式化为 yyyy-MM-dd，兼容 UTC ISO 8601 格式
        val airDate = formatAirDate(rawAirDate) ?: rawAirDate
        val totalEpisodes = anime.totalEpisodes
        val currentEpisodes = anime.currentEpisodes

        return try {
            val startDate = LocalDate.parse(airDate, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()

            if (today.isBefore(startDate)) {
                "${airDate} 开播"
            } else if (anime.isFinished || anime.status == AnimeStatus.COMPLETED) {
                "已完结"
            } else {
                val diffWeeks = ChronoUnit.WEEKS.between(startDate, today)
                if (totalEpisodes > 0 && diffWeeks > (totalEpisodes + 1)) {
                    "已完结"
                } else if (totalEpisodes > 0) {
                    val weekdayName = anime.airWeekday?.toWeekdayName()
                    if (weekdayName != null) {
                        "每周${weekdayName}更新"
                    } else {
                        "更新中"
                    }
                } else {
                    if (currentEpisodes > 0) {
                        "连载中 (更新至 ${currentEpisodes} 集)"
                    } else {
                        "连载中"
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun adjustWatchedEpisodes(delta: Int) {
        val anime = animeFlow.value ?: return
        val maxEps = anime.effectiveMaxEpisodes
        val newCount = (anime.watchedEpisodes + delta).coerceIn(0, if (maxEps > 0) maxEps else Int.MAX_VALUE)
        if (newCount == anime.watchedEpisodes) return
        applyWatchedEpisodesUpdate(anime, newCount)
    }

    fun updateWatchedEpisodes(newCount: Int) {
        val anime = animeFlow.value ?: return
        val maxEps = anime.effectiveMaxEpisodes
        val validCount = newCount.coerceIn(0, if (maxEps > 0) maxEps else Int.MAX_VALUE)
        if (validCount == anime.watchedEpisodes) return
        applyWatchedEpisodesUpdate(anime, validCount)
    }

    private fun applyWatchedEpisodesUpdate(anime: Anime, newCount: Int) {
        val autoComplete = autoCompleteEnabled.value

        var updatedAnime = anime.copy(watchedEpisodes = newCount)

        // 计划观看 → 正在观看
        if (anime.status == AnimeStatus.PLANNED && newCount > 0) {
            updatedAnime = updatedAnime.copy(status = AnimeStatus.WATCHING)
        }

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

        viewModelScope.launch {
            repository.updateAnime(updatedAnime)

            if (anime.bangumiId != null) {
                val syncManager = AppContainer.getSyncManager()
                if (updatedAnime.status != anime.status) {
                    syncManager.pushProgressThenStatus(anime.bangumiId, newCount, updatedAnime.status)
                } else {
                    syncManager.pushProgressToRemote(anime.bangumiId, newCount)
                }
            }
        }
    }

    fun dismissCompletedToast() {
        _showCompletedToast.value = false
    }

    fun incrementEpisode() {
        val anime = animeFlow.value ?: return
        val maxEps = anime.effectiveMaxEpisodes
        if (maxEps == 0 || anime.watchedEpisodes < maxEps) {
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

    fun showMatchDialog() {
        val anime = animeFlow.value ?: return
        _showMatchDialog.value = true
        _matchSearchState.value = MatchSearchState.Idle
        _matchSearchQuery.value = anime.title
    }

    fun hideMatchDialog() {
        _showMatchDialog.value = false
        _matchSearchState.value = MatchSearchState.Idle
    }

    fun updateMatchSearchQuery(query: String) {
        _matchSearchQuery.value = query
    }

    fun searchForMatch() {
        val query = _matchSearchQuery.value.trim()
        if (query.isBlank()) return

        val anime = animeFlow.value ?: return
        val searchSource = if (anime.bangumiId == null) SearchSource.BANGUMI else SearchSource.TMDB

        viewModelScope.launch {
            _matchSearchState.value = MatchSearchState.Searching
            try {
                val results = searchUseCase.search(query, searchSource)
                _matchSearchState.value = MatchSearchState.Results(results)
            } catch (e: Exception) {
                _matchSearchState.value = MatchSearchState.Failed(e.message ?: "搜索失败")
            }
        }
    }

    fun selectMatchResult(result: SearchResult) {
        val anime = animeFlow.value ?: return
        viewModelScope.launch {
            val updatedAnime = when (result.source) {
                SearchSource.BANGUMI -> anime.copy(bangumiId = result.sourceId)
                SearchSource.TMDB -> anime.copy(tmdbId = result.sourceId)
                SearchSource.ALL -> anime // ALL 不会出现在匹配结果中
            }
            repository.updateAnime(updatedAnime)
            _showMatchDialog.value = false
            _matchSearchState.value = MatchSearchState.Idle
        }
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

    fun updateEditAirWeekday(weekday: Int?) {
        _editState.value = _editState.value.copy(airWeekday = weekday)
    }

    fun updateEditSummary(summary: String) {
        _editState.value = _editState.value.copy(summary = summary)
    }

    fun updateEditTotalEpisodes(total: Int) {
        _editState.value = _editState.value.copy(totalEpisodes = total.coerceAtLeast(0))
    }

    fun adjustEditTotalEpisodes(delta: Int) {
        val current = _editState.value.totalEpisodes
        _editState.value = _editState.value.copy(totalEpisodes = (current + delta).coerceAtLeast(0))
    }

    fun updateEditCoverUrl(url: String?) {
        _editState.value = _editState.value.copy(coverUrl = url, localCoverUri = null)
    }

    fun updateEditLocalCoverUri(uri: String?) {
        if (uri == null) {
            _editState.value = _editState.value.copy(localCoverUri = null)
            return
        }
        viewModelScope.launch {
            try {
                val savedPath = copyCoverToInternalStorage(uri)
                if (savedPath != null) {
                    _editState.value = _editState.value.copy(localCoverUri = savedPath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy cover image", e)
            }
        }
    }

    private fun copyCoverToInternalStorage(contentUri: String): String? {
        return try {
            val uri = android.net.Uri.parse(contentUri)
            val coversDir = File(application.filesDir, "covers").apply { mkdirs() }
            val fileName = "cover_${animeId}_${System.currentTimeMillis()}.jpg"
            val destFile = File(coversDir, fileName)

            application.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy cover to internal storage", e)
            null
        }
    }

    private fun deleteOldCoverFile(oldCoverUrl: String?) {
        if (oldCoverUrl == null) return
        try {
            val file = File(oldCoverUrl)
            if (file.exists() && file.absolutePath.startsWith(application.filesDir.absolutePath)) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete old cover file", e)
        }
    }

    fun setEditingTitle(editing: Boolean) {
        _editState.value = _editState.value.copy(isEditingTitle = editing)
    }

    fun saveEditChanges() {
        val anime = animeFlow.value ?: return
        val edit = _editState.value
        if (!edit.isEditing) return

        viewModelScope.launch {
            val newCoverUrl = edit.localCoverUri ?: edit.coverUrl
            if (edit.localCoverUri != null && anime.coverUrl != edit.localCoverUri) {
                deleteOldCoverFile(anime.coverUrl)
            }
            val clampedWatched = if (edit.totalEpisodes > 0) {
                anime.watchedEpisodes.coerceAtMost(edit.totalEpisodes)
            } else {
                anime.watchedEpisodes
            }
            val updatedAnime = anime.copy(
                title = edit.title,
                coverUrl = newCoverUrl,
                totalEpisodes = edit.totalEpisodes,
                watchedEpisodes = clampedWatched,
                airDate = edit.airDate,
                airWeekday = edit.airWeekday,
                bangumiId = edit.bangumiId,
                summary = edit.summary ?: anime.summary
            )
            repository.updateAnime(updatedAnime)
            _editState.value = EditState()
        }
    }

    fun deleteAnime() {
        val anime = animeFlow.value ?: return
        viewModelScope.launch {
            repository.deleteAnime(anime)
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
                },
                watchedEpisodes = when {
                    // 手动切换为已看完时，自动将观看进度设为总集数
                    newStatus == AnimeStatus.COMPLETED
                        && anime.totalEpisodes > 0
                        && anime.watchedEpisodes < anime.totalEpisodes -> anime.totalEpisodes
                    // 从满进度切回非已看完时，自动退一集，避免满进度却显示正在观看
                    newStatus != AnimeStatus.COMPLETED
                        && anime.totalEpisodes > 0
                        && anime.watchedEpisodes >= anime.totalEpisodes -> anime.totalEpisodes - 1
                    else -> anime.watchedEpisodes
                }
            )
            repository.updateAnime(updatedAnime)

            // 手动切换为已看完时显示完结撒花
            if (newStatus == AnimeStatus.COMPLETED
                && anime.status != AnimeStatus.COMPLETED
                && completedToastEnabled.value
            ) {
                _showCompletedToast.value = true
            }

            if (anime.bangumiId != null) {
                val syncManager = AppContainer.getSyncManager()
                syncManager.pushStatusToRemote(anime.bangumiId, newStatus)
            }
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

    fun updateCoverSearchSource(source: SearchSource) {
        _coverSearch.value = _coverSearch.value.copy(source = source)
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

        val source = _coverSearch.value.source
        viewModelScope.launch {
            _coverSearch.value = _coverSearch.value.copy(
                isSearching = true,
                error = null
            )

            try {
                val results = searchUseCase.search(query, source)
                _coverSearch.value = _coverSearch.value.copy(
                    results = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                _coverSearch.value = _coverSearch.value.copy(
                    isSearching = false,
                    error = "搜索失败: ${resolveSearchError(e)}",
                    results = emptyList()
                )
            }
        }
    }

    fun selectCoverResult(result: SearchResult) {
        val newCoverUrl = result.coverUrl ?: return

        if (_editState.value.isEditing) {
            viewModelScope.launch {
                when (result.source) {
                    SearchSource.BANGUMI -> {
                        val detail = tryFetchDetail(result.sourceId)
                        val apiEps = detail?.eps
                        val apiTotalEps = detail?.totalEpisodes
                        val mainEps = if (apiEps != null && apiEps > 0) apiEps else 0
                        val allEps = if (apiTotalEps != null && apiTotalEps > 0) apiTotalEps else 0
                        val subjectEps = result.episodeCount
                        val finalEps = when {
                            mainEps > 0 -> mainEps
                            allEps > 0 -> allEps
                            subjectEps != null && subjectEps > 0 -> subjectEps
                            else -> _editState.value.totalEpisodes
                        }
                        val newSummary = detail?.summary?.cleanSummary() ?: result.summary ?: _editState.value.summary
                        _editState.value = _editState.value.copy(
                            title = result.title,
                            coverUrl = newCoverUrl,
                            localCoverUri = null,
                            totalEpisodes = finalEps,
                            airDate = detail?.date ?: result.airDate ?: _editState.value.airDate,
                            airWeekday = detail?.airWeekday ?: _editState.value.airWeekday,
                            bangumiId = result.sourceId,
                            summary = newSummary
                        )
                    }
                    SearchSource.TMDB -> {
                        val detail = try { repository.getTmdbTvDetail(result.sourceId) } catch (_: Exception) { null }
                        val eps = detail?.numberOfEpisodes
                        val finalEps = if (eps != null && eps > 0) eps else result.episodeCount ?: _editState.value.totalEpisodes
                        val newSummary = detail?.overview?.cleanSummary() ?: result.summary ?: _editState.value.summary
                        _editState.value = _editState.value.copy(
                            title = result.title,
                            coverUrl = newCoverUrl,
                            localCoverUri = null,
                            totalEpisodes = finalEps,
                            airDate = detail?.firstAirDate ?: result.airDate ?: _editState.value.airDate,
                            tmdbId = result.sourceId,
                            summary = newSummary
                        )
                    }
                    SearchSource.ALL -> { /* ALL 模式下结果已标记具体来源，不会走到这里 */ }
                }
            }
        } else {
            val anime = animeFlow.value ?: return
            viewModelScope.launch {
                when (result.source) {
                    SearchSource.BANGUMI -> {
                        val detail = tryFetchDetail(result.sourceId)
                        val summary = result.summary ?: detail?.summary?.cleanSummary()
                        val airDate = detail?.date ?: result.airDate ?: anime.airDate
                        val airWeekday = detail?.airWeekday ?: anime.airWeekday

                        val apiEps = detail?.eps
                        val apiTotalEps = detail?.totalEpisodes
                        val mainEps = if (apiEps != null && apiEps > 0) apiEps else 0
                        val allEps = if (apiTotalEps != null && apiTotalEps > 0) apiTotalEps else 0
                        val finalTotalEpisodes = when {
                            mainEps > 0 -> mainEps
                            allEps > 0 -> allEps
                            else -> anime.totalEpisodes
                        }
                        val clampedWatched = if (finalTotalEpisodes > 0) {
                            anime.watchedEpisodes.coerceAtMost(finalTotalEpisodes)
                        } else {
                            anime.watchedEpisodes
                        }

                        val isFinished = computeIsFinished(
                            airDate = airDate,
                            totalEpisodes = finalTotalEpisodes,
                            localStatus = anime.status
                        )

                        val updatedAnime = anime.copy(
                            coverUrl = newCoverUrl,
                            summary = summary ?: anime.summary,
                            airDate = airDate,
                            airWeekday = airWeekday,
                            totalEpisodes = finalTotalEpisodes,
                            watchedEpisodes = clampedWatched,
                            isFinished = isFinished,
                            bangumiId = result.sourceId
                        )
                        repository.updateAnime(updatedAnime)
                        repository.downloadCoverAsync(
                            animeId = updatedAnime.id,
                            coverUrl = updatedAnime.coverUrl,
                            bangumiId = updatedAnime.bangumiId,
                            tmdbId = updatedAnime.tmdbId
                        )
                    }
                    SearchSource.TMDB -> {
                        val detail = try { repository.getTmdbTvDetail(result.sourceId) } catch (_: Exception) { null }
                        val eps = detail?.numberOfEpisodes
                        val finalTotalEpisodes = if (eps != null && eps > 0) eps else anime.totalEpisodes
                        val clampedWatched = if (finalTotalEpisodes > 0) {
                            anime.watchedEpisodes.coerceAtMost(finalTotalEpisodes)
                        } else {
                            anime.watchedEpisodes
                        }
                        val airDate = detail?.firstAirDate ?: result.airDate ?: anime.airDate
                        val summary = detail?.overview?.cleanSummary() ?: result.summary ?: anime.summary

                        val isFinished = computeIsFinished(
                            airDate = airDate,
                            totalEpisodes = finalTotalEpisodes,
                            localStatus = anime.status
                        )

                        val updatedAnime = anime.copy(
                            coverUrl = newCoverUrl,
                            summary = summary,
                            airDate = airDate,
                            totalEpisodes = finalTotalEpisodes,
                            watchedEpisodes = clampedWatched,
                            isFinished = isFinished,
                            tmdbId = result.sourceId
                        )
                        repository.updateAnime(updatedAnime)
                        repository.downloadCoverAsync(
                            animeId = updatedAnime.id,
                            coverUrl = updatedAnime.coverUrl,
                            bangumiId = updatedAnime.bangumiId,
                            tmdbId = updatedAnime.tmdbId
                        )
                    }
                    SearchSource.ALL -> { /* ALL 模式下结果已标记具体来源，不会走到这里 */ }
                }
            }
        }
        _coverSearch.value = CoverSearchState()
    }

    private suspend fun tryFetchDetail(bangumiId: Int): com.aiexile.animetrack.data.network.BangumiSubjectDetail? {
        return repository.fetchBangumiDetail(bangumiId)
    }

    private suspend fun tryFetchSummary(bangumiId: Int): String? {
        return repository.fetchBangumiDetail(bangumiId)?.summary?.cleanSummary()
    }

    fun shareAnime(context: android.content.Context, shareNotes: String) {
        val anime = uiState.value.anime ?: return
        viewModelScope.launch {
            try {
                val bitmap = ShareCardRenderer.renderShareCard(context, anime, shareNotes, settingsRepository)
                val file = ShareCardRenderer.saveShareImage(context, bitmap)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, anime.title)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "分享番剧"))
                // 延迟清理临时文件，给系统分享足够时间读取
                viewModelScope.launch {
                    delay(5000)
                    ShareCardRenderer.cleanupShareImages(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "分享失败", e)
            }
        }
    }

    class Factory(
        private val animeId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val application = AppContainer.getApplication()
            val repository = AppContainer.getAnimeRepository()
            val settingsRepository = AppContainer.getSettingsRepository()
            val searchUseCase = SearchUseCase(repository)
            return AnimeDetailViewModel(application, repository, settingsRepository, animeId, searchUseCase) as T
        }
    }
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
