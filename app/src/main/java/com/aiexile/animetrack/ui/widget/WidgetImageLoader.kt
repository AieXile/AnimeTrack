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
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiexile.animetrack.util.resolveCoverModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

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
        canvas.drawPath(coverShape(width.toFloat(), height.toFloat()), paint)
        return output
    }

    /**
     * 与项目 [com.aiexile.animetrack.ui.components.SquircleShape] 同一套圆角几何：
     * `RoundedPolygon` + `CornerRounding(smoothing = 0.64)`，半径取短边的 22%，
     * 保证组件封面和 App 内卡片的圆角形态一致。
     */
    private fun coverShape(width: Float, height: Float): Path {
        val radius = min(width, height) * 0.22f
        val rounding = CornerRounding(radius = radius, smoothing = 0.64f)
        val polygon = RoundedPolygon.rectangle(
            width = width,
            height = height,
            perVertexRounding = listOf(rounding, rounding, rounding, rounding),
            centerX = width / 2f,
            centerY = height / 2f
        )
        val path = Path()
        polygon.toPath(path)
        return path
    }
}
