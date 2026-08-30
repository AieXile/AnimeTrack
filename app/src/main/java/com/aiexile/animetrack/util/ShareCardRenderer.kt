package com.aiexile.animetrack.util

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.FontFamilyType
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 分享卡片渲染器 — animal-island-ui 动森风格
 *
 * 设计规范来源: https://github.com/guokaigdg/animal-island-ui
 *   - docs/design-system/css-variables.md  (--animal-* 设计令牌)
 *   - docs/design-system/components/layout.md (Card / Title ribbon 像素规格)
 *
 * 遵循 skill 硬性规则:
 *   - 禁纯黑文字 / 冷灰背景,全部使用暖羊皮纸底 + 大地棕文字
 *   - 主色薄荷绿 #19C8B9,强调黄 #F5C31C,状态标签用 Title 燕尾丝带(13 色板)
 *   - 大圆角(卡片 20px→40f @2x),进度条用胶囊 + 3D game-button 底边
 *   - 字重: 正文 500 / 标题 700+,跟随应用内字体设置(MiSans / 自定义 / 系统)
 */
object ShareCardRenderer {

    private const val TAG = "ShareCardRenderer"

    // ================= animal-island-ui 设计令牌 (@2x, css-variables.md) =================

    /** 浅色主题(暖羊皮纸) */
    private val LIGHT = Palette(
        primary = 0xFF19C8B9.toInt(),        // --animal-primary
        primaryDark = 0xFF11A89B.toInt(),    // --animal-primary-active(3D 底边)
        primaryBg = 0xFFE6F9F6.toInt(),      // --animal-primary-bg
        star = 0xFFF5C31C.toInt(),           // --animal-warning
        textTitle = 0xFF794F27.toInt(),      // --animal-text
        textBody = 0xFF725D42.toInt(),       // --animal-text-body
        textSecondary = 0xFF9F927D.toInt(),  // --animal-text-secondary
        bg = 0xFFF8F8F0.toInt(),             // --animal-bg
        card = 0xFFF7F3DF.toInt(),           // --animal-bg-content
        cardDashed = 0xFFFAF8F2.toInt(),     // dashed Card 底色
        track = 0xFFF0ECE2.toInt(),          // --animal-bg-disabled
        border = 0xFFC4B89E.toInt(),         // --animal-border
        borderDashed = 0xFFE8DCC8.toInt(),   // dashed Card 边框
        borderInput = 0xFFD4C9B4.toInt(),    // --animal-shadow-input
        dot1 = 0x2EC4B89E.toInt(),           // 波点纹理层1(暖棕低透明)
        dot2 = 0x1EC4B89E.toInt()            // 波点纹理层2
    )

    /** 深色主题(暖棕暗版,同样禁纯黑/冷灰) */
    private val DARK = Palette(
        primary = 0xFF2FD4C5.toInt(),
        primaryDark = 0xFF189E90.toInt(),
        primaryBg = 0xFF16413C.toInt(),
        star = 0xFFFFCC00.toInt(),           // --animal-focus-yellow
        textTitle = 0xFFF2E7CE.toInt(),
        textBody = 0xFFD0C4AD.toInt(),
        textSecondary = 0xFFA89B84.toInt(),
        bg = 0xFF2E2820.toInt(),
        card = 0xFF3A332A.toInt(),
        cardDashed = 0xFF342E26.toInt(),
        track = 0xFF443C30.toInt(),
        border = 0xFF5A4F3D.toInt(),
        borderDashed = 0xFF4A4234.toInt(),
        borderInput = 0xFF57493A.toInt(),
        dot1 = 0x24FFFFFF.toInt(),
        dot2 = 0x16FFFFFF.toInt()
    )

    private data class Palette(
        val primary: Int,
        val primaryDark: Int,
        val primaryBg: Int,
        val star: Int,
        val textTitle: Int,
        val textBody: Int,
        val textSecondary: Int,
        val bg: Int,
        val card: Int,
        val cardDashed: Int,
        val track: Int,
        val border: Int,
        val borderDashed: Int,
        val borderInput: Int,
        val dot1: Int,
        val dot2: Int
    )

    /** Title 丝带色板(title.module.less): front 正面 / back 燕尾 / fold 折角 / text 文字 */
    private data class RibbonPalette(val front: Int, val back: Int, val fold: Int, val text: Int)

