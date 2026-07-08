package com.aiexile.animetrack.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

object ShareCardRenderer {

    private const val TAG = "ShareCardRenderer"

    suspend fun renderShareCard(
        context: Context,
        anime: Anime,
        shareNotes: String,
        settingsRepository: SettingsRepository
    ): Bitmap = withContext(Dispatchers.IO) {
        // 1080x1350 = 4:5 高清比例
        val widthPx = 1080
        val heightPx = 1350
        val paddingPx = 60
        val contentWidth = widthPx - paddingPx * 2
        val coverWidth = 300
        val coverHeight = 420
        val cornerRadius = 24f

        // 读取主题配色
        val isDark = when (settingsRepository.themeMode.first()) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val bgColor = if (isDark) Color.BLACK else Color.WHITE
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val subTextColor = if (isDark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
        val cardColor = if (isDark) 0xFF1A1A1A.toInt() else 0xFFF5F5F5.toInt()
        val dividerColor = if (isDark) 0xFF333333.toInt() else 0xFFDDDDDD.toInt()

        // 加载封面：本地文件直接用 BitmapFactory，远程 URL 用 Coil
        var coverBitmap: Bitmap? = null
        if (!anime.coverUrl.isNullOrBlank()) {
            try {
                val localPath = anime.coverUrl.removePrefix("file://")
                val localFile = File(localPath)
                if (localFile.exists() && localFile.isFile) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    coverBitmap = BitmapFactory.decodeFile(localFile.absolutePath, options)
                }
                if (coverBitmap == null) {
                    val request = ImageRequest.Builder(context)
                        .data(anime.coverUrl)
                        .size(coverWidth * 2, coverHeight * 2)
                        .allowHardware(false) // 软件Canvas不能绘制硬件Bitmap
                        .build()
                    val result = Coil.imageLoader(context).execute(request)
                    val drawable = (result as? SuccessResult)?.drawable
                    if (drawable != null) {
                        coverBitmap = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        ).also { bmp ->
                            val c = Canvas(bmp)
                            drawable.setBounds(0, 0, bmp.width, bmp.height)
                            drawable.draw(c)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "封面加载失败: ${anime.coverUrl}", e)
            }
        }

        // Paint 定义
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 48f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 32f
        }
        val ratingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
        }
        val notesPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 32f
        }
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        }
        val ratingNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
        }

        // 创建固定 4:5 画布
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        var y = paddingPx.toFloat()

        // === 封面行 ===
        val infoX = paddingPx + coverWidth + 30f
        val infoWidth = contentWidth - coverWidth - 30

        // 绘制封面（圆角）
        val coverDst = RectF(paddingPx.toFloat(), y, (paddingPx + coverWidth).toFloat(), y + coverHeight)
        val coverPath = Path()
        coverPath.addRoundRect(coverDst, cornerRadius, cornerRadius, Path.Direction.CW)

        if (coverBitmap != null) {
            canvas.save()
            canvas.clipPath(coverPath)
            // 居中裁剪绘制
            val srcW = coverBitmap.width.toFloat()
            val srcH = coverBitmap.height.toFloat()
            val scale = maxOf(coverWidth / srcW, coverHeight / srcH)
            val drawW = srcW * scale
            val drawH = srcH * scale
            val offsetX = coverDst.left + (coverWidth - drawW) / 2f
            val offsetY = coverDst.top + (coverHeight - drawH) / 2f
            canvas.drawBitmap(coverBitmap, null, RectF(offsetX, offsetY, offsetX + drawW, offsetY + drawH), null)
            canvas.restore()
            coverBitmap.recycle()
        } else {
            canvas.drawPath(coverPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor })
            val firstCharPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = subTextColor
                textSize = 120f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(anime.title.take(1), coverDst.centerX(), coverDst.centerY() + 40f, firstCharPaint)
        }

        // 右侧信息 - 标题（最多2行）
        val titleLayout = StaticLayout.Builder.obtain(anime.title, 0, anime.title.length, titlePaint, infoWidth.toInt())
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(infoX, y + 12f)
        titleLayout.draw(canvas)
        canvas.restore()

        var infoY = y + 12f + titleLayout.height + 16f

        // 状态标签
        val statusText = anime.status.displayName
        val tagWidth = tagPaint.measureText(statusText) + 30f
        val tagRect = RectF(infoX, infoY, infoX + tagWidth, infoY + 42f)
        canvas.drawRoundRect(tagRect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            alpha = 25
        })
        canvas.drawText(statusText, infoX + 15f, infoY + 32f, tagPaint)

        // 集数
        val episodeText = if (anime.totalEpisodes > 0) " · ${anime.watchedEpisodes}/${anime.totalEpisodes}集" else " · ${anime.watchedEpisodes}集"
        canvas.drawText(episodeText, infoX + tagWidth + 6f, infoY + 32f, bodyPaint)

        infoY += 60f

        // 评分
        if (anime.rating != null && anime.rating > 0) {
            canvas.drawText("★", infoX, infoY + 32f, ratingPaint)
            canvas.drawText(String.format(" %.1f", anime.rating), infoX + 38f, infoY + 32f, ratingNumPaint)
            infoY += 52f
        }

        // 完结
        if (anime.isFinished) {
            canvas.drawText("已完结", infoX, infoY + 32f, bodyPaint)
        }

        y += coverHeight + 40f

        // === 简介 ===
        if (!anime.summary.isNullOrBlank()) {
            val summaryText = anime.summary.take(150)
            val summaryLayout = StaticLayout.Builder.obtain(summaryText, 0, summaryText.length, bodyPaint, contentWidth)
                .setMaxLines(5)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(paddingPx.toFloat(), y)
            summaryLayout.draw(canvas)
            canvas.restore()
            y += summaryLayout.height + 28f
        }

        // === 备注 ===
        if (shareNotes.isNotBlank()) {
            val notesText = shareNotes.take(150)
            val notesLayout = StaticLayout.Builder.obtain(notesText, 0, notesText.length, notesPaint, contentWidth - 30)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val notesBoxHeight = notesLayout.height + 50f
            val notesBoxRect = RectF(paddingPx.toFloat(), y, (paddingPx + contentWidth).toFloat(), y + notesBoxHeight)
            canvas.drawRoundRect(notesBoxRect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor })
            canvas.save()
            canvas.translate(paddingPx + 15f, y + 25f)
            notesLayout.draw(canvas)
            canvas.restore()
            y += notesBoxHeight + 28f
        }

        // === 品牌水印 ===
        val brandY = heightPx - paddingPx - 20f
        canvas.drawLine(paddingPx.toFloat(), brandY, (widthPx / 2f - 80f), brandY, Paint().apply { color = dividerColor; strokeWidth = 1.5f })
        canvas.drawLine((widthPx / 2f + 80f), brandY, (widthPx - paddingPx).toFloat(), brandY, Paint().apply { color = dividerColor; strokeWidth = 1.5f })
        canvas.drawText("AnimeTrack", widthPx / 2f, brandY + 8f, brandPaint)

        bitmap
    }

    fun saveShareImage(context: Context, bitmap: Bitmap): File {
        val shareDir = File(context.cacheDir, "share")
        if (!shareDir.exists()) shareDir.mkdirs()
        val file = File(shareDir, "share_${System.currentTimeMillis()}.png")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        return file
    }

    fun cleanupShareImages(context: Context) {
        try {
            val shareDir = File(context.cacheDir, "share")
            if (shareDir.exists() && shareDir.isDirectory) {
                shareDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.startsWith("share_") && file.name.endsWith(".png")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理分享临时文件失败", e)
        }
    }
}
