package com.aiexile.animetrack.ui.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val url = settingsRepository.webdavUrl.first()
            val username = settingsRepository.webdavUsername.first()
            val password = settingsRepository.webdavPassword.first()
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
                val sardine = OkHttpSardine()
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
                    val resPath = resource.href.toString()
                    val resName = resPath.trimEnd('/').substringAfterLast('/')
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
