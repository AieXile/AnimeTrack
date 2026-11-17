package com.aiexile.animetrack.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.websource.WebSourceEngine
import com.aiexile.animetrack.data.websource.WebSourceHttpException
import com.aiexile.animetrack.data.websource.WebSourceRuleRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourcePage
import com.aiexile.animetrack.model.WebSourceRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

data class WebPageRequest(val url: String, val userAgent: String, val referer: String?)

sealed interface WebPageState {
    data object Idle : WebPageState
    data object Loading : WebPageState
    data class Success(val page: WebSourcePage) : WebPageState
    data class Failure(val error: WebSourceError) : WebPageState
}

sealed interface WebMediaState {
    data object Idle : WebMediaState
    data class Sniffing(val episode: WebSourceEpisode) : WebMediaState
}

/** 网页入口的选集会话，与规则搜索、导入状态分开保存。 */
class WebPageViewModel(
    private val engine: WebSourceEngine,
    private val repository: WebSourceRuleRepository
) : ViewModel() {
    private val _state = MutableStateFlow<WebPageState>(WebPageState.Idle)
    val state = _state.asStateFlow()
    private val _media = MutableStateFlow<WebMediaState>(WebMediaState.Idle)
    val mediaState = _media.asStateFlow()
    var request: WebPageRequest? = null
        private set
    private var pageJob: Job? = null

    fun load(url: String, userAgent: String?, referer: String?, force: Boolean = false) {
        if (!force && request?.url == url && _state.value !is WebPageState.Idle) return
        pageJob?.cancel()
        cancelMedia()
        request = WebPageRequest(url, userAgent ?: WebSourceRule.DEFAULT_USER_AGENT, referer)
        _state.value = WebPageState.Loading
        pageJob = viewModelScope.launch {
            try {
                val rule = repository.rules.first().firstOrNull {
                    it.baseUrl.toHttpUrlOrNull()?.host == url.toHttpUrlOrNull()?.host
                }
                val context = WebPageRequest(
                    url, userAgent ?: rule?.effectiveUserAgent ?: WebSourceRule.DEFAULT_USER_AGENT,
                    referer ?: rule?.effectiveReferer
                )
                request = context
                _state.value = WebPageState.Success(
                    engine.page(url, context.userAgent, context.referer, rule)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = WebPageState.Failure(when (error) {
                    is WebSourceHttpException -> WebSourceError(WebSourceErrorKind.HTTP, error.statusCode)
                    is IOException -> WebSourceError(WebSourceErrorKind.NETWORK)
                    else -> WebSourceError(WebSourceErrorKind.PARSE)
                })
            }
        }
    }

    fun assist(url: String) {
        if (request == null) return
        cancelMedia()
        _media.value = WebMediaState.Sniffing(WebSourceEpisode(
            (_state.value as? WebPageState.Success)?.page?.title.orEmpty(), url
        ))
    }

    fun cancelMedia() {
        _media.value = WebMediaState.Idle
    }

    fun close() {
        pageJob?.cancel()
        cancelMedia()
        request = null
        _state.value = WebPageState.Idle
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WebPageViewModel(
            AppContainer.getWebSourceEngine(), AppContainer.getWebSourceRuleRepository()
        ) as T
    }
}
