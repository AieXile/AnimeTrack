package com.aiexile.animetrack.data.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

object CoverDownloader {

    private const val TAG = "CoverDownloader"
    private const val COVERS_DIR = "anime_covers"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SafeDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getCoverFile(context: Context, bangumiId: Int): File {
        val dir = File(context.filesDir, COVERS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${bangumiId}.jpg")
    }

    suspend fun downloadAndLocalize(
        context: Context,
        coverUrl: String?,
        bangumiId: Int?
    ): String? {
        if (coverUrl.isNullOrBlank()) return coverUrl
        if (bangumiId == null) return coverUrl

        if (coverUrl.startsWith("/") || coverUrl.startsWith("file://")) {
            val localFile = File(coverUrl.removePrefix("file://"))
            return if (localFile.exists() && localFile.length() > 0) coverUrl else null
        }

        val destFile = getCoverFile(context, bangumiId)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        return try {
            withContext(Dispatchers.IO) {
                downloadTo(coverUrl, destFile)
            }
            if (destFile.exists() && destFile.length() > 0) {
                Log.d(TAG, "Cover downloaded: bangumiId=$bangumiId")
                destFile.absolutePath
            } else {
                Log.w(TAG, "Cover download produced empty file: bangumiId=$bangumiId")
                coverUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cover download failed: bangumiId=$bangumiId url=$coverUrl", e)
            coverUrl
        }
    }

    private fun downloadTo(url: String, destFile: File) {
        val requestBuilder = Request.Builder().url(url)

        // B站图片防盗链：hdslb.com 域名必须附带 Referer
        if (url.contains("hdslb.com", ignoreCase = true)) {
            requestBuilder.header("Referer", "https://www.bilibili.com/")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful && response.code != HttpURLConnection.HTTP_OK) {
            throw Exception("HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")

        val tempFile = File(destFile.parent, "${destFile.name}.tmp")
        try {
            tempFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile)
                tempFile.delete()
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
