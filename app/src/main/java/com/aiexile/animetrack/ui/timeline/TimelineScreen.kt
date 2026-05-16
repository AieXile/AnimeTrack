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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets


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
    
    var selectedIndex by remember { mutableIntStateOf(-1) }
    
    val hasWatchingSection = watchingAnimeList.isNotEmpty()
    val hasTimelineData = timelineData.isNotEmpty()
    
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val actualIndex = if (hasWatchingSection) {
            listState.firstVisibleItemIndex - 1
        } else {
            listState.firstVisibleItemIndex
        }
        if (actualIndex >= 0 && actualIndex < timelineData.size) {
            selectedIndex = actualIndex
        }
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 40.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "时间线",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
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
                    months = timelineData.map { "${it.month}月" },
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
                text = "暂无时间线记录",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "添加番剧并设置日期后，这里将显示时间线",
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
            text = "正在观看",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.outlineVariant
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (anime.status != AnimeStatus.COMPLETED) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${anime.watchedEpisodes}/${anime.totalEpisodes}集",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (anime.rating != null) {
                            Text(
                                text = "★ ${anime.rating}",
                                fontSize = 13.sp,
                                color = LocalAnimeColors.current.starFilled
                            )
                        }
                    }
                } else if (anime.rating != null) {
                    Text(
                        text = "★ ${anime.rating}",
                        fontSize = 13.sp,
                        color = LocalAnimeColors.current.starFilled,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            
            Text(
                text = "观看中",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
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
            .padding(start = 8.dp, bottom = 16.dp)
    ) {
        Box(
            modifier = Modifier.width(60.dp)
        ) {
            Text(
                text = entry.dateLabel,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
        
        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.outlineVariant
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (anime.status != AnimeStatus.COMPLETED) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${anime.watchedEpisodes}/${anime.totalEpisodes}集",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (anime.rating != null) {
                            Text(
                                text = "★ ${anime.rating}",
                                fontSize = 13.sp,
                                color = LocalAnimeColors.current.starFilled
                            )
                        }
                    }
                } else if (anime.rating != null) {
                    Text(
                        text = "★ ${anime.rating}",
                        fontSize = 13.sp,
                        color = LocalAnimeColors.current.starFilled,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            
            Text(
                text = anime.status.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = typeColor,
                modifier = Modifier
                    .background(
                        color = typeColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
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
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        months.forEachIndexed { index, month ->
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
