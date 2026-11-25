package com.aiexile.animetrack.ui.player.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.player.web.WebPlaybackHistoryRepository
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.web.PendingWebPlayback
import com.aiexile.animetrack.domain.playback.WebPlaybackResume
import com.aiexile.animetrack.domain.playback.WebPlaybackResumeTarget
import com.aiexile.animetrack.domain.playback.WebPlaybackQueue
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 历史卡片的续播目标；完成末集时重播本集，不回绕到第一集。 */
data class WebPlaybackHistoryItem(
    val entry: WebPlaybackHistoryRepository.Entry,
    val progress: PlaybackProgress?
) {
    val resume: WebPlaybackResumeTarget = WebPlaybackResume.target(entry, progress)
    val nextEpisode get() = entry.nextEpisode.takeIf { progress?.completed == true }
    val continueUrl get() = resume.request.url
    val continueTitle get() = resume.request.title
    val continueMediaId get() = resume.request.progressMediaId
}

/** 网页播放页状态：合并历史和持久化进度，不持有 WebView。 */
class WebPlaybackViewModel(
    private val historyRepository: WebPlaybackHistoryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {
    private val _librarySaveResult = MutableStateFlow<Boolean?>(null)
    internal val librarySaveResult = _librarySaveResult.asStateFlow()

    val history: StateFlow<List<WebPlaybackHistoryItem>> =
        combine(historyRepository.entries, playerRepository.progress) { entries, records ->
            entries.map { entry ->
                WebPlaybackHistoryItem(entry, records[entry.mediaId ?: "web:${entry.url}"])
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val progress = playerRepository.progress.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun remember(url: String, title: String?, userAgent: String? = null, referer: String? = null,
        seriesUrl: String? = null, queue: WebPlaybackQueue? = null,
        animeId: Int = 0, animeTitle: String? = null) {
        viewModelScope.launch {
            historyRepository.add(url, title, userAgent, referer, seriesUrl, queue,
                animeId = animeId.takeIf { it > 0 }, animeTitle = animeTitle?.takeIf { it.isNotBlank() })
        }
    }

    fun prepareContinue(item: WebPlaybackHistoryItem) {
        PendingWebPlayback.set(item.resume.request)
    }

    internal fun saveToLibrary(entry: WebPlaybackHistoryRepository.Entry) {
        viewModelScope.launch {
            _librarySaveResult.value = try {
                historyRepository.saveToLibrary(entry)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }

    internal fun clearLibrarySaveResult() { _librarySaveResult.value = null }

    fun remove(url: String) {
        viewModelScope.launch { historyRepository.remove(url) }
    }

    fun clear() {
        viewModelScope.launch { historyRepository.clear() }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WebPlaybackViewModel(AppContainer.getWebPlaybackHistoryRepository(),
                AppContainer.getPlayerRepository()) as T
        }
    }
}
