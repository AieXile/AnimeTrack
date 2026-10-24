package com.aiexile.animetrack.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.widget.WidgetDataProvider
import com.aiexile.animetrack.data.widget.WidgetSyncScheduler
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.navigation.WidgetEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 「在看」组件：封面 + 标题 + 进度条 + 已看/总集数。
 *
 * 2x2 为基础尺寸；排序为置顶 → 待看更新 → 最近观看。
 * 行点击进入番剧详情，头部与空白区域进入首页。
 */
class WatchingWidget : GlanceAppWidget() {

    // Exact：按桌面实际给出的尺寸排版，内容按行高放大，避免固定尺寸被拉伸后出现空白
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val state = withContext(Dispatchers.IO) {
            val items = WidgetDataProvider.loadWatching()
            // 并行解码封面：组件渲染有执行时限，串行加载多张远程图会明显拖慢首帧
            val covers = coroutineScope {
                items.take(MAX_COVER_ROWS)
                    .map { anime ->
                        async {
                            WidgetImageLoader.loadCover(appContext, anime.coverUrl)
                                ?.let { bitmap -> anime.id to bitmap }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .toMap()
            }
            WatchingState(
                items = items,
                covers = covers,
                lastSyncedAt = WidgetDataProvider.lastSyncedAt(appContext)
            )
        }
        val widgetContext = widgetLocalizedContext(appContext)
        val palette = widgetPalette(widgetContext)
        provideContent {
            CompositionLocalProvider(LocalContext provides widgetContext) {
                WatchingContent(state, palette)
            }
        }
    }

    companion object {
        private const val MAX_COVER_ROWS = 5
    }
}

private data class WatchingState(
    val items: List<Anime>,
    val covers: Map<Int, android.graphics.Bitmap>,
    val lastSyncedAt: Long?
)

@Composable
private fun WatchingContent(state: WatchingState, palette: WidgetPalette) {
    val context = LocalContext.current
    val size = LocalSize.current
    val sizeClass = currentWidgetSizeClass()
    val maxRows = widgetRowCount(size.height)
    val compactHeader = sizeClass == WidgetSizeClass.COMPACT ||
        sizeClass == WidgetSizeClass.WIDE ||
        sizeClass == WidgetSizeClass.TALL
    val padding = when (sizeClass) {
        WidgetSizeClass.COMPACT, WidgetSizeClass.WIDE -> 12.dp
        WidgetSizeClass.TALL -> 14.dp
        else -> 16.dp
    }
    // 2x2 / 4x2 高度紧张；4x4 放 6 条时也切单行，避免多行文本挤爆行高
    val singleLine = sizeClass == WidgetSizeClass.COMPACT ||
        sizeClass == WidgetSizeClass.WIDE ||
        (sizeClass == WidgetSizeClass.MEDIUM && maxRows >= 6)
    val rowGap = when (sizeClass) {
        WidgetSizeClass.COMPACT, WidgetSizeClass.WIDE -> 4.dp
        WidgetSizeClass.TALL -> when {
            size.height < 220.dp -> 8.dp
            size.height < 300.dp -> 5.dp
            else -> 6.dp
        }
        else -> 4.dp
    }
    val showFooter = sizeClass == WidgetSizeClass.LARGE && state.lastSyncedAt != null
    val titleMaxChars = when (sizeClass) {
        WidgetSizeClass.COMPACT -> 4
        WidgetSizeClass.WIDE -> 14
        WidgetSizeClass.TALL -> if (size.height >= 220.dp) 6 else 8
        WidgetSizeClass.MEDIUM -> 15
        WidgetSizeClass.LARGE -> 16
    }

    val visibleRows = state.items.size.coerceAtMost(maxRows)
    val headerH = if (compactHeader) 17.dp else 21.dp
    val dividerH = if (sizeClass == WidgetSizeClass.COMPACT || sizeClass == WidgetSizeClass.WIDE) {
        14.dp
    } else {
        21.dp
    }
    val footerH = if (showFooter) 17.dp else 0.dp
    val contentH = size.height - padding * 2 - headerH - dividerH - footerH
    val minRow = if (singleLine) 15.dp else 24.dp
    // 行槽包含行间距：Glance 每个 ViewGroup 只保留前 10 个子视图，
    // 行间不能再放独立 Spacer，否则第 5 行起会被整段丢弃
    val rowSlot = if (visibleRows > 0) {
        (contentH.value / visibleRows).dp.coerceAtLeast(minRow + rowGap)
    } else {
        minRow + rowGap
    }
    val rowHeight = (rowSlot - rowGap).coerceAtLeast(minRow)
    val coverSize = if (singleLine) {
        (rowHeight - 2.dp).coerceIn(14.dp, if (sizeClass == WidgetSizeClass.WIDE) 32.dp else 24.dp)
    } else {
        (rowHeight - 4.dp).coerceIn(20.dp, if (size.width < 180.dp) 40.dp else 96.dp)
    }
    val titleSize = if (singleLine) {
        when {
            rowHeight >= 26.dp -> 13
            rowHeight >= 20.dp -> 12
            else -> 11
        }
    } else {
        when {
            coverSize >= 48.dp -> 14
            rowHeight >= 30.dp -> 12
            else -> 11
        }
    }
    val progressSize = if (singleLine) {
        if (rowHeight >= 26.dp) 9 else 8
    } else {
        when {
            coverSize >= 48.dp -> 10
            rowHeight >= 30.dp -> 9
            else -> 8
        }
    }

    WidgetRoot(
        entry = WidgetEntry.MainTab(WidgetEntry.DEST_HOME),
        palette = palette,
        padding = padding
    ) {
        WidgetHeader(
            title = context.getString(R.string.widget_watching_title),
            subtitle = null,
            badge = if (state.items.isEmpty()) {
                null
            } else {
                context.getString(R.string.widget_count_format, state.items.size)
            },
            palette = palette,
            compact = compactHeader
        )
        WidgetDivider(
            palette,
            compact = sizeClass == WidgetSizeClass.COMPACT || sizeClass == WidgetSizeClass.WIDE
        )
        if (state.items.isEmpty()) {
            WidgetEmptyState(
                text = context.getString(R.string.widget_watching_empty),
                palette = palette,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        } else {
            state.items.take(maxRows).forEachIndexed { index, anime ->
                if (singleLine) {
                    WatchingSingleLineRow(
                        anime = anime,
                        cover = state.covers[anime.id],
                        coverSize = coverSize,
                        titleMaxChars = titleMaxChars,
                        showProgress = true,
                        titleSize = titleSize,
                        progressSize = progressSize,
                        rowHeight = rowSlot,
                        palette = palette
                    )
                } else {
                    WatchingRow(
                        anime = anime,
                        cover = state.covers[anime.id],
                        coverSize = coverSize,
                        titleMaxChars = titleMaxChars,
                        titleSize = titleSize,
                        progressSize = progressSize,
                        rowHeight = rowSlot,
                        palette = palette
                    )
                }
            }
            if (showFooter) {
                UpdatedAtFooter(palette, state.lastSyncedAt)
            }
        }
    }
}

class WatchingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchingWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetSyncScheduler.ensureScheduled(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSyncScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSyncScheduler.ensureScheduled(context)
    }
}
