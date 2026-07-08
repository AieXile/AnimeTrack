package com.aiexile.animetrack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(name = "usage_stats")

class UsageStatsRepository(private val context: Context) {

    private val dayFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyyMM")
    private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")

    // 获取当前日期的键
    private fun getDayKey(): String = LocalDate.now().format(dayFormatter)
    private fun getMonthKey(): String = YearMonth.now().format(monthFormatter)
    private fun getYearKey(): String = LocalDate.now().format(yearFormatter)

    // 生成 DataStore 键
    private fun openCountKey(period: String, dateKey: String) = intPreferencesKey("stats_${period}_${dateKey}_open")
    private fun usageSecondsKey(period: String, dateKey: String) = longPreferencesKey("stats_${period}_${dateKey}_seconds")
    private fun addedAnimeKey(period: String, dateKey: String) = intPreferencesKey("stats_${period}_${dateKey}_added")
    private fun completedAnimeKey(period: String, dateKey: String) = intPreferencesKey("stats_${period}_${dateKey}_completed")

    // 增量更新方法：同时更新日/月/年三个维度
    suspend fun incrementOpenCount() = withContext(Dispatchers.IO) {
        context.statsDataStore.edit { prefs ->
            val dayKey = getDayKey()
            val monthKey = getMonthKey()
            val yearKey = getYearKey()
            prefs[openCountKey("day", dayKey)] = (prefs[openCountKey("day", dayKey)] ?: 0) + 1
            prefs[openCountKey("month", monthKey)] = (prefs[openCountKey("month", monthKey)] ?: 0) + 1
            prefs[openCountKey("year", yearKey)] = (prefs[openCountKey("year", yearKey)] ?: 0) + 1
        }
    }

    suspend fun addUsageSeconds(seconds: Long) = withContext(Dispatchers.IO) {
        if (seconds < 5) return@withContext // 低于5秒不计
        context.statsDataStore.edit { prefs ->
            val dayKey = getDayKey()
            val monthKey = getMonthKey()
            val yearKey = getYearKey()
            prefs[usageSecondsKey("day", dayKey)] = (prefs[usageSecondsKey("day", dayKey)] ?: 0L) + seconds
            prefs[usageSecondsKey("month", monthKey)] = (prefs[usageSecondsKey("month", monthKey)] ?: 0L) + seconds
            prefs[usageSecondsKey("year", yearKey)] = (prefs[usageSecondsKey("year", yearKey)] ?: 0L) + seconds
        }
    }

    suspend fun incrementAddedAnime() = withContext(Dispatchers.IO) {
        context.statsDataStore.edit { prefs ->
            val dayKey = getDayKey()
            val monthKey = getMonthKey()
            val yearKey = getYearKey()
            prefs[addedAnimeKey("day", dayKey)] = (prefs[addedAnimeKey("day", dayKey)] ?: 0) + 1
            prefs[addedAnimeKey("month", monthKey)] = (prefs[addedAnimeKey("month", monthKey)] ?: 0) + 1
            prefs[addedAnimeKey("year", yearKey)] = (prefs[addedAnimeKey("year", yearKey)] ?: 0) + 1
        }
    }

    suspend fun incrementCompletedAnime() = withContext(Dispatchers.IO) {
        context.statsDataStore.edit { prefs ->
            val dayKey = getDayKey()
            val monthKey = getMonthKey()
            val yearKey = getYearKey()
            prefs[completedAnimeKey("day", dayKey)] = (prefs[completedAnimeKey("day", dayKey)] ?: 0) + 1
            prefs[completedAnimeKey("month", monthKey)] = (prefs[completedAnimeKey("month", monthKey)] ?: 0) + 1
            prefs[completedAnimeKey("year", yearKey)] = (prefs[completedAnimeKey("year", yearKey)] ?: 0) + 1
        }
    }

    // 查询方法
    fun getStats(period: StatsPeriod): Flow<UsageStats> {
        val dateKey = when (period) {
            StatsPeriod.DAY -> getDayKey()
            StatsPeriod.MONTH -> getMonthKey()
            StatsPeriod.YEAR -> getYearKey()
        }
        val periodStr = period.key

        return context.statsDataStore.data.map { prefs ->
            UsageStats(
                openCount = prefs[openCountKey(periodStr, dateKey)] ?: 0,
                usageSeconds = prefs[usageSecondsKey(periodStr, dateKey)] ?: 0L,
                addedAnime = prefs[addedAnimeKey(periodStr, dateKey)] ?: 0,
                completedAnime = prefs[completedAnimeKey(periodStr, dateKey)] ?: 0
            )
        }
    }
}
