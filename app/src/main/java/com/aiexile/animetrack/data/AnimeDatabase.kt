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
    version = 6,
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM anime WHERE bangumiId IS NOT NULL AND id NOT IN " +
                    "(SELECT MIN(id) FROM anime WHERE bangumiId IS NOT NULL GROUP BY bangumiId)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anime_bangumiId` ON `anime` (`bangumiId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN currentEpisodes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE anime ADD COLUMN hasNewUpdate INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        fun getDatabase(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "anime_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
