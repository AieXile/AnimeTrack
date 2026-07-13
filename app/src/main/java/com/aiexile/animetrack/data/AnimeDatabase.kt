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
    version = 15,
    exportSchema = false
)
@TypeConverters(AnimeTypeConverters::class)
abstract class AnimeDatabase : RoomDatabase() {
    
    abstract fun animeDao(): AnimeDao
    
    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        /**
         * 1→2 迁移：早期开发版（未公开发布），schema 与 v2、v3 相同（基础 10 字段）。
         * 空操作，仅提升版本号以衔接迁移链。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v1/v2/v3 schema 相同，无需变更
            }
        }

        /**
         * 2→3 迁移：早期开发版（未公开发布），schema 与 v3 相同（基础 10 字段）。
         * 空操作，仅提升版本号以衔接迁移链。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v2/v3 schema 相同，无需变更
            }
        }

        /**
         * 3→4 迁移：v0.2.x → v0.3.0
         * v3 仅有 10 个基础字段，v4 新增 airDate / summary / bangumiId / airWeekday / isFinished 共 5 个字段。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN airDate TEXT")
                db.execSQL("ALTER TABLE anime ADD COLUMN summary TEXT")
                db.execSQL("ALTER TABLE anime ADD COLUMN bangumiId INTEGER")
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

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anime ADD COLUMN summaryFetched INTEGER")
            }
        }

        /**
         * 14→15 迁移：重建表修复 summaryFetched 列。
         *
         * 之前 v14 首次发布时 MIGRATION_13_14 误用了 `NOT NULL DEFAULT 0`，
         * 导致迁移事务提交后 Room 校验失败（defaultValue='0' vs 期望 'undefined'），
         * 数据库卡在 v14 且 schema 不匹配。
         *
         * 此迁移通过重建表确保 summaryFetched 为可空列（无 DEFAULT），
         * 与实体定义 `Boolean? = null` 一致。
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                        seriesKey TEXT,
                        remoteCoverUrl TEXT,
                        summaryFetched INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO anime_new (
                        id, title, totalEpisodes, watchedEpisodes, status, rating, notes,
                        startDate, finishDate, coverUrl, airDate, summary, bangumiId, airWeekday,
                        isFinished, currentEpisodes, hasNewUpdate, syncRemarks, tmdbId, seriesKey,
                        remoteCoverUrl, summaryFetched
                    )
                    SELECT
                        id, title, totalEpisodes, watchedEpisodes, status, rating, notes,
                        startDate, finishDate, coverUrl, airDate, summary, bangumiId, airWeekday,
                        isFinished, currentEpisodes, hasNewUpdate, syncRemarks, tmdbId, seriesKey,
                        remoteCoverUrl, summaryFetched
                    FROM anime
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE anime")
                db.execSQL("ALTER TABLE anime_new RENAME TO anime")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anime_bangumiId` ON `anime` (`bangumiId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anime_tmdbId` ON `anime` (`tmdbId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_title` ON `anime` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_coverUrl` ON `anime` (`coverUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_isFinished_status` ON `anime` (`isFinished`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_anime_seriesKey` ON `anime` (`seriesKey`)")
            }
        }

        fun getDatabase(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "anime_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
