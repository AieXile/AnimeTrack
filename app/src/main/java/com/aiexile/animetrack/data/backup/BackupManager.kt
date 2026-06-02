package com.aiexile.animetrack.data.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.aiexile.animetrack.data.AnimeDao
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class RestoreResult(
    val totalCount: Int,
    val insertedCount: Int,
    val skippedCount: Int
)

object BackupManager {

    suspend fun backup(context: Context, strategy: Int, animeDao: AnimeDao): File =
        withContext(Dispatchers.IO) {
            when (strategy) {
                0 -> backupJson(context, animeDao)
                1 -> backupZip(context)
                else -> throw IllegalArgumentException("Unknown strategy: $strategy")
            }
        }

    private suspend fun backupJson(context: Context, animeDao: AnimeDao): File {
        val animes = animeDao.getAllAnimesList()
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(animes)
        val file = File(context.cacheDir, "AnimeTrack_Backup.json")
        file.writeText(json, Charsets.UTF_8)
        return file
    }

    private fun backupZip(context: Context): File {
        val db = AnimeDatabase.getDatabase(context)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()

        val dbFile = context.getDatabasePath("anime_database")
        val walFile = File(dbFile.parent, "anime_database-wal")
        val shmFile = File(dbFile.parent, "anime_database-shm")
        val coversDir = File(context.filesDir, "anime_covers")

        val zipFile = File(context.cacheDir, "AnimeTrack_Backup.zip")
        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zos ->
            if (dbFile.exists()) {
                val entry = ZipEntry("anime_database")
                zos.putNextEntry(entry)
                FileInputStream(dbFile).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }

            if (walFile.exists()) {
                val entry = ZipEntry("anime_database-wal")
                zos.putNextEntry(entry)
                FileInputStream(walFile).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }

            if (shmFile.exists()) {
                val entry = ZipEntry("anime_database-shm")
                zos.putNextEntry(entry)
                FileInputStream(shmFile).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }

            if (coversDir.exists() && coversDir.isDirectory) {
                coversDir.listFiles()?.forEach { coverFile ->
                    val entry = ZipEntry("covers/${coverFile.name}")
                    zos.putNextEntry(entry)
                    FileInputStream(coverFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }

        return zipFile
    }

    suspend fun restore(
        context: Context,
        strategy: Int,
        mode: Int,
        file: File,
        animeDao: AnimeDao
    ): RestoreResult = withContext(Dispatchers.IO) {
        val animes = when (strategy) {
            0 -> restoreJson(file)
            1 -> restoreZip(context, file)
            else -> throw IllegalArgumentException("Unknown strategy: $strategy")
        }

        when (mode) {
            0 -> restoreOverwrite(animes, animeDao)
            1 -> restoreMerge(animes, animeDao)
            else -> throw IllegalArgumentException("Unknown mode: $mode")
        }
    }

    private fun restoreJson(file: File): List<Anime> {
        val json = file.readText(Charsets.UTF_8)
        val gson = GsonBuilder().create()
        return gson.fromJson(json, Array<Anime>::class.java).toList()
    }

    private fun restoreZip(context: Context, file: File): List<Anime> {
        val tempDbFile = File(context.cacheDir, "temp_restore_db")
        val tempWalFile = File(context.cacheDir, "temp_restore_db-wal")
        val tempShmFile = File(context.cacheDir, "temp_restore_db-shm")

        var animes: List<Anime> = emptyList()

        ZipInputStream(FileInputStream(file).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "anime_database" -> {
                        FileOutputStream(tempDbFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry.name == "anime_database-wal" -> {
                        FileOutputStream(tempWalFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry.name == "anime_database-shm" -> {
                        FileOutputStream(tempShmFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry.name.startsWith("covers/") -> {
                        val coverName = entry.name.removePrefix("covers/")
                        val coverFile = File(File(context.filesDir, "anime_covers"), coverName)
                        coverFile.parentFile?.mkdirs()
                        FileOutputStream(coverFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (tempDbFile.exists()) {
            val db = SQLiteDatabase.openDatabase(
                tempDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            animes = readAnimesFromCursor(db)
            db.close()
            tempDbFile.delete()
            tempWalFile.delete()
            tempShmFile.delete()
        }

        return animes
    }

    private fun readAnimesFromCursor(db: SQLiteDatabase): List<Anime> {
        val animes = mutableListOf<Anime>()
        val cursor: Cursor = db.query("anime", null, null, null, null, null, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                animes.add(cursorToAnime(c))
            }
        }
        return animes
    }

    private fun cursorToAnime(cursor: Cursor): Anime {
        fun getInt(col: String): Int = cursor.getInt(cursor.getColumnIndexOrThrow(col))
        fun getString(col: String): String? = cursor.getString(cursor.getColumnIndexOrThrow(col))
        fun getFloat(col: String): Float? {
            val idx = cursor.getColumnIndexOrThrow(col)
            return if (cursor.isNull(idx)) null else cursor.getFloat(idx)
        }
        fun getLongNullable(col: String): Long? {
            val idx = cursor.getColumnIndexOrThrow(col)
            return if (cursor.isNull(idx)) null else cursor.getLong(idx)
        }
        fun getIntNullable(col: String): Int? {
            val idx = cursor.getColumnIndexOrThrow(col)
            return if (cursor.isNull(idx)) null else cursor.getInt(idx)
        }

        val statusStr = getString("status") ?: "PLANNED"
        val status = try {
            AnimeStatus.valueOf(statusStr)
        } catch (e: IllegalArgumentException) {
            AnimeStatus.PLANNED
        }

        return Anime(
            id = getInt("id"),
            title = getString("title") ?: "",
            totalEpisodes = getInt("totalEpisodes"),
            watchedEpisodes = getInt("watchedEpisodes"),
            status = status,
            rating = getFloat("rating"),
            notes = getString("notes") ?: "",
            startDate = getLongNullable("startDate"),
            finishDate = getLongNullable("finishDate"),
            coverUrl = getString("coverUrl"),
            airDate = getString("airDate"),
            summary = getString("summary"),
            bangumiId = getIntNullable("bangumiId"),
            airWeekday = getIntNullable("airWeekday"),
            isFinished = getInt("isFinished") != 0,
            currentEpisodes = getInt("currentEpisodes"),
            hasNewUpdate = getInt("hasNewUpdate") != 0
        )
    }

    private suspend fun restoreOverwrite(animes: List<Anime>, animeDao: AnimeDao): RestoreResult {
        animeDao.deleteAllAnimes()
        val cleaned = animes.map { it.copy(id = 0) }
        animeDao.insertAnimes(cleaned)
        return RestoreResult(
            totalCount = animes.size,
            insertedCount = animes.size,
            skippedCount = 0
        )
    }

    private suspend fun restoreMerge(animes: List<Anime>, animeDao: AnimeDao): RestoreResult {
        var insertedCount = 0
        var skippedCount = 0

        for (anime in animes) {
            val existing = if (anime.bangumiId != null) {
                animeDao.getAnimeByBangumiId(anime.bangumiId)
            } else {
                animeDao.getAnimeByTitle(anime.title)
            }

            if (existing == null) {
                animeDao.insertAnime(anime.copy(id = 0))
                insertedCount++
            } else {
                skippedCount++
            }
        }

        return RestoreResult(
            totalCount = animes.size,
            insertedCount = insertedCount,
            skippedCount = skippedCount
        )
    }
}
