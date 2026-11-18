package com.aiexile.animetrack.ui.player.local

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaMatchState
import com.aiexile.animetrack.data.media.LocalMediaPermissionState
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.media.LocalMediaScanState
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.playback.LocalMediaMatcher
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

 data class LocalMediaUiState(
    val sources: List<com.aiexile.animetrack.data.media.LocalMediaSourceEntity> = emptyList(),
    val selectedSourceId: Long? = null,
    val media: List<LocalMediaEntity> = emptyList(),
    val animeTitles: Map<Int, String> = emptyMap(),
    /** 匹配确认候选：番剧 id → 标题 */
    val animeOptions: List<Pair<Int, String>> = emptyList(),
    val isScanning: Boolean = false,
    val scannedFiles: Int = 0,
    val videoFiles: Int = 0,
    val currentPath: String = "",
    val error: String? = null
)

class LocalMediaViewModel(
    private val application: Application,
    private val localMediaRepository: LocalMediaRepository,
    private val animeRepository: AnimeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocalMediaUiState())
    val uiState: StateFlow<LocalMediaUiState> = _uiState.asStateFlow()

    private var mediaJob: Job? = null

    init {
        viewModelScope.launch {
            animeRepository.getAllAnimes().collect { animes ->
                _uiState.update {
                    it.copy(animeOptions = animes.map { anime -> anime.id to anime.title })
                }
            }
        }
        viewModelScope.launch {
            localMediaRepository.observeSources().collect { sources ->
                val previousSelected = _uiState.value.selectedSourceId
                val selected = previousSelected
                    ?.takeIf { id -> sources.any { it.id == id } }
                    ?: sources.firstOrNull()?.id
                _uiState.update { it.copy(sources = sources, selectedSourceId = selected) }
                if (selected != null && selected != previousSelected) {
                    observeMedia(selected)
                }
            }
        }
    }

    fun addFolder(uri: Uri, displayName: String) {
        viewModelScope.launch {
            try {
                application.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val source = localMediaRepository.addOrGetSource(uri, displayName)
                selectSource(source.id)
                scanSource(source.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "无法添加本地文件夹") }
            }
        }
    }

    fun selectSource(sourceId: Long) {
        _uiState.update { it.copy(selectedSourceId = sourceId, error = null) }
        observeMedia(sourceId)
    }

    fun scanSelectedSource() {
        _uiState.value.selectedSourceId?.let(::scanSource)
    }

    private fun scanSource(sourceId: Long) {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            val source = localMediaRepository.observeSources().first().firstOrNull { it.id == sourceId }
                ?: return@launch
            _uiState.update {
                it.copy(
                    selectedSourceId = sourceId,
                    isScanning = true,
                    scannedFiles = 0,
                    videoFiles = 0,
                    currentPath = "",
                    error = null
                )
            }
            try {
                localMediaRepository.scanSource(source) { progress ->
                    _uiState.update {
                        it.copy(
                            scannedFiles = progress.scannedFiles,
                            videoFiles = progress.videoFiles,
                            currentPath = progress.currentPath
                        )
                    }
                }
                autoMatch(sourceId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "扫描失败") }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    /** 先本地标题匹配，再对唯一未匹配标题请求 Bangumi，避免按文件重复请求。 */
    private suspend fun autoMatch(sourceId: Long) {
        val animes = animeRepository.getAllAnimes().first()
        val media = localMediaRepository.observeMedia(sourceId).first()
        val remoteCache = mutableMapOf<String, List<com.aiexile.animetrack.data.network.BangumiSubject>>()
        var remoteQueries = 0

        for (item in media) {
            if (item.isMissing || item.matchState == LocalMediaMatchState.IGNORED || item.isUserOverride) continue
            val title = item.parsedTitle.orEmpty()
            if (title.isBlank()) continue

            val localMatch = LocalMediaMatcher.match(title, item.seasonNumber, animes)
            if (localMatch.anime != null && localMatch.confidence >= 0.90f) {
                localMediaRepository.updateMatch(
                    mediaId = item.id,
                    animeId = localMatch.anime.id,
                    confidence = localMatch.confidence,
                    confirmed = false,
                    userOverride = false
                )
                continue
            }

            if (remoteQueries >= MAX_REMOTE_QUERIES) continue
            val candidates = remoteCache.getOrPut(title) {
                remoteQueries++
                runCatching { animeRepository.searchBangumi(title) }.getOrDefault(emptyList())
            }
            val matched = candidates.firstNotNullOfOrNull { candidate ->
                animes.firstOrNull { anime ->
                    anime.bangumiId == candidate.id ||
                        normalize(anime.title) == normalize(candidate.displayName)
                }
            }
            if (matched != null) {
                localMediaRepository.updateMatch(
                    mediaId = item.id,
                    animeId = matched.id,
                    confidence = 0.91f,
                    confirmed = false,
                    userOverride = false
                )
            }
        }
    }

    fun confirmMatch(mediaId: Long, animeId: Int?) {
        viewModelScope.launch {
            localMediaRepository.updateMatch(
                mediaId = mediaId,
                animeId = animeId,
                confidence = if (animeId == null) 0f else 1f,
                confirmed = true,
                userOverride = true
            )
        }
    }

    /** 解除错误关联；仍保留用户覆盖标记，重扫/自动匹配不会再次改回 */
    fun clearMatch(mediaId: Long) {
        viewModelScope.launch {
            localMediaRepository.updateMatch(
                mediaId = mediaId,
                animeId = null,
                confidence = 0f,
                confirmed = true,
                userOverride = true
            )
        }
    }

    fun deleteSource(sourceId: Long) {
        viewModelScope.launch {
            val source = localMediaRepository.observeSources().first().firstOrNull { it.id == sourceId }
                ?: return@launch
            localMediaRepository.deleteSource(source)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun observeMedia(sourceId: Long) {
        mediaJob?.cancel()
        mediaJob = viewModelScope.launch {
            localMediaRepository.observeMedia(sourceId).collect { media ->
                val ids = media.mapNotNull { it.matchedAnimeId }.toSet()
                val titles = animeRepository.getAllAnimes().first()
                    .filter { it.id in ids }
                    .associate { it.id to it.title }
                _uiState.update { it.copy(media = media, animeTitles = titles) }
            }
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[\\s._·•:：,，!！?？()（）\\[\\]{}【】\\-–—~～]+"), "")

    override fun onCleared() {
        mediaJob?.cancel()
        super.onCleared()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LocalMediaViewModel(
                application = AppContainer.getApplication(),
                localMediaRepository = AppContainer.getLocalMediaRepository(),
                animeRepository = AppContainer.getAnimeRepository()
            ) as T
        }
    }

    private companion object {
        const val MAX_REMOTE_QUERIES = 30
    }
}
