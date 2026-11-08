package com.aiexile.animetrack.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.StatsPeriod
import com.aiexile.animetrack.data.UsageStats
import com.aiexile.animetrack.data.UsageStatsRepository
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 使用统计弹窗的数据来源：所有 Repository / 数据库访问集中在这里，
 * Compose 层只消费状态，不再直接持有容器依赖。
 */
class UsageStatsViewModel(
    private val statsRepository: UsageStatsRepository,
    animeRepository: AnimeRepository,
    private val sessionStartTime: () -> Long
) : ViewModel() {

    val totalAnimeCount: StateFlow<Int> = animeRepository.getAllAnimes()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun stats(period: StatsPeriod): Flow<UsageStats> = statsRepository.getStats(period)

    /** 打开弹窗时结算一次当前会话的使用时长（与原实现一致：仅累计 >= 5 秒的时长） */
    fun recordSessionUsage() {
        viewModelScope.launch {
            val startTime = sessionStartTime()
            if (startTime > 0) {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                if (elapsedSeconds >= 5) {
                    statsRepository.addUsageSeconds(elapsedSeconds)
                }
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                UsageStatsViewModel(
                    statsRepository = AppContainer.getUsageStatsRepository(),
                    animeRepository = AppContainer.getAnimeRepository(),
                    sessionStartTime = { AppContainer.sessionStartTime }
                ) as T
        }
    }
}
