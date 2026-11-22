package com.aiexile.animetrack.ui.player.local

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkManager
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.media.LocalMediaScanScheduler
import com.aiexile.animetrack.data.media.LocalMediaSourceEntity
import com.aiexile.animetrack.data.media.LocalMediaAnimeGroup
import com.aiexile.animetrack.data.media.LocalMediaArchiveEntity
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.domain.SearchUseCase
import com.aiexile.animetrack.domain.playback.tmdbKey
import com.aiexile.animetrack.domain.playback.toArchive
import com.aiexile.animetrack.domain.playback.year
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.data.media.LocalScanStatus
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LocalMediaActionError { ADD_FOLDER, WRONG_FOLDER, REAUTHORIZE, SCAN_REQUEST, REMOVE_SOURCE, MATCH, VISIBILITY }

internal data class LocalMediaUiState(
    val sources: List<LocalMediaSourceEntity> = emptyList(),
    val selectedSourceId: Long? = null,
    val animeOptions: List<Pair<Int, String>> = emptyList(),
    val scan: LocalScanStatus? = null,
    val error: LocalMediaActionError? = null,
    val hiddenCount: Int = 0,
    val matchSearch: LocalMatchSearch = LocalMatchSearch()
)

internal data class LocalMatchSearch(
    val loading: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val failed: Boolean = false,
    val searched: Boolean = false
)

internal data class LocalMediaMatchTarget(
    val sourceId: Long,
    val title: String,
    val query: String,
    val mediaId: Long? = null,
    val movie: Boolean = false,
    val card: LocalMediaAnimeGroup? = null
)

