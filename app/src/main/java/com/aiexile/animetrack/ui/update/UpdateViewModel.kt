package com.aiexile.animetrack.ui.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.remote.UpdateInfo
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class UpdateUiState(
    val currentVersion: String = "",
    val updateInfo: UpdateInfo? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadComplete: Boolean = false,
    val error: String? = null,
    val isUpToDate: Boolean = false,
    val pendingInstallAfterPermission: Boolean = false,
    val apkAlreadyDownloaded: Boolean = false,
    val isSimulated: Boolean = false
)

class UpdateViewModel(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "UpdateViewModel"
        private const val APK_DIR = "updates"
        private const val PREFS_NAME = "update_download_state"
        private const val KEY_DOWNLOADING_VERSION = "downloading_version"
        private const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        private const val KEY_TOTAL_BYTES = "total_bytes"
    }

    private val _uiState = MutableStateFlow(UpdateUiState(currentVersion = BuildConfig.VERSION_NAME))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var cachedApkFile: File? = null
    private var downloadJob: kotlinx.coroutines.Job? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun checkForUpdate(force: Boolean = false) {
        if (_uiState.value.isChecking) return
        if (_uiState.value.updateInfo != null && !force) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, isUpToDate = false)
            try {
                val currentVersion = BuildConfig.VERSION_NAME
                val updateInfo = updateRepository.checkForUpdate(currentVersion)
                if (updateInfo != null) {
                    val skippedVersion = settingsRepository.skippedVersion.first()
                    if (!force && updateInfo.versionName == skippedVersion) {
                        Log.d(TAG, "Version ${updateInfo.versionName} was skipped")
                        _uiState.value = _uiState.value.copy(isChecking = false)
                    } else {
                        // 检查该版本的 APK 是否已下载完成
                        val apkAlreadyExists = checkApkAlreadyDownloaded(updateInfo.versionName)
                        _uiState.value = _uiState.value.copy(
                            updateInfo = updateInfo,
                            isChecking = false,
                            downloadComplete = apkAlreadyExists,
                            downloadProgress = if (apkAlreadyExists) 100 else 0,
                            apkAlreadyDownloaded = apkAlreadyExists
                        )
                        if (apkAlreadyExists) {
                            cachedApkFile = findApkFileByVersion(updateInfo.versionName)
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isChecking = false,
                        isUpToDate = force
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Check update failed", e)
                _uiState.value = _uiState.value.copy(isChecking = false)
            }
        }
    }

    fun skipVersion() {
        val info = _uiState.value.updateInfo ?: return
        viewModelScope.launch {
            settingsRepository.setSkippedVersion(info.versionName)
            _uiState.value = _uiState.value.copy(updateInfo = null)
        }
    }

    fun dismissUpdate() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = _uiState.value.copy(
            updateInfo = null,
            isUpToDate = false,
            pendingInstallAfterPermission = false,
            apkAlreadyDownloaded = false,
            isSimulated = false
        )
    }

    fun startDownload(context: Context) {
        val info = _uiState.value.updateInfo ?: return
        if (info.downloadUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "下载地址无效")
            return
        }

        val updateDir = File(context.cacheDir, APK_DIR)
        if (!updateDir.exists()) updateDir.mkdirs()

        val fileName = buildApkFileName(info.versionName)
        val targetFile = File(updateDir, fileName)

        downloadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = 0,
                downloadComplete = false,
                error = null,
                pendingInstallAfterPermission = false,
                apkAlreadyDownloaded = false
            )

            try {
                val remoteSize = fetchContentLength(info.downloadUrl)
                Log.d(TAG, "Remote Content-Length: $remoteSize")

                if (targetFile.exists() && targetFile.length() > 0) {
                    val localSize = targetFile.length()
                    Log.d(TAG, "Local file size: $localSize, remote size: $remoteSize")

                    if (remoteSize > 0 && localSize == remoteSize) {
                        Log.d(TAG, "APK already fully downloaded, skipping download")
                        cachedApkFile = targetFile
                        clearDownloadState(context)
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadComplete = true,
                            downloadProgress = 100,
                            apkAlreadyDownloaded = true
                        )
                        installApk(context)
                        return@launch
                    }

                    if (remoteSize <= 0 || localSize < remoteSize) {
                        Log.d(TAG, "Resuming download from byte $localSize")
                        resumeDownload(context, info.downloadUrl, targetFile, localSize, remoteSize)
                        return@launch
                    }

                    Log.d(TAG, "Local file larger than remote, re-downloading")
                    targetFile.delete()
                }

                fullDownload(context, info.downloadUrl, targetFile, remoteSize)
            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled")
                _uiState.value = _uiState.value.copy(isDownloading = false)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = "下载失败: ${e.message}"
                )
                clearDownloadState(context)
            }
        }
    }

    private suspend fun fetchContentLength(url: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val contentLength = response.header("Content-Length")?.toLongOrNull() ?: 0L
                response.close()
                contentLength
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch Content-Length", e)
                0L
            }
        }
    }

    private suspend fun fullDownload(
        context: Context,
        url: String,
        targetFile: File,
        totalSize: Long
    ) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength().let { if (it > 0) it else totalSize }

            saveDownloadState(context, 0L, contentLength)

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile)
            val buffer = ByteArray(8192)
            var downloadedBytes = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (contentLength > 0) {
                            val progress = ((downloadedBytes * 100) / contentLength).toInt()
                            _uiState.value = _uiState.value.copy(downloadProgress = progress)
                        }

                        if (downloadedBytes % (64 * 1024) == 0L) {
                            saveDownloadState(context, downloadedBytes, contentLength)
                        }
                    }
                }
            }

            response.close()
        }

        onDownloadComplete(context, targetFile)
    }

    private suspend fun resumeDownload(
        context: Context,
        url: String,
        targetFile: File,
        existingBytes: Long,
        totalSize: Long
    ) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$existingBytes-")
                .build()
            val response = okHttpClient.newCall(request).execute()

            val isPartial = response.code == 206
            if (!isPartial && !response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")

            val contentLength = if (isPartial) {
                body.contentLength().let { if (it > 0) it else (totalSize - existingBytes) }
            } else {
                targetFile.delete()
                body.contentLength().let { if (it > 0) it else totalSize }
            }

            val effectiveTotal = if (isPartial) existingBytes + contentLength else contentLength

            saveDownloadState(context, existingBytes, effectiveTotal)

            val initialProgress = if (effectiveTotal > 0) {
                ((existingBytes * 100) / effectiveTotal).toInt()
            } else 0
            _uiState.value = _uiState.value.copy(downloadProgress = initialProgress)

            val inputStream = body.byteStream()
            val raf = RandomAccessFile(targetFile, "rw")
            raf.seek(existingBytes)

            val buffer = ByteArray(8192)
            var downloadedBytes = existingBytes

            inputStream.use { input ->
                raf.use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (effectiveTotal > 0) {
                            val progress = ((downloadedBytes * 100) / effectiveTotal).toInt()
                            _uiState.value = _uiState.value.copy(downloadProgress = progress)
                        }

                        if (downloadedBytes % (64 * 1024) == 0L) {
                            saveDownloadState(context, downloadedBytes, effectiveTotal)
                        }
                    }
                }
            }

            response.close()
        }

        onDownloadComplete(context, targetFile)
    }

    private fun onDownloadComplete(context: Context, apkFile: File) {
        cachedApkFile = apkFile
        clearDownloadState(context)
        _uiState.value = _uiState.value.copy(
            isDownloading = false,
            downloadComplete = true,
            downloadProgress = 100
        )
        installApk(context)
    }

    fun installApk(context: Context) {
        if (_uiState.value.isSimulated) {
            _uiState.value = _uiState.value.copy(
                isDownloading = false,
                downloadComplete = false,
                downloadProgress = 0,
                updateInfo = null,
                isSimulated = false
            )
            return
        }

        try {
            val apkFile = cachedApkFile ?: findApkFile(context) ?: run {
                _uiState.value = _uiState.value.copy(error = "未找到安装包文件")
                return
            }

            if (!apkFile.exists()) {
                _uiState.value = _uiState.value.copy(error = "安装包文件不存在")
                cachedApkFile = null
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    _uiState.value = _uiState.value.copy(pendingInstallAfterPermission = true)
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            _uiState.value = _uiState.value.copy(pendingInstallAfterPermission = false)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install APK failed", e)
            _uiState.value = _uiState.value.copy(error = "安装失败: ${e.message}")
        }
    }

    fun checkPendingInstall(context: Context) {
        if (!_uiState.value.pendingInstallAfterPermission) return
        if (!_uiState.value.downloadComplete) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls()) {
                _uiState.value = _uiState.value.copy(pendingInstallAfterPermission = false)
                installApk(context)
            }
        }
    }

    fun tryRecoverDownload(context: Context) {
        val info = _uiState.value.updateInfo ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedVersion = prefs.getString(KEY_DOWNLOADING_VERSION, null) ?: return

        if (savedVersion != info.versionName) return

        val savedBytes = prefs.getLong(KEY_DOWNLOADED_BYTES, 0L)
        val totalBytes = prefs.getLong(KEY_TOTAL_BYTES, 0L)

        if (savedBytes <= 0 || totalBytes <= 0) return

        val fileName = buildApkFileName(info.versionName)
        val updateDir = File(context.cacheDir, APK_DIR)
        val targetFile = File(updateDir, fileName)

        if (!targetFile.exists()) return

        val localSize = targetFile.length()
        if (localSize < savedBytes - (64 * 1024)) return

        Log.d(TAG, "Recovering download: $savedVersion, local=${localSize}B, total=${totalBytes}B")

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = if (totalBytes > 0) ((localSize * 100) / totalBytes).toInt() else 0,
            downloadComplete = false,
            error = null,
            pendingInstallAfterPermission = false,
            apkAlreadyDownloaded = false
        )

        downloadJob = viewModelScope.launch {
            try {
                resumeDownload(context, info.downloadUrl, targetFile, localSize, totalBytes)
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(isDownloading = false)
            } catch (e: Exception) {
                Log.e(TAG, "Resume download failed", e)
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = "续传失败: ${e.message}"
                )
            }
        }
    }

    private fun findApkFile(context: Context): File? {
        val info = _uiState.value.updateInfo ?: return null
        val fileName = buildApkFileName(info.versionName)
        val updateDir = File(context.cacheDir, APK_DIR)
        val targetFile = File(updateDir, fileName)
        if (targetFile.exists()) return targetFile

        updateDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".apk")) return file
        }

        return null
    }

    private fun checkApkAlreadyDownloaded(versionName: String): Boolean {
        val fileName = buildApkFileName(versionName)
        val updateDir = File(AppContainer.getApplication().cacheDir, APK_DIR)
        val targetFile = File(updateDir, fileName)
        return targetFile.exists() && targetFile.length() > 0
    }

    private fun findApkFileByVersion(versionName: String): File? {
        val fileName = buildApkFileName(versionName)
        val updateDir = File(AppContainer.getApplication().cacheDir, APK_DIR)
        val targetFile = File(updateDir, fileName)
        return if (targetFile.exists()) targetFile else null
    }

    private fun buildApkFileName(versionName: String): String {
        val tag = versionName.removePrefix("v")
        return "AnimeTrack_${tag}.apk"
    }

    private fun saveDownloadState(
        context: Context,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val info = _uiState.value.updateInfo ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DOWNLOADING_VERSION, info.versionName)
            .putLong(KEY_DOWNLOADED_BYTES, downloadedBytes)
            .putLong(KEY_TOTAL_BYTES, totalBytes)
            .apply()
    }

    private fun clearDownloadState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun simulateUpdate() {
        if (_uiState.value.isDownloading || _uiState.value.isChecking) return

        val fakeInfo = UpdateInfo(
            versionName = "v9.9.9",
            changelog = "## 模拟更新\n\n这是一个**开发者模拟更新**，用于测试更新流程。\n\n- 模拟下载进度\n- 模拟安装按钮\n- 不会下载真实文件\n\n### 注意\n此更新为模拟数据，不会影响正常更新功能。",
            downloadUrl = "",
            apkSize = 0L,
            releaseUrl = ""
        )

        _uiState.value = _uiState.value.copy(
            updateInfo = fakeInfo,
            isSimulated = true,
            isUpToDate = false,
            isDownloading = false,
            downloadProgress = 0,
            downloadComplete = false,
            error = null,
            pendingInstallAfterPermission = false,
            apkAlreadyDownloaded = false
        )
    }

    fun startSimulatedDownload() {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = 0,
                downloadComplete = false,
                error = null
            )

            try {
                var progress = 0
                while (progress < 100) {
                    kotlinx.coroutines.delay(60)
                    progress = (progress + (1..5).random()).coerceAtMost(100)
                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                }
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadComplete = true,
                    downloadProgress = 100
                )
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(isDownloading = false)
            }
        }
    }
}
