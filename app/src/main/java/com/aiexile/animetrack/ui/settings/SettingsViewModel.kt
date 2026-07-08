package com.aiexile.animetrack.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.ExportAnimeService
import com.aiexile.animetrack.data.ImportResult
import com.aiexile.animetrack.data.MarkdownParser
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.util.cleanSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val importResult: ImportResult? = null,
    val duplicateCount: Int = 0,
    val isImporting: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: String? = null,
    val syncedCount: Int = 0,
    val totalToSync: Int = 0,
    val syncCompleted: Boolean = false,
    val exportMarkdown: String? = null
)

class SettingsViewModel(
    private val animeRepository: AnimeRepository,
    private val settingsRepository: com.aiexile.animetrack.data.SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val tmdbApiKey: StateFlow<String?> = settingsRepository.tmdbApiKey
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    fun setTmdbApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setTmdbApiKey(key)
        }
    }
    
    fun parseMarkdown(content: String) {
        val result = MarkdownParser.parse(content)
        
        var duplicateCount = 0
        viewModelScope.launch {
            for (parsed in result.animes) {
                val existing = animeRepository.getAnimeByTitle(parsed.title)
                if (existing != null) {
                    duplicateCount++
                }
            }
            _uiState.value = _uiState.value.copy(
                importResult = result,
                duplicateCount = duplicateCount
            )
        }
    }
    
    fun importAnimesAndSync(content: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            
            val result = MarkdownParser.parse(content)
            val animesToInsert = mutableListOf<Anime>()
            
            Log.d(TAG, "Parsed ${result.animes.size} animes from markdown")
            
            for (parsed in result.animes) {
                val existing = animeRepository.getAnimeByTitle(parsed.title)
                if (existing == null) {
                    animesToInsert.add(MarkdownParser.toAnimeEntity(parsed))
                } else {
                    Log.d(TAG, "Skipping duplicate: ${parsed.title}")
                }
            }
            
            Log.d(TAG, "Inserting ${animesToInsert.size} new animes")
            
            val insertedAnimes = mutableListOf<Anime>()
            for (anime in animesToInsert) {
                val id = animeRepository.insertAnime(anime)
                insertedAnimes.add(anime.copy(id = id.toInt()))
                Log.d(TAG, "Inserted: ${anime.title} with id=$id")
            }
            
            _uiState.value = _uiState.value.copy(isImporting = false)
            
            animeRepository.syncCoversInBackground(insertedAnimes)
        }
    }
    
    private suspend fun tryFetchSummary(bangumiId: Int): String? {
        return animeRepository.fetchBangumiDetail(bangumiId)?.summary?.cleanSummary()
    }
    
    fun resetSyncCompleted() {
        _uiState.value = _uiState.value.copy(syncCompleted = false)
    }
    
    private fun cleanTitleAndExtractNote(title: String): Pair<String, String> {
        val parenIndex = title.indexOf("(")
        val bracketIndex = title.indexOf("（")
        
        val splitIndex = when {
            parenIndex >= 0 && bracketIndex >= 0 -> minOf(parenIndex, bracketIndex)
            parenIndex >= 0 -> parenIndex
            bracketIndex >= 0 -> bracketIndex
            else -> -1
        }
        
        return if (splitIndex > 0) {
            val cleanTitle = title.substring(0, splitIndex).trim()
            val note = title.substring(splitIndex + 1)
                .removeSuffix(")")
                .removeSuffix("）")
                .trim()
            Pair(cleanTitle, note)
        } else {
            Pair(title.trim(), "")
        }
    }
    
    fun resetImportState() {
        _uiState.value = _uiState.value.copy(
            importResult = null,
            duplicateCount = 0
        )
    }

    fun prepareExport() {
        viewModelScope.launch {
            val animes = animeRepository.getAllAnimes().first()
            val markdown = ExportAnimeService.exportToMarkdown(animes)
            _uiState.value = _uiState.value.copy(exportMarkdown = markdown)
        }
    }

    fun clearExportMarkdown() {
        _uiState.value = _uiState.value.copy(exportMarkdown = null)
    }
    
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                AppContainer.getAnimeRepository(),
                AppContainer.getSettingsRepository()
            ) as T
        }
    }
}
