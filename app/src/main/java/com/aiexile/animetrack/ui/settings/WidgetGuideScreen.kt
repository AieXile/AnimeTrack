package com.aiexile.animetrack.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.widget.StatsWidgetReceiver
import com.aiexile.animetrack.ui.widget.TodayAiringWidgetReceiver
import com.aiexile.animetrack.ui.widget.WatchingWidgetReceiver

/**
 * 桌面组件说明页：三种组件的外观预览 + 一键添加。
 *
 * 系统组件选择器能添加，但发现性差；这里提供 requestPinAppWidget 快捷入口，
 * 桌面不支持固定组件时提示用户走“长按桌面 → 小组件”的手动路径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetGuideScreen(onBack: () -> Unit) {
    AppScaffold(
        title = stringResource(R.string.widget_guide_title),
        onBack = onBack
    ) { chrome ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = 16.dp + chrome.content.calculateTopPadding(),
                    bottom = 32.dp + chrome.content.calculateBottomPadding()
                )
            ) {
                item(key = "intro") {
                    Text(
                        text = stringResource(R.string.widget_guide_intro),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "today") {
                    WidgetTypeCard(
                        title = stringResource(R.string.widget_today_title),
                        description = stringResource(R.string.widget_today_desc),
                        provider = TodayAiringWidgetReceiver::class.java,
                        preview = { TodayWidgetPreview() }
                    )
                }
                item(key = "watching") {
                    WidgetTypeCard(
                        title = stringResource(R.string.widget_watching_title),
                        description = stringResource(R.string.widget_watching_desc),
                        provider = WatchingWidgetReceiver::class.java,
                        preview = { WatchingWidgetPreview() }
                    )
                }
                item(key = "stats") {
                    WidgetTypeCard(
                        title = stringResource(R.string.widget_stats_title),
                        description = stringResource(R.string.widget_stats_desc),
                        provider = StatsWidgetReceiver::class.java,
                        preview = { StatsWidgetPreview() }
                    )
                }
                item(key = "auto_sync") {
                    Text(
                        text = stringResource(R.string.widget_auto_sync_note),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetTypeCard(
    title: String,
    description: String,
    provider: Class<*>,
    preview: @Composable () -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(180.dp)
                    .clip(SquircleShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                preview()
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { requestPinWidget(context, provider) },
                shape = SquircleShape(12.dp)
            ) {
                Text(text = stringResource(R.string.widget_add_to_home))
            }
        }
    }
}

@Composable
private fun TodayWidgetPreview() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(SquircleShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.widget_today_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            PreviewPill(text = "2 部")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.height(5.dp))
        PreviewAnimeRow(newEpisode = true)
        PreviewAnimeRow(newEpisode = false)
    }
}

@Composable
private fun WatchingWidgetPreview() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(SquircleShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.widget_watching_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            PreviewPill(text = "2 部")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.height(5.dp))
        PreviewWatchingRow(progress = 0.62f, newEpisode = true)
        PreviewWatchingRow(progress = 0.25f, newEpisode = false)
    }
}

@Composable
private fun StatsWidgetPreview() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(SquircleShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.widget_stats_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        val labels = listOf(
            stringResource(R.string.widget_stat_watching),
            stringResource(R.string.widget_stat_today),
            stringResource(R.string.widget_stat_pending)
        )
        val values = listOf("12", "3", "1")
        repeat(3) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(SquircleShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = labels[index],
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = values[index],
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (index < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun PreviewAnimeRow(newEpisode: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewCover(
            colorStart = if (newEpisode) Color(0xFFB7C8E8) else Color(0xFFE2BBA4),
            colorEnd = if (newEpisode) Color(0xFF7189C4) else Color(0xFFC98B67)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewBar(widthFraction = 0.55f, height = 7.dp)
                if (newEpisode) {
                    Spacer(modifier = Modifier.width(5.dp))
                    PreviewPill(text = "新")
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            PreviewBar(widthFraction = 0.3f, height = 5.dp)
        }
    }
}

@Composable
private fun PreviewWatchingRow(progress: Float, newEpisode: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewCover(
            colorStart = if (newEpisode) Color(0xFFB7C8E8) else Color(0xFFC8E2C6),
            colorEnd = if (newEpisode) Color(0xFF7189C4) else Color(0xFF7BB085)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewBar(widthFraction = 0.5f, height = 7.dp)
                if (newEpisode) {
                    Spacer(modifier = Modifier.width(5.dp))
                    PreviewPill(text = "新")
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(SquircleShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(SquircleShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun PreviewCover(colorStart: Color, colorEnd: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(SquircleShape(7.dp))
            .background(Brush.linearGradient(listOf(colorStart, colorEnd)))
    )
}

@Composable
private fun PreviewPill(text: String) {
    Box(
        modifier = Modifier
            .clip(SquircleShape(9.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun PreviewBar(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(SquircleShape(height / 2f))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}


private fun requestPinWidget(context: Context, provider: Class<*>) {
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, R.string.widget_add_unsupported, Toast.LENGTH_LONG).show()
        return
    }
    val component = ComponentName(context, provider)
    if (!manager.requestPinAppWidget(component, null, null)) {
        Toast.makeText(context, R.string.widget_add_unsupported, Toast.LENGTH_LONG).show()
    }
}
