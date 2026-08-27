package com.aiexile.animetrack.data.remote

import android.util.Log
import com.aiexile.animetrack.data.network.RetrofitClient

class UpdateRepository {

    companion object {
        private const val TAG = "UpdateRepository"
        /** 更新日志中的强制更新标记，匹配 [FORCE_UPDATE] 或 <!-- force-update --> */
        private val FORCE_UPDATE_REGEX = Regex("""\s*\[FORCE_UPDATE]|\s*<!--\s*force-update\s*-->\s*""", RegexOption.IGNORE_CASE)
        private const val GITHUB_RELEASES_URL = "https://github.com/AieXile/AnimeTrack/releases"
        /** GitHub Release 下载直链的国内加速镜像前缀（App 直连 GitHub 下载慢，经镜像转发提速） */
        private const val GH_DOWNLOAD_MIRROR = "https://gh-proxy.com/"
        /** mirror 字段取值：服务器本地未同步，数据来自 GitHub 实时兜底 */
        private const val MIRROR_GITHUB = "github"

        /** 设备首选 ABI 对应的安装包标识，服务器/GitHub 按架构分包返回 */
        private val DEVICE_ABI: String = runCatching {
            val abis = android.os.Build.SUPPORTED_ABIS
            when {
                abis?.contains("arm64-v8a") == true -> "arm64-v8a"
                abis?.contains("armeabi-v7a") == true -> "armeabi-v7a"
                else -> "universal"
            }
        }.getOrDefault("universal")

        /** GitHub Release 资产文件名后缀（AnimeTrack-v1.2.3-v8a.apk 等） */
        private val ABI_ASSET_SUFFIX: String = when (DEVICE_ABI) {
            "arm64-v8a" -> "v8a"
            "armeabi-v7a" -> "v7a"
            else -> "universal"
        }
    }

