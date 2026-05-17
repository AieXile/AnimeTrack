package com.aiexile.animetrack.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.components.AddAnimeForm
import com.aiexile.animetrack.ui.components.AnimeCard
import com.aiexile.animetrack.ui.components.BottomNavigationBar
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.ui.theme.ThemeViewModel
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.WindowInsets

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory()),
    showBottomBar: Boolean = true,
    onNavigate: (String) -> Unit = {},
    onNavigateToDetail: (Int, String?) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    themeViewModel: ThemeViewModel? = null,
    fabLocation: FabLocation = FabLocation.BOTTOM_RIGHT,
    isCapsuleNav: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsState()
    val animeList by viewModel.animeList.collectAsState()
    val customGreeting by (themeViewModel?.customGreeting?.collectAsState() ?: mutableStateOf(""))
    val context = LocalContext.current

    LaunchedEffect(uiState.showCompletedToast) {
        if (uiState.showCompletedToast) {
            Toast.makeText(context, "完结撒花！", Toast.LENGTH_SHORT).show()
            viewModel.dismissCompletedToast()
        }
    }

    LaunchedEffect(uiState.showDuplicateToast) {
        if (uiState.showDuplicateToast) {
            Toast.makeText(context, "番剧已存在", Toast.LENGTH_SHORT).show()
            viewModel.dismissDuplicateToast()
        }
    }

    val filteredAnimeList = remember(animeList, uiState.selectedFilter) {
        viewModel.getFilteredAnimeList(animeList, uiState.selectedFilter)
    }
    val scope = rememberCoroutineScope()
    
    val gridState = viewModel.gridState
    
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val greetingText = viewModel.resolveGreetingText(customGreeting)
                    TypingGreeting(
                        greetingText = greetingText,
                        shouldAnimate = viewModel.shouldAnimateGreeting(greetingText),
                        onAnimated = { viewModel.onGreetingAnimated(it) }
                    )
                    if (fabLocation == FabLocation.TOP_BAR) {
                        IconButton(onClick = { viewModel.showBottomSheet() }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加番剧",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
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
            if (fabLocation == FabLocation.BOTTOM_RIGHT) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { viewModel.showBottomSheet() },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .offset(
                            y = if (isCapsuleNav) (-80).dp else 0.dp
                        )
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "添加番剧",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
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
            if (animeList.isEmpty()) {
                EmptyAnimePlaceholder(
                    modifier = Modifier.weight(1f)
                )
            } else {
                AnimeGrid(
                    animeList = filteredAnimeList,
                    hasAnyAnime = true,
                    newlyAddedAnimeId = uiState.newlyAddedAnimeId,
                    selectedAnimeId = uiState.selectedAnimeId,
                    onHighlightComplete = { viewModel.onHighlightCompleted() },
                    onAnimeClick = { anime ->
                        if (uiState.selectedAnimeId != null) {
                            viewModel.clearSelection()
                        } else {
                            onNavigateToDetail(anime.id, anime.coverUrl)
                        }
                    },
                    onAnimeLongPress = { anime -> viewModel.selectAnime(anime.id.toLong()) },
                    onStatusChange = { anime, status -> viewModel.updateAnimeStatus(anime, status) },
                    onDelete = { anime -> viewModel.deleteAnime(anime) },
                    selectedFilter = uiState.selectedFilter,
                    onFilterSelected = { viewModel.setFilter(it) },
                    gridState = gridState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    isCapsuleNav = isCapsuleNav
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
                },
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearching = uiState.isSearching,
                searchError = uiState.searchError,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.searchAnime() },
                onSearchResultSelect = { viewModel.selectSearchResult(it) }
            )
        }
    }
}