    // 丝带 13 色板节选,按观看状态映射
    private val RIBBON_WATCHING = RibbonPalette(0xFF82D5BB.toInt(), 0xFF40A880.toInt(), 0xFF186048.toInt(), 0xFFFFFFFF.toInt()) // app-teal
    private val RIBBON_COMPLETED = RibbonPalette(0xFF27D039.toInt(), 0xFF20992A.toInt(), 0xFF115017.toInt(), 0xFFFFFFFF.toInt()) // default green
    private val RIBBON_PLANNED = RibbonPalette(0xFFF7CD67.toInt(), 0xFFD4A030.toInt(), 0xFF8A6010.toInt(), 0xFF725D42.toInt())   // app-yellow
    private val RIBBON_DROPPED = RibbonPalette(0xFFFC736D.toInt(), 0xFFD43030.toInt(), 0xFF900010.toInt(), 0xFFFFFFFF.toInt())   // app-red

    private fun ribbonFor(status: AnimeStatus): RibbonPalette = when (status) {
        AnimeStatus.WATCHING -> RIBBON_WATCHING
        AnimeStatus.COMPLETED -> RIBBON_COMPLETED
        AnimeStatus.PLANNED -> RIBBON_PLANNED
        AnimeStatus.DROPPED -> RIBBON_DROPPED
    }

    /** 卡片字体对(正文 500 / 标题 700),custom 字体无字重时用 fakeBold */
    private data class CardTypeface(val regular: Typeface, val bold: Typeface, val fakeBold: Boolean)

