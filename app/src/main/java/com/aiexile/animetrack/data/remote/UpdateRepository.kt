package com.aiexile.animetrack.data.remote

import android.util.Log
import com.aiexile.animetrack.data.network.RetrofitClient

class UpdateRepository {

    companion object {
        private const val TAG = "UpdateRepository"
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
                UpdateInfo(
                    versionName = remoteVersion,
                    changelog = release.body,
                    downloadUrl = apkAsset?.browserDownloadUrl ?: "",
                    apkSize = apkAsset?.size ?: 0L,
                    releaseUrl = release.htmlUrl
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
}

data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val apkSize: Long,
    val releaseUrl: String
)
