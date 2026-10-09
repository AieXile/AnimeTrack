package com.aiexile.animetrack.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding


@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = viewModel(factory = TimelineViewModel.Factory()),
    showBottomBar: Boolean = true,
    onNavigate: (String) -> Unit = {}
) {
    val timelineData by viewModel.timelineData.collectAsState()
    val watchingAnimeList by viewModel.watchingAnimeList.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val monthFormat = stringResource(R.string.timeline_month_format)
    
    var selectedIndex by remember { mutableIntStateOf(-1) }
    // 标记程序化滚动(点击/拖动索引器触发的滚动),期间抑制被动高亮更新,避免高亮慢半拍
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    val hasWatchingSection = watchingAnimeList.isNotEmpty()
    val hasTimelineData = timelineData.isNotEmpty()

    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(50)
            .collect { firstVisibleIndex ->
                // 程序化滚动进行中:高亮已由点击/拖动立即设置,跳过中间状态的被动更新
                if (isProgrammaticScroll) return@collect
                // 直接读取 watchingAnimeList.isNotEmpty() 而非局部变量 hasWatchingSection，
                // 因为 LaunchedEffect(Unit) 仅在首次组合时启动一次,捕获的 Boolean 局部变量会是陈旧值；
                // 而 watchingAnimeList 是 collectAsState 的 State 代理,每次访问读取最新值。
                val actualIndex = if (watchingAnimeList.isNotEmpty()) {
                    firstVisibleIndex - 1
                } else {
                    firstVisibleIndex
                }
                if (actualIndex >= 0 && actualIndex < timelineData.size) {
                    selectedIndex = actualIndex
                }
            }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.nav_timeline),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = "timeline",
                    onNavigate = onNavigate
                )
            }
        }
    ) { paddingValues ->
        if (!hasWatchingSection && !hasTimelineData) {
            EmptyTimelinePlaceholder(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TimelineList(
                    watchingAnimeList = watchingAnimeList,
                    timelineData = timelineData,
                    listState = listState,
                    modifier = Modifier.weight(1f)
                )
                
                MonthIndexer(
                    months = timelineData.map { String.format(monthFormat, it.month) },
                    hasWatchingSection = hasWatchingSection,
                    currentIndex = selectedIndex,
                    onIndexClick = { index ->
                        // 点击立即高亮,不等滚动动画推进(否则高亮慢半拍)
                        selectedIndex = index
                        isProgrammaticScroll = true
                        scope.launch {
                            try {
                                val targetIndex = if (hasWatchingSection) index + 1 else index
                                listState.animateScrollToItem(targetIndex)
                            } finally {
                                isProgrammaticScroll = false
                            }
                        }
                    },
                    onIndexSeek = { index ->
                        // 拖动定位:立即高亮 + 瞬时滚动(无动画),高频调用时新滚动自动取消旧滚动
                        selectedIndex = index
                        scope.launch {
                            val targetIndex = if (hasWatchingSection) index + 1 else index
                            listState.scrollToItem(targetIndex)
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyTimelinePlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.Icon(
                painter = rememberAppIconPainter(AppIcon.CALENDAR_VIEW_DAY),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = stringResource(R.string.timeline_empty_title),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.timeline_empty_hint),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun TimelineList(
    watchingAnimeList: List<Anime>,
    timelineData: List<TimelineMonth>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        if (watchingAnimeList.isNotEmpty()) {
            item(key = "watching_section") {
                WatchingSection(
                    animeList = watchingAnimeList
                )
            }
        }
        
        items(timelineData, key = { it.yearMonth }) { monthData ->
            TimelineMonthSection(
                monthData = monthData
            )
        }
    }
}

@Composable
private fun WatchingSection(
    animeList: List<Anime>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.timeline_watching),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        // 已知性能问题: forEach 在 LazyColumn 的 item{} 内渲染多个子项,未利用懒加载特性。
        // 未拆分为 LazyColumn.items() 的原因: 拆分会将 title/cards/spacer 分离为独立 LazyColumn item,
        // 破坏 selectedIndex 计算逻辑(该逻辑依赖 watching section 占据恰好 1 个 item 槽位,
        // 见 LaunchedEffect 中 firstVisibleItemIndex - 1 的映射),导致 MonthIndexer 高亮错位。
        // 保持 forEach 以维持视觉与交互行为完全不变。
        animeList.forEach { anime ->
            WatchingAnimeCard(
                anime = anime
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WatchingAnimeCard(
    anime: Anime,
    modifier: Modifier = Modifier
) {
    // 番剧信息（标题/进度/星星）整体用紧凑背景小胶囊包裹，状态用纯文字，避免臃肿。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = SquircleShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 置顶标识：与主界面卡片角标一致的图钉样式
                if (anime.isPinned) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                        contentDescription = stringResource(R.string.common_pin),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = anime.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (anime.status != AnimeStatus.COMPLETED) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.timeline_progress_format, anime.watchedEpisodes, anime.totalEpisodes),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (anime.rating != null) {
                        RatingText(rating = anime.rating)
                    }
                }
            } else if (anime.rating != null) {
                RatingText(
                    rating = anime.rating,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** 星星 + 评分文本：星星以内联内容嵌入，底部压在文字基线上（AboveBaseline），与数字上下严格平齐 */
@Composable
private fun RatingText(
    rating: Float,
    modifier: Modifier = Modifier
) {
    val starColor = LocalAnimeColors.current.starFilled
    val ratingText = buildAnnotatedString {
        appendInlineContent("star", "★")
        // 窄空格（约普通空格一半宽），星星与数字间距适中
        append(" " + rating.toString())
    }
    Text(
        text = ratingText,
        fontSize = 12.sp,
        color = starColor,
        modifier = modifier,
        inlineContent = mapOf(
            "star" to InlineTextContent(
                Placeholder(
                    // 略高于数字大写高度，底部压在基线上保持底部对齐
                    width = 12.sp,
                    height = 12.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                )
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.STAR_SHINE),
                    contentDescription = null,
                    tint = starColor,
                    // 下移使星星相对数字上下对称超出（AboveBaseline 底部贴基线，超出量全在顶部会显偏高）
                    modifier = Modifier.fillMaxSize().offset(y = 1.5.dp)
                )
            }
        )
    )
}

@Composable
private fun TimelineMonthSection(
    monthData: TimelineMonth,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = monthData.yearMonth,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        // 已知性能问题: forEach 在 LazyColumn 的 items{} 内渲染多个子项,未利用懒加载特性。
        // 未拆分为 LazyColumn.items() 的原因: 拆分会将 title/entries/spacer 分离为独立 LazyColumn item,
        // 破坏 selectedIndex 计算逻辑(该逻辑依赖每个月份占据恰好 1 个 item 槽位,
        // 见 LaunchedEffect 中 actualIndex < timelineData.size 的边界判断),
        // 导致 MonthIndexer 高亮错位。保持 forEach 以维持视觉与交互行为完全不变。
        monthData.entries.forEach { entry ->
            TimelineEntryItem(
                entry = entry
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun TimelineEntryItem(
    entry: TimelineEntry,
    modifier: Modifier = Modifier
) {
    val typeColor = when (entry.type) {
        EntryType.FINISHED -> LocalAnimeColors.current.finished
        EntryType.DROPPED -> LocalAnimeColors.current.dropped
        EntryType.WATCHING -> LocalAnimeColors.current.watching
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 日期作为本组标题放在最上方，下方紧跟本日所有番剧小胶囊。
            // 不再左右分列，也不再使用竖线。
            Text(
                text = entry.dateLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            entry.animeList.forEach { anime ->
                TimelineAnimeCard(
                    anime = anime,
                    typeColor = typeColor
                )
            }
        }
    }
}

@Composable
private fun TimelineAnimeCard(
    anime: Anime,
    typeColor: Color,
    modifier: Modifier = Modifier
) {
    // 番剧名 + 进度 + 星星与右侧观看状态徽章共用同一个卡片背景，徽章嵌入卡片右端垂直居中。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = SquircleShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anime.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.timeline_progress_format, anime.watchedEpisodes, anime.totalEpisodes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (anime.rating != null) {
                    RatingText(rating = anime.rating)
                }
            }
        }

        Text(
            text = anime.status.displayName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = typeColor,
            modifier = Modifier
                .padding(start = 8.dp)
                .background(
                    color = typeColor.copy(alpha = 0.12f),
                    shape = SquircleShape(10.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun MonthIndexer(
    months: List<String>,
    hasWatchingSection: Boolean,
    currentIndex: Int,
    onIndexClick: (Int) -> Unit,
    onIndexSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 拖动中标记:控制月份气泡显隐
    var isSeeking by remember { mutableStateOf(false) }
    // 内容区高度(px),用于计算每个月份的均匀 slot 高度
    var contentHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .width(32.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = SquircleShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(vertical = 8.dp)
            .onSizeChanged { contentHeightPx = it.height }
            // 按住拖动:根据手指 Y 坐标实时定位月份(Scrubber 式快速导航)
            .pointerInput(months) {
                // 根据手指 Y 坐标换算月份下标(月份均匀分布,按高度比例映射)
                fun indexOfY(y: Float): Int =
                    ((y / size.height) * months.size).toInt().coerceIn(0, months.size - 1)
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (months.isNotEmpty()) {
                            isSeeking = true
                            onIndexSeek(indexOfY(offset.y))
                        }
                    },
                    onDragEnd = { isSeeking = false },
                    onDragCancel = { isSeeking = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        if (months.isNotEmpty()) onIndexSeek(indexOfY(change.position.y))
                    }
                )
            }
    ) {
        if (months.isNotEmpty() && contentHeightPx > 0) {
            val density = LocalDensity.current
            val slotPx = contentHeightPx.toFloat() / months.size
            val slotDp = with(density) { slotPx.toDp() }

            // 高亮下标动画化:高亮浮层从旧位置平滑滚动到新位置,而非逐项闪变
            val animatedIndex by animateFloatAsState(
                targetValue = currentIndex.toFloat(),
                animationSpec = tween(durationMillis = 150),
                label = "indexerIndex"
            )

            // 高亮圆形浮层:绘制在月份文字下层,随 animatedIndex 平滑滚动
            if (currentIndex >= 0) {
                val highlightPx = minOf(slotPx, with(density) { 28.dp.toPx() })
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                0,
                                (animatedIndex * slotPx + (slotPx - highlightPx) / 2f).roundToInt()
                            )
                        }
                        .size(with(density) { highlightPx.toDp() })
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                )
            }

            months.forEachIndexed { index, month ->
                // 浮层滑过一半时切换文字样式,与高亮到达保持同步
                val isCurrent = abs(index - animatedIndex) < 0.5f

                val alpha by animateFloatAsState(
                    targetValue = if (isCurrent) 1f else 0.5f,
                    animationSpec = tween(durationMillis = 150),
                    label = "alpha"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, (index * slotPx).roundToInt()) }
                        .size(width = 32.dp, height = slotDp)
                        .clickable { onIndexClick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = month,
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(alpha)
                    )
                }
            }

            // 拖动时的月份气泡:索引器左侧的白色圆形徽章,垂直居中于当前月份
            AnimatedVisibility(
                visible = isSeeking && currentIndex in months.indices,
                enter = fadeIn(tween(durationMillis = 100)),
                exit = fadeOut(tween(durationMillis = 200)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val centerY = currentIndex * slotPx + slotPx / 2f
                        IntOffset(
                            -44.dp.roundToPx(),   // 36dp 圆 + 8dp 间距
                            (centerY - 18.dp.roundToPx()).roundToInt()
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(elevation = 2.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    // getOrNull 防御:退出动画期间 currentIndex 可能已越界
                    Text(
                        text = months.getOrNull(currentIndex).orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        // 白底固定深色文字,保证明暗主题下均可读
                        color = Color(0xDE000000)
                    )
                }
            }
        }
    }
}
