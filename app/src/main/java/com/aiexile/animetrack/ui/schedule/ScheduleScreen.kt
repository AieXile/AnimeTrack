package com.aiexile.animetrack.ui.schedule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.settings.SettingsGroup
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.util.formatAirDate
import com.aiexile.animetrack.util.resolveCoverModel
import com.aiexile.animetrack.util.coverMemoryCacheKey
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
private fun weekdayLabels(): List<String> = listOf(
    "?",
    stringResource(R.string.schedule_weekday_1),
    stringResource(R.string.schedule_weekday_2),
    stringResource(R.string.schedule_weekday_3),
    stringResource(R.string.schedule_weekday_4),
    stringResource(R.string.schedule_weekday_5),
    stringResource(R.string.schedule_weekday_6),
    stringResource(R.string.schedule_weekday_7)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    onAnimeClick: (Int) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory()),
    settingsRepository: SettingsRepository? = null
) {
    val groupedAnimes by viewModel.groupedAnimes.collectAsState()
    val selectedWeekday by viewModel.selectedWeekday.collectAsState()
    val todayAnimes by viewModel.todayAnimes.collectAsState()
    val tomorrowAnimes by viewModel.tomorrowAnimes.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val showCalendarButton by (settingsRepository?.showCalendarButton?.collectAsState(true) ?: mutableStateOf(true))

    val labels = weekdayLabels()
    val weekdayLabelFormat = stringResource(R.string.schedule_weekday_label_format)

    var showScheduleSheet by remember { mutableStateOf(false) }
    val scheduleSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.schedule_board_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.schedule_weekly_overview),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                },
                actions = {
                    if (showCalendarButton) {
                        IconButton(onClick = { showScheduleSheet = true }) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = stringResource(R.string.schedule_preview),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
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

    if (showScheduleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScheduleSheet = false },
            sheetState = scheduleSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ScheduleSheetContent(
                todayAnimes = todayAnimes,
                tomorrowAnimes = tomorrowAnimes,
                todayWeekdayLabel = String.format(weekdayLabelFormat, labels[viewModel.currentTodayWeekday]),
                tomorrowWeekdayLabel = String.format(weekdayLabelFormat, labels[(viewModel.currentTodayWeekday % 7) + 1])
            )
        }
    }
}

@Composable
private fun ScheduleSheetContent(
    todayAnimes: List<Anime>,
    tomorrowAnimes: List<Anime>,
    todayWeekdayLabel: String,
    tomorrowWeekdayLabel: String
) {
    val labels = weekdayLabels()
    val weeklyUpdateFormat = stringResource(R.string.schedule_weekly_update_format)
    val airDateFormat = stringResource(R.string.schedule_air_date_format)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsGroup(title = stringResource(R.string.schedule_today_updates), subtitle = todayWeekdayLabel) {
                if (todayAnimes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_no_updates),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        todayAnimes.forEach { anime ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = anime.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    anime.airDate?.let { date ->
                                        Text(
                                            text = String.format(airDateFormat, formatAirDate(date)),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    anime.airWeekday?.let { weekday ->
                                        Text(
                                            text = String.format(weeklyUpdateFormat, labels[weekday]),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.schedule_tomorrow_updates), subtitle = tomorrowWeekdayLabel) {
                if (tomorrowAnimes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_no_updates),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tomorrowAnimes.forEach { anime ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = anime.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    anime.airDate?.let { date ->
                                        Text(
                                            text = String.format(airDateFormat, formatAirDate(date)),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    anime.airWeekday?.let { weekday ->
                                        Text(
                                            text = String.format(weeklyUpdateFormat, labels[weekday]),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun WeekdaySelector(
    selectedWeekday: Int,
    onWeekdaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val labels = weekdayLabels()

    Box(
        modifier = modifier
            .clip(SquircleShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedWeekday

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onWeekdaySelected(index) }
                        .padding(vertical = 4.dp)
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
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> isPressed = false
            }
        }
    }
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
            .clip(SquircleShape(12.dp))
            .shadow(elevation = 2.dp, shape = SquircleShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onClick() }
    ) {
        if (anime.coverUrl != null) {
            val coverUrl = anime.coverUrl
            val context = LocalContext.current
            val coverRequest = remember(coverUrl) {
                ImageRequest.Builder(context)
                    .data(resolveCoverModel(coverUrl))
                    .memoryCacheKey(coverMemoryCacheKey(coverUrl))
                    .placeholderMemoryCacheKey(coverMemoryCacheKey(coverUrl))
                    .build()
            }
            AsyncImage(
                model = coverRequest,
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
    val labels = weekdayLabels()
    val noUpdateFormat = stringResource(R.string.schedule_no_update_format)

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
                text = if (weekday == 0) stringResource(R.string.schedule_no_uncategorized) else String.format(noUpdateFormat, labels[weekday]),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = if (weekday == 0) stringResource(R.string.schedule_all_categorized) else stringResource(R.string.schedule_no_update_today),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                textAlign = TextAlign.Center
            )
        }
    }
}
