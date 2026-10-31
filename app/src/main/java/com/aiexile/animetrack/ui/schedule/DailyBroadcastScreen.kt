package com.aiexile.animetrack.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.network.BangumiCalendarDay
import com.aiexile.animetrack.data.network.BangumiCalendarSubject
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** 每日放送看板的列宽：手机 96dp（约可见 3 列半），600dp 以上放宽到 120dp（平板 7 列正好铺满） */
private val CompactColumnWidth = 96.dp
private val ExpandedColumnWidth = 120.dp
private val ColumnSpacing = 6.dp
private val RowSpacing = 6.dp
private val BoardHorizontalPadding = 16.dp
private val HeaderHeight = 52.dp
private val HeaderCornerRadius = 12.dp

/** 色带与首行卡片之间的留白 */
private val HeaderContentGap = 8.dp

// 色带四角全圆，悬在列表上方；列表从色带下方留出一段空隙开始
private val HeaderShape = SquircleShape(HeaderCornerRadius)

/**
 * Bangumi 每日放送看板。
 *
 * 7 列（周一 → 周日）横向可滚动，表头按星期配色，单列宽度固定，平板宽屏下一屏可见整周。
 * 点击未入库条目打开预览弹层，已入库条目直接进入详情页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBroadcastScreen(
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    viewModel: DailyBroadcastViewModel = viewModel(factory = DailyBroadcastViewModel.Factory())
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var previewSubject by remember { mutableStateOf<BangumiCalendarSubject?>(null) }

    val addedToast = stringResource(R.string.daily_broadcast_added_toast)
    val duplicateToast = stringResource(R.string.daily_broadcast_duplicate_toast)
    val failedToast = stringResource(R.string.daily_broadcast_add_failed_toast)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DailyBroadcastEvent.Added -> {
                    previewSubject = null
                    snackbarHostState.showSnackbar(addedToast)
                }
                is DailyBroadcastEvent.Duplicate -> snackbarHostState.showSnackbar(duplicateToast)
                is DailyBroadcastEvent.Failed -> snackbarHostState.showSnackbar(failedToast)
            }
        }
    }

    val todayCount = state.days.getOrNull(viewModel.currentWeekday - 1)?.items?.size ?: 0
    val subtitle = stringResource(
        R.string.daily_broadcast_subtitle_format,
        state.totalCount,
        todayCount
    )
    val staleSuffix = state.updatedAt?.let { fetchedAt ->
        val date = Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val dateText = stringResource(
            R.string.daily_broadcast_cache_date_format,
            date.monthValue,
            date.dayOfMonth
        )
        stringResource(R.string.daily_broadcast_stale_format, dateText)
    }

    AppScaffold(
        title = stringResource(R.string.daily_broadcast_title),
        onBack = onBack,
        titleContent = {
            Column {
                Text(
                    text = stringResource(R.string.daily_broadcast_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (state.days.isNotEmpty()) {
                    Text(
                        text = if (state.stale && staleSuffix != null) "$subtitle · $staleSuffix" else subtitle,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = { viewModel.load(forceRefresh = true) },
                enabled = !state.isRefreshing
            ) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.SYNC),
                        contentDescription = stringResource(R.string.daily_broadcast_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { chrome ->
        // 顶/底内边距交给滚动容器（contentPadding），内容才会滚到顶栏下面；
        // 玻璃档才有可模糊的内容，标准档下这两个值为 0（内边距已由 chrome.layout 施加）
        val topInset = chrome.content.calculateTopPadding()
        val bottomInset = chrome.content.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(chrome.layout)
        ) {
            when {
                state.isLoading -> LoadingState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomInset)
                )
                state.error != null && state.days.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(forceRefresh = true) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomInset)
                )
                state.days.isEmpty() || state.totalCount == 0 -> EmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomInset)
                )
                else -> WeeklyBoard(
                    days = state.days,
                    todayWeekday = viewModel.currentWeekday,
                    addedAnimeIdsByBangumi = state.addedAnimeIdsByBangumi,
                    addingBangumiIds = state.addingBangumiIds,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    onSubjectClick = { subject ->
                        val localId = state.animeIdOf(subject)
                        if (localId != null) onOpenDetail(localId) else previewSubject = subject
                    }
                )
            }
        }
    }

    previewSubject?.let { subject ->
        DailyBroadcastPreviewSheet(
            subject = subject,
            isAdded = state.animeIdOf(subject) != null,
            isAdding = subject.id in state.addingBangumiIds,
            onDismiss = { previewSubject = null },
            onAdd = { viewModel.addToLibrary(subject) },
            onOpenDetail = {
                val localId = state.animeIdOf(subject)
                previewSubject = null
                if (localId != null) onOpenDetail(localId)
            }
        )
    }
}

@Composable
private fun WeeklyBoard(
    days: List<BangumiCalendarDay>,
    todayWeekday: Int,
    addedAnimeIdsByBangumi: Map<Int, Int>,
    addingBangumiIds: Set<Int>,
    topInset: Dp,
    bottomInset: Dp,
    onSubjectClick: (BangumiCalendarSubject) -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var containerWidth by remember { mutableStateOf(configuration.screenWidthDp.dp) }
    val columnWidth = if (containerWidth >= 600.dp) ExpandedColumnWidth else CompactColumnWidth
    val step = columnWidth + ColumnSpacing
    val boardWidth = step * 7 - ColumnSpacing
    val rowCount = remember(days) { days.maxOfOrNull { it.items.size } ?: 0 }
    val scrollState = rememberScrollState()
    var didInitialScroll by remember { mutableStateOf(false) }

    // 首次拿到数据后横向定位到今天所在列
    LaunchedEffect(rowCount, columnWidth) {
        if (rowCount > 0 && !didInitialScroll) {
            val offset = with(density) {
                (BoardHorizontalPadding + step * (todayWeekday - 1)).toPx()
            }
            scrollState.scrollTo(offset.roundToInt().coerceAtLeast(0))
            didInitialScroll = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                // 实测容器宽度：pane 压缩/展开时列宽随之自适应
                containerWidth = with(density) { coordinates.size.width.toDp() }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState)
                .padding(horizontal = BoardHorizontalPadding)
        ) {
            Box(
                modifier = Modifier
                    .width(boardWidth)
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topInset + HeaderHeight + HeaderContentGap,
                        bottom = bottomInset + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(RowSpacing)
                ) {
                    items(rowCount) { rowIndex ->
                        Row(horizontalArrangement = Arrangement.spacedBy(ColumnSpacing)) {
                            days.forEach { day ->
                                Box(modifier = Modifier.width(columnWidth)) {
                                    val subject = day.items.getOrNull(rowIndex)
                                    if (subject != null) {
                                        DailyBroadcastCard(
                                            subject = subject,
                                            isAdded = addedAnimeIdsByBangumi.containsKey(subject.id),
                                            isAdding = subject.id in addingBangumiIds,
                                            onClick = { onSubjectClick(subject) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 星期色带画在列表之后：浮在卡片上方，卡片从底下滚过，
                // 阴影用来表达层级，同时消除色带与首行之间的白缝
                WeekdayHeaderRow(
                    days = days,
                    todayWeekday = todayWeekday,
                    columnWidth = columnWidth,
                    boardWidth = boardWidth,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = topInset)
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow(
    days: List<BangumiCalendarDay>,
    todayWeekday: Int,
    columnWidth: Dp,
    boardWidth: Dp,
    modifier: Modifier = Modifier
) {
    val dark = isAppDarkTheme()
    val step = columnWidth + ColumnSpacing

    // 连续色带：各天色块首尾相接（不再留横缝），只在外层裁出圆角，
    // 段宽取含列间距的步长，使色块分界与下方卡片列对齐
    Row(
        modifier = modifier
            .width(boardWidth)
            .height(HeaderHeight)
            .clip(HeaderShape)
    ) {
        days.forEachIndexed { index, day ->
            val weekday = index + 1
            val color = weekdayColor(weekday).let { if (dark) it.copy(alpha = 0.82f) else it }
            val isToday = weekday == todayWeekday

            Box(
                modifier = Modifier
                    .width(step)
                    .fillMaxHeight()
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(
                            stringResource(R.string.schedule_weekday_label_format),
                            weekdayText(weekday)
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(
                            R.string.daily_broadcast_day_count_format,
                            day.items.size
                        ),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // 今天：底部一条白色短横作为指示，不用外框/内框，
                // 避免在连续色带上把当天色块圈成一个突兀的独立块
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .width(22.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

/** 7 个固定色相，与 Bangumi 每日放送的配色对应；深色主题下由调用方压低透明度 */
private fun weekdayColor(weekday: Int): Color = when (weekday) {
    1 -> Color(0xFFF4511E)
    2 -> Color(0xFFFB8C00)
    3 -> Color(0xFFF9A825)
    4 -> Color(0xFF43A047)
    5 -> Color(0xFF00ACC1)
    6 -> Color(0xFF1E88E5)
    else -> Color(0xFFE53935)
}

@Composable
private fun weekdayText(weekday: Int): String = when (weekday) {
    1 -> stringResource(R.string.schedule_weekday_1)
    2 -> stringResource(R.string.schedule_weekday_2)
    3 -> stringResource(R.string.schedule_weekday_3)
    4 -> stringResource(R.string.schedule_weekday_4)
    5 -> stringResource(R.string.schedule_weekday_5)
    6 -> stringResource(R.string.schedule_weekday_6)
    else -> stringResource(R.string.schedule_weekday_7)
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.ERROR),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = stringResource(R.string.daily_broadcast_error_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.daily_broadcast_retry))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.EVENT_BUSY),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = stringResource(R.string.daily_broadcast_empty),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
