package com.aiexile.animetrack.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.BangumiCalendarSubject
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.util.coverMemoryCacheKey
import com.aiexile.animetrack.util.formatAirDateDisplay
import com.aiexile.animetrack.util.resolveCoverModel

private val CoverShape = SquircleShape(12.dp)

/**
 * 未入库条目的预览弹层：看基本信息，一键添加为「计划观看」。
 *
 * 未入库条目没有本地详情页（详情页强绑定 Room 条目），所以这里承担「看详情」的职责；
 * 已添加时提供「查看详情」跳转到正式详情页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBroadcastPreviewSheet(
    subject: BangumiCalendarSubject,
    isAdded: Boolean,
    isAdding: Boolean,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverThumbnail(subject = subject)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subject.nameCn.isNullOrBlank() && subject.nameCn != subject.name) {
                        Text(
                            text = subject.name,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val airDate = formatAirDateDisplay(subject.airDate)
                    if (airDate != null) {
                        Text(
                            text = stringResource(R.string.schedule_air_date_format, airDate),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    MetaLine(subject = subject)
                }
            }

            val summary = subject.summary?.trim()
            if (!summary.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            if (isAdded) {
                Button(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.daily_broadcast_open_detail))
                }
            } else {
                Button(
                    onClick = onAdd,
                    enabled = !isAdding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.daily_broadcast_add))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    uriHandler.openUri(subject.url ?: "https://bgm.tv/subject/${subject.id}")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.daily_broadcast_open_bangumi))
            }
        }
    }
}

@Composable
private fun CoverThumbnail(subject: BangumiCalendarSubject) {
    Box(
        modifier = Modifier
            .width(92.dp)
            .height(122.dp)
            .clip(CoverShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        val coverUrl = subject.coverUrl
        if (coverUrl != null) {
            val context = LocalContext.current
            val request = remember(coverUrl) {
                ImageRequest.Builder(context)
                    .data(resolveCoverModel(coverUrl))
                    .memoryCacheKey(coverMemoryCacheKey(coverUrl))
                    .placeholderMemoryCacheKey(coverMemoryCacheKey(coverUrl))
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = subject.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Text(
                text = subject.displayName.take(2),
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** 评分 · 话数 · 放送星期，用「·」串成一行；缺失的字段自动跳过 */
@Composable
private fun MetaLine(subject: BangumiCalendarSubject) {
    val scoreText = subject.score?.let { String.format("%.1f", it) }
    val episodeText = subject.episodeCount?.let {
        stringResource(R.string.daily_broadcast_episodes_format, it)
    }
    val weekdayLine = weekdayText(subject.airWeekday)?.let {
        stringResource(R.string.schedule_weekly_update_format, it)
    }
    val parts = listOfNotNull(scoreText, episodeText, weekdayLine)
    if (parts.isEmpty()) return

    Text(
        text = parts.joinToString(" · "),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun weekdayText(weekday: Int?): String? = when (weekday) {
    1 -> stringResource(R.string.schedule_weekday_1)
    2 -> stringResource(R.string.schedule_weekday_2)
    3 -> stringResource(R.string.schedule_weekday_3)
    4 -> stringResource(R.string.schedule_weekday_4)
    5 -> stringResource(R.string.schedule_weekday_5)
    6 -> stringResource(R.string.schedule_weekday_6)
    7 -> stringResource(R.string.schedule_weekday_7)
    else -> null
}
