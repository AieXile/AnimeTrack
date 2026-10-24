package com.aiexile.animetrack.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiexile.animetrack.util.resolveCoverModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Coil 2 没有 Glance 适配：组件渲染前把封面解码成 Bitmap，再交给 Glance 的 ImageProvider。
 *
 * RemoteViews 在 Android 12 以下不支持对 ImageView 做圆角裁剪，因此这里直接按超椭圆
 * 裁切位图，保证低版本桌面上的封面圆角也平滑一致。
 */
object WidgetImageLoader {

    // RemoteViews 通过 Binder 传递位图，单次事务约 1MB：封面分辨率与数量都必须受控
    private const val COVER_PX = 160

    suspend fun loadCover(context: Context, coverUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
        val model = resolveCoverModel(coverUrl) ?: return@withContext null
        val request = ImageRequest.Builder(context.applicationContext)
            .data(model)
            .size(COVER_PX, COVER_PX)
            // RemoteViews 需要软件位图，硬件位图在跨进程传递时不可用
            .allowHardware(false)
            .build()
        when (val result = context.applicationContext.imageLoader.execute(request)) {
            is SuccessResult -> runCatching {
                clipToSquircle(result.drawable.toBitmap(COVER_PX, COVER_PX))
            }.getOrNull()
            else -> null
        }
    }

    private fun clipToSquircle(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source
        val output = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        // 直接以位图作 shader 填充超椭圆路径：不依赖混合模式，软件画布上也能得到抗锯齿圆角
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(squirclePath(width.toFloat(), height.toFloat()), paint)
        return output
    }

    /** 与桌面背景一致思路的超椭圆路径（Lamé curve），曲率连续，避免普通圆角的生硬转角 */
    private fun squirclePath(width: Float, height: Float, n: Double = 5.0, steps: Int = 160): Path {
        val path = Path()
        val cx = width / 2f
        val cy = height / 2f
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val ct = cos(t)
            val st = sin(t)
            val x = cx + sign(ct) * abs(ct).pow(2.0 / n) * cx
            val y = cy + sign(st) * abs(st).pow(2.0 / n) * cy
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat())
            else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }
}
