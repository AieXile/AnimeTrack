package com.aiexile.animetrack.data.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.aiexile.animetrack.data.AnimeDao
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
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

    private const val TAG = "Backup"

    suspend fun backup(
        context: Context,
        strategy: Int,
        database: AnimeDatabase = AnimeDatabase.getDatabase(context)
    ): File =
        withContext(Dispatchers.IO) {
            val file = when (strategy) {
                0 -> backupJson(context, database)
                1 -> backupZip(context, database)
                else -> throw IllegalArgumentException("Unknown strategy: $strategy")
            }
            AppLogManager.i(TAG, "备份完成: strategy=$strategy, ${file.name} ${file.length()}B")
            file
        }

    private suspend fun backupJson(context: Context, database: AnimeDatabase): File {
        val data = database.withTransaction {
            val folders = database.favoriteFolderDao()
            AnimeBackup(database.animeDao().getAllAnimesList(), folders.getFolders(), folders.getEntries())
        }
        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(if (data.folders.isEmpty()) data.animes else data)
        val file = File(context.cacheDir, "AnimeTrack_Backup.json")
        file.writeText(json, Charsets.UTF_8)
        return file
    }

    private suspend fun backupZip(context: Context, database: AnimeDatabase): File {
        val helper = database.openHelper
        helper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

        val dbFile = context.getDatabasePath(requireNotNull(helper.databaseName))
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        // 下载封面目录（CoverDownloader 本地化的封面）
        val coversDir = File(context.filesDir, "anime_covers")
        // 手动设置封面目录（用户自选图片，copyCoverToInternalStorage 保存）
        val customCoversDir = File(context.filesDir, "covers")

        val zipFile = File(context.cacheDir, "AnimeTrack_Backup.zip")
        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zos ->
            database.withTransaction {
                putZipFile(zos, dbFile, "anime_database")
                putZipFile(zos, walFile, "anime_database-wal")
                putZipFile(zos, shmFile, "anime_database-shm")
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

            // 手动设置的封面存于 filesDir/covers，数据库中 coverUrl 指向其绝对路径，
            // 不打包的话恢复后路径仍存在但文件缺失，封面将无法显示
            if (customCoversDir.exists() && customCoversDir.isDirectory) {
                customCoversDir.listFiles()?.forEach { coverFile ->
                    val entry = ZipEntry("custom_covers/${coverFile.name}")
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

    private fun putZipFile(zip: ZipOutputStream, file: File, name: String) {
        if (!file.isFile) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    suspend fun restore(
        context: Context,
        strategy: Int,
        mode: Int,
        file: File,
        database: AnimeDatabase = AnimeDatabase.getDatabase(context)
    ): RestoreResult = withContext(Dispatchers.IO) {
        val backup = try {
            when (strategy) {
                0 -> restoreJson(file)
                1 -> restoreZip(context, file)
                else -> throw IllegalArgumentException("Unknown strategy: $strategy")
            }
        } catch (e: Exception) {
            // 备份文件损坏 / 版本不匹配 / 非本应用文件：UI 只会弹一句「恢复失败」，
            // 这里带上文件大小与策略，便于判断是下载不全还是解析问题
            AppLogManager.e(TAG, "解析备份文件失败: strategy=$strategy, ${file.name} (${file.length()}B)", e)
            throw e
        }
        AppLogManager.i(TAG, "备份文件解析完成: strategy=$strategy, ${backup.animes.size} 部")

        require(mode == 0 || mode == 1) { "Unknown mode: $mode" }
        val result = database.withTransaction {
            val folderDao = database.favoriteFolderDao()
            val animeDao = database.animeDao()
            if (mode == 0) {
                folderDao.deleteFolders()
                animeDao.deleteAllAnimes()
            }
            val (result, animeIds) = restoreAnimes(backup.animes, animeDao, merge = mode == 1)
            restoreFavoriteFolders(backup, folderDao, animeIds, overwrite = mode == 0)
            result
        }
        AppLogManager.i(
            TAG,
            "恢复完成: mode=$mode, 共 ${result.totalCount} 部，新增 ${result.insertedCount}，" +
                "跳过 ${result.skippedCount} 部"
        )
        result
    }

    private fun restoreJson(file: File): AnimeBackup {
        val json = file.bufferedReader().use { JsonParser.parseReader(it) }
        val gson = GsonBuilder().create()
        if (json.isJsonArray) return AnimeBackup(gson.fromJson(json, Array<Anime>::class.java).toList())
        require(json.isJsonObject && json.asJsonObject.get("animes")?.isJsonArray == true)
        val backup = gson.fromJson(json, AnimeBackup::class.java)
        return AnimeBackup(
            backup.animes,
            json.asJsonObject.get("folders")?.takeIf { it.isJsonArray }?.let { backup.folders }.orEmpty(),
            json.asJsonObject.get("folderEntries")?.takeIf { it.isJsonArray }?.let { backup.folderEntries }.orEmpty()
        )
    }

    private fun restoreZip(context: Context, file: File): AnimeBackup {
        val tempDir = kotlin.io.path.createTempDirectory(context.cacheDir.toPath(), "anime_restore").toFile()
        try {
            return readZip(context, file, tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun readZip(context: Context, file: File, tempDir: File): AnimeBackup {
        val tempDbFile = File(tempDir, "temp_restore_db")
        val tempWalFile = File(tempDir, "temp_restore_db-wal")
        val tempShmFile = File(tempDir, "temp_restore_db-shm")

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
                    entry.name.startsWith("custom_covers/") -> {
                        val coverName = entry.name.removePrefix("custom_covers/")
                        val coverFile = File(File(context.filesDir, "covers"), coverName)
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

        require(tempDbFile.isFile) { "Database missing from backup" }
        return SQLiteDatabase.openDatabase(tempDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            readFavoriteFolders(db, readAnimesFromCursor(db))
        }
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
            val idx = cursor.getColumnIndex(col)
            return if (idx < 0 || cursor.isNull(idx)) null else cursor.getInt(idx)
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
            tmdbId = getIntNullable("tmdbId"),
            remoteSyncId = cursor.getColumnIndex("remoteSyncId").takeIf { it >= 0 }?.let(cursor::getString),
            airWeekday = getIntNullable("airWeekday"),
            isFinished = getInt("isFinished") != 0,
            currentEpisodes = getInt("currentEpisodes"),
            hasNewUpdate = getInt("hasNewUpdate") != 0,
            airEndDate = getString("airEndDate"),
            isFavorite = getIntNullable("isFavorite") == 1
        )
    }

    private suspend fun restoreAnimes(
        animes: List<Anime>,
        animeDao: AnimeDao,
        merge: Boolean
    ): Pair<RestoreResult, Map<Int, Int>> {
        var insertedCount = 0
        val ids = mutableMapOf<Int, Int>()
        for (anime in animes) {
            val inserted = if (!merge) animeDao.insertAnime(anime.copy(id = 0)) else -1
            val existing = if (inserted > 0) null else findExistingAnime(animeDao, anime)
            val localId = when {
                inserted > 0 -> inserted
                existing != null -> existing.id.toLong()
                merge -> animeDao.insertAnime(anime.copy(id = 0))
                else -> -1
            }
            if (localId <= 0) continue
            ids[anime.id] = localId.toInt()
            if (existing == null) {
                insertedCount++
            } else if (anime.isFavorite && !existing.isFavorite) {
                animeDao.setFavorite(existing.id, true)
            }
        }
        return RestoreResult(animes.size, insertedCount, animes.size - insertedCount) to ids
    }

    private suspend fun findExistingAnime(dao: AnimeDao, anime: Anime): Anime? =
        anime.bangumiId?.let { dao.getAnimeByBangumiId(it) }
            ?: anime.tmdbId?.let { dao.getAnimeByTmdbId(it) }
            ?: anime.remoteSyncId?.let { dao.getAnimeByRemoteSyncId(it) }
            ?: if (anime.bangumiId == null && anime.tmdbId == null) dao.getAnimeByTitle(anime.title) else null
}
