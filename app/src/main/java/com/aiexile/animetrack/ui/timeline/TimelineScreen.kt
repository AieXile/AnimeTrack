package com.aiexile.animetrack.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding


@OptIn(ExperimentalMaterial3Api::class)
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
    
    val hasWatchingSection = watchingAnimeList.isNotEmpty()
    val hasTimelineData = timelineData.isNotEmpty()
    
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .debounce(50)
            .collect { firstVisibleIndex ->
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
                        scope.launch {
                            val targetIndex = if (hasWatchingSection) index + 1 else index
                            listState.animateScrollToItem(targetIndex)
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
                imageVector = Icons.Outlined.Timeline,
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
            Text(
                text = anime.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

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
                        Text(
                            text = "★ ${anime.rating}",
                            fontSize = 12.sp,
                            color = LocalAnimeColors.current.starFilled
                        )
                    }
                }
            } else if (anime.rating != null) {
                Text(
                    text = "★ ${anime.rating}",
                    fontSize = 12.sp,
                    color = LocalAnimeColors.current.starFilled,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
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
                    Text(
                        text = "★ ${anime.rating}",
                        fontSize = 12.sp,
                        color = LocalAnimeColors.current.starFilled
                    )
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(32.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = SquircleShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        months.forEachIndexed { index, month ->
            // isSelected 为简单直接 Boolean 比较(index == currentIndex),
            // 且 animateFloatAsState 本身仅在 targetValue 真正变化时触发新动画,
            // 无需 derivedStateOf 包裹(index/currentIndex 均非 Snapshot State,派生无收益)。
            val isSelected = index == currentIndex

            val alpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.5f,
                animationSpec = tween(durationMillis = 150),
                label = "alpha"
            )
            
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                    )
                    .clickable { onIndexClick(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = month,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(alpha)
                )
            }
        }
    }
}
