package com.aiexile.animetrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aiexile.animetrack.model.Anime

@Database(
    entities = [Anime::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(AnimeTypeConverters::class)
abstract class AnimeDatabase : RoomDatabase() {
    
    abstract fun animeDao(): AnimeDao
    
    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null
        
        fun getDatabase(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "anime_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
