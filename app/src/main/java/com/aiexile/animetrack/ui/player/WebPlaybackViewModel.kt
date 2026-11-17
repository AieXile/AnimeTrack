package com.aiexile.animetrack.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.player.WebPlaybackHistoryRepository
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.player.PendingWebPlayback
import com.aiexile.animetrack.data.player.PendingWebPlaybackRequest
import com.aiexile.animetrack.data.player.WebSourceClassifier
import com.aiexile.animetrack.domain.media.WebPlaybackQueue
import com.aiexile.animetrack.model.PlaybackProgress
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 历史卡片的续播目标；完成末集时重播本集，不回绕到第一集。 */
data class WebPlaybackHistoryItem(
    val entry: WebPlaybackHistoryRepository.Entry,
    val progress: PlaybackProgress?
) {
    val nextEpisode get() = entry.nextEpisode.takeIf { progress?.completed == true }
    val continueUrl get() = nextEpisode?.url ?: entry.url
    val continueTitle get() = nextEpisode?.let { next ->
        entry.seriesTitle?.let { "$it · ${next.name}" } ?: next.name
    } ?: entry.title
    val continueMediaId get() = if (nextEpisode != null) entry.nextMediaId else entry.mediaId
}

/** 网页播放页状态：合并历史和持久化进度，不持有 WebView。 */
class WebPlaybackViewModel(
    private val historyRepository: WebPlaybackHistoryRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {

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
        seriesUrl: String? = null, queue: WebPlaybackQueue? = null) {
        viewModelScope.launch { historyRepository.add(url, title, userAgent, referer, seriesUrl, queue) }
    }

    fun prepareContinue(item: WebPlaybackHistoryItem) {
        val entry = item.entry
        val target = item.continueUrl
        val direct = WebSourceClassifier.isCandidate(target)
        val referer = if (entry.referer == entry.url) target else entry.referer
        PendingWebPlayback.set(PendingWebPlaybackRequest(
            url = target, mimeType = WebSourceClassifier.mimeType(target), title = item.continueTitle,
            pageUrl = target.takeUnless { direct }, userAgent = entry.userAgent, referer = referer,
            resolvePage = true, restoreQueue = !direct, progressMediaId = item.continueMediaId,
            seriesUrl = entry.seriesUrl))
    }

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
