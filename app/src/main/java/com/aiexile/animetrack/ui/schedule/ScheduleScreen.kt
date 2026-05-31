package com.aiexile.animetrack.ui.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding

private val weekdayLabels = listOf("?", "一", "二", "三", "四", "五", "六", "日")

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    onAnimeClick: (Int) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory())
) {
    val groupedAnimes by viewModel.groupedAnimes.collectAsState()
    val selectedWeekday by viewModel.selectedWeekday.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = selectedWeekday,
        pageCount = { 8 }
    )

    LaunchedEffect(selectedWeekday) {
        if (pagerState.currentPage != selectedWeekday) {
            pagerState.scrollToPage(selectedWeekday)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { settledPage ->
                if (!pagerState.isScrollInProgress) {
                    if (settledPage != viewModel.selectedWeekday.value) {
                        viewModel.selectWeekday(settledPage)
                    }
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
                        text = "追番看板",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "每周更新一览",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            WeekdaySelector(
                selectedWeekday = pagerState.currentPage,
                onWeekdaySelected = { weekday ->
                    viewModel.selectWeekday(weekday)
                    coroutineScope.launch {
                        pagerState.scrollToPage(weekday)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val animes = groupedAnimes[page].orEmpty()

                if (animes.isEmpty()) {
                    EmptyWeekdayContent(weekday = page)
                } else {
                    AnimeCoverGrid(
                        animes = animes,
                        onClick = { animeId ->
                            onAnimeClick(animeId)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdaySelector(
    selectedWeekday: Int,
    onWeekdaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekdayLabels.forEachIndexed { index, label ->
                val isSelected = index == selectedWeekday

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onWeekdaySelected(index) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeCoverGrid(
    animes: List<Anime>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(animes, key = { it.id }) { anime ->
            CoverCard(anime = anime, onClick = { onClick(anime.id) })
        }
    }
}

@Composable
private fun CoverCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pressScale"
    )

    Box(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .then(
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            try {
                                waitForUpOrCancellation()
                            } finally {
                                isPressed = false
                            }
                        }
                    }
                }
            )
    ) {
        if (anime.coverUrl != null) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val gradientBackground = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = anime.title.take(2),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun EmptyWeekdayContent(
    weekday: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.EventBusy,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                text = if (weekday == 0) "暂无未分类番剧" else "周${weekdayLabels[weekday]}无更新",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = if (weekday == 0) "所有番剧已归类" else "今日无更新，去休息吧",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                textAlign = TextAlign.Center
            )
        }
    }
}
