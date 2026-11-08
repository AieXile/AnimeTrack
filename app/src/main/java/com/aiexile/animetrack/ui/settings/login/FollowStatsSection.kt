package com.aiexile.animetrack.ui.settings.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.components.SquircleShape

/**
 * 追番统计栏：单容器四分栏（总追番/在看/计划/已看完）。
 */
@Composable
internal fun FollowStatsSection(animeRepository: AnimeRepository) {
    // 追番统计栏：单容器四分栏（总追番/在看/计划/已看完）
    // 与原实现一致：Flow 用 remember 持有，避免每次重组重新订阅数据库
    val statusCountsFlow = remember(animeRepository) { animeRepository.getStatusCounts() }
    val statusCounts by statusCountsFlow
        .collectAsStateWithLifecycle(initialValue = emptyList())
    Spacer(modifier = Modifier.height(24.dp))
    Surface(
        shape = SquircleShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        val countsMap = statusCounts.associate { it.status to it.count }
        val watchingCount = countsMap[AnimeStatus.WATCHING] ?: 0
        val plannedCount = countsMap[AnimeStatus.PLANNED] ?: 0
        val completedCount = countsMap[AnimeStatus.COMPLETED] ?: 0
        val totalCount = watchingCount + plannedCount + completedCount +
            (countsMap[AnimeStatus.DROPPED] ?: 0)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(count = totalCount, label = stringResource(R.string.user_login_stat_total), isPrimary = false, modifier = Modifier.weight(1f))
            StatDivider()
            StatItem(count = watchingCount, label = stringResource(R.string.user_login_stat_watching), isPrimary = true, modifier = Modifier.weight(1f))
            StatDivider()
            StatItem(count = plannedCount, label = stringResource(R.string.user_login_stat_planned), isPrimary = false, modifier = Modifier.weight(1f))
            StatDivider()
            StatItem(count = completedCount, label = stringResource(R.string.user_login_stat_completed), isPrimary = false, modifier = Modifier.weight(1f))
        }
    }
}

/** 追番统计栏的单项（数字 + 标签，在看项用主题色强调） */
@Composable
private fun StatItem(
    count: Int,
    label: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 追番统计栏的分隔细线 */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .size(width = 0.5.dp, height = 28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
