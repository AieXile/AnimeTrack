package com.aiexile.animetrack.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
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
import com.aiexile.animetrack.util.cleanSummary
import com.aiexile.animetrack.ui.theme.ThemeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DataManageViewModel(
    private val animeRepository: AnimeRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DataManageViewModel"
        private const val API_DELAY_MS = 800L
    }

    // 表单输入状态 — 公开可写
    val webdavUrl = MutableStateFlow("")
    val webdavUsername = MutableStateFlow("")
    val webdavPassword = MutableStateFlow("")
    val backupStrategy = MutableStateFlow(0)
    val restoreMode = MutableStateFlow(0)

    // 操作反馈状态 — 内部可写，外部只读
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow("")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _duplicateCount = MutableStateFlow(0)
    val duplicateCount: StateFlow<Int> = _duplicateCount.asStateFlow()

    private val _pendingContent = MutableStateFlow<String?>(null)
    val pendingContent: StateFlow<String?> = _pendingContent.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow<String?>(null)
    val syncProgress: StateFlow<String?> = _syncProgress.asStateFlow()

    private val _syncedCount = MutableStateFlow(0)
    val syncedCount: StateFlow<Int> = _syncedCount.asStateFlow()

    private val _totalToSync = MutableStateFlow(0)
    val totalToSync: StateFlow<Int> = _totalToSync.asStateFlow()

    private val _exportMarkdown = MutableStateFlow<String?>(null)
    val exportMarkdown: StateFlow<String?> = _exportMarkdown.asStateFlow()

    fun loadConfig(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            webdavUrl.value = themeViewModel.webdavUrl.value
            webdavUsername.value = themeViewModel.webdavUsername.value
            webdavPassword.value = themeViewModel.webdavPassword.value
            backupStrategy.value = themeViewModel.webdavBackupStrategy.value
            restoreMode.value = themeViewModel.webdavRestoreMode.value
        }
    }

    fun saveConfig(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            themeViewModel.setWebdavUrl(webdavUrl.value)
            themeViewModel.setWebdavUsername(webdavUsername.value)
            themeViewModel.setWebdavPassword(webdavPassword.value)
            themeViewModel.setWebdavBackupStrategy(backupStrategy.value)
            themeViewModel.setWebdavRestoreMode(restoreMode.value)
        }
    }

    fun testConnection(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            saveConfig(themeViewModel)
            _isLoading.value = true
            _loadingMessage.value = "正在测试连接..."
            try {
                val result = WebDAVClient.checkConnection(webdavUrl.value, webdavUsername.value, webdavPassword.value)
                if (result.isSuccess) {
                    _snackbarMessage.value = "连接成功！"
                } else {
                    _snackbarMessage.value = "连接失败：${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "连接失败：${e.message}"
            } finally {
                _isLoading.value = false
                _loadingMessage.value = ""
            }
        }
    }

    fun backupNow(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            saveConfig(themeViewModel)
            _isLoading.value = true
            _loadingMessage.value = "正在备份..."
            try {
                val context = AppContainer.getApplication()
                val backupFile = BackupManager.backup(context, backupStrategy.value, getAnimeDao())

                _loadingMessage.value = "正在上传..."
                val result = WebDAVClient.upload(
                    webdavUrl.value, webdavUsername.value, webdavPassword.value,
                    backupFile, backupStrategy.value
                )
                if (result.isSuccess) {
                    themeViewModel.setWebdavLastSyncTime(System.currentTimeMillis())
                    _snackbarMessage.value = "备份成功！"
                } else {
                    _snackbarMessage.value = "上传失败：${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _snackbarMessage.value = "备份失败：${e.message}"
            } finally {
                _isLoading.value = false
                _loadingMessage.value = ""
            }
        }
    }

    fun restoreNow(themeViewModel: ThemeViewModel) {
        viewModelScope.launch {
            saveConfig(themeViewModel)
            _isLoading.value = true
            _loadingMessage.value = "正在下载..."
            try {
                val context = AppContainer.getApplication()
                val strategy = backupStrategy.value
                val ext = if (strategy == 0) "json" else "zip"
                val downloadFile = java.io.File(context.cacheDir, "AnimeTrack_Restore.$ext")

                val downloadResult = WebDAVClient.download(
                    webdavUrl.value, webdavUsername.value, webdavPassword.value,
                    downloadFile, strategy
                )
                if (downloadResult.isFailure) {
                    _snackbarMessage.value = "下载失败：${downloadResult.exceptionOrNull()?.message}"
                    return@launch
                }

                _loadingMessage.value = "正在恢复..."
                val restoreResult = BackupManager.restore(
                    context, strategy, restoreMode.value, downloadFile, getAnimeDao()
                )
                _snackbarMessage.value = "恢复完成：共 ${restoreResult.totalCount} 部，新增 ${restoreResult.insertedCount} 部，跳过 ${restoreResult.skippedCount} 部"
                downloadFile.delete()
            } catch (e: Exception) {
                _snackbarMessage.value = "恢复失败：${e.message}"
            } finally {
                _isLoading.value = false
                _loadingMessage.value = ""
            }
        }
    }

    private fun getAnimeDao(): com.aiexile.animetrack.data.AnimeDao {
        return com.aiexile.animetrack.data.AnimeDatabase.getDatabase(AppContainer.getApplication()).animeDao()
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
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
            _importResult.value = result
            _duplicateCount.value = dupCount
        }
    }

    fun importAnimesAndSync(content: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loadingMessage.value = "正在导入..."

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

            _isLoading.value = false
            _loadingMessage.value = ""
            _snackbarMessage.value = "成功导入 ${animesToInsert.size} 部番剧"

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

        _isSyncing.value = true
        _totalToSync.value = animesWithoutCover.size
        _syncedCount.value = 0
        _syncProgress.value = null

        var count = 0

        try {
            for (anime in animesWithoutCover) {
                try {
                    _syncProgress.value = "正在补全: ${anime.title}"

                    val (cleanTitle, extractedNote) = cleanTitleAndExtractNote(anime.title)

                    val results = animeRepository.searchBangumi(cleanTitle)
                    val bestMatch = results.firstOrNull()

                    if (bestMatch != null) {
                        val detail = try {
                            RetrofitClient.bangumiApi.getSubjectDetail(bestMatch.id)
                        } catch (_: Exception) {
                            null
                        }

                        val summary = detail?.summary?.cleanSummary()
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

                    _syncedCount.value = count
                    delay(API_DELAY_MS)
                } catch (_: Exception) {
                }
            }
        } finally {
            _isSyncing.value = false
            _syncProgress.value = null
            if (count > 0) {
                _snackbarMessage.value = "已补全 $count 个番剧封面"
            }
        }
    }

    fun resetImportState() {
        _importResult.value = null
        _duplicateCount.value = 0
        _pendingContent.value = null
    }

    fun prepareExport() {
        viewModelScope.launch {
            val animes = animeRepository.getAllAnimes().first()
            val markdown = ExportAnimeService.exportToMarkdown(animes)
            _exportMarkdown.value = markdown
        }
    }

    fun clearExportMarkdown() {
        _exportMarkdown.value = null
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
