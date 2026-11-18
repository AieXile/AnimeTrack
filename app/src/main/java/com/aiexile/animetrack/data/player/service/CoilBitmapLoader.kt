package com.aiexile.animetrack.data.player.service

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.aiexile.animetrack.R
import com.aiexile.animetrack.util.resolveCoverModel
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * 媒体会话封面加载器：桥接项目共享 Coil ImageLoader 与 Media3 BitmapLoader。
 *
 * - 支持本地裸路径（/files/anime_covers/...）、file://、http(s):// 及 content://；
 * - 复用 AnimeTrackApp 的代理、Bangumi 反代/wsrv.nl 回退、B 站防盗链 Referer；
 * - 强制软件位图以兼容通知栏/锁屏跨进程渲染；
 * - 未关联番剧或封面加载失败时兜底应用图标。
 */
@UnstableApi
class CoilBitmapLoader(context: Context) : BitmapLoader {

    companion object {
        private const val MAX_COVER_PX = 512
        private const val APP_ICON_PX = 256

        fun appIconUri(context: Context): Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .path(R.mipmap.ic_launcher.toString())
            .build()
    }

    private val appContext = context.applicationContext
    private val appIconTargetUri = appIconUri(appContext)
    private val fallbackByteLoader = DataSourceBitmapLoader.Builder(appContext).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean =
        fallbackByteLoader.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        fallbackByteLoader.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        val job = scope.launch {
            try {
                val bitmap = loadBitmapSuspend(uri)
                if (bitmap != null) {
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Failed to decode bitmap for $uri"))
                }
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    future.cancel(false)
                } else {
                    future.setException(t)
                }
            }
        }
        future.addListener({
            if (future.isCancelled) {
                job.cancel()
            }
        }, MoreExecutors.directExecutor())
        return future
    }

    fun release() {
        scope.cancel()
    }

    private suspend fun loadBitmapSuspend(uri: Uri): Bitmap? {
        if (uri == appIconTargetUri) {
            return loadAppIconBitmap()
        }
        val model: Any = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> resolveCoverModel(uri.path)
            ContentResolver.SCHEME_CONTENT -> uri
            null -> resolveCoverModel(uri.toString())
            else -> resolveCoverModel(uri.toString())
        } ?: return loadAppIconBitmap()

        val isLocalFile = model is File
        val request = ImageRequest.Builder(appContext)
            .data(model)
            .size(MAX_COVER_PX, MAX_COVER_PX)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .apply {
                if (isLocalFile) {
                    diskCachePolicy(CachePolicy.DISABLED)
                }
            }
            .build()

        val result = appContext.imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = runCatching { result.drawable.toBitmap() }.getOrNull()
            if (bitmap != null) {
                return if (bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }
            }
        }
        return loadAppIconBitmap()
    }

    private fun loadAppIconBitmap(): Bitmap? {
        val drawable = ContextCompat.getDrawable(appContext, R.mipmap.ic_launcher) ?: return null
        return runCatching {
            drawable.toBitmap(APP_ICON_PX, APP_ICON_PX, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}