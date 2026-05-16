package com.aiexile.animetrack.ui.schedule

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ScheduleUiState(
    val groupedAnimes: Map<Int, List<Anime>> = emptyMap(),
    val selectedWeekday: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let {
        when (it) {
            Calendar.SUNDAY -> 7
            else -> it - 1
        }
    }
)

class ScheduleViewModel(
    private val repository: AnimeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ScheduleViewModel"
    }

    val uiState: StateFlow<ScheduleUiState>

    init {
        val todayWeekday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let {
            when (it) {
                Calendar.SUNDAY -> 7
                else -> it - 1
            }
        }

        uiState = repository.getAiringAnimes()
            .map { animes ->
                val grouped = animes.groupBy { it.airWeekday ?: 0 }
                ScheduleUiState(
                    groupedAnimes = grouped,
                    selectedWeekday = todayWeekday
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = ScheduleUiState(selectedWeekday = todayWeekday)
            )

        backfillMissingDetails()
    }

    private fun backfillMissingDetails() {
        viewModelScope.launch {
            try {
                val animes = repository.getAiringAnimes()
                    .stateIn(
                        scope = viewModelScope,
                        started = SharingStarted.Eagerly,
                        initialValue = emptyList()
                    )
                    .value

                val needsBackfill = animes.filter {
                    it.bangumiId != null && it.airWeekday == null
                }

                Log.d(TAG, "Backfill: found ${needsBackfill.size} animes missing airWeekday")

                for (anime in needsBackfill) {
                    try {
                        val detail = RetrofitClient.bangumiApi.getSubjectDetail(anime.bangumiId!!)
                        val updatedAnime = anime.copy(
                            airWeekday = detail.airWeekday ?: anime.airWeekday,
                            airDate = detail.date ?: anime.airDate,
                            summary = detail.summary?.trim()?.replace(Regex("\n{3,}"), "\n\n")
                                ?: anime.summary
                        )
                        repository.updateAnime(updatedAnime)
                        Log.d(TAG, "Backfill: updated ${anime.title} airWeekday=${updatedAnime.airWeekday}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Backfill: failed for ${anime.title}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backfill: failed to load animes", e)
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScheduleViewModel(AppContainer.getAnimeRepository()) as T
        }
    }
}
