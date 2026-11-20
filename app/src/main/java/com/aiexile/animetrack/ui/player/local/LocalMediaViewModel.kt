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
import com.aiexile.animetrack.data.media.LocalScanStatus
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LocalMediaActionError { ADD_FOLDER, WRONG_FOLDER, REAUTHORIZE, SCAN_REQUEST, REMOVE_SOURCE, MATCH }

internal data class LocalMediaUiState(
    val sources: List<LocalMediaSourceEntity> = emptyList(),
    val selectedSourceId: Long? = null,
    val animeTitles: Map<Int, String> = emptyMap(),
    val animeOptions: List<Pair<Int, String>> = emptyList(),
    val scan: LocalScanStatus? = null,
    val error: LocalMediaActionError? = null
)

class LocalMediaViewModel internal constructor(
    private val application: Application,
    private val mediaRepository: LocalMediaRepository,
    animeRepository: AnimeRepository,
    private val scheduler: LocalMediaScanScheduler,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalMediaUiState())
    internal val uiState: StateFlow<LocalMediaUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    internal val mediaPages: Flow<PagingData<LocalMediaEntity>> = uiState
        .map { it.selectedSourceId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(PagingData.empty()) else mediaRepository.pagePresentMedia(id) }
        .cachedIn(viewModelScope)

    private var selectionJob: Job? = null
    private var permissionCheckJob: Job? = null

    init {
        viewModelScope.launch {
            animeRepository.getAllAnimes().collect { animes ->
                val (options, titles) = withContext(Dispatchers.Default) {
                    val entries = animes.map { it.id to it.title }
                    entries to entries.toMap()
                }
                _uiState.update { it.copy(animeOptions = options, animeTitles = titles) }
            }
        }
        viewModelScope.launch {
            var initial = true
            mediaRepository.observeSources().collect { sources ->
                val previous = _uiState.value.selectedSourceId ?: savedState.get<Long>(SELECTED_SOURCE)
                val selected = previous?.takeIf { id -> sources.any { it.id == id } } ?: sources.firstOrNull()?.id
                val changed = _uiState.value.selectedSourceId != selected
                _uiState.update {
                    if (changed) it.copy(sources = sources, selectedSourceId = selected, scan = null)
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
        _uiState.update { it.copy(selectedSourceId = sourceId, scan = null, error = null) }
        observeSelection(sourceId)
    }

    fun scanSelectedSource() = launchAction(LocalMediaActionError.SCAN_REQUEST) {
        _uiState.value.selectedSourceId?.let { scheduler.enqueue(it) }
    }

    fun cancelSelectedScan() = launchAction(LocalMediaActionError.SCAN_REQUEST) {
        _uiState.value.selectedSourceId?.let { scheduler.cancel(it) }
    }

    fun confirmMatch(mediaId: Long, animeId: Int?) = launchAction(LocalMediaActionError.MATCH) {
        mediaRepository.updateUserMatch(mediaId, animeId)
    }

    fun clearMatch(mediaId: Long) = confirmMatch(mediaId, null)

    fun deleteSource(sourceId: Long) = launchAction(LocalMediaActionError.REMOVE_SOURCE) {
        scheduler.cancel(sourceId)
        mediaRepository.getSource(sourceId)?.let { mediaRepository.deleteSource(it) }
    }

    private fun launchAction(error: LocalMediaActionError, action: suspend () -> Unit) {
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
    }

    private fun observeSelection(sourceId: Long?) {
        savedState[SELECTED_SOURCE] = sourceId
        selectionJob?.cancel()
        if (sourceId == null) return
        selectionJob = viewModelScope.launch {
            scheduler.observe(sourceId).collect { scan ->
                _uiState.update { if (it.selectedSourceId == sourceId) it.copy(scan = scan) else it }
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
