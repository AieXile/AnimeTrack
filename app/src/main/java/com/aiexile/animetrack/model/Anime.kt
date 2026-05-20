package com.aiexile.animetrack.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AnimeStatus(val displayName: String) {
    WATCHING("正在观看"),
    COMPLETED("已看完"),
    PLANNED("计划观看"),
    DROPPED("已弃番")
}

@Entity(
    tableName = "anime",
    indices = [Index(value = ["bangumiId"], unique = true)]
)
data class Anime(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val totalEpisodes: Int,
    val watchedEpisodes: Int,
    val status: AnimeStatus,
    val rating: Float?,
    val notes: String,
    val startDate: Long? = null,
    val finishDate: Long? = null,
    val coverUrl: String? = null,
    val airDate: String? = null,
    val summary: String? = null,
    val bangumiId: Int? = null,
    val airWeekday: Int? = null,
    val isFinished: Boolean = false,
    val currentEpisodes: Int = 0,
    val hasNewUpdate: Boolean = false
) {
    val progress: Float
        get() = if (totalEpisodes > 0) watchedEpisodes.toFloat() / totalEpisodes else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val effectiveMaxEpisodes: Int
        get() = if (totalEpisodes > 0) totalEpisodes else if (currentEpisodes > 0) currentEpisodes else 0
}
