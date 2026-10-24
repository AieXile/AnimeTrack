package com.aiexile.animetrack.data.widget

import android.content.Context
import androidx.core.content.edit
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.util.getCurrentWeekday
import com.aiexile.animetrack.util.isUnaired

/**
 * 桌面组件的数据读取入口。
 *
 * 组件不持有 ViewModel：每次渲染直接从 Room 读取本地快照，因此不依赖登录态与网络。
 * 查询口径与 app 内保持一致：
 * - 今日放送：WATCHING/PLANNED、未完结、排除未开播（与首页今日计数、每日通知一致）；
 * - 在看：status = WATCHING，置顶 → 待看更新 → 最近观看时间；
 * - 统计：在追 / 今日更新 / 待看更新 / 已看完 / 累计观看集数。
 */
object WidgetDataProvider {

    private const val PREFS_NAME = "widget_meta"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"

    suspend fun loadTodayAiring(): List<Anime> {
        val dao = AppContainer.getAnimeDatabase().animeDao()
        return dao.getAiringAnimesByWeekday(getCurrentWeekday())
            .filter { !isUnaired(it.airDate) }
            .sortedWith(compareByDescending<Anime> { it.hasNewUpdate }.thenBy { it.title })
    }

    suspend fun loadWatching(): List<Anime> {
        val dao = AppContainer.getAnimeDatabase().animeDao()
        return dao.getAllAnimesList()
            .filter { it.status == AnimeStatus.WATCHING }
            .sortedWith(
                compareByDescending<Anime> { it.isPinned }
                    .thenByDescending { it.hasNewUpdate }
                    .thenByDescending { it.lastProgressAt ?: 0L }
            )
    }

    suspend fun loadStats(): WidgetStats {
        val all = AppContainer.getAnimeDatabase().animeDao().getAllAnimesList()
        val today = getCurrentWeekday()
        val airingStatuses = setOf(AnimeStatus.WATCHING, AnimeStatus.PLANNED)
        return WidgetStats(
            watching = all.count { it.status == AnimeStatus.WATCHING },
            todayUpdates = all.count {
                it.airWeekday == today &&
                    !it.isFinished &&
                    it.status in airingStatuses &&
                    !isUnaired(it.airDate)
            },
            pendingUpdates = all.count { it.hasNewUpdate },
            completed = all.count { it.status == AnimeStatus.COMPLETED },
            planned = all.count { it.status == AnimeStatus.PLANNED },
            dropped = all.count { it.status == AnimeStatus.DROPPED },
            totalWatchedEpisodes = all.sumOf { it.watchedEpisodes },
            totalAnimes = all.size
        )
    }

    /** 最近一次后台同步完成的时间（组件上「更新于」的数据来源） */
    fun lastSyncedAt(context: Context): Long? {
        val value = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_AT, 0L)
        return value.takeIf { it > 0L }
    }

    fun markSyncedNow(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()) }
    }
}

data class WidgetStats(
    val watching: Int,
    val todayUpdates: Int,
    val pendingUpdates: Int,
    val completed: Int,
    val planned: Int,
    val dropped: Int,
    val totalWatchedEpisodes: Int,
    val totalAnimes: Int
)
