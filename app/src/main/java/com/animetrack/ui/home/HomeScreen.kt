package com.animetrack.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.animetrack.model.Anime
import com.animetrack.model.ThemeMode
import com.animetrack.ui.components.AddAnimeForm
import com.animetrack.ui.components.AnimeCard
import com.animetrack.ui.components.BottomNavigationBar
import com.animetrack.ui.theme.Primary
import com.animetrack.ui.theme.ThemeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory()),
    themeViewModel: ThemeViewModel,
    showBottomBar: Boolean = true,
    onNavigate: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val animeList by viewModel.animeList.collectAsState()
    val themeMode by themeViewModel.themeMode.collectAsState()
    val filteredAnimeList = remember(animeList, uiState.selectedFilter) {
        viewModel.getFilteredAnimeList(animeList, uiState.selectedFilter)
    }
    val scope = rememberCoroutineScope()
    
    val gridState = rememberLazyGridState()
    
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    LaunchedEffect(bottomSheetState.currentValue) {
        if (bottomSheetState.currentValue == androidx.compose.material3.SheetValue.Hidden 
            && uiState.isBottomSheetVisible) {
            viewModel.hideBottomSheet()
        }
    }
    
    LaunchedEffect(uiState.shouldScrollToTop) {
        if (uiState.shouldScrollToTop) {
            delay(100)
            gridState.animateScrollToItem(index = 0)
            viewModel.onScrollCompleted()
        }
    }
    
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress && uiState.selectedAnimeId != null) {
            viewModel.clearSelection()
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelection()
        }
    }
    
    val themeIcon = when (themeMode) {
        ThemeMode.SYSTEM -> Icons.Filled.Settings
        ThemeMode.LIGHT -> Icons.Filled.LightMode
        ThemeMode.DARK -> Icons.Filled.DarkMode
    }
    
    val themeDescription = when (themeMode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "亮色模式"
        ThemeMode.DARK -> "暗色模式"
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "AnimeTrack",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { themeViewModel.cycleThemeMode() }) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = themeDescription
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = "home",
                    onNavigate = onNavigate
                )
            }
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { viewModel.showBottomSheet() },
                containerColor = Primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "添加番剧",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (uiState.selectedAnimeId != null) {
                                viewModel.clearSelection()
                            }
                        }
                    )
                }
        ) {
            FilterBar(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) }
            )
            
            if (filteredAnimeList.isEmpty()) {
                EmptyAnimePlaceholder(
                    modifier = Modifier.weight(1f)
                )
            } else {
                AnimeGrid(
                    animeList = filteredAnimeList,
                    newlyAddedAnimeId = uiState.newlyAddedAnimeId,
                    selectedAnimeId = uiState.selectedAnimeId,
                    onHighlightComplete = { viewModel.onHighlightCompleted() },
                    onAnimeClick = { viewModel.clearSelection() },
                    onAnimeLongPress = { anime -> viewModel.selectAnime(anime.id.toLong()) },
                    onStatusChange = { anime, status -> viewModel.updateAnimeStatus(anime, status) },
                    onDelete = { anime -> viewModel.deleteAnime(anime) },
                    gridState = gridState
                )
            }
        }
        
        if (uiState.isBottomSheetVisible) {
            AddAnimeBottomSheet(
                sheetState = bottomSheetState,
                formState = uiState.formState,
                formError = uiState.formError,
                onFormStateChange = { viewModel.updateFormState(it) },
                onSave = { viewModel.saveAnime() },
                onDismiss = {
                    scope.launch {
                        bottomSheetState.hide()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAnimeBottomSheet(
    sheetState: SheetState,
    formState: com.animetrack.ui.components.AddAnimeFormState,
    formError: String?,
    onFormStateChange: (com.animetrack.ui.components.AddAnimeFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val watchedEpisodesError = if (formState.watchedEpisodes > formState.totalEpisodes) {
        "已看集数不能超过总集数"
    } else {
        null
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp)
            ) {
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    width = 32.dp,
                    height = 4.dp,
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            AddAnimeForm(
                formState = formState,
                onFormStateChange = onFormStateChange,
                watchedEpisodesError = watchedEpisodesError
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary
                    ),
                    enabled = formError == null && formState.title.isNotBlank()
                ) {
                    Text(
                        text = "保存",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    selectedFilter: AnimeFilter,
    onFilterSelected: (AnimeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(AnimeFilter.entries) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.displayName,
                        fontSize = 14.sp
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = Color.White
                ),
                border = null
            )
        }
    }
}

@Composable
private fun EmptyAnimePlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.LiveTv,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .alpha(0.3f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "暂无番剧，点击下方按钮添加",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.alpha(0.6f)
            )
        }
    }
}

@Composable
private fun AnimeGrid(
    animeList: List<Anime>,
    newlyAddedAnimeId: Long?,
    selectedAnimeId: Long?,
    onHighlightComplete: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongPress: (Anime) -> Unit,
    onStatusChange: (Anime, com.animetrack.model.AnimeStatus) -> Unit,
    onDelete: (Anime) -> Unit,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        state = gridState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
    ) {
        items(
            items = animeList,
            key = { it.id }
        ) { anime ->
            val isNew = anime.id.toLong() == newlyAddedAnimeId
            val isSelected = anime.id.toLong() == selectedAnimeId
            
            if (isNew) {
                NewAnimeCardWrapper(
                    anime = anime,
                    onHighlightComplete = onHighlightComplete,
                    onClick = { onAnimeClick(anime) },
                    onLongPress = { onAnimeLongPress(anime) },
                    isSelected = isSelected,
                    onStatusChange = { onStatusChange(anime, it) },
                    onDelete = { onDelete(anime) }
                )
            } else {
                AnimeCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    onLongPress = { onAnimeLongPress(anime) },
                    isSelected = isSelected,
                    onStatusChange = { onStatusChange(anime, it) },
                    onDelete = { onDelete(anime) }
                )
            }
        }
    }
}

@Composable
private fun NewAnimeCardWrapper(
    anime: Anime,
    onHighlightComplete: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    isSelected: Boolean,
    onStatusChange: (com.animetrack.model.AnimeStatus) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hasAnimated by remember { mutableStateOf(false) }
    
    val highlightAlpha by animateFloatAsState(
        targetValue = if (hasAnimated) 0f else 1f,
        animationSpec = tween(durationMillis = 1500),
        finishedListener = {
            onHighlightComplete()
        },
        label = "highlight"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (hasAnimated) 1f else 1.02f,
        animationSpec = tween(durationMillis = 300),
        label = "scale"
    )
    
    LaunchedEffect(Unit) {
        delay(100)
        hasAnimated = true
    }
    
    Box(
        modifier = modifier
    ) {
        AnimeCard(
            anime = anime,
            onClick = onClick,
            onLongPress = onLongPress,
            isSelected = isSelected,
            onStatusChange = onStatusChange,
            onDelete = onDelete,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
        
        if (!isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = Primary.copy(alpha = highlightAlpha * 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }
    }
}