    /**
     * 检查更新：自建服务器（https://www.aiexile.top/update）优先，
     * 服务器请求失败时回退 GitHub Releases。
     * 服务器返回 prerelease 版本时视为无更新（不触发 GitHub 兜底）。
     * mirror=github（服务器本地未同步、实时转发 GitHub 数据）时，
     * App 直接切换 GitHub API 获取日志与下载链接，直连失败则退回服务器转发数据保底。
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        // 1. 自建服务器优先（携带设备架构，服务器返回对应分包）
        try {
            val server = RetrofitClient.updateApi.getUpdate(DEVICE_ABI)
            Log.d(TAG, "Server version: ${server.version} (mirror=${server.mirror}), Local version: $currentVersion")

            if (server.prerelease) {
                Log.d(TAG, "Server latest is a prerelease, ignored")
                return null
            }

            if (VersionComparator.isNewerVersion(server.version, currentVersion)) {
                // 服务器本地未同步（数据来自 GitHub 实时兜底）→ 切换 GitHub 直连
                if (server.mirror.equals(MIRROR_GITHUB, ignoreCase = true)) {
                    Log.d(TAG, "Server data is GitHub-sourced, switching to GitHub API directly")
                    val fromGithub = checkForUpdateFromGithub(currentVersion)
                    if (fromGithub != null) return fromGithub
                    // App 直连 GitHub 失败（如国内网络受限），退回服务器转发的 GitHub 数据保底
                    Log.w(TAG, "Direct GitHub check unavailable, using server-forwarded GitHub data")
                    return server.toUpdateInfo(DownloadSource.GITHUB)
                }
                return server.toUpdateInfo(DownloadSource.SERVER)
            }
            Log.d(TAG, "App is up to date (server)")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Update server check failed, fallback to GitHub", e)
        }

        // 2. 服务器整体不可达 → GitHub 兜底
        return checkForUpdateFromGithub(currentVersion)
    }

    /**
     * 当前版本更新日志：服务器无按版本查询接口，
     * 直接返回最新版 notes（当前已是最新版时内容一致）；
     * 服务器失败时回退 GitHub 按版本 tag 查询。
     */
    suspend fun getCurrentVersionChangelog(currentVersion: String): String? {
        try {
            val server = RetrofitClient.updateApi.getUpdate(DEVICE_ABI)
            if (server.notes.isNotBlank()) {
                return server.notes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch changelog from server failed, fallback to GitHub", e)
        }
        return try {
            RetrofitClient.githubUpdateApi.getReleaseByTag(currentVersion).body.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch changelog for $currentVersion", e)
            null
        }
    }

    private suspend fun checkForUpdateFromGithub(currentVersion: String): UpdateInfo? {
        return try {
            val release = RetrofitClient.githubUpdateApi.getLatestRelease()
            val remoteVersion = release.tagName
            Log.d(TAG, "GitHub fallback version: $remoteVersion, Local version: $currentVersion")

            if (VersionComparator.isNewerVersion(remoteVersion, currentVersion)) {
                // 优先按设备架构选择分包资产，Release 无对应资产时回退任意 APK
                val apkAsset = release.assets.find {
                    it.name.endsWith("-$ABI_ASSET_SUFFIX.apk", ignoreCase = true)
                } ?: release.assets.find {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                // 从 release body 检测强制更新标记 [FORCE_UPDATE]，并从 changelog 中移除该标记
                val rawBody = release.body
                val isForceUpdate = FORCE_UPDATE_REGEX.containsMatchIn(rawBody)
                val cleanedChangelog = FORCE_UPDATE_REGEX.replace(rawBody, "").trim()
                UpdateInfo(
                    versionName = remoteVersion,
                    changelog = cleanedChangelog,
                    // GitHub 直链经国内镜像加速；镜像不可用时用户仍可跳转 releaseUrl 手动下载
                    downloadUrl = apkAsset?.browserDownloadUrl?.let { GH_DOWNLOAD_MIRROR + it } ?: "",
                    apkSize = apkAsset?.size ?: 0L,
                    releaseUrl = release.htmlUrl,
                    apkDigest = apkAsset?.digest ?: "",
                    isForceUpdate = isForceUpdate,
                    downloadSource = DownloadSource.GITHUB
                )
            } else {
                Log.d(TAG, "App is up to date (GitHub)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update on GitHub", e)
            null
        }
    }

    /** 自建服务器响应 → 通用更新信息 */
    private fun ServerUpdateResponse.toUpdateInfo(source: DownloadSource): UpdateInfo {
        // 强制更新标记仍从 notes 中解析，与 GitHub 路径保持一致
        val isForceUpdate = FORCE_UPDATE_REGEX.containsMatchIn(notes)
        val cleanedChangelog = FORCE_UPDATE_REGEX.replace(notes, "").trim()
        return UpdateInfo(
            versionName = version,
            changelog = cleanedChangelog,
            downloadUrl = url,
            apkSize = size,
            releaseUrl = GITHUB_RELEASES_URL,
            apkDigest = sha256,
            isForceUpdate = isForceUpdate,
            publishDate = formatDate(date),
            downloadSource = source
        )
    }

    /** ISO 8601 日期（如 "2026-08-18T19:02:58Z"）→ 本地时区 "yyyy-MM-dd"，解析失败原样返回 */
    private fun formatDate(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            java.time.OffsetDateTime.parse(raw)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        } catch (e: Exception) {
            Log.w(TAG, "Unparseable date: $raw")
            raw
        }
    }
}

/** APK 下载/更新数据来源，用于 UI 提示下载速度差异 */
enum class DownloadSource {
    /** 自建服务器直链（服务器本地已同步），下载快 */
    SERVER,
    /** GitHub 直链（服务器未同步实时兜底或服务器不可达），国内可能慢 */
    GITHUB
}

data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val apkSize: Long,
    val releaseUrl: String,
    /** APK 的 SHA-256 摘要，GitHub 源格式如 "sha256:xxxxxx"，服务器源为裸 hex；为空表示无校验信息 */
    val apkDigest: String = "",
    /** 是否为强制更新（从更新日志中的 [FORCE_UPDATE] 标记解析） */
    val isForceUpdate: Boolean = false,
    /** 发布日期（已格式化为 "yyyy-MM-dd"，为空不展示） */
    val publishDate: String = "",
    /** 更新数据来源（server 镜像/GitHub 兜底），驱动弹窗下载源提示 */
    val downloadSource: DownloadSource = DownloadSource.SERVER
)
