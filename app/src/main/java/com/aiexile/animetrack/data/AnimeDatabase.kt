package com.aiexile.animetrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aiexile.animetrack.model.Anime

@Database(
    entities = [Anime::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(AnimeTypeConverters::class)
abstract class AnimeDatabase : RoomDatabase() {
    
    abstract fun animeDao(): AnimeDao
    
    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN airWeekday INTEGER")
                db.execSQL("ALTER TABLE anime ADD COLUMN isFinished INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        fun getDatabase(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "anime_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
