package com.aiexile.animetrack.model

import androidx.compose.runtime.Immutable
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
@Immutable
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
    /** 番剧完结日期（yyyy-MM-dd）。来自 Bangumi infobox「播放结束」字段，仅番剧完结后才会被填充。null 表示未拉取过或番剧尚未完结。 */
    val airEndDate: String? = null,
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
    val summaryFetched: Boolean? = null,
    /**
     * 用户手动覆盖的连载状态。null 表示按 [computeIsFinished] 自动判定；
     * true 表示强制视为「连载中」（isFinished=false），false 表示强制视为「已完结」（isFinished=true）。
     * 用于详情页编辑界面允许用户手动修正系统自动判定的完结状态。
     */
    val airingStatusOverride: Boolean? = null,
    /** 最近一次更新观看进度的时间戳。用于主界面排序（最近更新进度的排前面）。null 表示从未更新过。 */
    val lastProgressAt: Long? = null,
    /** 用户手动置顶。置顶的卡片固定排在列表最前，不受筛选/排序影响。 */
    val isPinned: Boolean = false,
    /**
     * 跨设备稳定的远程同步 ID（UUID）。bangumiId 为空的番剧（手动添加/B站同步）
     * 上传订阅时用作服务端 animeId，替代仅本设备稳定的本地自增 id，避免多设备
     * 拉取合并时因 ID 语义错位产生重复记录。bangumiId 非空的番剧不使用此字段。
     */
    val remoteSyncId: String? = null
) {
    val progress: Float
        get() = if (totalEpisodes > 0) watchedEpisodes.toFloat() / totalEpisodes else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val effectiveMaxEpisodes: Int
        get() = if (totalEpisodes > 0) totalEpisodes else if (currentEpisodes > 0) currentEpisodes else 0
}
