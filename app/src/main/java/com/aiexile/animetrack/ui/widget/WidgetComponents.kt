package com.aiexile.animetrack.ui.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.navigation.WidgetEntry
import com.aiexile.animetrack.ui.navigation.widgetEntryIntent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 三个组件共用的 Glance 渲染原语。
 *
 * 视觉规格：
 * - 背景为超椭圆 9-patch（任意拉伸保持平滑圆角）；
 * - 头部 = 品牌色竖条 + 标题 + primary 实底胶囊计数；
 * - 列表行 = 封面 + 标题 + 进度信息，封面位图为预切圆角（RemoteViews 低版本不支持圆角裁剪）。
 */

/**
 * 尺寸档位（参考大厂组件的 responsive 布局）：
 * - COMPACT：2x2 窄而矮；WIDE：4x2 宽而矮；TALL：2x3~2x5 窄而高；
 * - MEDIUM / LARGE：4x4 及以上宽而高。
 * 不同档位不是简单缩放，而是切换行数、封面尺寸与信息层级。
 */
internal enum class WidgetSizeClass { COMPACT, WIDE, TALL, MEDIUM, LARGE }

@androidx.compose.runtime.Composable
internal fun currentWidgetSizeClass(): WidgetSizeClass = sizeClass(LocalSize.current)

internal fun sizeClass(size: DpSize): WidgetSizeClass = when {
    size.height < 160.dp && size.width >= 180.dp -> WidgetSizeClass.WIDE
    size.height < 160.dp -> WidgetSizeClass.COMPACT
    size.width < 180.dp -> WidgetSizeClass.TALL
    size.height < 280.dp -> WidgetSizeClass.MEDIUM
    else -> WidgetSizeClass.LARGE
}

/** 由组件实际高度反推展示条数。
 *
 * ColorOS / OnePlus 桌面为 4 列 × 6 行，单行约 70~78dp（2×2 实测上报 147.7dp），
 * 且宿主会在卡片尺寸外再补约 8dp 内边距，所以不能按宽度或固定档位判断：
 * 2~3 行给 3 条，4 行给 6 条，5 行给 7 条，阈值取两档中间值，避免贴边抖动。
 */
internal fun widgetRowCount(height: Dp): Int = when {
    height < 273.dp -> 3
    height < 343.dp -> 6
    else -> 7
}

/** 组件根：超椭圆底 + 点击（Android 12+ 由宿主圆角裁剪，低版本由 9-patch 自绘） */
@androidx.compose.runtime.Composable
internal fun WidgetRoot(
    entry: WidgetEntry,
    palette: WidgetPalette,
    padding: Dp = 14.dp,
    content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(palette.backgroundRes))
            .clickable(actionStartActivity(widgetEntryIntent(context, entry)))
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                // 横向再多留一点白，2x2 小尺寸下内容不贴边
                .padding(horizontal = padding + 2.dp, vertical = padding)
        ) {
            content()
        }
    }
}

@androidx.compose.runtime.Composable
internal fun WidgetHeader(
    title: String,
    subtitle: String?,
    badge: String?,
    palette: WidgetPalette,
    compact: Boolean
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(if (compact) 15.dp else 18.dp)
                .cornerRadius(2.dp)
                .background(ColorProvider(palette.primary)),
            content = { }
        )
        Spacer(modifier = GlanceModifier.width(if (compact) 4.dp else 7.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.onSurface),
                    fontSize = if (compact) 13.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            if (!compact && subtitle != null) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(palette.onSurfaceVariant),
                        fontSize = 10.sp
                    )
                )
            }
        }
        if (badge != null) {
            // 胶囊 9-patch 自带最小宽度，Text 默认左对齐会让数字贴左，用 Box 居中
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(palette.pillRes))
                    .padding(horizontal = if (compact) 6.dp else 9.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(palette.onPrimary),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
internal fun WidgetDivider(palette: WidgetPalette, compact: Boolean = false) {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            // 标题 → 分割线 → 首行：上下都留白，避免贴太紧
            .padding(top = if (compact) 5.dp else 10.dp, bottom = if (compact) 8.dp else 10.dp)
            .height(1.dp)
            .background(ColorProvider(palette.outline))
    )
}

