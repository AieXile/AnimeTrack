package com.aiexile.animetrack.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.aiexile.animetrack.data.widget.WidgetDataProvider
import com.aiexile.animetrack.data.widget.WidgetStats
import com.aiexile.animetrack.data.widget.WidgetSyncScheduler
import com.aiexile.animetrack.ui.navigation.WidgetEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「追番统计」组件。
 *
 * 与列表组件一致，按桌面真实尺寸排版：
 * - 高度决定展示多少指标（主指标优先，空间足够才追加次级指标）；
 * - 指标行按可用高度均分，字号随行高放大，不用空白填充；
 * - 窄尺寸纵向排布，宽尺寸三指标横排 + 次级指标纵向排布。
 */
class StatsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val state = withContext(Dispatchers.IO) {
            StatsWidgetState(
                stats = WidgetDataProvider.loadStats(),
                lastSyncedAt = WidgetDataProvider.lastSyncedAt(appContext)
            )
        }
        val widgetContext = widgetLocalizedContext(appContext)
        val palette = widgetPalette(widgetContext)
        provideContent {
            CompositionLocalProvider(LocalContext provides widgetContext) {
                StatsContent(state, palette)
            }
        }
    }
}

private data class StatsWidgetState(
    val stats: WidgetStats,
    val lastSyncedAt: Long?
)

private data class StatEntry(val label: String, val value: Int)

@Composable
private fun StatsContent(state: StatsWidgetState, palette: WidgetPalette) {
    val context = LocalContext.current
    val size = LocalSize.current
    val padding = when {
        size.height < 100.dp -> 10.dp
        size.height < 160.dp -> 12.dp
        else -> 16.dp
    }

    WidgetRoot(
        entry = WidgetEntry.MainTab(WidgetEntry.DEST_HOME),
        palette = palette,
        padding = padding
    ) {
        if (state.stats.totalAnimes == 0) {
            WidgetEmptyState(
                text = context.getString(R.string.widget_no_anime),
                palette = palette,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
            return@WidgetRoot
        }

        val stats = state.stats
        val wide = size.width >= 180.dp
        val primary = listOf(
            StatEntry(context.getString(R.string.widget_stat_watching), stats.watching),
            StatEntry(context.getString(R.string.widget_stat_today), stats.todayUpdates),
            StatEntry(context.getString(R.string.widget_stat_pending), stats.pendingUpdates)
        )
        val secondary = buildSecondaryEntries(stats, context, size.height)
        val showFooter = state.lastSyncedAt != null && size.height >= 160.dp

        // 极端矮的 2x1：只保留在追数量
        if (!wide && size.height < 80.dp) {
            MiniStats(primary.first(), palette)
            return@WidgetRoot
        }

        val compactHeader = !wide || size.height < 160.dp
        val showHeader = size.height >= 100.dp
        if (showHeader) {
            WidgetHeader(
                title = context.getString(R.string.widget_stats_title),
                subtitle = null,
                badge = null,
                palette = palette,
                compact = compactHeader
            )
            WidgetDivider(palette, compact = !wide)
        }
        val headerH = if (showHeader) (if (compactHeader) 17.dp else 21.dp) else 0.dp
        val dividerH = if (showHeader) (if (!wide) 14.dp else 21.dp) else 0.dp
        val reservedTop = headerH + dividerH
        if (wide) {
            WideStatsContent(
                primary = primary,
                secondary = secondary,
                palette = palette,
                size = size,
                padding = padding,
                reservedTop = reservedTop,
                showFooter = showFooter
            )
        } else {
            VerticalStatsContent(
                entries = primary + secondary,
                palette = palette,
                size = size,
                padding = padding,
                reservedTop = reservedTop,
                showFooter = showFooter
            )
        }
        if (showFooter) {
            UpdatedAtFooter(palette, state.lastSyncedAt)
        }
    }
}

private fun buildSecondaryEntries(
    stats: WidgetStats,
    context: Context,
    height: Dp
): List<StatEntry> {
    val entries = mutableListOf<StatEntry>()
    if (height >= 160.dp) {
        entries += StatEntry(context.getString(R.string.widget_stat_completed), stats.completed)
    }
    if (height >= 220.dp) {
        entries += StatEntry(context.getString(R.string.widget_stat_episodes), stats.totalWatchedEpisodes)
    }
    if (height >= 300.dp) {
        entries += StatEntry(context.getString(R.string.widget_stat_total), stats.totalAnimes)
    }
    if (height >= 380.dp) {
        entries += StatEntry(context.getString(R.string.widget_stat_planned), stats.planned)
    }
    if (height >= 440.dp) {
        entries += StatEntry(context.getString(R.string.widget_stat_dropped), stats.dropped)
    }
    return entries
}

@Composable
private fun MiniStats(entry: StatEntry, palette: WidgetPalette) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.label,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(palette.onSurfaceVariant),
                fontSize = 12.sp
            )
        )
        Text(
            text = entry.value.toString(),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(palette.primary),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun ColumnScope.VerticalStatsContent(
    entries: List<StatEntry>,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.DpSize,
    padding: Dp,
    reservedTop: Dp,
    showFooter: Boolean
) {
    if (entries.isEmpty()) return
    val footerH = if (showFooter) 17.dp else 0.dp
    val available = size.height - padding * 2 - reservedTop - footerH
    val dividers = (entries.size - 1).coerceAtLeast(0)
    val rowHeight = ((available.value - dividers) / entries.size).dp.coerceAtLeast(18.dp)
    // 行与分割线合并为一个子视图：Glance 每个 ViewGroup 只保留前 10 个子视图，
    // 分割线作为独立子视图会把后面的指标挤掉
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            val showDivider = index < entries.lastIndex
            MetricLine(
                entry = entry,
                palette = palette,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(rowHeight + if (showDivider) 1.dp else 0.dp),
                valueSize = when {
                    rowHeight >= 44.dp -> 20
                    rowHeight >= 32.dp -> 17
                    rowHeight >= 24.dp -> 15
                    else -> 13
                },
                labelSize = when {
                    rowHeight >= 44.dp -> 14
                    rowHeight >= 32.dp -> 12
                    rowHeight >= 24.dp -> 11
                    else -> 10
                },
                showDivider = showDivider
            )
        }
    }
}

