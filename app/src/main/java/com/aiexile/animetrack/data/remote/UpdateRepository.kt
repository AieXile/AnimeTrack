package com.aiexile.animetrack.data.remote

import android.util.Log
import com.aiexile.animetrack.data.network.RetrofitClient

class UpdateRepository {

    companion object {
        private const val TAG = "UpdateRepository"
        /** Release body 中的强制更新标记，匹配 [FORCE_UPDATE] 或 <!-- force-update --> */
        private val FORCE_UPDATE_REGEX = Regex("""\s*\[FORCE_UPDATE]|\s*<!--\s*force-update\s*-->\s*""", RegexOption.IGNORE_CASE)
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return try {
            val release = RetrofitClient.updateApi.getLatestRelease()
            val remoteVersion = release.tagName
            Log.d(TAG, "Remote version: $remoteVersion, Local version: $currentVersion")

            if (VersionComparator.isNewerVersion(remoteVersion, currentVersion)) {
                val apkAsset = release.assets.find {
                    it.name.endsWith(".apk", ignoreCase = true)
                }
                // 从 release body 检测强制更新标记 [FORCE_UPDATE]，并从 changelog 中移除该标记
                val rawBody = release.body
                val isForceUpdate = FORCE_UPDATE_REGEX.containsMatchIn(rawBody)
                val cleanedChangelog = FORCE_UPDATE_REGEX.replace(rawBody, "").trim()
                UpdateInfo(
                    versionName = remoteVersion,
                    changelog = cleanedChangelog,
                    downloadUrl = apkAsset?.browserDownloadUrl ?: "",
                    apkSize = apkAsset?.size ?: 0L,
                    releaseUrl = release.htmlUrl,
                    apkDigest = apkAsset?.digest ?: "",
                    isForceUpdate = isForceUpdate
                )
            } else {
                Log.d(TAG, "App is up to date")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update", e)
            null
        }
    }

    suspend fun getCurrentVersionChangelog(currentVersion: String): String? {
        return try {
            val release = RetrofitClient.updateApi.getReleaseByTag(currentVersion)
            release.body.ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch changelog for $currentVersion", e)
            null
        }
    }
}

data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val apkSize: Long,
    val releaseUrl: String,
    /** GitHub asset 的摘要，格式如 "sha256:xxxxxx"，为空表示无校验信息 */
    val apkDigest: String = "",
    /** 是否为强制更新（从 release body 中的 [FORCE_UPDATE] 标记解析） */
    val isForceUpdate: Boolean = false
)