class LocalMediaViewModel internal constructor(
    private val application: Application,
    private val mediaRepository: LocalMediaRepository,
    private val animeRepository: AnimeRepository,
    private val scheduler: LocalMediaScanScheduler,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalMediaUiState())
    internal val uiState: StateFlow<LocalMediaUiState> = _uiState.asStateFlow()

    // 返回播放器时要立即重放已加载的页面，否则空网格会把恢复的滚动索引夹回顶部。
    private class SourcePages(
        val sourceId: Long,
        val job: Job,
        val groups: Flow<PagingData<LocalMediaAnimeGroup>>,
        val unclassified: Flow<PagingData<LocalMediaEntity>>
    )

    private var sourcePages: SourcePages? = null

    private fun pagesFor(sourceId: Long): SourcePages {
        sourcePages?.takeIf { it.sourceId == sourceId }?.let { return it }
        sourcePages?.job?.cancel()
        val context = viewModelScope.coroutineContext
        val job = Job(context[Job])
        val scope = CoroutineScope(context + job)
        return SourcePages(sourceId, job, mediaRepository.pageAnimeGroups(sourceId).cachedIn(scope),
            mediaRepository.pageUnclassifiedMedia(sourceId).cachedIn(scope)).also { sourcePages = it }
    }

    internal fun animeGroups(sourceId: Long) = pagesFor(sourceId).groups

    internal fun unclassifiedMedia(sourceId: Long) = pagesFor(sourceId).unclassified

    internal fun animeMedia(group: LocalMediaAnimeGroup) = if (group.archiveKey != null || group.reviewKey != null) {
        mediaRepository.pageArchiveMedia(group.sourceId, group.archiveKey, group.reviewKey)
    } else mediaRepository.pageAnimeMedia(group.sourceId, group.animeId)

    private val searchUseCase = SearchUseCase(animeRepository)
    private var matchSearchJob: Job? = null

    internal fun hiddenMedia(sourceId: Long) = mediaRepository.pageHiddenMedia(sourceId)

    private var selectionJob: Job? = null
    private var permissionCheckJob: Job? = null

    init {
        viewModelScope.launch {
            animeRepository.getAllAnimes().collect { animes ->
                val options = withContext(Dispatchers.Default) { animes.map { it.id to it.title } }
                _uiState.update { it.copy(animeOptions = options) }
            }
        }
        viewModelScope.launch {
            var initial = true
            mediaRepository.observeSources().collect { sources ->
                val previous = _uiState.value.selectedSourceId ?: savedState.get<Long>(SELECTED_SOURCE)
                val selected = previous?.takeIf { id -> sources.any { it.id == id } } ?: sources.firstOrNull()?.id
                val changed = _uiState.value.selectedSourceId != selected
                _uiState.update {
                    if (changed) it.copy(sources = sources, selectedSourceId = selected, scan = null, hiddenCount = 0)
                    else it.copy(sources = sources)
                }
                if (changed) observeSelection(selected)
                if (initial) {
                    initial = false
                    refreshPermissions()
                }
            }
        }
    }

    fun refreshPermissions() {
        val sources = _uiState.value.sources
        if (sources.isEmpty() || permissionCheckJob?.isActive == true) return
        permissionCheckJob = viewModelScope.launch {
            try {
                mediaRepository.refreshMissingGrants(sources)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 系统暂时不可用时保留现状，扫描时会再次检查授权。
            }
        }
    }

    fun addFolder(uri: Uri, displayName: String) = launchAction(LocalMediaActionError.ADD_FOLDER) {
        val source = withContext(Dispatchers.IO) {
            application.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            mediaRepository.addOrGetSource(uri, displayName)
        }
        selectSource(source.id)
        scheduler.enqueue(source.id)
    }

    fun reauthorize(sourceId: Long, uri: Uri) = launchAction(LocalMediaActionError.REAUTHORIZE) {
        if (!mediaRepository.reauthorizeSource(sourceId, uri)) {
            _uiState.update { it.copy(error = LocalMediaActionError.WRONG_FOLDER) }
            return@launchAction
        }
        selectSource(sourceId)
        scheduler.enqueue(sourceId)
    }

    fun selectSource(sourceId: Long) {
        if (_uiState.value.selectedSourceId == sourceId) return
        _uiState.update { it.copy(selectedSourceId = sourceId, scan = null, error = null, hiddenCount = 0) }
        observeSelection(sourceId)
    }

    fun scanSelectedSource() = enqueueSelectedScan(reidentify = false)

    fun reidentifySelectedSource() = enqueueSelectedScan(reidentify = true)

    private fun enqueueSelectedScan(reidentify: Boolean) = launchAction(LocalMediaActionError.SCAN_REQUEST) {
        _uiState.value.selectedSourceId?.let { scheduler.enqueue(it, reidentify) }
    }

    fun cancelSelectedScan() = launchAction(LocalMediaActionError.SCAN_REQUEST) {
        _uiState.value.selectedSourceId?.let { scheduler.cancel(it) }
    }

    fun confirmMatch(mediaId: Long, animeId: Int?) = launchAction(LocalMediaActionError.MATCH) {
        mediaRepository.updateUserMatch(mediaId, animeId)
    }

    internal fun searchMatches(query: String, movie: Boolean) {
        matchSearchJob?.cancel()
        val title = query.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(matchSearch = LocalMatchSearch()) }
            return
        }
        _uiState.update { it.copy(matchSearch = LocalMatchSearch(loading = true)) }
        matchSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            var failed = false
            suspend fun fetch(block: suspend () -> List<SearchResult>): List<SearchResult> = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
                emptyList()
            }
            val bangumi = fetch { searchUseCase.search(title, SearchSource.BANGUMI) }
            val tmdb = fetch {
                if (!movie) searchUseCase.search(title, SearchSource.TMDB) else {
                    RetrofitClient.tmdbApi.searchMovie(title).results.map {
                        SearchResult(SearchSource.TMDB, it.id, it.title, it.coverUrl, null, it.date, null, it.overview, "")
                    }
                }
            }
            _uiState.update {
                it.copy(matchSearch = LocalMatchSearch(results = bangumi + tmdb, failed = failed, searched = true))
            }
        }
    }

    internal fun confirmCardMatch(card: LocalMediaAnimeGroup): Job = launchAction(LocalMediaActionError.MATCH) {
        mediaRepository.confirmCardMatch(card)
    }

    internal fun clearCardMatch(card: LocalMediaAnimeGroup): Job = launchAction(LocalMediaActionError.MATCH) {
        mediaRepository.saveManualArchive(card.sourceId, null, null, null, card = card)
    }

    internal fun pickLocalMatch(target: LocalMediaMatchTarget, animeId: Int): Job = launchAction(LocalMediaActionError.MATCH) {
        val anime = checkNotNull(animeRepository.getAnimeById(animeId))
        mediaRepository.saveManualArchive(target.sourceId, target.mediaId, anime.toArchive(target.sourceId), animeId,
            card = target.card)
    }

    internal fun pickRemoteMatch(target: LocalMediaMatchTarget, result: SearchResult): Job = launchAction(LocalMediaActionError.MATCH) {
        val bangumi = result.source == SearchSource.BANGUMI
        val archive = LocalMediaArchiveEntity(
            target.sourceId, if (bangumi) "bgm:${result.sourceId}" else tmdbKey(result.sourceId, target.movie),
            result.title, result.coverUrl, result.sourceId.takeIf { bangumi }, result.sourceId.takeUnless { bangumi },
            result.airDate?.year(), result.summary, result.episodeCount
        )
        // 手动选远端条目只修正媒体归档，不隐式授权追番进度同步。
        mediaRepository.saveManualArchive(target.sourceId, target.mediaId, archive, null, card = target.card)
    }

    internal fun clearMatch(target: LocalMediaMatchTarget): Job = launchAction(LocalMediaActionError.MATCH) {
        mediaRepository.saveManualArchive(target.sourceId, target.mediaId, null, null, card = target.card)
    }

    fun setHidden(sourceId: Long, mediaId: Long, hidden: Boolean) = launchAction(LocalMediaActionError.VISIBILITY) {
        mediaRepository.setHidden(sourceId, mediaId, hidden)
    }

    fun deleteSource(sourceId: Long) = launchAction(LocalMediaActionError.REMOVE_SOURCE) {
        scheduler.cancel(sourceId)
        mediaRepository.getSource(sourceId)?.let { mediaRepository.deleteSource(it) }
    }

    private fun launchAction(error: LocalMediaActionError, action: suspend () -> Unit): Job =
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(error = error) }
            }
        }

    private fun observeSelection(sourceId: Long?) {
        savedState[SELECTED_SOURCE] = sourceId
        selectionJob?.cancel()
        if (sourceId == null) {
            sourcePages?.job?.cancel()
            sourcePages = null
            return
        }
        selectionJob = viewModelScope.launch {
            launch {
                scheduler.observe(sourceId).collect { scan ->
                    _uiState.update { if (it.selectedSourceId == sourceId) it.copy(scan = scan) else it }
                }
            }
            mediaRepository.observeHiddenCount(sourceId).collect { count ->
                _uiState.update { if (it.selectedSourceId == sourceId) it.copy(hiddenCount = count) else it }
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = AppContainer.getApplication()
            return LocalMediaViewModel(
                application,
                AppContainer.getLocalMediaRepository(),
                AppContainer.getAnimeRepository(),
                LocalMediaScanScheduler(WorkManager.getInstance(application)),
                extras.createSavedStateHandle()
            ) as T
        }
    }

    private companion object {
        const val SELECTED_SOURCE = "localMediaSourceId"
    }
}
