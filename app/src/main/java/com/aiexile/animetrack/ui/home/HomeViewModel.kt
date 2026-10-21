package com.aiexile.animetrack.ui.home

import android.util.Log
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.ListSortMode
import com.aiexile.animetrack.data.RatingStandard
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.TmdbTvDetail
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.data.sse.SseEventTypes
import com.aiexile.animetrack.data.sync.BilibiliSyncManager
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.SearchUseCase
import com.aiexile.animetrack.domain.UpdateCheckUseCase
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.util.computeIsFinished
import com.aiexile.animetrack.util.getCurrentWeekday
import com.aiexile.animetrack.util.isAirDateInFuture
import com.aiexile.animetrack.util.isUnaired
import com.aiexile.animetrack.util.NetworkErrorUtils
import com.aiexile.animetrack.ui.components.AddAnimeFormState
import com.aiexile.animetrack.ui.update.UpdateViewModel
import com.aiexile.animetrack.ui.announcement.AnnouncementViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

sealed class AutoSyncState {
    object Idle : AutoSyncState()
    object Syncing : AutoSyncState()
    data class Completed(val count: Int) : AutoSyncState()
    data class Failed(val message: String) : AutoSyncState()
}

data class HomeUiState(
    val selectedFilter: AnimeFilter = AnimeFilter.ALL,
    val isLoading: Boolean = true,
    val isBottomSheetVisible: Boolean = false,
    /** 请求关闭添加面板：由 UI 层执行 bottomSheetState.hide() 动画后再走统一收尾，
     *  避免 SheetState 残留 Expanded 锚点导致下次打开面板位置偏高 */
    val pendingSheetDismiss: Boolean = false,
    val formState: AddAnimeFormState = AddAnimeFormState(),
    val formError: String? = null,
    val newlyAddedAnimeId: Long? = null,
    val shouldScrollToTop: Boolean = false,
    val selectedAnimeId: Long? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val hasSearched: Boolean = false,
    val searchSource: SearchSource = SearchSource.BANGUMI,
    val showCompletedToast: Boolean = false,
    val showDuplicateToast: Boolean = false,
    val showFormDialog: Boolean = false,
    val highlightedAnimeIds: Set<Long> = emptySet(),
    val todayUpdatePinnedIds: Set<Long> = emptySet(),
    val localSearchQuery: String = "",
    val isLocalSearchActive: Boolean = false,
    /** 手动排序编辑模式：主界面网格进入长按拖拽状态 */
    val isSortEditMode: Boolean = false,
    /** 排序编辑模式下的内存快照（拖拽仅改此列表，点保存才写库） */
    val sortEditList: List<Anime> = emptyList()
)

enum class AnimeFilter(val displayName: String) {
    ALL("全部"),
    WATCHING("正在观看"),
    COMPLETED("已看完"),
    PLANNED("计划观看"),
    DROPPED("已弃番"),
    NOT_AIRED("未放送")
}

