package com.aiexile.animetrack.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.BangumiCalendarRepository
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.BangumiCalendarDay
import com.aiexile.animetrack.data.network.BangumiCalendarSubject
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.util.NetworkErrorUtils
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.util.computeIsFinished
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aiexile.animetrack.data.network.BangumiCalendarWeekday
import com.aiexile.animetrack.util.getCurrentWeekday

/** 每日放送页的界面状态 */
data class DailyBroadcastUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** 固定 7 个元素，下标 0 = 周一 … 6 = 周日 */
    val days: List<BangumiCalendarDay> = emptyList(),
    val error: String? = null,
    /** 数据写入缓存的时间，null 表示还没有任何数据 */
    val updatedAt: Long? = null,
    /** true 表示刷新失败、当前展示的是旧缓存 */
    val stale: Boolean = false,
    /** bangumiId → 本地番剧 id，用于「已添加」标记与跳转详情 */
    val addedAnimeIdsByBangumi: Map<Int, Int> = emptyMap(),
    val addingBangumiIds: Set<Int> = emptySet()
) {
    val totalCount: Int
        get() = days.sumOf { it.items.size }

    fun animeIdOf(subject: BangumiCalendarSubject): Int? =
        addedAnimeIdsByBangumi[subject.id]
}

/** 一次性事件：添加结果提示 */
sealed interface DailyBroadcastEvent {
    data class Added(val title: String, val animeId: Int) : DailyBroadcastEvent
    data class Duplicate(val title: String) : DailyBroadcastEvent
    data class Failed(val title: String) : DailyBroadcastEvent
}

class DailyBroadcastViewModel(
    private val calendarRepository: BangumiCalendarRepository,
    private val animeRepository: AnimeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DailyBroadcast"

        /** 日历接口不返回话数时按 12 集估算，与手动添加表单的默认值保持一致 */
        private const val DEFAULT_TOTAL_EPISODES = 12
    }

    private val _uiState = MutableStateFlow(DailyBroadcastUiState())
    val uiState: StateFlow<DailyBroadcastUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DailyBroadcastEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    /** 今天（1=周一 … 7=周日）动态读取，避免 ViewModel 长驻时跨午夜失效 */
    val currentWeekday: Int get() = getCurrentWeekday()

    init {
        load()
        observeLibrary()
    }

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = state.days.isEmpty(),
                    isRefreshing = state.days.isNotEmpty() || forceRefresh,
                    error = null
                )
            }
            try {
                val snapshot = calendarRepository.load(forceRefresh)
                _uiState.update { state ->
                    state.copy(
                        days = normalize(snapshot.days),
                        updatedAt = snapshot.fetchedAt,
                        stale = snapshot.stale,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                AppLogManager.e(TAG, "加载每日放送失败", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = NetworkErrorUtils.resolveNetworkError(
                            e,
                            NetworkErrorUtils.hostOfSource(SearchSource.BANGUMI),
                            settingsRepository
                        )
                    )
                }
            }
        }
    }

    /**
     * 把条目添加为「计划观看」。
     * 话数/放送日期/简介都来自日历接口本身，不需要再查条目详情。
     */
    fun addToLibrary(subject: BangumiCalendarSubject) {
        val state = _uiState.value
        if (subject.id in state.addingBangumiIds) return
        if (state.addedAnimeIdsByBangumi.containsKey(subject.id)) {
            _events.tryEmit(DailyBroadcastEvent.Duplicate(subject.displayName))
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(addingBangumiIds = it.addingBangumiIds + subject.id) }
            try {
                val existing = animeRepository.getAnimeByBangumiId(subject.id)
                if (existing != null) {
                    _events.emit(DailyBroadcastEvent.Duplicate(subject.displayName))
                    return@launch
                }

                val totalEpisodes = subject.episodeCount ?: DEFAULT_TOTAL_EPISODES
                val airDate = subject.airDate
                val airWeekday = subject.airWeekday?.takeIf { it in 1..7 }
                    ?: weekdayOf(subject.id)

                val anime = Anime(
                    title = subject.displayName.trim(),
                    totalEpisodes = totalEpisodes,
                    watchedEpisodes = 0,
                    status = AnimeStatus.PLANNED,
                    rating = null,
                    notes = "",
                    coverUrl = subject.coverUrl,
                    summary = subject.summary?.cleanSummary(),
                    bangumiId = subject.id,
                    airDate = airDate,
                    airWeekday = airWeekday,
                    isFinished = computeIsFinished(airDate, totalEpisodes, AnimeStatus.PLANNED)
                )
                val id = animeRepository.insertAnime(anime)
                animeRepository.downloadCoverAsync(
                    animeId = id.toInt(),
                    coverUrl = anime.coverUrl,
                    bangumiId = anime.bangumiId,
                    tmdbId = null
                )
                AppLogManager.i(TAG, "每日放送添加: ${anime.title} (id=$id)")
                _events.emit(DailyBroadcastEvent.Added(anime.title, id.toInt()))
            } catch (e: Exception) {
                AppLogManager.e(TAG, "每日放送添加失败: ${subject.displayName}", e)
                _events.emit(DailyBroadcastEvent.Failed(subject.displayName))
            } finally {
                _uiState.update { it.copy(addingBangumiIds = it.addingBangumiIds - subject.id) }
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            animeRepository.getAllAnimes()
                .map { animes ->
                    animes.mapNotNull { anime ->
                        anime.bangumiId?.let { bangumiId -> bangumiId to anime.id }
                    }.toMap()
                }
                .collect { mapping ->
                    _uiState.update { it.copy(addedAnimeIdsByBangumi = mapping) }
                }
        }
    }

    /** 接口没给 air_weekday 时，用条目所在的日历列兜底 */
    private fun weekdayOf(subjectId: Int): Int? =
        _uiState.value.days.firstOrNull { day -> day.items.any { it.id == subjectId } }?.weekdayId

    /** 补齐 7 天并仅保留动画条目，保证看板始终是 7 列 */
    private fun normalize(days: List<BangumiCalendarDay>): List<BangumiCalendarDay> {
        val byWeekday = days.groupBy { it.weekdayId }
        return (1..7).map { weekday ->
            BangumiCalendarDay(
                weekday = byWeekday[weekday]?.firstOrNull()?.weekday
                    ?: BangumiCalendarWeekday(id = weekday),
                items = byWeekday[weekday].orEmpty()
                    .flatMap { it.items.orEmpty() }
                    .filter { it.type == 2 }
            )
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DailyBroadcastViewModel(
                calendarRepository = AppContainer.getBangumiCalendarRepository(),
                animeRepository = AppContainer.getAnimeRepository(),
                settingsRepository = AppContainer.getSettingsRepository()
            ) as T
        }
    }
}
