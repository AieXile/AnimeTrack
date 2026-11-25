package com.aiexile.animetrack.ui.player.webdav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.player.VideoFormats
import com.aiexile.animetrack.data.player.webdav.PlayerWebDavDirectory
import com.aiexile.animetrack.data.player.webdav.WebDavPlaybackIdentity
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val listGeneration: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = true
)

class WebDAVBrowseViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "WebDAVBrowseViewModel"
    }

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()
    internal val resume = settingsRepository.playerWebdavResume
    private var browseJob: Job? = null
    private var activeConfig: PlayerWebDavDirectory.Config? = null
    private val pageAnchors = mutableMapOf<String, SettingsRepository.WebDavScrollAnchor>()

    /**
     * 浏览目录。path 为空时恢复最近一次成功目录；显式 path 则只加载用户指定位置。
     */
    fun browseDirectory(path: String? = null) {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            val config = settingsRepository.playerWebdavDirectoryConfig.first() ?: run {
                activeConfig = null
                _uiState.value = BrowseUiState(isConfigured = false)
                return@launch
            }
            if (config != activeConfig) pageAnchors.clear()
            activeConfig = config

            val remembered = settingsRepository.playerWebdavLastBrowsePath.first()
            val lastSelected = settingsRepository.playerWebdavLastSelectedFile.first()
            val rememberedIsValid = remembered.isNotBlank() &&
                isWithinRoot(remembered, config.root.toString())
            _uiState.update { it.copy(
                lastBrowsePath = remembered.takeIf { rememberedIsValid }.orEmpty(),
                lastSelectedFile = lastSelected.takeIf { isWithinRoot(it, config.root.toString()) }.orEmpty()
            ) }
            val normalizedRequested = path?.let(::normalizeUrl)
            val targetPath = normalizedRequested
                ?.takeIf { isWithinRoot(it, config.root.toString()) }
                ?: if (rememberedIsValid) normalizeUrl(remembered) else config.root.toString()
            val restoringRememberedPath = path == null &&
                rememberedIsValid && targetPath != config.root.toString()

            loadDirectory(
                config = config,
                targetPath = targetPath,
                fallbackToRootOnFailure = restoringRememberedPath
            )
        }
    }

    private suspend fun loadDirectory(
        config: PlayerWebDavDirectory.Config,
        targetPath: String,
        fallbackToRootOnFailure: Boolean
    ) {
        val directory = checkNotNull(WebDavPlaybackIdentity.directory(targetPath))
        val rootPath = config.root.toString()
        _uiState.update {
            it.copy(
                isLoading = true,
                files = emptyList(),
                error = null,
                currentPath = targetPath,
                mediaRootPath = rootPath
            )
        }

        try {
            val resources = PlayerWebDavDirectory.list(config, directory, useCache = false)
            if (settingsRepository.playerWebdavDirectoryConfig.first() != config) {
                browseDirectory()
                return
            }
            // 只有列举成功才覆盖最近目录，失败路径不会污染记忆。
            settingsRepository.setPlayerWebdavLastBrowsePath(targetPath)

            val isRoot = PlayerWebDavDirectory.samePath(directory, config.root)
            val items = mutableListOf<WebDAVFileItem>()

            if (!isRoot) {
                items.add(
                    WebDAVFileItem(
                        name = "..",
                        path = getParentPath(targetPath, rootPath),
                        isDirectory = true
                    )
                )
            }

            for (resource in resources) {
                val entry = PlayerWebDavDirectory.entryUrl(directory, resource) ?: continue
                if (PlayerWebDavDirectory.samePath(entry, directory)) continue
                val resPath = entry.toString()
                val resName = resource.displayName?.takeIf { it.isNotBlank() }
                    ?: entry.pathSegments.lastOrNull { it.isNotEmpty() }.orEmpty()
                if (resName.isBlank()) continue

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

            val directoryKey = directory.encodedPath
            val anchor = pageAnchors[directoryKey] ?: settingsRepository.webDavScrollAnchor(config, directoryKey)
            val anchorIndex = items.indexOfFirst { item ->
                anchor != null && pathOnly(item.path) == anchor.itemPath
            }

            _uiState.value = _uiState.value.copy(
                files = items,
                isLoading = false,
                currentPath = targetPath,
                mediaRootPath = rootPath,
                lastBrowsePath = targetPath,
                scrollIndex = anchorIndex.coerceAtLeast(0),
                scrollOffset = if (anchorIndex >= 0) anchor?.offsetPx ?: 0 else 0,
                listGeneration = _uiState.value.listGeneration + 1,
                error = null
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            AppLogManager.w(TAG, "WebDAV 目录查询失败 (${e.javaClass.simpleName})")
            if (fallbackToRootOnFailure && e is PlayerWebDavDirectory.HttpFailure && e.status in listOf(404, 410)) {
                // 只有目录确认失效才回退，断网或认证失败不覆盖上次位置。
                loadDirectory(
                    config = config,
                    targetPath = rootPath,
                    fallbackToRootOnFailure = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (e is PlayerWebDavDirectory.HttpFailure) "WebDAV HTTP ${e.status}" else "无法读取 WebDAV 目录"
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

    fun rememberScroll(generation: Long, index: Int, offset: Int) {
        val state = _uiState.value
        val config = activeConfig ?: return
        if (state.isLoading || state.listGeneration != generation) return
        val directory = pathOnly(state.currentPath) ?: return
        val item = state.files.getOrNull(index)?.let { pathOnly(it.path) } ?: return
        val anchor = SettingsRepository.WebDavScrollAnchor(item, offset.coerceAtLeast(0))
        if (pageAnchors[directory] == anchor) return
        if (pageAnchors.size >= 32 && directory !in pageAnchors) pageAnchors.remove(pageAnchors.keys.first())
        pageAnchors[directory] = anchor
        viewModelScope.launch {
            try {
                settingsRepository.saveWebDavScroll(config, directory, anchor)
            } catch (_: java.io.IOException) {
                AppLogManager.w(TAG, "无法保存 WebDAV 浏览位置")
            }
        }
    }

    private fun pathOnly(raw: String): String? = WebDavPlaybackIdentity.directory(raw)?.encodedPath

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
        pageAnchors.clear()
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

    private fun normalizeUrl(url: String): String = WebDavPlaybackIdentity.directory(url)?.toString().orEmpty()

    private fun samePath(first: String, second: String): Boolean =
        first.trimEnd('/') == second.trimEnd('/')

    private fun isWithinRoot(path: String, root: String): Boolean {
        val candidate = WebDavPlaybackIdentity.directory(path) ?: return false
        val boundary = WebDavPlaybackIdentity.directory(root) ?: return false
        return WebDavPlaybackIdentity.isWithin(candidate, boundary)
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
            val settingsRepository = AppContainer.getSettingsRepository()
            return WebDAVBrowseViewModel(settingsRepository) as T
        }
    }
}
