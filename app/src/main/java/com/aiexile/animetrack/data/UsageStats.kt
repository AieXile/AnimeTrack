package com.aiexile.animetrack.data

data class UsageStats(
    val openCount: Int = 0,
    val usageSeconds: Long = 0,
    val addedAnime: Int = 0,
    val completedAnime: Int = 0
)

enum class StatsPeriod(val key: String) {
    DAY("day"),
    MONTH("month"),
    YEAR("year")
}
