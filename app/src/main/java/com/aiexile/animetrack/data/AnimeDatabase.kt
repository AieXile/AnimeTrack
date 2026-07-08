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
    version = 13,
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_title` ON `anime` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_coverUrl` ON `anime` (`coverUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_isFinished_status` ON `anime` (`isFinished`, `status`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN syncRemarks TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN tmdbId INTEGER DEFAULT NULL")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anime_tmdbId` ON `anime` (`tmdbId`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN seriesKey TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_seriesKey` ON `anime` (`seriesKey`)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN isSubscribed INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite 不支持 DROP COLUMN，通过重建表移除 isSubscribed 字段
                db.execSQL(
                    """
                    CREATE TABLE anime_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        totalEpisodes INTEGER NOT NULL,
                        watchedEpisodes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        rating REAL,
                        notes TEXT NOT NULL,
                        startDate INTEGER,
                        finishDate INTEGER,
                        coverUrl TEXT,
                        airDate TEXT,
                        summary TEXT,
                        bangumiId INTEGER,
                        airWeekday INTEGER,
                        isFinished INTEGER NOT NULL,
                        currentEpisodes INTEGER NOT NULL,
                        hasNewUpdate INTEGER NOT NULL,
                        syncRemarks TEXT,
                        tmdbId INTEGER,
                        seriesKey TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO anime_new (
                        id, title, totalEpisodes, watchedEpisodes, status, rating, notes,
                        startDate, finishDate, coverUrl, airDate, summary, bangumiId, airWeekday,
                        isFinished, currentEpisodes, hasNewUpdate, syncRemarks, tmdbId, seriesKey
                    )
                    SELECT
                        id, title, totalEpisodes, watchedEpisodes, status, rating, notes,
                        startDate, finishDate, coverUrl, airDate, summary, bangumiId, airWeekday,
                        isFinished, currentEpisodes, hasNewUpdate, syncRemarks, tmdbId, seriesKey
                    FROM anime
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE anime")
                db.execSQL("ALTER TABLE anime_new RENAME TO anime")
                // 重建索引
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anime_bangumiId` ON `anime` (`bangumiId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anime_tmdbId` ON `anime` (`tmdbId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_title` ON `anime` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_coverUrl` ON `anime` (`coverUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_isFinished_status` ON `anime` (`isFinished`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_seriesKey` ON `anime` (`seriesKey`)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN remoteCoverUrl TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "anime_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