@androidx.compose.runtime.Composable
internal fun WidgetEmptyState(text: String, palette: WidgetPalette, modifier: GlanceModifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(palette.onSurfaceVariant),
                fontSize = 12.sp
            )
        )
    }
}

@androidx.compose.runtime.Composable
internal fun TodayAiringRow(
    anime: Anime,
    cover: Bitmap?,
    coverSize: Dp,
    titleMaxChars: Int,
    compactProgress: Boolean,
    titleSize: Int = 12,
    progressSize: Int = 9,
    rowHeight: Dp = 24.dp,
    palette: WidgetPalette
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(
                actionStartActivity(
                    widgetEntryIntent(context, WidgetEntry.AnimeDetail(anime.id))
                )
            )
            .padding(vertical = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(anime, cover, coverSize, palette)
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = anime.title.ellipsize(titleMaxChars),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(palette.onSurface),
                        fontSize = titleSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (anime.hasNewUpdate) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    NewEpisodePill(palette)
                }
            }
            Text(
                text = if (compactProgress) {
                    compactProgressText(context, anime)
                } else {
                    progressText(context, anime)
                },
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.onSurfaceVariant),
                    fontSize = progressSize.sp
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
internal fun WatchingRow(
    anime: Anime,
    cover: Bitmap?,
    coverSize: Dp,
    titleMaxChars: Int,
    titleSize: Int = 12,
    progressSize: Int = 9,
    rowHeight: Dp = 24.dp,
    palette: WidgetPalette
) {
    val context = LocalContext.current
    val maxEpisodes = anime.effectiveMaxEpisodes
    val fraction = if (maxEpisodes > 0) {
        (anime.watchedEpisodes.toFloat() / maxEpisodes).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(
                actionStartActivity(
                    widgetEntryIntent(context, WidgetEntry.AnimeDetail(anime.id))
                )
            )
            .padding(vertical = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(anime, cover, coverSize, palette)
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = anime.title.ellipsize(titleMaxChars),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(palette.onSurface),
                        fontSize = titleSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (anime.hasNewUpdate) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    NewEpisodePill(palette)
                }
            }
            Spacer(modifier = GlanceModifier.height(1.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(if (titleSize >= 14) 4.dp else 3.dp),
                    color = ColorProvider(palette.primary),
                    backgroundColor = ColorProvider(palette.outline)
                )
                if (maxEpisodes > 0) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = context.getString(
                            R.string.widget_progress_compact_format,
                            anime.watchedEpisodes,
                            maxEpisodes
                        ),
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(palette.onSurfaceVariant),
                            fontSize = progressSize.sp
                        )
                    )
                }
            }
        }
    }
}