    suspend fun renderShareCard(
        context: Context,
        anime: Anime,
        shareNotes: String,
        settingsRepository: SettingsRepository
    ): Bitmap = withContext(Dispatchers.IO) {
        // 1080x1350 = 4:5 高清比例
        val widthPx = 1080
        val heightPx = 1350

        // 读取主题配色
        val isDark = when (settingsRepository.themeMode.first()) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        val pal = if (isDark) DARK else LIGHT

        // 读取字体设置,与 App 内显示保持一致
        val tf = resolveTypeface(context, settingsRepository)

        // ---------- 版式常量(@2x) ----------
        val cardLeft = 64f
        val cardRight = widthPx - 64f
        val padIn = 52f
        val contentLeft = cardLeft + padIn
        val contentRight = cardRight - padIn
        val contentW = (contentRight - contentLeft).toInt()
        val cardTop = 168f
        val radiusCard = 40f
        val radiusCover = 32f

        val coverW = 300f
        val coverH = 400f
        val coverTop = cardTop + padIn
        val coverLeft = contentLeft
        val coverBottom = coverTop + coverH
        val infoLeft = coverLeft + coverW + 44f
        val infoW = (contentRight - infoLeft).toInt()

        // ---------- Paints ----------
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textTitle
            textSize = 50f
            typeface = tf.bold
            isFakeBoldText = tf.fakeBold
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textBody
            textSize = 31f
            typeface = tf.regular
        }
        val ratingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textTitle
            textSize = 46f
            typeface = tf.bold
            isFakeBoldText = tf.fakeBold
        }
        val percentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textTitle
            textSize = 34f
            typeface = tf.bold
            isFakeBoldText = tf.fakeBold
            textAlign = Paint.Align.RIGHT
        }
        val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.primary
            textSize = 26f
            typeface = tf.bold
            isFakeBoldText = tf.fakeBold
        }
        val summaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textBody
            textSize = 32f
            typeface = tf.regular
        }
        val notesPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textBody
            textSize = 31f
            typeface = tf.regular
        }

        // ---------- 加载封面 ----------
        var coverBitmap: Bitmap? = loadCover(context, anime, coverW, coverH)

        // ---------- 布局测量(先量后画,确定内容卡实际高度) ----------
        val titleTop = coverTop + 6f
        val titleLayout = StaticLayout.Builder.obtain(anime.title, 0, anime.title.length, titlePaint, infoW)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        var iy = titleTop + titleLayout.height + 26f

        // 评分行
        val hasRating = anime.rating != null && anime.rating > 0
        val ratingRowTop = iy
        if (hasRating) iy += 64f

        // 集数行
        val episodeText = if (anime.totalEpisodes > 0) {
            "已看 ${anime.watchedEpisodes} / ${anime.totalEpisodes} 集"
        } else {
            "已看 ${anime.watchedEpisodes} 集"
        }
        val episodeBaseline = iy + 32f
        iy += 54f

        // 进度条行
        val barH = 30f
        val barW = infoW - 104f
        val barTop = iy
        val barRect = RectF(infoLeft, barTop, infoLeft + barW, barTop + barH)
        if (anime.totalEpisodes > 0) iy += barH + 32f

        // 完结胶囊行
        val pillRect = RectF(infoLeft, iy, infoLeft + pillTextPaint.measureText("已完结") + 48f, iy + 46f)
        if (anime.isFinished) iy += 62f
        val infoBottom = iy

        // 分隔线(圆点虚线)与后续段落
        val dividerY = maxOf(coverBottom, infoBottom) + 40f
        var cursorY = dividerY

        var summaryLayout: StaticLayout? = null
        var summaryLabelBaseline = dividerY + 46f
        var summaryTop = summaryLabelBaseline + 26f
        if (!anime.summary.isNullOrBlank()) {
            summaryLabelBaseline = cursorY + 46f
            summaryTop = summaryLabelBaseline + 26f
            val maxLines = if (shareNotes.isBlank()) 4 else 3
            val summaryText = anime.summary.take(160)
            summaryLayout = StaticLayout.Builder.obtain(summaryText, 0, summaryText.length, summaryPaint, contentW)
                .setMaxLines(maxLines)
                .setLineSpacing(6f, 1f)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            cursorY = summaryTop + summaryLayout.height
        }

        var notesLayout: StaticLayout? = null
        var notesBoxTop = 0f
        var notesBoxBottom = 0f
        if (shareNotes.isNotBlank()) {
            cursorY += 36f
            notesBoxTop = cursorY
            val notesText = shareNotes.take(90)
            notesLayout = StaticLayout.Builder.obtain(notesText, 0, notesText.length, notesPaint, contentW - 64)
                .setMaxLines(2)
                .setLineSpacing(6f, 1f)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            notesBoxBottom = notesBoxTop + 40f + 16f + notesLayout.height + 32f
            cursorY = notesBoxBottom
        }
        val cardBottom = cursorY + padIn

        // ---------- 画布:羊皮纸底 + 双层波点 ----------
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(pal.bg)
        drawPolkaDots(canvas, widthPx, heightPx, pal.dot1, pal.dot2)

        // ---------- 大内容卡 ----------
        canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, radiusCard, radiusCard, paint(pal.card))

        // ---------- 封面(圆角 + 描边) ----------
        val coverDst = RectF(coverLeft, coverTop, coverLeft + coverW, coverBottom)
        val coverPath = Path().apply { addRoundRect(coverDst, radiusCover, radiusCover, Path.Direction.CW) }
        if (coverBitmap != null) {
            canvas.save()
            canvas.clipPath(coverPath)
            // 居中裁剪绘制
            val srcW = coverBitmap.width.toFloat()
            val srcH = coverBitmap.height.toFloat()
            val scale = maxOf(coverW / srcW, coverH / srcH)
            val drawW = srcW * scale
            val drawH = srcH * scale
            val offsetX = coverDst.left + (coverW - drawW) / 2f
            val offsetY = coverDst.top + (coverH - drawH) / 2f
            canvas.drawBitmap(coverBitmap, null, RectF(offsetX, offsetY, offsetX + drawW, offsetY + drawH), null)
            canvas.restore()
            coverBitmap.recycle()
        } else {
            // 占位:暖底 + 首字
            canvas.drawPath(coverPath, paint(pal.track))
            val firstCharPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.textSecondary
                textSize = 110f
                typeface = tf.bold
                isFakeBoldText = tf.fakeBold
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(anime.title.take(1), coverDst.centerX(), coverDst.centerY() + 38f, firstCharPaint)
        }
        canvas.drawPath(coverPath, strokePaint(pal.border, 5f))

        // ---------- 右侧信息列 ----------
        canvas.withTranslate(infoLeft, titleTop) { titleLayout.draw(this) }

        if (hasRating) {
            canvas.drawPath(starPath(infoLeft + 22f, ratingRowTop + 22f, 21f), paint(pal.star))
            canvas.drawText(String.format(Locale.US, "%.1f", anime.rating), infoLeft + 56f, ratingRowTop + 40f, ratingPaint)
        }

        canvas.drawText(episodeText, infoLeft, episodeBaseline, bodyPaint)

        // 进度条(胶囊 + 3D 底边,game-button 风格)
        if (anime.totalEpisodes > 0) {
            canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, paint(pal.track))
            canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, strokePaint(pal.borderInput, 3f))
            val progress = anime.progress.coerceIn(0f, 1f)
            if (progress > 0f) {
                val fillRight = barRect.left + barW * progress
                val fillPath = Path().apply {
                    addRoundRect(barRect.left, barRect.top, fillRight, barRect.bottom, barH / 2f, barH / 2f, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(fillPath)
                canvas.drawRect(barRect.left, barRect.top, fillRight, barRect.bottom, paint(pal.primaryDark))
                canvas.drawRect(barRect.left, barRect.top, fillRight, barRect.bottom - 9f, paint(pal.primary))
                canvas.restore()
            }
            canvas.drawText("${anime.progressPercent}%", infoLeft + infoW, barTop + barH - 1f, percentPaint)
        }

        // 完结小胶囊
        if (anime.isFinished) {
            canvas.drawRoundRect(pillRect, 23f, 23f, paint(pal.primaryBg))
            val fm = pillTextPaint.fontMetrics
            val baseline = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText("已完结", pillRect.centerX(), baseline, pillTextPaint)
        }

        // ---------- 分隔线(圆点虚线) ----------
        canvas.drawLine(
            contentLeft, dividerY, contentRight, dividerY,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pal.borderDashed
                strokeWidth = 7f
                strokeCap = Paint.Cap.ROUND
                pathEffect = DashPathEffect(floatArrayOf(0.1f, 30f), 0f)
            }
        )

        // ---------- 简介 ----------
        summaryLayout?.let { layout ->
            drawSectionLabel(canvas, contentLeft, summaryLabelBaseline, "简介", pal, tf)
            canvas.withTranslate(contentLeft, summaryTop) { layout.draw(this) }
        }

        // ---------- 备注(dashed Card) ----------
        notesLayout?.let { layout ->
            canvas.drawRoundRect(contentLeft, notesBoxTop, contentRight, notesBoxBottom, 28f, 28f, paint(pal.cardDashed))
            canvas.drawRoundRect(contentLeft, notesBoxTop, contentRight, notesBoxBottom, 28f, 28f, strokePaint(pal.borderDashed, 4.5f).apply {
                pathEffect = DashPathEffect(floatArrayOf(22f, 16f), 0f)
            })
            drawSectionLabel(canvas, contentLeft + 32f, notesBoxTop + 42f, "我的备注", pal, tf)
            canvas.withTranslate(contentLeft + 32f, notesBoxTop + 58f) { layout.draw(this) }
        }

        // ---------- 状态燕尾丝带(压在卡片上沿) ----------
        drawRibbon(
            canvas,
            cx = widthPx / 2f,
            top = 88f,
            em = 44f,
            text = anime.status.displayName,
            ribbon = ribbonFor(anime.status),
            tf = tf
        )

        // ---------- 品牌胶囊 ----------
        drawBrandPill(canvas, widthPx, heightPx, pal, tf)

        bitmap
    }

    // ==================== 绘制子程序 ====================

    /** animal-island-ui Title 燕尾丝带:燕尾(z1) → 折角(z2) → 正面(z3) → 文字(z4) */
    private fun drawRibbon(
        canvas: Canvas,
        cx: Float,
        top: Float,
        em: Float,
        text: String,
        ribbon: RibbonPalette,
        tf: CardTypeface
    ) {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ribbon.text
            textSize = em * 0.92f
            typeface = tf.bold
            isFakeBoldText = tf.fakeBold
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }
        val textW = textPaint.measureText(text)
        val containerW = textW + 3.2f * em   // padding: 0 1.6em
        val containerH = 2f * em             // height: 2em
        val left = cx - containerW / 2f
        val bottom = top + containerH

        val backPaint = paint(ribbon.back)
        val foldPaint = paint(ribbon.fold)
        val frontPaint = paint(ribbon.front).apply {
            // filter: drop-shadow(0 0.08em 0.12em rgba(0,0,0,.05))
            setShadowLayer(0.18f * em, 0f, 0.08f * em, 0x14000000)
        }

        // 1) 燕尾:1.7em × 1.7em,bottom -0.4em,外扩 0.6em,外端 V 形鱼尾切口
        val tailW = 1.7f * em
        val tailTop = bottom - 1.3f * em
        val tailBottom = bottom + 0.4f * em
        val tailMidY = (tailTop + tailBottom) / 2f
        // 左:polygon(100% 0, 100% 100%, 0 100%, 30% 50%, 0 0)
        val ltLeft = left - 0.6f * em
        val ltRight = ltLeft + tailW
        Path().apply {
            moveTo(ltRight, tailTop)
            lineTo(ltRight, tailBottom)
            lineTo(ltLeft, tailBottom)
            lineTo(ltLeft + 0.3f * tailW, tailMidY)
            lineTo(ltLeft, tailTop)
            close()
        }.let { canvas.drawPath(it, backPaint) }
        // 右:polygon(0 0, 100% 0, 70% 50%, 100% 100%, 0 100%)
        val rtRight = left + containerW + 0.6f * em
        val rtLeft = rtRight - tailW
        Path().apply {
            moveTo(rtLeft, tailTop)
            lineTo(rtRight, tailTop)
            lineTo(rtRight - 0.3f * tailW, tailMidY)
            lineTo(rtRight, tailBottom)
            lineTo(rtLeft, tailBottom)
            close()
        }.let { canvas.drawPath(it, backPaint) }

        // 2) 折角阴影三角:0.95em × 0.45em,top: calc(100% - 0.05em)
        val foldW = 0.95f * em
        val foldH = 0.45f * em
        val fy = bottom - 0.05f * em
        // 左:border-width 0 0.95em 0.45em 0 → 三角 (x0,fy) (x0+foldW,fy) (x0+foldW,fy+foldH)
        val lfLeft = left + 0.15f * em
        Path().apply {
            moveTo(lfLeft, fy)
            lineTo(lfLeft + foldW, fy)
            lineTo(lfLeft + foldW, fy + foldH)
            close()
        }.let { canvas.drawPath(it, foldPaint) }
        // 右:border-width 0 0 0.45em 0.95em → 三角 (x1,fy) (x1,fy+foldH) (x1+foldW,fy)
        val rfLeft = left + containerW - 0.16f * em - foldW
        Path().apply {
            moveTo(rfLeft, fy)
            lineTo(rfLeft, fy + foldH)
            lineTo(rfLeft + foldW, fy)
            close()
        }.let { canvas.drawPath(it, foldPaint) }

        // 3) 正面主体:inset 0 0.1em,radius 0.2em,底部内阴影 0.06em
        val frontLeft = left + 0.1f * em
        val frontW = containerW - 0.2f * em
        val frontRect = RectF(frontLeft, top, frontLeft + frontW, bottom)
        canvas.drawRoundRect(frontRect, 0.2f * em, 0.2f * em, frontPaint)
        val frontPath = Path().apply { addRoundRect(frontRect, 0.2f * em, 0.2f * em, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(frontPath)
        canvas.drawRect(frontRect.left, bottom - 0.06f * em, frontRect.right, bottom, paint(0x0D000000))
        canvas.restore()

        // 4) 文字:垂直居中 + 0.11em CJK 光学下移
        val fm = textPaint.fontMetrics
        val baseline = top + containerH / 2f - (fm.ascent + fm.descent) / 2f + 0.11f * em
        canvas.drawText(text, cx, baseline, textPaint)
    }

    /** 章节小标签:薄荷绿圆点 + 加粗小标题 */
    private fun drawSectionLabel(canvas: Canvas, x: Float, baselineY: Float, label: String, pal: Palette, tf: CardTypeface) {
        canvas.drawCircle(x + 9f, baselineY - 11f, 9f, paint(pal.primary))
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textSecondary
            textSize = 28f
            typeface = tf.bold
            isFakeBoldText = tf.fakeBold
        }
        canvas.drawText(label, x + 32f, baselineY, labelPaint)
    }

    /** 品牌胶囊:底部居中,card 底 + border 描边 + 两侧薄荷绿小星 */
    private fun drawBrandPill(canvas: Canvas, widthPx: Int, heightPx: Int, pal: Palette, tf: CardTypeface) {
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.textSecondary
            textSize = 30f
            typeface = tf.bold
            isFakeBoldText = tf.fakeBold
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }
        val text = "AnimeTrack"
        val textW = brandPaint.measureText(text)
        val pillH = 64f
        val pillW = textW + 110f
        val pillLeft = widthPx / 2f - pillW / 2f
        val pillTop = heightPx - 100f - pillH / 2f
        val pillRect = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, paint(pal.card))
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, strokePaint(pal.border, 3f))

        val fm = brandPaint.fontMetrics
        val baseline = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, widthPx / 2f, baseline, brandPaint)
        val starPaint = paint(pal.primary)
        canvas.drawPath(starPath(pillLeft + 34f, pillRect.centerY(), 11f), starPaint)
        canvas.drawPath(starPath(pillRect.right - 34f, pillRect.centerY(), 11f), starPaint)
    }

    /** 双层波点纹理(Card pattern 同款:大点 28px 网格 + 小点 14px 偏移网格,@2x) */
    private fun drawPolkaDots(canvas: Canvas, w: Int, h: Int, color1: Int, color2: Int) {
        val p1 = paint(color1)
        var y = 12f
        while (y < h) {
            var x = 12f
            while (x < w) {
                canvas.drawCircle(x, y, 4f, p1)
                x += 56f
            }
            y += 56f
        }
        val p2 = paint(color2)
        y = 40f
        while (y < h) {
            var x = 40f
            while (x < w) {
                canvas.drawCircle(x, y, 2.5f, p2)
                x += 56f
            }
            y += 56f
        }
    }

    /** 五角星路径(顶点朝上) */
    private fun starPath(cx: Float, cy: Float, r: Float): Path {
        val inner = r * 0.382f
        return Path().apply {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) r else inner
                val angle = Math.PI * i / 5.0 - Math.PI / 2.0
                val x = cx + radius * cos(angle).toFloat()
                val y = cy + radius * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private inline fun Canvas.withTranslate(x: Float, y: Float, block: Canvas.() -> Unit) {
        save()
        translate(x, y)
        block()
        restore()
    }

    /** 按应用字体设置解析卡片字体:MiSans(内置)/ 自定义字体 / 系统 */
    private suspend fun resolveTypeface(context: Context, settingsRepository: SettingsRepository): CardTypeface {
        val system500 = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val systemBold = Typeface.create("sans-serif", Typeface.BOLD)
        return when (settingsRepository.fontFamilyFlow.first()) {
            FontFamilyType.MISANS.name -> CardTypeface(
                ResourcesCompat.getFont(context, R.font.misans_medium) ?: system500,
                ResourcesCompat.getFont(context, R.font.misans_bold) ?: systemBold,
                fakeBold = false
            )
            FontFamilyType.CUSTOM.name -> {
                val custom = runCatching {
                    settingsRepository.customFontPathFlow.first()
                        .takeIf { it.isNotBlank() }
                        ?.let { Typeface.createFromFile(File(it)) }
                }.getOrNull()
                if (custom != null) CardTypeface(custom, custom, fakeBold = true)
                else CardTypeface(system500, systemBold, fakeBold = false)
            }
            else -> CardTypeface(system500, systemBold, fakeBold = false)
        }
    }

    /** 加载封面:本地文件直接用 BitmapFactory,远程 URL 用 Coil */
    private suspend fun loadCover(context: Context, anime: Anime, coverWidth: Float, coverHeight: Float): Bitmap? {
        if (anime.coverUrl.isNullOrBlank()) return null
        return try {
            val localPath = anime.coverUrl.removePrefix("file://")
            val localFile = File(localPath)
            if (localFile.exists() && localFile.isFile) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(localFile.absolutePath, options)
            } else {
                val request = ImageRequest.Builder(context)
                    .data(anime.coverUrl)
                    .size((coverWidth * 2).toInt(), (coverHeight * 2).toInt())
                    .allowHardware(false) // 软件Canvas不能绘制硬件Bitmap
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val drawable = (result as? SuccessResult)?.drawable
                if (drawable != null) {
                    Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    ).also { bmp ->
                        val c = Canvas(bmp)
                        drawable.setBounds(0, 0, bmp.width, bmp.height)
                        drawable.draw(c)
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "封面加载失败: ${anime.coverUrl}", e)
            null
        }
    }

    /**
     * 将分享卡片写入系统媒体库(Pictures/AnimeTrack)。
     *
     * 使用 MediaStore 而非 cache + FileProvider:QQ 群聊发图是异步上传,
     * 对临时授权的 cache URI 读取会失败;MediaStore URI 任意进程随时可读,
     * 私聊/群聊均稳定。代价是分享图会保留在相册中。
     */
    suspend fun saveShareImageToGallery(context: Context, bitmap: Bitmap): android.net.Uri? =
        withContext(Dispatchers.IO) {
            try {
                val filename = "AnimeTrack_Share_${System.currentTimeMillis()}.png"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AnimeTrack")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext null

                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                } ?: run {
                    resolver.delete(uri, null, null)
                    return@withContext null
                }
                bitmap.recycle()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val updateValues = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    resolver.update(uri, updateValues, null, null)
                }
                uri
            } catch (e: Exception) {
                Log.e(TAG, "保存分享图片到媒体库失败", e)
                null
            }
        }
}
