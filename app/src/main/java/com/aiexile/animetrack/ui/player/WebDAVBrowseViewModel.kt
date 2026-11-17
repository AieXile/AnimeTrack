package com.aiexile.animetrack.ui.player

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.player.PlayerWebDavHttpClient
import com.aiexile.animetrack.data.player.VideoFormats
import com.aiexile.animetrack.data.player.WebDavDirectoryCache
import com.aiexile.animetrack.di.AppContainer
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

 data class WebDAVFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modified: String? = null
)

data class BrowseUiState(
    val files: List<WebDAVFileItem> = emptyList(),
    val currentPath: String = "",
    val mediaRootPath: String = "",
    val lastBrowsePath: String = "",
    val lastSelectedFile: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = true
)

class WebDAVBrowseViewModel(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "WebDAVBrowseViewModel"
    }

    private data class WebDavConfig(
        val serverUrl: String,
        val username: String,
        val password: String,
        val mediaRootPath: String,
        val trustAllCerts: Boolean
    )

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    /**
     * 浏览目录。path 为空时恢复最近一次成功目录；显式 path 则只加载用户指定位置。
     */
    fun browseDirectory(path: String? = null) {
        viewModelScope.launch {
            val config = readConfig() ?: run {
                _uiState.value = BrowseUiState(isConfigured = false)
                return@launch
            }

            val remembered = settingsRepository.playerWebdavLastBrowsePath.first()
            val normalizedRequested = path?.let(::normalizeUrl)
            val rememberedIsValid = remembered.isNotBlank() &&
                isWithinRoot(remembered, config.mediaRootPath)
            val targetPath = normalizedRequested
                ?.takeIf { isWithinRoot(it, config.mediaRootPath) }
                ?: if (rememberedIsValid) normalizeUrl(remembered) else config.mediaRootPath
            val restoringRememberedPath = path == null &&
                rememberedIsValid && targetPath != config.mediaRootPath

            loadDirectory(
                config = config,
                targetPath = targetPath,
                fallbackToRootOnFailure = restoringRememberedPath
            )
        }
    }

    private suspend fun readConfig(): WebDavConfig? {
        val url = settingsRepository.playerWebdavUrl.first().trim()
        if (url.isBlank()) return null

        val serverUrl = normalizeUrl(url)
        val configuredRoot = settingsRepository.playerWebdavMediaRoot.first()
        val mediaRootPath = configuredRoot
            .takeIf { it.isNotBlank() && isSameServer(it, serverUrl) }
            ?.let(::normalizeUrl)
            ?: serverUrl

        return WebDavConfig(
            serverUrl = serverUrl,
            username = settingsRepository.playerWebdavUsername.first(),
            password = settingsRepository.playerWebdavPassword.first(),
            mediaRootPath = mediaRootPath,
            trustAllCerts = settingsRepository.playerWebdavTrustAllCerts.first()
        )
    }

    private suspend fun loadDirectory(
        config: WebDavConfig,
        targetPath: String,
        fallbackToRootOnFailure: Boolean
    ) {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                currentPath = targetPath,
                mediaRootPath = config.mediaRootPath
            )
        }

        try {
            val sardine = OkHttpSardine(PlayerWebDavHttpClient.create(config.trustAllCerts))
            if (config.username.isNotEmpty()) {
                sardine.setCredentials(config.username, config.password)
            }

            val resources = withContext(Dispatchers.IO) {
                sardine.list(targetPath, 1)
            }
            // 供播放前外挂字幕扫描复用（命中缓存则点播零网络请求起播）
            WebDavDirectoryCache.put(targetPath, resources)
            // 只有列举成功才覆盖最近目录，失败路径不会污染记忆。
            settingsRepository.setPlayerWebdavLastBrowsePath(targetPath)

            val isRoot = samePath(targetPath, config.mediaRootPath)
            val items = mutableListOf<WebDAVFileItem>()

            if (!isRoot) {
                items.add(
                    WebDAVFileItem(
                        name = "..",
                        path = getParentPath(targetPath, config.mediaRootPath),
                        isDirectory = true
                    )
                )
            }

            for (resource in resources) {
                // NAS 可能返回相对 href（如 /Movies/xxx.mkv），统一转为绝对 URL
                val resPath = toAbsoluteUrl(resource.href.toString(), targetPath)
                val resName = resource.displayName?.takeIf { it.isNotBlank() }
                    ?: android.net.Uri.decode(resPath.trimEnd('/').substringAfterLast('/'))
                if (resName.isBlank()) continue

                // Skip the directory itself (first result is always the listed directory)
                if (samePath(resPath, targetPath)) continue

                val isDir = resource.isDirectory
                if (isDir) {
                    items.add(
                        WebDAVFileItem(
                            name = resName,
                            path = normalizeUrl(resPath),
                            isDirectory = true,
                            modified = resource.modified?.toString()
                        )
                    )
                } else if (VideoFormats.isVideoFile(resName)) {
                    items.add(
                        WebDAVFileItem(
                            name = resName,
                            path = resPath,
                            isDirectory = false,
                            size = resource.contentLength ?: 0L,
                            modified = resource.modified?.toString()
                        )
                    )
                }
            }

            // Sort: directories first, then files alphabetically
            items.sortWith(
                compareByDescending<WebDAVFileItem> { it.isDirectory || it.name == ".." }
                    .thenBy { it.name.lowercase() }
            )

            _uiState.value = _uiState.value.copy(
                files = items,
                isLoading = false,
                currentPath = targetPath,
                mediaRootPath = config.mediaRootPath,
                lastBrowsePath = targetPath,
                error = null
            )
        } catch (e: Exception) {
            AppLogManager.e(TAG, "Failed to browse directory: $targetPath", e)
            if (fallbackToRootOnFailure && !samePath(targetPath, config.mediaRootPath)) {
                // 最近路径失效时清掉两项临时记忆，再只回退到固定媒体根目录一次。
                settingsRepository.clearPlayerWebdavBrowseMemory()
                loadDirectory(
                    config = config,
                    targetPath = config.mediaRootPath,
                    fallbackToRootOnFailure = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "未知错误"
                )
            }
        }
    }

    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        val rootPath = _uiState.value.mediaRootPath
        val parentPath = getParentPath(currentPath, rootPath)
        if (!samePath(parentPath, currentPath)) {
            browseDirectory(parentPath)
        }
    }

    fun navigateToMediaRoot() {
        val root = _uiState.value.mediaRootPath
        if (root.isNotBlank()) browseDirectory(root)
    }

    /** 将当前成功打开的目录设为固定媒体根目录。 */
    fun setCurrentAsMediaRoot() {
        val current = _uiState.value.currentPath
        if (current.isBlank()) return
        viewModelScope.launch {
            val normalized = normalizeUrl(current)
            settingsRepository.setPlayerWebdavMediaRoot(normalized)
            settingsRepository.setPlayerWebdavLastBrowsePath(normalized)
            _uiState.update {
                it.copy(mediaRootPath = normalized, lastBrowsePath = normalized)
            }
        }
    }

    fun clearBrowseMemory() {
        viewModelScope.launch {
            settingsRepository.clearPlayerWebdavBrowseMemory()
            _uiState.update { it.copy(lastBrowsePath = "", lastSelectedFile = "") }
            browseDirectory(_uiState.value.mediaRootPath)
        }
    }

    fun markFileSelected(path: String) {
        viewModelScope.launch {
            settingsRepository.setPlayerWebdavLastSelectedFile(path)
            _uiState.update { it.copy(lastSelectedFile = path) }
        }
    }

    fun isVideoFile(name: String): Boolean = VideoFormats.isVideoFile(name)

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.isBlank()) "" else trimmed.trimEnd('/') + "/"
    }

    private fun samePath(first: String, second: String): Boolean =
        first.trimEnd('/') == second.trimEnd('/')

    private fun isSameServer(first: String, second: String): Boolean {
        val a = first.toHttpUrlOrNull() ?: return false
        val b = second.toHttpUrlOrNull() ?: return false
        return a.scheme == b.scheme && a.host == b.host && a.port == b.port
    }

    /** 目录只能在固定媒体根目录下，避免恢复到另一台服务器或根外路径。 */
    private fun isWithinRoot(path: String, root: String): Boolean {
        val candidate = path.toHttpUrlOrNull() ?: return false
        val boundary = root.toHttpUrlOrNull() ?: return false
        if (!isSameServer(path, root)) return false

        val candidatePath = candidate.encodedPath.trimEnd('/')
        val boundaryPath = boundary.encodedPath.trimEnd('/')
        return boundaryPath.isBlank() ||
            candidatePath == boundaryPath ||
            candidatePath.startsWith("$boundaryPath/")
    }

    /** 将 WebDAV 响应中的 href 规范化为绝对 URL。 */
    private fun toAbsoluteUrl(href: String, requestUrl: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return try {
            requestUrl.toHttpUrlOrNull()?.resolve(href)?.toString() ?: href
        } catch (_: Exception) {
            href
        }
    }

    private fun getParentPath(path: String, boundary: String): String {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        val candidate = if (lastSlash > 0) {
            normalizeUrl(trimmed.substring(0, lastSlash))
        } else {
            normalizeUrl(path)
        }
        return if (isWithinRoot(candidate, boundary)) candidate else boundary
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val application = AppContainer.getApplication()
            val settingsRepository = AppContainer.getSettingsRepository()
            return WebDAVBrowseViewModel(application, settingsRepository) as T
        }
    }
}
