package com.aiexile.animetrack.data.network

import android.content.Context
import android.util.Log
import com.aiexile.animetrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object CoverDownloader {

    private const val TAG = "CoverDownloader"
    // 持久化封面目录：位于 filesDir，卸载后随应用数据一起清除，但与 cacheDir 隔离，
    // 不会被系统在低存储时清理，也不会与 Coil diskCache（cacheDir/image_cache）混淆。
    private const val COVERS_DIR = "anime_covers"

    private val client: OkHttpClient by lazy {
        RetrofitClient.baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * 返回 bangumiId 对应的持久化封面 File（位于 `filesDir/anime_covers/{bangumiId}.jpg`）。
     *
     * 该目录由 CoverDownloader 自管理，文件持久化保存。调用方加载此 File 时，
     * 应禁用 Coil diskCache（推荐通过 [com.aiexile.animetrack.util.coverImageRequestForList]
     * 构建 ImageRequest），避免在 `cacheDir/image_cache/` 中形成双份存储。
     */
    fun getCoverFile(context: Context, bangumiId: Int): File {
        val dir = File(context.filesDir, COVERS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${bangumiId}.jpg")
    }

    /**
     * 返回按 prefix+id 命名的持久化封面 File（位于 `filesDir/anime_covers/{prefix}_{id}.jpg`）。
     *
     * 同 [getCoverFile]：调用方加载此 File 时应禁用 Coil diskCache，
     * 推荐通过 [com.aiexile.animetrack.util.coverImageRequestForList] 构建 ImageRequest。
     */
    fun getCoverFileById(context: Context, id: Int, prefix: String = "bgm"): File {
        val dir = File(context.filesDir, COVERS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${prefix}_${id}.jpg")
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

        return localizeTo(coverUrl, getCoverFile(context, bangumiId), "bangumiId=$bangumiId")
    }

    suspend fun downloadAndLocalizeById(
        context: Context,
        coverUrl: String?,
        id: Int,
        prefix: String = "bgm"
    ): String? {
        if (coverUrl.isNullOrBlank()) return coverUrl

        if (coverUrl.startsWith("/") || coverUrl.startsWith("file://")) {
            val localFile = File(coverUrl.removePrefix("file://"))
            return if (localFile.exists() && localFile.length() > 0) coverUrl else null
        }

        return localizeTo(coverUrl, getCoverFileById(context, id, prefix), "${prefix}_$id")
    }

    /**
     * 将远程封面下载到指定目标文件的通用实现。
     * @param tag 仅用于日志标识
     */
    private suspend fun localizeTo(coverUrl: String, destFile: File, tag: String): String? {
        if (destFile.exists() && destFile.length() > 0) {
            return destFile.absolutePath
        }

        return try {
            withContext(Dispatchers.IO) {
                downloadTo(coverUrl, destFile)
            }
            if (destFile.exists() && destFile.length() > 0) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Cover downloaded: $tag")
                destFile.absolutePath
            } else {
                Log.w(TAG, "Cover download produced empty file: $tag")
                coverUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cover download failed: $tag url=$coverUrl", e)
            coverUrl
        }
    }

    private fun downloadTo(url: String, destFile: File) {
        // lain.bgm.tv 图片走 wsrv.nl 代理加速
        val proxiedUrl = if (url.contains("lain.bgm.tv")) {
            "https://wsrv.nl/?url=$url"
        } else {
            url
        }

        val requestBuilder = Request.Builder().url(proxiedUrl)

        // B站图片防盗链：hdslb.com 域名必须附带 Referer
        if (url.contains("hdslb.com", ignoreCase = true)) {
            requestBuilder.header("Referer", "https://www.bilibili.com/")
        }

        var response = client.newCall(requestBuilder.build()).execute()

        // wsrv.nl 代理失败时回退到原始 URL
        if (!response.isSuccessful && proxiedUrl != url) {
            response.close()
            val fallbackRequest = Request.Builder().url(url).build()
            response = client.newCall(fallbackRequest).execute()
        }

        if (!response.isSuccessful) {
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
