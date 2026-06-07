package com.aiexile.animetrack.ui.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import com.aiexile.animetrack.util.formatDateMonthDay
import com.aiexile.animetrack.util.formatDateYearMonth
import java.util.Calendar
import java.util.Date

data class TimelineMonth(
    val yearMonth: String,
    val year: Int,
    val month: Int,
    val entries: List<TimelineEntry>,
    val isWatchingSection: Boolean = false
)

data class TimelineEntry(
    val date: Long?,
    val dateLabel: String,
    val animeList: List<Anime>,
    val type: EntryType
)

enum class EntryType {
    WATCHING,
    FINISHED,
    DROPPED
}

class TimelineViewModel(
    private val repository: AnimeRepository
) : ViewModel() {
    
    val timelineData: StateFlow<List<TimelineMonth>>
    val watchingAnimeList: StateFlow<List<Anime>>
    
    companion object {
        private const val TAG = "TimelineViewModel"
    }
    
    init {
        val sharedAnimeList = repository.getAllAnimes()
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                replay = 1
            )

        watchingAnimeList = sharedAnimeList
            .map { animeList -> animeList.filter { it.status == AnimeStatus.WATCHING } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList()
            )

        timelineData = sharedAnimeList
            .map { animeList -> groupAnimeByMonth(animeList) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList()
            )
    }
    
    private fun groupAnimeByMonth(animeList: List<Anime>): List<TimelineMonth> {
        val entries = mutableListOf<TimelineEntry>()
        
        animeList.forEach { anime ->
            when (anime.status) {
                AnimeStatus.COMPLETED -> {
                    if (anime.finishDate != null) {
                        entries.add(
                            TimelineEntry(
                                date = anime.finishDate,
                                dateLabel = formatDateMonthDay(anime.finishDate),
                                animeList = listOf(anime),
                                type = EntryType.FINISHED
                            )
                        )
                    }
                }
                AnimeStatus.DROPPED -> {
                    if (anime.finishDate != null) {
                        entries.add(
                            TimelineEntry(
                                date = anime.finishDate,
                                dateLabel = formatDateMonthDay(anime.finishDate),
                                animeList = listOf(anime),
                                type = EntryType.DROPPED
                            )
                        )
                    }
                }
                AnimeStatus.WATCHING -> {
                }
                AnimeStatus.PLANNED -> {
                }
            }
        }
        
        val groupedByDate = entries
            .filter { it.date != null }
            .groupBy { it.date }
            .map { (date, entryList) ->
                val combinedAnimeList = entryList.flatMap { it.animeList }
                val type = entryList.firstOrNull()?.type ?: EntryType.FINISHED
                TimelineEntry(
                    date = date,
                    dateLabel = entryList.first().dateLabel,
                    animeList = combinedAnimeList,
                    type = type
                )
            }
            .sortedByDescending { it.date }
        
        val calendar = Calendar.getInstance()
        
        val groupedByMonth = groupedByDate.groupBy { entry ->
            calendar.timeInMillis = entry.date ?: 0L
            "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}"
        }.map { (_, entries) ->
            calendar.timeInMillis = entries.first().date ?: 0L
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            
            TimelineMonth(
                yearMonth = formatDateYearMonth(entries.first().date ?: 0L),
                year = year,
                month = month,
                entries = entries
            )
        }.sortedByDescending { it.year * 100 + it.month }
        
        return groupedByMonth
    }
    
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TimelineViewModel(AppContainer.getAnimeRepository()) as T
        }
    }
}
