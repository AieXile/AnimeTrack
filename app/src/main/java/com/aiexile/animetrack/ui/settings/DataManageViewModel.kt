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
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.util.cleanSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DataManageViewModel(
    private val animeRepository: AnimeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "DataManageViewModel"
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

    private val _exportMarkdown = MutableStateFlow<String?>(null)
    val exportMarkdown: StateFlow<String?> = _exportMarkdown.asStateFlow()

    fun loadConfig() {
        viewModelScope.launch {
            webdavUrl.value = settingsRepository.webdavUrl.first()
            webdavUsername.value = settingsRepository.webdavUsername.first()
            webdavPassword.value = settingsRepository.webdavPassword.first()
            backupStrategy.value = settingsRepository.webdavBackupStrategy.first()
            restoreMode.value = settingsRepository.webdavRestoreMode.first()
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            settingsRepository.setWebdavUrl(webdavUrl.value)
            settingsRepository.setWebdavUsername(webdavUsername.value)
            settingsRepository.setWebdavPassword(webdavPassword.value)
            settingsRepository.setWebdavBackupStrategy(backupStrategy.value)
            settingsRepository.setWebdavRestoreMode(restoreMode.value)
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            saveConfig()
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

    fun backupNow() {
        viewModelScope.launch {
            saveConfig()
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
                    settingsRepository.setWebdavLastSyncTime(System.currentTimeMillis())
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

    fun restoreNow() {
        viewModelScope.launch {
            saveConfig()
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
                // 恢复完成后，防抖触发后端订阅同步（WebDAV 恢复绕过 Repository，需显式补同步）
                animeRepository.triggerSyncSubscriptionsFromServerDebounced()
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

            animeRepository.syncCoversInBackground(insertedAnimes)
            // 导入完成后，防抖触发后端订阅同步
            animeRepository.triggerSyncSubscriptionsFromServerDebounced()
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
            return DataManageViewModel(
                AppContainer.getAnimeRepository(),
                AppContainer.getSettingsRepository()
            ) as T
        }
    }
}