@Composable
private fun ColumnScope.WideStatsContent(
    primary: List<StatEntry>,
    secondary: List<StatEntry>,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.DpSize,
    padding: Dp,
    reservedTop: Dp,
    showFooter: Boolean
) {
    val footerH = if (showFooter) 17.dp else 0.dp
    val available = size.height - padding * 2 - reservedTop - footerH

    if (secondary.isEmpty()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(available.coerceAtLeast(30.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            primary.forEach { entry ->
                StatCell(
                    entry = entry,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
        return
    }

    val cellsHeight = (available.value * 0.42f).dp.coerceIn(32.dp, 76.dp)
    val secondaryAvailable = available - cellsHeight - 1.dp
    val dividers = (secondary.size - 1).coerceAtLeast(0)
    val rowHeight = ((secondaryAvailable.value - dividers) / secondary.size).dp.coerceAtLeast(18.dp)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(cellsHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            primary.forEach { entry ->
                StatCell(
                    entry = entry,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
        StatsRowDivider(palette)
        secondary.forEachIndexed { index, entry ->
            val showDivider = index < secondary.lastIndex
            MetricLine(
                entry = entry,
                palette = palette,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(rowHeight + if (showDivider) 1.dp else 0.dp),
                valueSize = when {
                    rowHeight >= 44.dp -> 20
                    rowHeight >= 32.dp -> 17
                    rowHeight >= 24.dp -> 15
                    else -> 13
                },
                labelSize = when {
                    rowHeight >= 44.dp -> 14
                    rowHeight >= 32.dp -> 12
                    rowHeight >= 24.dp -> 11
                    else -> 10
                },
                showDivider = showDivider
            )
        }
    }
}

@Composable
private fun StatsRowDivider(palette: WidgetPalette) {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ColorProvider(palette.outline))
    )
}

@Composable
private fun StatCell(
    entry: StatEntry,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = entry.value.toString(),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(palette.primary),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = entry.label,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(palette.onSurfaceVariant),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun MetricLine(
    entry: StatEntry,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    valueSize: Int = 15,
    labelSize: Int = 12,
    showDivider: Boolean = false
) {
    Column(modifier = modifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(5.dp)
                    .cornerRadius(2.5.dp)
                    .background(ColorProvider(palette.primary)),
                content = { }
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = entry.label,
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = ColorProvider(palette.onSurfaceVariant),
                    fontSize = labelSize.sp
                )
            )
            Text(
                text = entry.value.toString(),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(palette.onSurface),
                    fontSize = valueSize.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        if (showDivider) {
            StatsRowDivider(palette)
        }
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()

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
