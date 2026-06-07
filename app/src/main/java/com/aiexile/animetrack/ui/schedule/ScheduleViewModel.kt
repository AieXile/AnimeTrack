package com.aiexile.animetrack.ui.schedule

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.util.computeIsFinished
import com.aiexile.animetrack.util.getCurrentWeekday
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleViewModel(
    private val repository: AnimeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ScheduleViewModel"
    }

    private val todayWeekday = getCurrentWeekday()

    val currentTodayWeekday: Int = todayWeekday

    private val tomorrowWeekday = (todayWeekday % 7) + 1

    private val _selectedWeekday = MutableStateFlow(todayWeekday)
    val selectedWeekday: StateFlow<Int> = _selectedWeekday.asStateFlow()

    val groupedAnimes: StateFlow<Map<Int, List<Anime>>> = repository.getAiringAnimes()
        .map { animes -> animes.groupBy { it.airWeekday ?: 0 } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyMap()
        )

    val todayUpdateCount: StateFlow<Int> = groupedAnimes.map { it[todayWeekday]?.size ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = 0
        )

    val todayAnimes: StateFlow<List<Anime>> = groupedAnimes.map { grouped ->
        (grouped[todayWeekday] ?: emptyList()).sortedBy { it.airDate }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    val tomorrowAnimes: StateFlow<List<Anime>> = groupedAnimes.map { grouped ->
        (grouped[tomorrowWeekday] ?: emptyList()).sortedBy { it.airDate }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    fun selectWeekday(weekday: Int) {
        _selectedWeekday.value = weekday
    }

    init {
        backfillMissingDetails()
    }

    private fun backfillMissingDetails() {
        viewModelScope.launch {
            try {
                val animes = repository.getAiringAnimes().first()

                val needsBackfill = animes.filter {
                    it.bangumiId != null && it.airWeekday == null
                }

                Log.d(TAG, "Backfill: found ${needsBackfill.size} animes missing airWeekday")

                for (anime in needsBackfill) {
                    try {
                        val detail = RetrofitClient.bangumiApi.getSubjectDetail(anime.bangumiId!!)
                        val updatedAirDate = anime.airDate ?: detail.date
                        val updatedAirWeekday = detail.airWeekday ?: anime.airWeekday
                        val updatedAnime = anime.copy(
                            airWeekday = updatedAirWeekday,
                            airDate = updatedAirDate,
                            summary = detail.summary?.cleanSummary()
                                ?: anime.summary,
                            isFinished = computeIsFinished(updatedAirDate, anime.totalEpisodes, anime.status)
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
