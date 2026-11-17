package com.aiexile.animetrack.data.media

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object LocalMediaScanState {
    const val IDLE = "IDLE"
    const val SCANNING = "SCANNING"
    const val COMPLETED = "COMPLETED"
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
        Index(value = ["episodeNumber"])
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
    val updatedAt: Long = System.currentTimeMillis()
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
