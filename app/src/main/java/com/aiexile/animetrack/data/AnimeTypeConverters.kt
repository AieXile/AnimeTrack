package com.aiexile.animetrack.data

import androidx.room.TypeConverter
import com.aiexile.animetrack.model.AnimeStatus

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
}