class HomeViewModel(
    private val repository: AnimeRepository,
    private val settingsRepository: SettingsRepository,
    val updateViewModel: UpdateViewModel,
    val announcementViewModel: AnnouncementViewModel,
    private val searchUseCase: SearchUseCase,
    private val updateCheckUseCase: UpdateCheckUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "AnimeTrack"
        /**
         * 排序诊断日志的 tag：走 [AppLogManager] 落盘，正式版同样生效，
         * 反馈界面勾选「附带 App 日志」后可在 app_log.txt 里按 "Sort" 检索。
         */
        private const val SORT_LOG_TAG = "Sort"
        /** 排序明细条数上限：只打前 N 条，避免长列表把日志撑大 */
        private const val SORT_LOG_TOP_N = 8
        /** 单条日志里标题的最大长度，防止超长标题撑爆单行 */
        private const val SORT_LOG_TITLE_MAX = 20
        private val greetingPrefixes = listOf("Hi", "Hey", "Hello")
    }
    
    val gridState = LazyGridState()

    /**
     * 主页顶栏是否被下滑隐藏（「下滑隐藏顶栏」开关开启时由滚动驱动）。
     * MainOverlay 中计算写入，HomeScreen 顶栏占位与 MainOverlay 顶栏/FAB 同步消费。
     * 搜索激活期间显隐冻结；列表滚回顶部时恢复显示。
     */
    var isTopBarHidden by mutableStateOf(false)
        private set

    fun updateTopBarHidden(hidden: Boolean) {
        isTopBarHidden = hidden
    }
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var greetingHasAnimated = false
    private var lastAnimatedGreeting = ""
    private val randomGreetingPrefix = greetingPrefixes.random()

    fun resolveGreetingText(customGreeting: String): String {
        return if (customGreeting.isNotEmpty()) customGreeting else randomGreetingPrefix
    }

    fun shouldAnimateGreeting(text: String): Boolean {
        return !greetingHasAnimated || text != lastAnimatedGreeting
    }

    fun onGreetingAnimated(text: String) {
        greetingHasAnimated = true
        lastAnimatedGreeting = text
    }
    
    val animeList: StateFlow<List<Anime>> = repository.getAllAnimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val todayUpdateCount: StateFlow<Int> = animeList.map { animes ->
        val todayWeekday = getCurrentWeekday()
        // 排除未放送的番剧：仅有 airWeekday 匹配但尚未开播（airDate 在未来）的不计入今日更新
        // 仅统计在追/计划观看的番剧：弃番（DROPPED）的 isFinished 缺少兜底重算，
        // 可能长期残留 false 脏数据导致误报，与 highlightTodayUpdates 及每日通知逻辑对齐
        animes.count {
            it.airWeekday == todayWeekday
                && !it.isFinished
                && !isUnaired(it.airDate)
                && (it.status == AnimeStatus.WATCHING || it.status == AnimeStatus.PLANNED)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    /** 主界面列表排序方式（自动排序 / 手动顺序），持久化到 DataStore */
    val listSortMode: StateFlow<ListSortMode> = settingsRepository.listSortMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ListSortMode.RECENT_PROGRESS
        )

    /** 影响列表排序/分组的筛选参数，用于派生列表的去重防抖 */
    private data class ListParams(
        val filter: AnimeFilter,
        val searchQuery: String,
        val pinnedIds: Set<Long>,
        val sortMode: ListSortMode
    )

    private val listParams: StateFlow<ListParams> = combine(
        _uiState,
        listSortMode
    ) { state, sortMode ->
        ListParams(state.selectedFilter, state.localSearchQuery, state.todayUpdatePinnedIds, sortMode)
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ListParams(AnimeFilter.ALL, "", emptySet(), ListSortMode.RECENT_PROGRESS)
        )

    // 系列堆叠开关：hoist 到 ViewModel，避免 Composable 重建时 collectAsState 初始值闪烁
    // 导致 displayList item 数量变化（true=堆叠少项 / false=拆分多项），从而引发滚动位置漂移
    val seriesStackEnabled: StateFlow<Boolean> = settingsRepository.seriesStackEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true
        )

    // 堆叠卡片顶层页码记忆（系列 stableKey → topIndex）：翻页结算时写入 DataStore，
    // 冷启动恢复，杀死后台后顶层不再重置回第一张
    val seriesStackTopIndices: StateFlow<Map<String, Int>> = settingsRepository.seriesStackTopIndices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    fun setSeriesStackTopIndex(seriesKey: String, topIndex: Int) {
        viewModelScope.launch {
            settingsRepository.setSeriesStackTopIndex(seriesKey, topIndex)
        }
    }

    /**
     * 派生的首页列表：仅在数据或筛选参数变化时重新排序/分组。
     * 排序 + SeriesMatcher 正则分组是 CPU 密集计算，通过 flowOn(Default) 移出主线程，
     * 避免启动期 DB 写入触发的多次重算阻塞滑动帧；结果仍经 distinctUntilChanged 去重。
     *
     * 堆叠开启：调用 SeriesMatcher.groupAnimeList 分组为 Series；
     * 堆叠关闭：不分组，直接返回扁平 Single 列表，完全按主排序排列。
     */
    val filteredAnimeListItems: StateFlow<List<AnimeListItem>> =
        combine(animeList, listParams, seriesStackEnabled) { animes, params, stackEnabled ->
            val filtered = getFilteredAnimeList(animes, params.filter, params.searchQuery, params.pinnedIds, params.sortMode)
            val items = if (stackEnabled) {
                SeriesMatcher.groupAnimeList(filtered)
            } else {
                filtered.map { AnimeListItem.Single(it) }
            }
            logGrouping(stackEnabled, items)
            items
        }.flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private val _autoSyncState = MutableStateFlow<AutoSyncState>(AutoSyncState.Idle)
    val autoSyncState: StateFlow<AutoSyncState> = _autoSyncState.asStateFlow()

    private var autoSyncTriggered = false

    fun highlightTodayUpdates() {
        val todayWeekday = getCurrentWeekday()
        val todayAnimeIds = animeList.value
            .filter {
                it.airWeekday == todayWeekday
                    && !it.isFinished
                    && (it.status == AnimeStatus.WATCHING || it.status == AnimeStatus.PLANNED)
            }
            .map { it.id.toLong() }
            .toSet()
        if (todayAnimeIds.isEmpty()) return
        _uiState.update {
            it.copy(
                highlightedAnimeIds = todayAnimeIds,
                todayUpdatePinnedIds = todayAnimeIds
            )
        }
    }

    /**
     * 清除「今日更新」的临时状态：发光高亮与列表临时置顶必须成对清除。
     * 若只清高亮，todayUpdatePinnedIds 会残留整个会话，当天的卡将长期钉在
     * 列表最前（压在正在观看卡之上），且后续改状态也不会归位。
     */
    fun clearHighlight() {
        val clearedPins = _uiState.value.todayUpdatePinnedIds.size
        if (clearedPins > 0) {
            AppLogManager.i(SORT_LOG_TAG, "清除今日更新临时置顶: $clearedPins 部，列表按当前排序方式恢复")
        }
        if (_uiState.value.highlightedAnimeIds.isNotEmpty() || clearedPins > 0) {
            _uiState.update {
                it.copy(
                    highlightedAnimeIds = emptySet(),
                    todayUpdatePinnedIds = emptySet()
                )
            }
        }
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }
    
    init {
        // 批次 A（立即）：UI 数据优先加载
        viewModelScope.launch {
            animeList.first()
            _uiState.update { it.copy(isLoading = false) }
        }

        // 批次 B（延迟 500ms）：短任务
        viewModelScope.launch {
            delay(500)
            repository.reassignSeriesKeys()
            checkAiringAnimeUpdates()
        }
        viewModelScope.launch {
            delay(500)
            updateViewModel.checkForUpdate()
            announcementViewModel.fetchAnnouncements()
            // 将启动弹窗（更新/公告）整体活跃状态上报全局，
            // 供 AnimeTrackApp 强制邮箱绑定 Dialog 等待其结束后再显示，避免弹窗叠加。
            // observedActive 防竞态：检查请求为异步发起，初值快照可能尚无 isChecking/isLoading，
            // 在观察到首次活跃前保持阻塞（true），避免绑定弹窗抢先弹出后被更新弹窗叠加。
            var observedActive = false
            combine(
                updateViewModel.uiState,
                announcementViewModel.uiState
            ) { updateState, announceState ->
                val active = updateState.isChecking || updateState.updateInfo != null ||
                    announceState.isLoading || announceState.showDialog || announceState.pendingShow
                if (active) observedActive = true
                !observedActive || active
            }
                .distinctUntilChanged()
                .collect { active ->
                    AppContainer.startupDialogsActive.value = active
                }
        }

        // 协调公告与更新弹窗的显示顺序：仅当更新弹窗实际显示时阻塞公告，弹窗关闭后释放。
        // 更新检查进行中不阻塞（检查只是网络请求、可能很慢，公告无需干等）；
        // 若检查后确有新版，更新弹窗会叠在公告上方（HomeScreen 中 UpdateDialog 后组合、层级更高）。
        viewModelScope.launch {
            combine(
                updateViewModel.uiState,
                announcementViewModel.uiState
            ) { updateState, announceState ->
                val updateBlocking = updateState.updateInfo != null
                !updateBlocking && announceState.pendingShow
            }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    announcementViewModel.releasePendingShow()
                }
        }

        // 批次 C（延迟至 firstFrameRendered）：长任务
        viewModelScope.launch {
            AppContainer.firstFrameRendered.first { it }
            AppContainer.getSyncManager().syncRemoteToLocal()
        }
        viewModelScope.launch {
            AppContainer.firstFrameRendered.first { it }
            delay(500) // 与 syncRemoteToLocal 错峰
            triggerAutoSync()
        }

        // SSE 版本发布广播：立即重新检查更新（force 覆盖已有结果，
        // 让已打开 App 的用户第一时间收到新版本弹窗）
        viewModelScope.launch {
            AppContainer.getSseClient().events
                .filter { it.type == SseEventTypes.APP_UPDATE }
                .collect { updateViewModel.checkForUpdate(force = true) }
        }

        // 观看状态/进度变更后卡片会重排到列表顶部：同步将网格滚回顶部，
        // 保证变更后的卡片在可视区域内（详见 observeProgressChangesAndScrollToTop）
        observeProgressChangesAndScrollToTop()
    }

    /**
     * 由 HomeScreen 在首帧渲染完成后调用（监听 gridState 首个可见项），
     * 标记 [AppContainer.firstFrameRendered] 以触发批次 C 的长任务。
     */
    fun markFirstFrameRendered() {
        AppContainer.markFirstFrameRendered()
    }

    fun triggerAutoSync() {
        if (autoSyncTriggered) return
        autoSyncTriggered = true
        viewModelScope.launch {
            try {
                val bilibiliAuthManager = AppContainer.getBilibiliAuthManager()
                val isLoggedIn = bilibiliAuthManager.isLoggedIn.first()
                if (!isLoggedIn) return@launch

                val autoSyncEnabled = bilibiliAuthManager.bilibiliAutoSync.first()
                if (!autoSyncEnabled) return@launch

                val lastSyncTime = bilibiliAuthManager.lastSyncTime.first()
                val oneHourAgo = System.currentTimeMillis() - 3600000
                if (lastSyncTime > oneHourAgo) return@launch

                _autoSyncState.value = AutoSyncState.Syncing

                val bilibiliSyncManager = AppContainer.getBilibiliSyncManager()
                val result = bilibiliSyncManager.fetchAndSyncFiltered()

                _autoSyncState.value = if (result.isSuccess) {
                    AutoSyncState.Completed(result.getOrDefault(0))
                } else {
                    AppLogManager.e(TAG, "Auto sync failed: ${result.exceptionOrNull()?.message}")
                    AutoSyncState.Failed(result.exceptionOrNull()?.message ?: "同步失败")
                }

                // 3 秒后自动消失
                delay(3000)
                _autoSyncState.value = AutoSyncState.Idle
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Auto sync error", e)
                _autoSyncState.value = AutoSyncState.Failed(e.message ?: "同步失败")
                delay(3000)
                _autoSyncState.value = AutoSyncState.Idle
            }
        }
    }

    fun checkAiringAnimeUpdates() {
        viewModelScope.launch {
            updateCheckUseCase.checkAiringAnimeUpdates()
        }
    }
    
    fun setFilter(filter: AnimeFilter) {
        _uiState.update { 
            it.copy(
                selectedFilter = filter,
                selectedAnimeId = null
            )
        }
    }
    
    fun showBottomSheet() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Showing bottom sheet")
        _uiState.update { 
            it.copy(
                isBottomSheetVisible = true,
                pendingSheetDismiss = false,
                showFormDialog = false,
                formState = AddAnimeFormState(),
                formError = null,
                selectedAnimeId = null,
                searchResults = emptyList(),
                searchQuery = "",
                hasSearched = false,
                searchError = null
            )
        }
    }
    
    fun hideBottomSheet() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Hiding bottom sheet")
        _uiState.update { 
            it.copy(
                isBottomSheetVisible = false,
                pendingSheetDismiss = false,
                showFormDialog = false,
                formState = AddAnimeFormState(),
                formError = null
            )
        }
    }
    
    fun selectAnime(animeId: Long?) {
        _uiState.update { it.copy(selectedAnimeId = animeId) }
    }
    
    fun clearSelection() {
        _uiState.update { it.copy(selectedAnimeId = null) }
    }
    
    fun updateAnimeStatus(anime: Anime, newStatus: AnimeStatus) {
        viewModelScope.launch {
            val updatedAnime = if (newStatus == AnimeStatus.COMPLETED) {
                val newWatchedEpisodes = if (anime.totalEpisodes > 0) {
                    anime.totalEpisodes
                } else {
                    anime.watchedEpisodes
                }
                anime.copy(
                    status = newStatus,
                    isFinished = true,
                    watchedEpisodes = newWatchedEpisodes,
                    // 看完日期已有值则保留，切走状态再切回已看完时不被今天的日期覆盖
                    finishDate = if (anime.finishDate == null) System.currentTimeMillis() else anime.finishDate,
                    lastProgressAt = System.currentTimeMillis()
                )
            } else {
                // 非已看完状态同样保留 finishDate（历史看完日期，仅用户手动清除才置空）
                anime.copy(
                    status = newStatus,
                    isFinished = false,
                    lastProgressAt = System.currentTimeMillis()
                )
            }
            repository.updateAnime(updatedAnime)
            AppLogManager.i(
                SORT_LOG_TAG,
                "卡片改状态: ${anime.title} ${anime.status.sortLogLabel()} -> ${newStatus.sortLogLabel()}，" +
                    "时间键已刷新（模式=${listSortMode.value}；自动排序会重新分段，手动顺序不移动）"
            )

            if (anime.bangumiId != null) {
                val syncManager = AppContainer.getSyncManager()
                if (newStatus == AnimeStatus.COMPLETED && anime.totalEpisodes > 0) {
                    syncManager.pushProgressThenStatus(anime.bangumiId, anime.totalEpisodes, newStatus)
                } else {
                    syncManager.pushStatusToRemote(anime.bangumiId, newStatus)
                }
            }

            if (newStatus == AnimeStatus.COMPLETED) {
                val showToast = settingsRepository.completedToastEnabled.first()
                if (showToast) {
                    _uiState.update { it.copy(showCompletedToast = true) }
                }
            }
            clearSelection()
        }
    }

    fun dismissCompletedToast() {
        _uiState.update { it.copy(showCompletedToast = false) }
    }

    fun dismissDuplicateToast() {
        _uiState.update { it.copy(showDuplicateToast = false) }
    }

    fun clearAnimeUpdateFlag(animeId: Int) {
        viewModelScope.launch {
            repository.clearNewUpdate(animeId)
        }
    }

    /** 切换置顶状态：置顶的卡片固定排在列表最前，不受筛选/排序影响 */
    fun togglePin(anime: Anime) {
        viewModelScope.launch {
            repository.setPinned(anime.id, !anime.isPinned)
            AppLogManager.i(SORT_LOG_TAG, "置顶${if (!anime.isPinned) "开启" else "关闭"}: ${anime.title}")
            clearSelection()
        }
    }

    /**
     * 进入手动排序编辑模式：以当前可见列表（拆开系列堆叠）为快照，
     * 拖拽只改内存快照，点保存才批量写库。编辑期间筛选/搜索保持锁定。
     */
    fun startSortEditMode() {
        val snapshot = filteredAnimeListItems.value.flatMap { item ->
            when (item) {
                is AnimeListItem.Single -> listOf(item.anime)
                is AnimeListItem.Series -> item.animeList
                is AnimeListItem.ExpandedSeriesCard -> listOf(item.anime)
            }
        }
        if (snapshot.size < 2) return
        AppLogManager.i(
            SORT_LOG_TAG,
            "进入手动排序编辑: filter=${_uiState.value.selectedFilter} 快照=${snapshot.size} 条" +
                " 置顶=${snapshot.count { it.isPinned }} 未排序=${snapshot.count { it.manualOrder == null }}"
        )
        // 排序编辑前清空本地搜索，避免搜索过滤子集造成排序语义混乱
        _uiState.update {
            it.copy(
                localSearchQuery = "",
                isLocalSearchActive = false,
                isSortEditMode = true,
                sortEditList = snapshot
            )
        }
    }

    /**
     * 拖拽交换（来自 ReorderableLazyGrid 的 onMove）。
     * 置顶区/非置顶区锁定：跨区移动直接忽略（置顶卡片始终固定在最前）。
     */
    fun onSortItemMove(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.sortEditList
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            if (list[fromIndex].isPinned != list[toIndex].isPinned) {
                return@update state
            }
            state.copy(
                sortEditList = list.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            )
        }
    }

    /** 取消排序编辑：丢弃内存快照，不写库 */
    fun cancelSortEditMode() {
        _uiState.update { it.copy(isSortEditMode = false, sortEditList = emptyList()) }
    }

    /**
     * 保存手动顺序：把编辑快照的新顺序映射回全局 effective 序列并整体重编号（0..n-1），
     * 只写 manualOrder 变化的条目；随后排序方式切换为 MANUAL。
     *
     * 映射规则：快照条目在全局序列中占有一组槽位（升序），新顺序按序填入这组槽位。
     * 由于 effective 序列与快照都是「置顶在前」，置顶/非置顶分区天然保持一致，
     * 在筛选视图下编辑也只会重排该筛选子集内部的相对顺序，不污染其他条目。
     */
    fun saveSortEditOrder() {
        val edited = _uiState.value.sortEditList
        if (edited.isEmpty()) {
            cancelSortEditMode()
            return
        }
        viewModelScope.launch {
            try {
                val effective = animeList.value.sortedWith(manualOrderComparator)
                val effectiveIds = effective.map { it.id }.toHashSet()
                val presentEdited = edited.filter { it.id in effectiveIds }
                if (presentEdited.isNotEmpty()) {
                    val editedIds = presentEdited.map { it.id }.toHashSet()
                    val slots = effective.withIndex().filter { it.value.id in editedIds }.map { it.index }
                    val newEffective = effective.toMutableList()
                    slots.forEachIndexed { i, slot -> newEffective[slot] = presentEdited[i] }
                    val updates = newEffective.mapIndexedNotNull { index, anime ->
                        if (anime.manualOrder != index) anime.id to index else null
                    }
                    repository.updateManualOrders(updates)
                    AppLogManager.i(
                        SORT_LOG_TAG,
                        "保存手动排序: 快照 ${presentEdited.size} 条，写入 ${updates.size} 条；" +
                            "排序方式 -> MANUAL"
                    )
                }
                settingsRepository.setListSortMode(ListSortMode.MANUAL)
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to save manual sort order", e)
            } finally {
                cancelSortEditMode()
            }
        }
    }

    /** 切换排序方式（筛选菜单内单选） */
    fun setListSortMode(mode: ListSortMode) {
        val previous = listSortMode.value
        viewModelScope.launch {
            settingsRepository.setListSortMode(mode)
            AppLogManager.i(SORT_LOG_TAG, "排序方式切换: $previous -> $mode")
        }
    }

    fun hideFormDialog() {
        _uiState.update { it.copy(showFormDialog = false) }
    }

    fun showManualAddDialog() {
        _uiState.update {
            it.copy(
                formState = AddAnimeFormState(),
                formError = null,
                showFormDialog = true
            )
        }
    }
    
    fun deleteAnime(anime: Anime) {
        viewModelScope.launch {
            repository.deleteAnime(anime)
            if (BuildConfig.DEBUG) Log.d(TAG, "Deleted anime: ${anime.title}")
            clearSelection()
        }
    }
    
    fun updateFormState(formState: AddAnimeFormState) {
        val error = validateForm(formState)
        _uiState.update { 
            it.copy(
                formState = formState,
                formError = error
            )
        }
    }
    
    private fun validateForm(formState: AddAnimeFormState): String? {
        return when {
            formState.title.isBlank() -> "请输入番剧名称"
            formState.watchedEpisodes > formState.totalEpisodes -> "已看集数不能超过总集数"
            else -> null
        }
    }

    /** 将浮点数四舍五入到最近的 0.5 步进值 */
    private fun roundToHalf(value: Float): Float {
        val steps = (value * 2f).let { kotlin.math.round(it) }
        val clamped = steps.coerceIn(0f, 10f) // 0-5 星，步进 0.5
        return clamped / 2f
    }

    fun saveAnime() {
        val formState = _uiState.value.formState
        
        if (BuildConfig.DEBUG) Log.d(TAG, "saveAnime called with title: ${formState.title}")
        
        val error = validateForm(formState)
        if (error != null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Validation failed: $error")
            _uiState.update { it.copy(formError = error) }
            return
        }
        
        viewModelScope.launch {
            if (formState.bangumiId != null) {
                val existing = repository.getAnimeByBangumiId(formState.bangumiId)
                if (existing != null) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Duplicate bangumiId: ${formState.bangumiId}")
                    _uiState.update { it.copy(showDuplicateToast = true) }
                    return@launch
                }
            }

            if (formState.tmdbId != null) {
                val existing = repository.getAnimeByTmdbId(formState.tmdbId)
                if (existing != null) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Duplicate tmdbId: ${formState.tmdbId}")
                    _uiState.update { it.copy(showDuplicateToast = true) }
                    return@launch
                }
            }

            val anime = Anime(
                title = formState.title.trim(),
                totalEpisodes = formState.totalEpisodes,
                watchedEpisodes = formState.watchedEpisodes,
                status = formState.status,
                rating = formState.rating,
                notes = formState.notes.trim(),
                startDate = formState.startDate,
                finishDate = formState.finishDate,
                coverUrl = formState.coverUrl,
                summary = formState.summary,
                bangumiId = formState.bangumiId,
                tmdbId = formState.tmdbId,
                airDate = formState.airDate,
                airWeekday = formState.airWeekday,
                currentEpisodes = formState.currentEpisodes,
                airEndDate = formState.airEndDate,
                isFinished = computeIsFinished(formState.airDate, formState.totalEpisodes, formState.status, formState.airEndDate)
            )
            
            if (BuildConfig.DEBUG) Log.d(TAG, "Inserting anime: $anime")
            
            try {
                val id = repository.insertAnime(anime)
                if (BuildConfig.DEBUG) Log.d(TAG, "Anime inserted successfully with id: $id")
                AppLogManager.i(
                    SORT_LOG_TAG,
                    "新增番剧: ${anime.title} 状态=${anime.status.sortLogLabel()}" +
                        "（manualOrder 为空：手动顺序下排在置顶之后、手动区之前；自动排序按区块分段）"
                )
                
                _uiState.update { 
                    it.copy(
                        // 不直接移除面板，改由 UI 层播放 hide() 动画后统一收尾，
                        // 防止 SheetState 残留 Expanded 锚点导致下次打开位置偏高
                        pendingSheetDismiss = true,
                        showFormDialog = false,
                        formState = AddAnimeFormState(),
                        formError = null,
                        newlyAddedAnimeId = id,
                        shouldScrollToTop = true
                    )
                }

                repository.downloadCoverAsync(
                    animeId = id.toInt(),
                    coverUrl = anime.coverUrl,
                    bangumiId = anime.bangumiId,
                    tmdbId = anime.tmdbId
                )

                if (anime.bangumiId != null && (anime.airWeekday == null || anime.airDate == null || anime.summary == null || anime.airEndDate == null)) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val detail = repository.fetchBangumiDetail(anime.bangumiId) ?: return@launch
                            val dbAnime = repository.getAnimeById(id.toInt()) ?: return@launch
                            val updatedAirDate = dbAnime.airDate ?: detail.date
                            val updatedAirWeekday = dbAnime.airWeekday ?: detail.airWeekday
                            // 解析 infobox「播放结束」日期（仅番剧完结后 Bangumi 才会填充）
                            val parsedAirEndDate = detail.parseEndDate()
                            // 仅在有新字段可补齐时才写库，避免无意义写入
                            val hasNewField = updatedAirWeekday != dbAnime.airWeekday
                                || updatedAirDate != dbAnime.airDate
                                || (dbAnime.summary == null && detail.summary != null)
                                || (parsedAirEndDate != null && parsedAirEndDate != dbAnime.airEndDate)
                            if (!hasNewField) return@launch
                            val updated = dbAnime.copy(
                                airWeekday = updatedAirWeekday,
                                airDate = updatedAirDate,
                                summary = dbAnime.summary ?: detail.summary?.cleanSummary(),
                                airEndDate = parsedAirEndDate ?: dbAnime.airEndDate,
                                isFinished = computeIsFinished(updatedAirDate, dbAnime.totalEpisodes, dbAnime.status, parsedAirEndDate ?: dbAnime.airEndDate, dbAnime.airingStatusOverride)
                            )
                            repository.updateAnimeInternal(updated)
                            if (BuildConfig.DEBUG) Log.d(TAG, "Backfilled detail for animeId=$id")
                        } catch (e: Exception) {
                            AppLogManager.e(TAG, "Failed to backfill detail for animeId=$id", e)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to insert anime", e)
            }
        }
    }
    
    fun onScrollCompleted() {
        _uiState.update { it.copy(shouldScrollToTop = false) }
    }

    /**
     * 触发列表滚动到顶部（由外层 MainOverlay 的"回到顶部" FAB 调用）。
     * HomeScreen 内部的 LaunchedEffect 会监听 [HomeUiState.shouldScrollToTop] 执行实际滚动。
     */
    fun scrollToTop() {
        _uiState.update { it.copy(shouldScrollToTop = true) }
    }

    /**
     * 监听观看状态/进度变更（特征：已有番剧的 lastProgressAt 被刷新），把主界面网格滚回顶部。
     *
     * 变更后的卡片会按最近进度重排到列表顶部，若网格停留在原滚动位置：
     * - 主页卡片菜单改状态：卡片直接从可视区域消失；
     * - 详情页改进度/状态：返回时卡片不在可视区域，共享元素退出动画丢失。
     * 因此检测到变更立即滚回顶部。详情页打开期间 HomeScreen 虽未组合，
     * 但 gridState 存活于本 ViewModel，scrollToItem 在脱离组合时记录目标位置，
     * 返回主页时网格自顶部开始组合，卡片位于首屏，退出动画正常匹配。
     *
     * 仅比对已有条目的 lastProgressAt（同步流程只改 status/进度不写此字段），
     * 避免自动同步引起的列表变动误触发跳顶。
     */
    private fun observeProgressChangesAndScrollToTop() {
        viewModelScope.launch {
            var previousById: Map<Int, Anime>? = null
            animeList.collect { current ->
                val previous = previousById
                previousById = current.associateBy { it.id }
                if (previous == null) return@collect
                val progressChanged = current.any { new ->
                    val old = previous[new.id]
                    old != null && old.lastProgressAt != new.lastProgressAt
                }
                if (progressChanged) {
                    // 手动排序模式下列表不随进度重排，跳顶反而打断用户浏览，跳过
                    val mode = listSortMode.value
                    if (mode != ListSortMode.MANUAL) {
                        AppLogManager.i(SORT_LOG_TAG, "进度变更: 自动排序重排并滚回顶部（mode=$mode）")
                        gridState.scrollToItem(index = 0)
                    } else {
                        AppLogManager.i(SORT_LOG_TAG, "进度变更: 手动顺序保持位置、不跳顶（mode=$mode）")
                    }
                }
            }
        }
    }
    
    fun onHighlightCompleted() {
        _uiState.update { it.copy(newlyAddedAnimeId = null) }
    }
    
    /**
     * 手动排序模式的排序键：置顶优先 → 未参与排序的新条目（manualOrder 为 null）在最前 →
     * 已设位置条目按 manualOrder 升序 → 最近修改时间降序兜底。
     *
     * 新条目（手动添加 / 同步新增 / 备份恢复）从不写 manualOrder：若按 Int.MAX_VALUE
     * 排在末尾，会全部沉到最后一页；这里把「未设位置」视为最新条目，排在置顶区之后、
     * 手动区之前，同组按 lastProgressAt 降序（最近添加的在最上面）。
     * 已设位置条目不再跟随 lastProgressAt 自动重排（顺序完全由拖拽结果决定）。
     */
    private val manualOrderComparator: Comparator<Anime> =
        compareBy<Anime> { !it.isPinned }
            .thenBy { it.manualOrder != null }
            .thenBy { it.manualOrder ?: 0 }
            .thenByDescending { it.lastProgressAt ?: it.startDate ?: Long.MIN_VALUE }

    // ===== 排序诊断日志 =====
    // 走 AppLogManager 落盘（正式版同样生效），反馈界面勾选「附带 App 日志」后，
    // 可在导出的 app_log.txt 中按 "Sort" 检索。仅记录排序键，不写用户隐私数据。

    /** 排序/分组日志的互斥锁与去重签名：仅在结果变化时写一条，避免同序重复刷屏 */
    private val sortLogLock = Any()
    private var lastSortLogSignature: String? = null
    private var lastGroupingLogSignature: String? = null

    private val sortLogTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    /** 排序日志里的时间键：null 表示 lastProgressAt 与 startDate 都缺失（自动排序会沉到段底） */
    private fun formatSortLogTime(timestamp: Long?): String =
        timestamp?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(sortLogTimeFormat)
        } ?: "无时间键"

    /** 排序日志里的状态简称 */
    private fun AnimeStatus.sortLogLabel(): String = when (this) {
        AnimeStatus.WATCHING -> "在追"
        AnimeStatus.COMPLETED -> "已看完"
        AnimeStatus.PLANNED -> "计划"
        AnimeStatus.DROPPED -> "已弃番"
    }

    /**
     * 记录一次排序结果，分两行：
     * - 摘要：模式 / 筛选 / 输入输出条数 / 置顶 / 今日临时置顶 / 未排序新条目 / 状态分布 / 区块分布；
     * - 明细：前 [SORT_LOG_TOP_N] 条的排序键（状态、置顶、manualOrder、区块、时间键）。
     *
     * 用于排查「新加的番为什么在最前/沉到最后一页」「改状态为什么不掉下去」「未放送为什么
     * 排在计划观看后面」这类问题：自动模式看区块与时间键，手动模式看 manualOrder 与
     * 「未排序新条目」的落位。
     */
    private fun logSortResult(
        sortMode: ListSortMode,
        filter: AnimeFilter,
        searchQuery: String,
        totalInput: Int,
        filtered: List<Anime>,
        result: List<Anime>,
        pinnedIds: Set<Long>
    ) {
        val signature = buildString {
            append(sortMode).append('|').append(filter).append('|')
            append(searchQuery).append('|').append(pinnedIds.size).append('|')
            result.forEach { append(it.id).append(',') }
        }
        synchronized(sortLogLock) {
            if (signature == lastSortLogSignature) return
            lastSortLogSignature = signature
        }

        val summary = buildString {
            append("排序计算 mode=").append(sortMode)
            append(" filter=").append(filter)
            append(" 输入=").append(totalInput)
            append(" 输出=").append(result.size)
            append(" 置顶=").append(result.count { it.isPinned })
            append(" 今日临时置顶=").append(pinnedIds.size)
            append(" 未排序新条目=").append(filtered.count { it.manualOrder == null })
            if (searchQuery.isNotBlank()) append(" 搜索=").append(searchQuery)
            append(" 状态分布=")
            append(
                AnimeStatus.entries.joinToString("/") { status ->
                    "${status.sortLogLabel()}${filtered.count { it.status == status }}"
                }
            )
            append(" 区块分布=")
            append(
                AutoSortBlock.entries.joinToString("/") { block ->
                    "${block.displayName}${filtered.count { it.autoSortBlock() == block }}"
                }
            )
        }
        val detail = buildString {
            append("排序前").append(minOf(result.size, SORT_LOG_TOP_N)).append("条: ")
            append(
                result.take(SORT_LOG_TOP_N).mapIndexed { index, anime ->
                    buildString {
                        append('#').append(index + 1).append(' ')
                        append(anime.title.take(SORT_LOG_TITLE_MAX))
                        append('[').append(anime.status.sortLogLabel())
                        if (anime.isPinned) append("/置顶")
                        append("/手动=").append(anime.manualOrder?.toString() ?: "null")
                        val block = anime.autoSortBlock()
                        append("/段=").append(block.displayName)
                        append("/键=").append(formatSortLogTime(anime.recentModifiedAt()))
                        if (block == AutoSortBlock.COMPLETED) {
                            append("/看完=").append(formatSortLogTime(anime.finishDate))
                        }
                        append(']')
                    }
                }.joinToString(" | ")
            )
            if (result.size > SORT_LOG_TOP_N) append(" …(共").append(result.size).append("条)")
        }
        AppLogManager.i(SORT_LOG_TAG, summary)
        AppLogManager.i(SORT_LOG_TAG, detail)
    }

    /** 记录堆叠分组结果：确认新条目是否被并进系列组（系列组位置取排序里先出现的那条） */
    private fun logGrouping(stackEnabled: Boolean, items: List<AnimeListItem>) {
        val groups = items.filterIsInstance<AnimeListItem.Series>()
        val signature = buildString {
            append(stackEnabled).append('|').append(items.size)
            groups.forEach { group ->
                append('|').append(group.stableKey).append(':')
                append(group.animeList.joinToString(",") { it.id.toString() })
            }
        }
        synchronized(sortLogLock) {
            if (signature == lastGroupingLogSignature) return
            lastGroupingLogSignature = signature
        }
        val detail = if (groups.isEmpty()) {
            "无系列组"
        } else {
            groups.joinToString(" | ") { group ->
                group.baseTitle + "(" +
                    group.animeList.joinToString(",") { "${it.title.take(10)}#${it.id}" } + ")"
            }
        }
        AppLogManager.i(
            SORT_LOG_TAG,
            "堆叠分组: 堆叠=${if (stackEnabled) "开" else "关"} 网格项=${items.size} 系列组=${groups.size} $detail"
        )
    }

    // ===== 自动排序（状态区块 + 区块内部排序） =====
    // 区块顺序固定为 正在观看 → 计划观看 → 未放送 → 已看完 → 已弃番，区块内部再按各自规则排序。
    // 手动顺序模式不经过这里（完全由 manualOrder 决定）。

    /** 自动排序区块：enum 声明顺序即列表里的区块顺序 */
    private enum class AutoSortBlock(val displayName: String) {
        WATCHING("正在观看"),
        PLANNED("计划观看"),
        UNAIRED("未放送"),
        COMPLETED("已看完"),
        DROPPED("已弃番")
    }

    /**
     * 判定番剧所属区块：
     * - 已看完 / 已弃番 按状态归类（即使放送日期在未来也仍属对应状态，避免状态「消失」）；
     * - 其余条目尚未开播 → 未放送（详情页对未放送番剧隐藏状态组件，等待区不能混进在追段）；
     * - 否则按 正在观看 / 计划观看。
     *
     * 用 [isAirDateInFuture] 而非 isUnaired：无放送日期的条目不算未放送，与「未放送」筛选一致。
     */
    private fun Anime.autoSortBlock(): AutoSortBlock = when {
        status == AnimeStatus.COMPLETED -> AutoSortBlock.COMPLETED
        status == AnimeStatus.DROPPED -> AutoSortBlock.DROPPED
        isAirDateInFuture(airDate) -> AutoSortBlock.UNAIRED
        status == AnimeStatus.PLANNED -> AutoSortBlock.PLANNED
        else -> AutoSortBlock.WATCHING
    }

    /** 「最近修改」时间：添加番剧 / 改进度 / 改状态都会刷新 [Anime.lastProgressAt]，旧数据回退 startDate */
    private fun Anime.recentModifiedAt(): Long? = lastProgressAt ?: startDate

    /** 段内排序键：最近修改降序；缺失时间视为最旧，沉到段底 */
    private val recentModifiedDesc: Comparator<Anime> =
        compareByDescending { it.recentModifiedAt() ?: Long.MIN_VALUE }

    /** 已看完段：看完日期降序（最近刚看完在前），看完日期相同则最近修改在前 */
    private val completedDesc: Comparator<Anime> =
        compareByDescending<Anime> { it.finishDate ?: Long.MIN_VALUE }
            .thenByDescending { it.recentModifiedAt() ?: Long.MIN_VALUE }

    /** 区块内部排序：只有已看完段用看完日期，其余段统一最近修改优先 */
    private fun AutoSortBlock.innerComparator(): Comparator<Anime> =
        if (this == AutoSortBlock.COMPLETED) completedDesc else recentModifiedDesc

    /**
     * 自动排序：按 [AutoSortBlock] 切段后逐段排序，段顺序与段内规则互不干扰。
     * 每部番只判定一次区块（未放送判定需要解析放送日期），避免在 O(n log n) 次比较里重复解析。
     */
    private fun autoSortList(animes: List<Anime>): List<Anime> {
        if (animes.isEmpty()) return animes
        val byBlock = animes.groupBy { it.autoSortBlock() }
        return AutoSortBlock.entries.flatMap { block ->
            byBlock[block]?.sortedWith(block.innerComparator()) ?: emptyList()
        }
    }

    fun getFilteredAnimeList(
        animeList: List<Anime>,
        filter: AnimeFilter,
        searchQuery: String = "",
        pinnedIds: Set<Long> = emptySet(),
        sortMode: ListSortMode = ListSortMode.RECENT_PROGRESS
    ): List<Anime> {
        // 排序日志统一由 logSortResult 输出（含模式/筛选/条数/前 N 条排序键），此处不再单独打点
        val filtered = when (filter) {
            AnimeFilter.ALL -> animeList
            AnimeFilter.WATCHING -> animeList.filter { it.status == AnimeStatus.WATCHING }
            AnimeFilter.COMPLETED -> animeList.filter { it.status == AnimeStatus.COMPLETED }
            AnimeFilter.PLANNED -> animeList.filter { it.status == AnimeStatus.PLANNED }
            AnimeFilter.DROPPED -> animeList.filter { it.status == AnimeStatus.DROPPED }
            AnimeFilter.NOT_AIRED -> animeList.filter { isAirDateInFuture(it.airDate) }
        }

        // 手动排序模式：完全按用户拖拽保存的顺序，忽略区块分组与进度时间键
        val sorted = if (sortMode == ListSortMode.MANUAL) {
            filtered.sortedWith(manualOrderComparator)
        } else {
            autoSortList(filtered)
        }

        val result = if (searchQuery.isNotBlank()) {
            sorted.filter { it.title.contains(searchQuery, ignoreCase = true) }
        } else if (pinnedIds.isNotEmpty()) {
            val pinned = sorted.filter { it.id.toLong() in pinnedIds }
            val unpinned = sorted.filter { it.id.toLong() !in pinnedIds }
            pinned + unpinned
        } else {
            sorted
        }

        // 用户手动置顶的卡片固定排在最前，不受筛选/排序/搜索影响；
        // sortedBy 为稳定排序，多个置顶之间及其余卡片仍保持上述排序顺序
        val finalList = result.sortedBy { !it.isPinned }
        logSortResult(
            sortMode = sortMode,
            filter = filter,
            searchQuery = searchQuery,
            totalInput = animeList.size,
            filtered = filtered,
            result = finalList,
            pinnedIds = pinnedIds
        )
        return finalList
    }

    fun updateLocalSearchQuery(query: String) {
        _uiState.update { it.copy(localSearchQuery = query) }
    }
    
    fun startLocalSearch() {
        _uiState.update { it.copy(isLocalSearchActive = true) }
    }
    
    fun clearLocalSearch() {
        _uiState.update { it.copy(localSearchQuery = "", isLocalSearchActive = false) }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchSource(source: SearchSource) {
        _uiState.update { it.copy(searchSource = source) }
    }

    fun searchAnime() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null) }
            return
        }

        val source = _uiState.value.searchSource
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, hasSearched = true) }

            try {
                val results = searchUseCase.search(query, source)
                if (BuildConfig.DEBUG) Log.d(TAG, "Search results: ${results.size} items (source=$source)")
                _uiState.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Search failed", e)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = "搜索失败: ${
                            NetworkErrorUtils.resolveNetworkError(
                                e, NetworkErrorUtils.hostOfSource(source), settingsRepository
                            )
                        }",
                        searchResults = emptyList()
                    )
                }
            }
        }
    }
    
    fun selectSearchResult(result: SearchResult) {
        val totalEpisodes = result.episodeCount ?: 12
        val summary = result.summary?.cleanSummary()
        // 源评分（10 分制）按 /2 转为 5 分制，并四舍五入到 0.5
        val sourceRating = result.rating?.let { roundToHalf(it / 2f) }

        _uiState.update {
            it.copy(
                formState = it.formState.copy(
                    title = result.title,
                    totalEpisodes = totalEpisodes,
                    coverUrl = result.coverUrl,
                    rating = sourceRating,
                    summary = summary,
                    bangumiId = if (result.source == SearchSource.BANGUMI) result.sourceId else null,
                    tmdbId = if (result.source == SearchSource.TMDB) result.sourceId else null
                ),
                showFormDialog = true
            )
        }

        viewModelScope.launch {
            // 评分标准为 MANUAL 时，清空源评分，由用户在表单中手动选择
            if (settingsRepository.ratingStandard.first() == RatingStandard.MANUAL) {
                _uiState.update {
                    it.copy(formState = it.formState.copy(rating = null))
                }
            }
            try {
                when (result.source) {
                    SearchSource.BANGUMI -> {
                        val detail = repository.fetchBangumiDetail(result.sourceId) ?: return@launch
                        val apiEps = detail.eps
                        val apiTotalEps = detail.totalEpisodes

                        val mainEps = if (apiEps != null && apiEps > 0) apiEps else 0
                        val allEps = if (apiTotalEps != null && apiTotalEps > 0) apiTotalEps else 0

                        val finalTotal = when {
                            mainEps > 0 -> mainEps
                            allEps > 0 -> allEps
                            else -> 0
                        }

                        // 解析 infobox「播放结束」日期（仅番剧完结后 Bangumi 才会填充）
                        val parsedAirEndDate = detail.parseEndDate()

                        _uiState.update { state ->
                            val updatedAirDate = detail.date ?: state.formState.airDate
                            val isNotYetAired = isAirDateInFuture(updatedAirDate)
                            state.copy(
                                formState = state.formState.copy(
                                    totalEpisodes = if (finalTotal > 0) finalTotal else state.formState.totalEpisodes,
                                    currentEpisodes = if (finalTotal > 0) 0 else if (mainEps > 0) 0 else state.formState.currentEpisodes,
                                    airDate = updatedAirDate,
                                    airWeekday = detail.airWeekday ?: state.formState.airWeekday,
                                    summary = detail.summary?.cleanSummary()
                                        ?: state.formState.summary,
                                    airEndDate = parsedAirEndDate ?: state.formState.airEndDate,
                                    status = if (isNotYetAired) AnimeStatus.PLANNED else state.formState.status
                                )
                            )
                        }
                    }
                    SearchSource.TMDB -> {
                        val detail = repository.getTmdbTvDetail(result.sourceId)
                        val eps = detail.numberOfEpisodes
                        val finalTotal = if (eps != null && eps > 0) eps else 0

                        _uiState.update { state ->
                            val updatedAirDate = detail.firstAirDate ?: state.formState.airDate
                            val isNotYetAired = isAirDateInFuture(updatedAirDate)
                            state.copy(
                                formState = state.formState.copy(
                                    totalEpisodes = if (finalTotal > 0) finalTotal else state.formState.totalEpisodes,
                                    airDate = updatedAirDate,
                                    summary = detail.overview?.cleanSummary()
                                        ?: state.formState.summary,
                                    status = if (isNotYetAired) AnimeStatus.PLANNED else state.formState.status
                                )
                            )
                        }
                    }
                    SearchSource.ALL -> { /* ALL 模式下结果已标记具体来源，不会走到这里 */ }
                }
            } catch (e: Exception) {
                AppLogManager.e(TAG, "Failed to fetch detail for ${result.source}Id: ${result.sourceId}", e)
            }
        }
    }
    
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = AppContainer.getAnimeRepository()
            val settingsRepository = AppContainer.getSettingsRepository()
            val updateRepository = UpdateRepository()
            val updateViewModel = UpdateViewModel(updateRepository, settingsRepository)
            val userAuthManager = AppContainer.getUserAuthManager()
            val announcementViewModel = AnnouncementViewModel(settingsRepository, userAuthManager)
            val searchUseCase = SearchUseCase(repository)
            val updateCheckUseCase = UpdateCheckUseCase(repository)
            return HomeViewModel(repository, settingsRepository, updateViewModel, announcementViewModel, searchUseCase, updateCheckUseCase) as T
        }
    }
}
