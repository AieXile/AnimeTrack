package com.aiexile.animetrack.ui.player.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.websource.WebSourceEngine
import com.aiexile.animetrack.data.websource.WebSourceHttpException
import com.aiexile.animetrack.data.websource.WebSourceRuleCodec
import com.aiexile.animetrack.data.websource.WebSourceRuleInvalidException
import com.aiexile.animetrack.data.websource.WebSourceRuleRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.WebSourceEntry
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourceRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class WebSourceErrorKind { NETWORK, HTTP, PARSE, EMPTY, INVALID_RULE, IMPORT }

data class WebSourceError(
    val kind: WebSourceErrorKind,
    val statusCode: Int? = null,
    val detail: String? = null
)

sealed interface WebSourceSearchState {
    data object Idle : WebSourceSearchState
    data object Loading : WebSourceSearchState
    data class Success(
        val rule: WebSourceRule,
        val entries: List<WebSourceEntry>
    ) : WebSourceSearchState

    data class Failure(val error: WebSourceError) : WebSourceSearchState
}

sealed interface WebSourceEpisodeState {
    data object Idle : WebSourceEpisodeState
    data object Loading : WebSourceEpisodeState
    data class Success(
        val rule: WebSourceRule,
        val entry: WebSourceEntry,
        val episodes: List<WebSourceEpisode>
    ) : WebSourceEpisodeState

    data class Failure(
        val rule: WebSourceRule,
        val entry: WebSourceEntry,
        val error: WebSourceError
    ) : WebSourceEpisodeState
}

sealed interface WebSourceImportState {
    data object Idle : WebSourceImportState
    data object Loading : WebSourceImportState
    data class Success(
        val imported: Int,
        val skipped: Int,
        val issues: List<WebSourceRuleCodec.ImportIssue> = emptyList()
    ) : WebSourceImportState
    data class Failure(
        val detail: String?,
        val issues: List<WebSourceRuleCodec.ImportIssue> = emptyList()
    ) : WebSourceImportState
}

/** 规则数据源：搜索、选集与导入，播放页仍交给 WebView 嗅探。 */
class WebSourceViewModel(
    private val repository: WebSourceRuleRepository,
    private val engine: WebSourceEngine
) : ViewModel() {

    val rules: StateFlow<List<WebSourceRule>> = repository.rules.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val selectedRule: StateFlow<WebSourceRule?> = combine(
        repository.rules,
        repository.selectedName
    ) { rules, name ->
        rules.firstOrNull { it.name == name } ?: rules.firstOrNull()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    private val _searchState = MutableStateFlow<WebSourceSearchState>(WebSourceSearchState.Idle)
    val searchState: StateFlow<WebSourceSearchState> = _searchState.asStateFlow()

    private val _episodeState = MutableStateFlow<WebSourceEpisodeState>(WebSourceEpisodeState.Idle)
    val episodeState: StateFlow<WebSourceEpisodeState> = _episodeState.asStateFlow()

    private val _importState = MutableStateFlow<WebSourceImportState>(WebSourceImportState.Idle)
    val importState: StateFlow<WebSourceImportState> = _importState.asStateFlow()

    private var lastRule: WebSourceRule? = null
    private var lastKeyword: String = ""
    private var lastEntry: WebSourceEntry? = null

    fun selectRule(rule: WebSourceRule) {
        viewModelScope.launch { repository.select(rule.name) }
    }

    fun search(rule: WebSourceRule, keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        lastRule = rule
        lastKeyword = trimmed
        lastEntry = null
        _episodeState.value = WebSourceEpisodeState.Idle
        viewModelScope.launch {
            _searchState.value = WebSourceSearchState.Loading
            try {
                val entries = engine.search(rule, trimmed)
                _searchState.value = if (entries.isEmpty()) {
                    WebSourceSearchState.Failure(
                        WebSourceError(WebSourceErrorKind.EMPTY)
                    )
                } else {
                    WebSourceSearchState.Success(rule, entries)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure("search", error)
                _searchState.value = WebSourceSearchState.Failure(describe(error))
            }
        }
    }

    fun retrySearch() {
        lastRule?.let { search(it, lastKeyword) }
    }

    fun openEntry(rule: WebSourceRule, entry: WebSourceEntry) {
        lastRule = rule
        lastEntry = entry
        viewModelScope.launch {
            _episodeState.value = WebSourceEpisodeState.Loading
            try {
                val episodes = engine.episodes(rule, entry)
                _episodeState.value = if (episodes.isEmpty()) {
                    WebSourceEpisodeState.Failure(
                        rule,
                        entry,
                        WebSourceError(WebSourceErrorKind.EMPTY)
                    )
                } else {
                    WebSourceEpisodeState.Success(rule, entry, episodes)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure("episodes", error)
                _episodeState.value = WebSourceEpisodeState.Failure(
                    rule,
                    entry,
                    describe(error)
                )
            }
        }
    }

    fun retryEpisodes() {
        val rule = lastRule ?: return
        val entry = lastEntry ?: return
        openEntry(rule, entry)
    }

    fun resetEpisodes() {
        _episodeState.value = WebSourceEpisodeState.Idle
    }

    fun reset() {
        _searchState.value = WebSourceSearchState.Idle
        _episodeState.value = WebSourceEpisodeState.Idle
        lastEntry = null
    }

    fun importText(raw: String) {
        viewModelScope.launch {
            _importState.value = WebSourceImportState.Loading
            importRaw(raw)
        }
    }

    fun importFromUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _importState.value = WebSourceImportState.Loading
            try {
                importRaw(engine.fetchRuleSource(trimmed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure("import", error)
                _importState.value = WebSourceImportState.Failure(error.message)
            }
        }
    }

    fun clearImportState() {
        _importState.value = WebSourceImportState.Idle
    }

    fun deleteRule(name: String) {
        viewModelScope.launch { repository.delete(name) }
    }

    private suspend fun importRaw(raw: String) {
        val result = withContext(Dispatchers.Default) {
            WebSourceRuleCodec.decode(raw)
        }
        val decoded = result.getOrNull()
        if (decoded == null) {
            val issues = (result.exceptionOrNull() as? WebSourceRuleCodec.NoUsableRulesException)
                ?.issues.orEmpty()
            _importState.value = WebSourceImportState.Failure(null, issues)
            return
        }
        repository.importRules(decoded.rules)
        _importState.value = WebSourceImportState.Success(
            imported = decoded.rules.size,
            skipped = decoded.skipped.size,
            issues = decoded.issues
        )
    }

    private fun describe(error: Throwable): WebSourceError = when (error) {
        is WebSourceHttpException -> WebSourceError(
            WebSourceErrorKind.HTTP,
            statusCode = error.statusCode
        )
        is WebSourceRuleInvalidException -> WebSourceError(WebSourceErrorKind.INVALID_RULE)
        is IOException -> WebSourceError(WebSourceErrorKind.NETWORK, detail = error.message)
        else -> WebSourceError(WebSourceErrorKind.PARSE, detail = error.message)
    }

    private fun logFailure(step: String, error: Throwable) {
        AppLogManager.w("WebSource", "$step failed: ${error.message}")
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WebSourceViewModel(
                repository = AppContainer.getWebSourceRuleRepository(),
                engine = AppContainer.getWebSourceEngine()
            ) as T
        }
    }
}
