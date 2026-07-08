package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.data.StatsPeriod
import com.aiexile.animetrack.data.UsageStats
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.launch
import kotlin.math.ceil

@Composable
fun UsageStatsDialog(
    onDismiss: () -> Unit
) {
    val statsRepository = remember { AppContainer.getUsageStatsRepository() }
    val scope = rememberCoroutineScope()
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.DAY) }
    val stats by statsRepository.getStats(selectedPeriod).collectAsState(initial = UsageStats())
    val totalAnimeCount by remember {
        AppContainer.getAnimeDatabase().animeDao().getAllAnimes()
    }.collectAsState(initial = emptyList())

    // 打开 Dialog 时刷新一次当前会话的使用时长
    LaunchedEffect(Unit) {
        scope.launch {
            val startTime = AppContainer.sessionStartTime
            if (startTime > 0) {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                if (elapsedSeconds >= 5) {
                    statsRepository.addUsageSeconds(elapsedSeconds)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "使用统计",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Tab 切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsPeriodTab(
                        label = "今日",
                        selected = selectedPeriod == StatsPeriod.DAY,
                        onClick = { selectedPeriod = StatsPeriod.DAY },
                        modifier = Modifier.weight(1f)
                    )
                    StatsPeriodTab(
                        label = "本月",
                        selected = selectedPeriod == StatsPeriod.MONTH,
                        onClick = { selectedPeriod = StatsPeriod.MONTH },
                        modifier = Modifier.weight(1f)
                    )
                    StatsPeriodTab(
                        label = "本年",
                        selected = selectedPeriod == StatsPeriod.YEAR,
                        onClick = { selectedPeriod = StatsPeriod.YEAR },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 统计项
                AnimatedContent(
                    targetState = selectedPeriod,
                    transitionSpec = {
                        val order = listOf(StatsPeriod.DAY, StatsPeriod.MONTH, StatsPeriod.YEAR)
                        val direction = if (order.indexOf(targetState) > order.indexOf(initialState)) 1 else -1
                        (slideInHorizontally { w -> direction * w / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> -direction * w / 4 } + fadeOut())
                    },
                    label = "statsTransition"
                ) { _ ->
                    Column {
                        StatsItem(
                            label = "打开次数",
                            value = "${stats.openCount} 次"
                        )
                        StatsItem(
                            label = "使用时长",
                            value = if (stats.usageSeconds > 0) "${ceil(stats.usageSeconds / 60.0).toInt()} 分钟" else "0 分钟"
                        )
                        StatsItem(
                            label = "添加番剧",
                            value = "${stats.addedAnime} 部"
                        )
                        StatsItem(
                            label = "完结番剧",
                            value = "${stats.completedAnime} 部"
                        )
                        StatsItem(
                            label = "总番剧数",
                            value = "${totalAnimeCount.size} 部"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun StatsPeriodTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val selectedColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF1A1A1A)
    val unselectedColor = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
    val selectedTextColor = if (isDark) Color.Black else Color.White
    val unselectedTextColor = if (isDark) Color(0xFFBDBDBD) else Color(0xFF666666)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) selectedColor else unselectedColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) selectedTextColor else unselectedTextColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

@Composable
private fun StatsItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
