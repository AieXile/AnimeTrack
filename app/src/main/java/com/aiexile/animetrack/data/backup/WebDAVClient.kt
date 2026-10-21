package com.aiexile.animetrack.data.backup

import com.aiexile.animetrack.data.log.AppLogManager
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object WebDAVClient {

    private const val TAG = "WebDAV"

    private fun createSardine(username: String, password: String): Sardine {
        val sardine = OkHttpSardine()
        if (username.isNotEmpty()) {
            sardine.setCredentials(username, password)
        }
        return sardine
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        return "$trimmed/"
    }

    private fun buildRemotePath(baseUrl: String, strategy: Int): String {
        val base = normalizeUrl(baseUrl)
        val fileName = if (strategy == 0) "AnimeTrack_Backup.json" else "AnimeTrack_Backup.zip"
        return "${base}AnimeTrack/$fileName"
    }

    private fun buildRemoteDir(baseUrl: String): String {
        val base = normalizeUrl(baseUrl)
        return "${base}AnimeTrack/"
    }

    suspend fun upload(
        url: String,
        username: String,
        password: String,
        file: File,
        strategy: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine(username, password)
            val remoteDir = buildRemoteDir(url)

            try {
                sardine.createDirectory(remoteDir)
            } catch (e: Exception) {
                // 目录已存在是最常见的情况，只在详细日志里留痕
                AppLogManager.d(TAG, "创建远程目录忽略异常: $remoteDir, ${e.message}")
            }

            val remotePath = buildRemotePath(url, strategy)
            sardine.put(remotePath, file, "application/octet-stream")
            AppLogManager.i(TAG, "备份上传成功: $remotePath (${file.length()}B)")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogManager.e(TAG, "备份上传失败: ${buildRemotePath(url, strategy)} (${file.length()}B)", e)
            Result.failure(e)
        }
    }

    suspend fun download(
        url: String,
        username: String,
        password: String,
        destFile: File,
        strategy: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine(username, password)
            val remotePath = buildRemotePath(url, strategy)
            val inputStream = sardine.get(remotePath)
            inputStream.use { input ->
                FileOutputStream(destFile).use { fos ->
                    input.copyTo(fos)
                }
            }
            AppLogManager.i(TAG, "备份下载成功: $remotePath (${destFile.length()}B)")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogManager.e(TAG, "备份下载失败: ${buildRemotePath(url, strategy)}", e)
            Result.failure(e)
        }
    }

    suspend fun checkConnection(
        url: String,
        username: String,
        password: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine(username, password)
            val base = normalizeUrl(url)
            sardine.list(base, 0)
            Result.success(true)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            AppLogManager.w(TAG, "WebDAV 连接检测失败: $url, ${e.javaClass.simpleName}: $msg")
            if (msg.contains("403", ignoreCase = true)) {
                Result.failure(Exception("连接失败：403 Forbidden，请检查用户名和密码是否正确"))
            } else if (msg.contains("401", ignoreCase = true)) {
                Result.failure(Exception("连接失败：401 未授权，请检查用户名和密码"))
            } else if (msg.contains("404", ignoreCase = true)) {
                Result.failure(Exception("连接失败：404 未找到，请检查服务器地址是否正确"))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun remoteFileExists(
        url: String,
        username: String,
        password: String,
        strategy: Int
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val sardine = createSardine(username, password)
            val remotePath = buildRemotePath(url, strategy)
            val exists = sardine.exists(remotePath)
            Result.success(exists)
        } catch (e: Exception) {
            AppLogManager.w(TAG, "检查远程备份是否存在失败: ${buildRemotePath(url, strategy)}", e)
            Result.failure(e)
        }
    }
}
