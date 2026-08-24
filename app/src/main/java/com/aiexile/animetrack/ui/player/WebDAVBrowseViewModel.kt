package com.aiexile.animetrack.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.player.PlayerWebDavHttpClient
import com.aiexile.animetrack.di.AppContainer
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "webm", "mov", "flv", "wmv", "ts", "m4v"
        )
    }

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    fun browseDirectory(path: String? = null) {
        viewModelScope.launch {
            val url = settingsRepository.playerWebdavUrl.first()
            val username = settingsRepository.playerWebdavUsername.first()
            val password = settingsRepository.playerWebdavPassword.first()
            val mediaPath = settingsRepository.webdavMediaPath.first()

            if (url.isBlank()) {
                _uiState.value = BrowseUiState(isConfigured = false)
                return@launch
            }

            val targetPath = path ?: mediaPath.ifBlank { normalizeUrl(url) }

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentPath = targetPath
            )

            try {
                val trustAll = settingsRepository.playerWebdavTrustAllCerts.first()
                val sardine = OkHttpSardine(PlayerWebDavHttpClient.create(trustAll))
                if (username.isNotEmpty()) {
                    sardine.setCredentials(username, password)
                }

                val resources = withContext(Dispatchers.IO) {
                    sardine.list(targetPath, 1)
                }

                val currentNormalized = normalizeUrl(url)
                val isRoot = targetPath.trimEnd('/') == currentNormalized.trimEnd('/')

                val items = mutableListOf<WebDAVFileItem>()

                if (!isRoot) {
                    items.add(
                        WebDAVFileItem(
                            name = "..",
                            path = getParentPath(targetPath),
                            isDirectory = true
                        )
                    )
                }

                for (resource in resources) {
                    // NAS 可能返回相对 href（如 /Movies/xxx.mkv），统一转为绝对 URL
                    val resPath = toAbsoluteUrl(resource.href.toString(), targetPath)
                    // 显示名优先用服务器提供的 displayName（中文等非 ASCII 字符不会是百分号编码）
                    val resName = resource.displayName?.takeIf { it.isNotBlank() }
                        ?: android.net.Uri.decode(resPath.trimEnd('/').substringAfterLast('/'))
                    if (resName.isBlank()) continue

                    // Skip the directory itself (first result is always the listed directory)
                    if (normalizeUrl(resPath) == normalizeUrl(targetPath)) continue

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
                    } else if (isVideoFile(resName)) {
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
                items.sortWith(compareByDescending<WebDAVFileItem> { it.isDirectory || it.name == ".." }
                    .thenBy { it.name.lowercase() })

                _uiState.value = _uiState.value.copy(
                    files = items,
                    isLoading = false,
                    currentPath = targetPath
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to browse directory: $targetPath", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "未知错误"
                )
            }
        }
    }

    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        val parentPath = getParentPath(currentPath)
        if (parentPath != currentPath) {
            browseDirectory(parentPath)
        }
    }

    fun isVideoFile(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        return "$trimmed/"
    }

    /** 将 WebDAV 响应中的 href 规范化为绝对 URL：
     *  部分服务器（如飞牛 OS）返回相对路径，需以当前请求 URL 为基准解析；
     *  resolve 会正确处理百分号编码与 ../ 等相对引用。 */
    private fun toAbsoluteUrl(href: String, requestUrl: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return try {
            requestUrl.toHttpUrlOrNull()?.resolve(href)?.toString() ?: href
        } catch (e: Exception) {
            href
        }
    }

    private fun getParentPath(path: String): String {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash > 0) {
            normalizeUrl(trimmed.substring(0, lastSlash))
        } else {
            path
        }
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