/** 单行行：2x2 / 4x2 等高度紧张时使用，避免多行文本把行高撑大 */
@androidx.compose.runtime.Composable
internal fun TodayAiringSingleLineRow(
    anime: Anime,
    cover: Bitmap?,
    coverSize: Dp,
    titleMaxChars: Int,
    showProgress: Boolean = true,
    titleSize: Int = 12,
    progressSize: Int = 9,
    rowHeight: Dp = 18.dp,
    palette: WidgetPalette
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(
                actionStartActivity(
                    widgetEntryIntent(context, WidgetEntry.AnimeDetail(anime.id))
                )
            )
            .padding(vertical = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(anime, cover, coverSize, palette)
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = anime.title.ellipsize(titleMaxChars),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(palette.onSurface),
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (anime.hasNewUpdate) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            NewEpisodeDot(palette)
        }
        if (showProgress) {
            Spacer(modifier = GlanceModifier.width(5.dp))
            Text(
                text = compactProgressText(context, anime),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.onSurfaceVariant),
                    fontSize = progressSize.sp
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
internal fun WatchingSingleLineRow(
    anime: Anime,
    cover: Bitmap?,
    coverSize: Dp,
    titleMaxChars: Int,
    showProgress: Boolean = true,
    titleSize: Int = 12,
    progressSize: Int = 9,
    rowHeight: Dp = 18.dp,
    palette: WidgetPalette
) {
    val context = LocalContext.current
    val maxEpisodes = anime.effectiveMaxEpisodes
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(rowHeight)
            .clickable(
                actionStartActivity(
                    widgetEntryIntent(context, WidgetEntry.AnimeDetail(anime.id))
                )
            )
            .padding(vertical = 0.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(anime, cover, coverSize, palette)
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = anime.title.ellipsize(titleMaxChars),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(palette.onSurface),
                fontSize = titleSize.sp,
                fontWeight = FontWeight.Medium
            )
        )
        if (anime.hasNewUpdate) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            NewEpisodeDot(palette)
        }
        if (showProgress && maxEpisodes > 0) {
            Spacer(modifier = GlanceModifier.width(5.dp))
            Text(
                text = context.getString(
                    R.string.widget_progress_compact_format,
                    anime.watchedEpisodes,
                    maxEpisodes
                ),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.onSurfaceVariant),
                    fontSize = progressSize.sp
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun NewEpisodeDot(palette: WidgetPalette) {
    Box(
        modifier = GlanceModifier
            .size(4.dp)
            .cornerRadius(2.dp)
            .background(ColorProvider(palette.primary)),
        content = { }
    )
}

@androidx.compose.runtime.Composable
private fun CoverImage(
    anime: Anime,
    cover: Bitmap?,
    size: Dp,
    palette: WidgetPalette
) {
    if (cover != null) {
        // 位图在 WidgetImageLoader 中已按超椭圆裁切（低版本 RemoteViews 不支持圆角裁剪）；
        // Android 12+ 再叠加一次 ImageView 圆角，避免某些启动器对位图 alpha 的处理差异。
        val imageModifier = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            GlanceModifier.size(size).cornerRadius(size * 0.22f)
        } else {
            GlanceModifier.size(size)
        }
        Image(
            provider = ImageProvider(cover),
            contentDescription = null,
            modifier = imageModifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = GlanceModifier
                .size(size)
                .background(ImageProvider(palette.placeholderRes)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = anime.title.take(1),
                style = TextStyle(
                    color = ColorProvider(palette.onSurfaceVariant),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun NewEpisodePill(palette: WidgetPalette) {
    val context = LocalContext.current
    Text(
        text = context.getString(R.string.widget_new_episode_badge),
        maxLines = 1,
        modifier = GlanceModifier
            .background(ImageProvider(palette.pillRes))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        style = TextStyle(
            color = ColorProvider(palette.onPrimary),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

@androidx.compose.runtime.Composable
internal fun UpdatedAtFooter(palette: WidgetPalette, lastSyncedAt: Long?) {
    val context = LocalContext.current
    if (lastSyncedAt == null) return
    val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(lastSyncedAt))
    Text(
        text = context.getString(R.string.widget_updated_at, time),
        maxLines = 1,
        modifier = GlanceModifier.padding(top = 4.dp),
        style = TextStyle(
            color = ColorProvider(palette.onSurfaceVariant),
            fontSize = 9.sp
        )
    )
}

internal fun todayLabel(context: Context): String {
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMdEEE")
    return SimpleDateFormat(pattern, locale).format(Date())
}

private fun compactProgressText(context: Context, anime: Anime): String {
    val maxEpisodes = anime.effectiveMaxEpisodes
    return if (maxEpisodes > 0) {
        context.getString(
            R.string.widget_progress_compact_format,
            anime.watchedEpisodes,
            maxEpisodes
        )
    } else {
        context.getString(R.string.widget_progress_only_format, anime.watchedEpisodes)
    }
}

private fun progressText(context: Context, anime: Anime): String = when {
    anime.currentEpisodes > 0 -> context.getString(
        R.string.widget_progress_latest_format,
        anime.watchedEpisodes,
        anime.currentEpisodes
    )
    anime.totalEpisodes > 0 -> context.getString(
        R.string.widget_progress_total_format,
        anime.watchedEpisodes,
        anime.totalEpisodes
    )
    else -> context.getString(R.string.widget_progress_only_format, anime.watchedEpisodes)
}

private fun String.ellipsize(maxChars: Int): String =
    if (length <= maxChars) this else take(maxChars - 1) + "…"