@Composable
private fun TypingGreeting(
    greetingText: String,
    shouldAnimate: Boolean,
    onAnimated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var visibleCharCount by remember { mutableIntStateOf(if (shouldAnimate) 0 else greetingText.length) }

    LaunchedEffect(greetingText, shouldAnimate) {
        if (shouldAnimate) {
            delay(500)
            for (i in 1..greetingText.length) {
                visibleCharCount = i
                delay(100)
            }
            onAnimated(greetingText)
        } else {
            visibleCharCount = greetingText.length
        }
    }

    Text(
        text = greetingText.substring(0, visibleCharCount.coerceAtMost(greetingText.length)),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAnimeBottomSheet(
    sheetState: SheetState,
    formState: com.aiexile.animetrack.ui.components.AddAnimeFormState,
    formError: String?,
    onFormStateChange: (com.aiexile.animetrack.ui.components.AddAnimeFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    searchQuery: String = "",
    searchResults: List<com.aiexile.animetrack.data.network.BangumiSubject> = emptyList(),
    isSearching: Boolean = false,
    searchError: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSearchResultSelect: (com.aiexile.animetrack.data.network.BangumiSubject) -> Unit = {},
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp)
            ) {
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    width = 32.dp,
                    height = 4.dp,
                    shape = RoundedCornerShape(2.dp),
                    color = MaterialTheme.colorScheme.outline
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
            Text(
                text = "添加新番剧",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    placeholder = { Text("搜索番剧...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Button(
                    onClick = onSearch,
                    enabled = searchQuery.isNotBlank() && !isSearching,
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isSearching) "..." else "搜索")
                }
            }
            
            if (searchError != null) {
                Text(
                    text = searchError,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            
            if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults.size) { index ->
                        val result = searchResults[index]
                        SearchResultItem(
                            subject = result,
                            onClick = { onSearchResultSelect(result) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
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
                        containerColor = MaterialTheme.colorScheme.primary
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
private fun SearchResultItem(
    subject: com.aiexile.animetrack.data.network.BangumiSubject,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = subject.coverUrl,
            contentDescription = subject.displayName,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subject.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            
            Text(
                text = subject.episodeCountText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (subject.score != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = String.format("%.1f", subject.score),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalAnimeColors.current.starFilled
                )
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = LocalAnimeColors.current.starFilled,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterMenu(
    selectedFilter: AnimeFilter,
    onFilterSelected: (AnimeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "筛选",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        ) {
            AnimeFilter.entries.forEach { filter ->
                val isSelected = filter == selectedFilter

                DropdownMenuItem(
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = filter.displayName,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onFilterSelected(filter)
                        expanded = false
                    },
                    colors = androidx.compose.material3.MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyAnimePlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.LiveTv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "还没有添加任何番剧",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            Text(
                text = "点击右下角的 + 按钮添加",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnimeGrid(
    animeList: List<Anime>,
    hasAnyAnime: Boolean = false,
    newlyAddedAnimeId: Long?,
    selectedAnimeId: Long?,
    onHighlightComplete: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongPress: (Anime) -> Unit,
    onStatusChange: (Anime, com.aiexile.animetrack.model.AnimeStatus) -> Unit,
    onDelete: (Anime) -> Unit,
    selectedFilter: AnimeFilter,
    onFilterSelected: (AnimeFilter) -> Unit,
    gridState: LazyGridState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isCapsuleNav: Boolean = false,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val horizontalPadding = 24.dp
    val cardMinWidth = 140.dp
    val spacing = 12.dp
    
    val availableWidth = screenWidthDp - horizontalPadding
    val calculatedColumns = ((availableWidth + spacing) / (cardMinWidth + spacing)).toInt()
    val columns = minOf(calculatedColumns.coerceAtLeast(3), 4)
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState,
        contentPadding = PaddingValues(bottom = if (isCapsuleNav) 96.dp else 16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilterMenu(
                    selectedFilter = selectedFilter,
                    onFilterSelected = onFilterSelected
                )
            }
        }

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
                    onDelete = { onDelete(anime) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            } else {
                AnimeCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    onLongPress = { onAnimeLongPress(anime) },
                    isSelected = isSelected,
                    onStatusChange = { onStatusChange(anime, it) },
                    onDelete = { onDelete(anime) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }

        if (hasAnyAnime && animeList.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "该分类下暂无番剧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NewAnimeCardWrapper(
    anime: Anime,
    onHighlightComplete: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    isSelected: Boolean,
    onStatusChange: (com.aiexile.animetrack.model.AnimeStatus) -> Unit,
    onDelete: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
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
                        color = MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha * 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
    }
}
