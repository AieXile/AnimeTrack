package com.aiexile.animetrack.data.media

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object LocalMediaScanState {
    const val IDLE = "IDLE"
    const val SCANNING = "SCANNING"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    const val FAILED = "FAILED"
}

object LocalMediaPermissionState {
    const val GRANTED = "GRANTED"
    const val REVOKED = "REVOKED"
}

object LocalMediaMatchState {
    const val UNMATCHED = "UNMATCHED"
    const val AUTO_MATCHED = "AUTO_MATCHED"
    const val CONFIRMED = "CONFIRMED"
    const val IGNORED = "IGNORED"
}

/** 文件类型：正片参与选集连播，特典只单独播放。 */
object LocalEpisodeType {
    const val MAIN = "MAIN"
    const val EXTRA = "EXTRA"
    const val UNKNOWN = "UNKNOWN"
}

@Entity(
    tableName = "local_source",
    indices = [Index(value = ["treeUri"], unique = true)]
)
data class LocalMediaSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val lastScanAt: Long? = null,
    val videoCount: Int = 0,
    val scanState: String = LocalMediaScanState.IDLE,
    val scanError: String? = null,
    val permissionState: String = LocalMediaPermissionState.GRANTED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "local_media",
    foreignKeys = [
        ForeignKey(
            entity = LocalMediaSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId", "relativePath"], unique = true),
        Index(value = ["sourceId"]),
        Index(value = ["matchedAnimeId"]),
        Index(value = ["matchState"]),
        Index(value = ["episodeNumber"]),
        Index(value = ["sourceId", "groupKey"]),
        Index(value = ["sourceId", "archiveKey"])
    ]
)
data class LocalMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val documentUri: String,
    val relativePath: String,
    val fileName: String,
    val size: Long = 0,
    val lastModified: Long = 0,
    val parsedTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeEndNumber: Int? = null,
    val matchState: String = LocalMediaMatchState.UNMATCHED,
    val matchedAnimeId: Int? = null,
    val confidence: Float = 0f,
    val isUserOverride: Boolean = false,
    val isMissing: Boolean = false,
    /** MAIN / EXTRA / UNKNOWN；旧数据为 null，视为未知 */
    val episodeType: String? = null,
    /** 文件所在目录的 documentId，用于查找同目录外挂字幕 */
    val parentDocumentId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    /** 用户显式隐藏，与扫描缺失和匹配状态独立。 */
    @ColumnInfo(defaultValue = "0") val isHidden: Boolean = false,
    @ColumnInfo(defaultValue = "0") val parserVersion: Int = 0,
    val groupKey: String? = null,
    val archiveKey: String? = null,
    val titleEvidence: String? = null,
    val baseTitle: String? = null,
    val releaseYear: Int? = null,
    val decimalEpisode: String? = null,
    val originalEpisode: String? = null,
    val bangumiHint: Int? = null,
    val tmdbHint: Int? = null,
    val episodeOffset: Int? = null,
    val sidecarFingerprint: String? = null,
    @ColumnInfo(defaultValue = "0") val movie: Boolean = false,
    @ColumnInfo(defaultValue = "'NONE'") val scrapeState: String = LocalMediaScrapeState.NONE,
    @ColumnInfo(defaultValue = "0") val explicitEpisodeNumbers: Boolean = false,
    @ColumnInfo(defaultValue = "0") val groupOverride: Boolean = false
)

/** WebDAV 索引存储 HTTP(S) 请求地址，本地文件使用 SAF URI。 */
internal val LocalMediaEntity.isWebDav: Boolean
    get() = documentUri.startsWith("https://") || documentUri.startsWith("http://")

object LocalMediaScrapeState {
    const val NONE = "NONE"
    const val WAITING = "WAITING"
    const val REVIEW = "REVIEW"
    const val IDENTIFIED = "IDENTIFIED"
    const val ENRICH = "ENRICH"
}

/** 只属于媒体来源，不能代替追番记录，也不参与订阅同步。 */
@Entity(
    tableName = "local_media_archive",
    primaryKeys = ["sourceId", "key"],
    foreignKeys = [ForeignKey(
        entity = LocalMediaSourceEntity::class,
        parentColumns = ["id"],
        childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class LocalMediaArchiveEntity(
    val sourceId: Long,
    val key: String,
    val title: String,
    val coverUrl: String? = null,
    val bangumiId: Int? = null,
    val tmdbId: Int? = null,
    val year: Int? = null,
    val summary: String? = null,
    val episodeCount: Int? = null
)

/**
 * 单集完成记录。
 * 与「播放位置」分开：位置表达续播点，完成记录表达这一集是否看完，
 * 跳看与重看不依赖删除位置记录来推断。
 */
@Entity(
    tableName = "local_episode_progress",
    indices = [
        Index(value = ["animeId", "episodeNumber"], unique = true),
        Index(value = ["animeId"])
    ]
)
data class LocalEpisodeProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val animeId: Int,
    val seasonNumber: Int?,
    val episodeNumber: Int,
    val completedAt: Long
)
