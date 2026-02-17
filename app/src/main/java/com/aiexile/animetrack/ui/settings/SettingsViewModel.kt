package com.aiexile.animetrack.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.ImportResult
import com.aiexile.animetrack.data.MarkdownParser
import com.aiexile.animetrack.data.network.BangumiSearchRequest
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val importResult: ImportResult? = null,
    val duplicateCount: Int = 0,
    val isImporting: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: String? = null,
    val syncedCount: Int = 0,
    val totalToSync: Int = 0,
    val syncCompleted: Boolean = false
)

class SettingsViewModel(
    private val animeRepository: AnimeRepository
) : ViewModel() {
    
    companion object {
        private const val TAG = "SettingsViewModel"
        private const val API_DELAY_MS = 800L
    }
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
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
    
    fun importAnimes(content: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            
            val result = MarkdownParser.parse(content)
            val animesToInsert = mutableListOf<Anime>()
            
            for (parsed in result.animes) {
                val existing = animeRepository.getAnimeByTitle(parsed.title)
                if (existing == null) {
                    animesToInsert.add(MarkdownParser.toAnimeEntity(parsed))
                }
            }
            
            if (animesToInsert.isNotEmpty()) {
                animeRepository.insertAnimes(animesToInsert)
            }
            
            _uiState.value = SettingsUiState(isImporting = false)
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
            
            doAutoSyncAnimeCovers(insertedAnimes)
        }
    }
    
    private suspend fun doAutoSyncAnimeCovers(animesToSync: List<Anime> = emptyList()) {
        val animesWithoutCover = if (animesToSync.isNotEmpty()) {
            animesToSync
        } else {
            animeRepository.getAnimesWithoutCover()
        }
        
        Log.d(TAG, "Found ${animesWithoutCover.size} animes without cover")
        
        if (animesWithoutCover.isEmpty()) {
            return
        }
        
        _uiState.value = _uiState.value.copy(
            isSyncing = true,
            totalToSync = animesWithoutCover.size,
            syncedCount = 0,
            syncProgress = null
        )
        
        var syncedCount = 0
        
        try {
            for (anime in animesWithoutCover) {
                try {
                    _uiState.value = _uiState.value.copy(
                        syncProgress = "正在补全: ${anime.title}"
                    )
                    
                    val (cleanTitle, extractedNote) = cleanTitleAndExtractNote(anime.title)
                    
                    Log.d(TAG, "Searching for: $cleanTitle")
                    
                    val response = RetrofitClient.bangumiApi.searchSubjects(
                        BangumiSearchRequest(
                            keyword = cleanTitle,
                            type = listOf(2),
                            limit = 10
                        )
                    )
                    
                    Log.d(TAG, "API returned ${response.data.size} results for: $cleanTitle")
                    
                    if (response.data.isNotEmpty()) {
                        Log.d(TAG, "First result: ${response.data[0].name}, coverUrl: ${response.data[0].coverUrl}")
                    }
                    
                    val bestMatch = response.data
                        .sortedWith(
                            compareByDescending<BangumiSubject> {
                                (it.total_episodes ?: 0) > 0 || (it.eps ?: 0) > 0
                            }.thenByDescending {
                                !it.name_cn.isNullOrBlank()
                            }
                        )
                        .firstOrNull()
                    
                    if (bestMatch != null) {
                        val updatedAnime = anime.copy(
                            title = cleanTitle,
                            coverUrl = bestMatch.coverUrl,
                            rating = bestMatch.score?.toFloat(),
                            totalEpisodes = bestMatch.episodeCount ?: anime.totalEpisodes,
                            notes = if (extractedNote.isNotEmpty()) extractedNote else anime.notes
                        )
                        
                        animeRepository.updateAnime(updatedAnime)
                        syncedCount++
                        
                        Log.d(TAG, "Synced cover for: ${anime.title} -> ${bestMatch.coverUrl}")
                    } else {
                        Log.d(TAG, "No match found for: ${anime.title}")
                    }
                    
                    _uiState.value = _uiState.value.copy(syncedCount = syncedCount)
                    
                    delay(API_DELAY_MS)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync cover for: ${anime.title}", e)
                }
            }
        } finally {
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncProgress = null,
                syncCompleted = true
            )
            Log.d(TAG, "Auto sync completed. Synced $syncedCount/${animesWithoutCover.size} animes")
        }
    }
    
    fun resetSyncCompleted() {
        _uiState.value = _uiState.value.copy(syncCompleted = false)
    }
    
    fun autoSyncAnimeCovers() {
        viewModelScope.launch {
            doAutoSyncAnimeCovers()
        }
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
    
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(AppContainer.getAnimeRepository()) as T
        }
    }
}
