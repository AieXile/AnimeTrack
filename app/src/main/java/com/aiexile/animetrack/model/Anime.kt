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
    indices = [
        Index(value = ["bangumiId"], unique = true),
        Index(value = ["tmdbId"], unique = true),
        Index(value = ["title"]),
        Index(value = ["coverUrl"]),
        Index(value = ["isFinished", "status"]),
        Index(value = ["seriesKey"])
    ]
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
    val hasNewUpdate: Boolean = false,
    val syncRemarks: String? = null,
    val tmdbId: Int? = null,
    /** 系列识别 key（= baseTitle），同系列多季番剧共享。null 表示未识别为系列。 */
    val seriesKey: String? = null,
    /** 远程封面 URL（wsrv.nl 代理或原始 URL），用于同步到后端。coverUrl 被本地化后仍保留此值。 */
    val remoteCoverUrl: String? = null,
    /** 是否已从 API 获取过简介。true 表示已尝试获取（无论 summary 是否为空），null/false 表示尚未获取。 */
    val summaryFetched: Boolean? = null
) {
    val progress: Float
        get() = if (totalEpisodes > 0) watchedEpisodes.toFloat() / totalEpisodes else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val effectiveMaxEpisodes: Int
        get() = if (totalEpisodes > 0) totalEpisodes else if (currentEpisodes > 0) currentEpisodes else 0
}
