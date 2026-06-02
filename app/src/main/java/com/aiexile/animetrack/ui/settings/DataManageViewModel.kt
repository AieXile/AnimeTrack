package com.aiexile.animetrack.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.ExportAnimeService
import com.aiexile.animetrack.data.ImportResult
import com.aiexile.animetrack.data.MarkdownParser
import com.aiexile.animetrack.data.backup.BackupManager
import com.aiexile.animetrack.data.backup.WebDAVClient
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.theme.ThemeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DataManageViewModel(
    private val animeRepository: AnimeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DataManageViewModel"
        private const val API_DELAY_MS = 800L
    }

    var webdavUrl by mutableStateOf("")
    var webdavUsername by mutableStateOf("")
    var webdavPassword by mutableStateOf("")
    var backupStrategy by mutableStateOf(0)
    var restoreMode by mutableStateOf(0)

    var isLoading by mutableStateOf(false)
        private set
    var loadingMessage by mutableStateOf("")
        private set
    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    var importResult by mutableStateOf<ImportResult?>(null)
        private set
    var duplicateCount by mutableStateOf(0)
        private set
    var pendingContent by mutableStateOf<String?>(null)
        private set

    var isSyncing by mutableStateOf(false)
        private set
    var syncProgress by mutableStateOf<String?>(null)
        private set
    var syncedCount by mutableStateOf(0)
        private set
    var totalToSync by mutableStateOf(0)
        private set

    var exportMarkdown by mutableStateOf<String?>(null)
        private set

    fun loadConfig(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            webdavUrl = themeViewModel.webdavUrl.value
            webdavUsername = themeViewModel.webdavUsername.value
            webdavPassword = themeViewModel.webdavPassword.value
            backupStrategy = themeViewModel.webdavBackupStrategy.value
            restoreMode = themeViewModel.webdavRestoreMode.value
        }
    }

    fun saveConfig(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            themeViewModel.setWebdavUrl(webdavUrl)
            themeViewModel.setWebdavUsername(webdavUsername)
            themeViewModel.setWebdavPassword(webdavPassword)
            themeViewModel.setWebdavBackupStrategy(backupStrategy)
            themeViewModel.setWebdavRestoreMode(restoreMode)
        }
    }

    fun testConnection(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            saveConfig(themeViewModel)
            isLoading = true
            loadingMessage = "正在测试连接..."
            try {
                val result = WebDAVClient.checkConnection(webdavUrl, webdavUsername, webdavPassword)
                if (result.isSuccess) {
                    snackbarMessage = "连接成功！"
                } else {
                    snackbarMessage = "连接失败：${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                snackbarMessage = "连接失败：${e.message}"
            } finally {
                isLoading = false
                loadingMessage = ""
            }
        }
    }

    fun backupNow(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            saveConfig(themeViewModel)
            isLoading = true
            loadingMessage = "正在备份..."
            try {
                val context = AppContainer.getApplication()
                val backupFile = BackupManager.backup(context, backupStrategy, getAnimeDao())

                loadingMessage = "正在上传..."
                val result = WebDAVClient.upload(
                    webdavUrl, webdavUsername, webdavPassword,
                    backupFile, backupStrategy
                )
                if (result.isSuccess) {
                    themeViewModel.setWebdavLastSyncTime(System.currentTimeMillis())
                    snackbarMessage = "备份成功！"
                } else {
                    snackbarMessage = "上传失败：${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                snackbarMessage = "备份失败：${e.message}"
            } finally {
                isLoading = false
                loadingMessage = ""
            }
        }
    }

    fun restoreNow(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            saveConfig(themeViewModel)
            isLoading = true
            loadingMessage = "正在下载..."
            try {
                val context = AppContainer.getApplication()
                val strategy = backupStrategy
                val ext = if (strategy == 0) "json" else "zip"
                val downloadFile = java.io.File(context.cacheDir, "AnimeTrack_Restore.$ext")

                val downloadResult = WebDAVClient.download(
                    webdavUrl, webdavUsername, webdavPassword,
                    downloadFile, strategy
                )
                if (downloadResult.isFailure) {
                    snackbarMessage = "下载失败：${downloadResult.exceptionOrNull()?.message}"
                    return@launch
                }

                loadingMessage = "正在恢复..."
                val restoreResult = BackupManager.restore(
                    context, strategy, restoreMode, downloadFile, getAnimeDao()
                )
                snackbarMessage = "恢复完成：共 ${restoreResult.totalCount} 部，新增 ${restoreResult.insertedCount} 部，跳过 ${restoreResult.skippedCount} 部"
                downloadFile.delete()
            } catch (e: Exception) {
                snackbarMessage = "恢复失败：${e.message}"
            } finally {
                isLoading = false
                loadingMessage = ""
            }
        }
    }

    private fun getAnimeDao(): com.aiexile.animetrack.data.AnimeDao {
        return com.aiexile.animetrack.data.AnimeDatabase.getDatabase(AppContainer.getApplication()).animeDao()
    }

    fun clearSnackbar() {
        snackbarMessage = null
    }

    fun parseMarkdown(content: String) {
        val result = MarkdownParser.parse(content)

        var dupCount = 0
        viewModelScope.launch {
            for (parsed in result.animes) {
                val existing = animeRepository.getAnimeByTitle(parsed.title)
                if (existing != null) {
                    dupCount++
                }
            }
            importResult = result
            duplicateCount = dupCount
        }
    }

    fun importAnimesAndSync(content: String) {
        viewModelScope.launch {
            isLoading = true
            loadingMessage = "正在导入..."

            val result = MarkdownParser.parse(content)
            val animesToInsert = mutableListOf<Anime>()

            Log.d(TAG, "Parsed ${result.animes.size} animes from markdown")

            for (parsed in result.animes) {
                val existing = animeRepository.getAnimeByTitle(parsed.title)
                if (existing == null) {
                    animesToInsert.add(MarkdownParser.toAnimeEntity(parsed))
                } else {
                    Log.d(TAG, "Skipping duplicate: ${parsed.title}")
                }
            }

            val insertedAnimes = mutableListOf<Anime>()
            for (anime in animesToInsert) {
                val id = animeRepository.insertAnime(anime)
                insertedAnimes.add(anime.copy(id = id.toInt()))
            }

            isLoading = false
            loadingMessage = ""
            snackbarMessage = "成功导入 ${animesToInsert.size} 部番剧"

            doAutoSyncAnimeCovers(insertedAnimes)
        }
    }

    private suspend fun doAutoSyncAnimeCovers(animesToSync: List<Anime> = emptyList()) {
        val animesWithoutCover = if (animesToSync.isNotEmpty()) {
            animesToSync
        } else {
            animeRepository.getAnimesWithoutCover()
        }

        if (animesWithoutCover.isEmpty()) return

        isSyncing = true
        totalToSync = animesWithoutCover.size
        syncedCount = 0
        syncProgress = null

        var count = 0

        try {
            for (anime in animesWithoutCover) {
                try {
                    syncProgress = "正在补全: ${anime.title}"

                    val (cleanTitle, extractedNote) = cleanTitleAndExtractNote(anime.title)

                    val results = animeRepository.searchBangumi(cleanTitle)
                    val bestMatch = results.firstOrNull()

                    if (bestMatch != null) {
                        val detail = try {
                            RetrofitClient.bangumiApi.getSubjectDetail(bestMatch.id)
                        } catch (_: Exception) {
                            null
                        }

                        val summary = detail?.summary?.trim()?.replace(Regex("\n{3,}"), "\n\n")
                            ?: bestMatch.summary

                        val apiEps = detail?.eps
                        val apiTotalEps = detail?.totalEpisodes

                        val mainEps = if (apiEps != null && apiEps > 0) apiEps else 0
                        val allEps = if (apiTotalEps != null && apiTotalEps > 0) apiTotalEps else 0

                        val finalTotalEpisodes = when {
                            mainEps > 0 -> mainEps
                            allEps > 0 -> allEps
                            else -> anime.totalEpisodes
                        }
                        val finalCurrentEpisodes = if (mainEps > 0 || allEps > 0) 0 else anime.currentEpisodes

                        val newWatchedEpisodes = if (
                            anime.status == AnimeStatus.COMPLETED
                            && anime.watchedEpisodes == 0
                            && finalTotalEpisodes > 0
                        ) finalTotalEpisodes else anime.watchedEpisodes

                        val updatedAnime = anime.copy(
                            title = cleanTitle,
                            coverUrl = bestMatch.coverUrl,
                            rating = detail?.score?.toFloat() ?: bestMatch.score?.toFloat(),
                            totalEpisodes = finalTotalEpisodes,
                            currentEpisodes = finalCurrentEpisodes,
                            watchedEpisodes = newWatchedEpisodes,
                            summary = summary,
                            bangumiId = bestMatch.id,
                            airDate = detail?.date ?: anime.airDate,
                            airWeekday = detail?.airWeekday ?: anime.airWeekday,
                            notes = if (extractedNote.isNotEmpty()) extractedNote else anime.notes
                        )

                        animeRepository.updateAnime(updatedAnime)
                        animeRepository.downloadCoverAsync(
                            animeId = updatedAnime.id,
                            coverUrl = updatedAnime.coverUrl,
                            bangumiId = updatedAnime.bangumiId
                        )
                        count++
                    }

                    syncedCount = count
                    delay(API_DELAY_MS)
                } catch (_: Exception) {
                }
            }
        } finally {
            isSyncing = false
            syncProgress = null
            if (count > 0) {
                snackbarMessage = "已补全 $count 个番剧封面"
            }
        }
    }

    fun resetImportState() {
        importResult = null
        duplicateCount = 0
        pendingContent = null
    }

    fun prepareExport() {
        viewModelScope.launch {
            val animes = animeRepository.getAllAnimes().first()
            val markdown = ExportAnimeService.exportToMarkdown(animes)
            exportMarkdown = markdown
        }
    }

    fun clearExportMarkdown() {
        exportMarkdown = null
    }

    private fun cleanTitleAndExtractNote(title: String): Pair<String, String> {
        val parenIndex = title.indexOf("(")
        val bracketIndex = title.indexOf("（")

        val splitIndex = when {
            parenIndex >= 0 && bracketIndex >= 0 -> minOf(parenIndex, bracketIndex)
            parenIndex >= 0 -> parenIndex
            bracketIndex >= 0 -> bracketIndex
            else -> -1
        }

        return if (splitIndex > 0) {
            val cleanTitle = title.substring(0, splitIndex).trim()
            val note = title.substring(splitIndex + 1)
                .removeSuffix(")")
                .removeSuffix("）")
                .trim()
            Pair(cleanTitle, note)
        } else {
            Pair(title.trim(), "")
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DataManageViewModel(AppContainer.getAnimeRepository()) as T
        }
    }
}
