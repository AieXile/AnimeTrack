package com.aiexile.animetrack.data

import androidx.room.TypeConverter
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.PendingDeletionKeyType

class AnimeTypeConverters {
    
    @TypeConverter
    fun fromAnimeStatus(status: AnimeStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toAnimeStatus(value: String): AnimeStatus {
        return try {
            AnimeStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            AnimeStatus.PLANNED
        }
    }

    @TypeConverter
    fun fromPendingDeletionKeyType(type: PendingDeletionKeyType): String {
        return type.name
    }

    /**
     * 未知值回退 LEGACY_LOCAL：补偿重试会跳过该类型，
     * 宁可残留到 TTL 过期也不能按错误类型误删其他番剧。
     */
    @TypeConverter
    fun toPendingDeletionKeyType(value: String): PendingDeletionKeyType {
        return try {
            PendingDeletionKeyType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PendingDeletionKeyType.LEGACY_LOCAL
        }
    }
}
