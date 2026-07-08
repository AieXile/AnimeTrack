package com.aiexile.animetrack.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.net.Uri
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.ui.components.EmptyCoverPlaceholder
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.util.formatAirDate
import com.aiexile.animetrack.util.resolveCoverModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CoverAspectRatio = 2f / 3f
private val CardCornerRadius = 16.dp

@Composable
private fun cardContainerColor() = MaterialTheme.colorScheme.surfaceContainerLowest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimeDetailScreen(
    animeId: Int,
    coverUrl: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: AnimeDetailViewModel = viewModel(
        key = "anime_detail_$animeId",
        factory = AnimeDetailViewModel.Factory(animeId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editState = uiState.editState
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val settingsRepository = remember { com.aiexile.animetrack.di.AppContainer.getSettingsRepository() }
    val shareButtonEnabled by settingsRepository.shareButtonEnabled.collectAsState(initial = false)

    val showMatchDialog by viewModel.showMatchDialog.collectAsState()
    val matchSearchQuery by viewModel.matchSearchQuery.collectAsState()
    val matchSearchState by viewModel.matchSearchState.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateEditLocalCoverUri(it.toString()) }
    }

    LaunchedEffect(uiState.showCompletedToast) {
        if (uiState.showCompletedToast) {
            Toast.makeText(context, "完结撒花！", Toast.LENGTH_SHORT).show()
            viewModel.dismissCompletedToast()
        }
    }

    BackHandler(enabled = uiState.coverSearch.isVisible || editState.isEditing) {
        when {
            uiState.coverSearch.isVisible -> viewModel.hideCoverSearch()
            editState.isEditingTitle -> viewModel.setEditingTitle(false)
            editState.isEditing -> {
                if (viewModel.hasUnsavedChanges()) {
                    showDiscardDialog = true
                } else {
                    viewModel.exitEditMode()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (editState.isEditing) {
                                if (viewModel.hasUnsavedChanges()) {
                                    showDiscardDialog = true
                                } else {
                                    viewModel.exitEditMode()
                                }
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (editState.isEditing) {
                            TextButton(onClick = {
                                viewModel.saveEditChanges()
                                focusManager.clearFocus()
                            }) {
                                Text("保存", color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (uiState.anime != null) {
                            val anime = uiState.anime!!
                            IconButton(onClick = { onNavigateToPlayer(animeId) }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "播放",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            val missingBangumi = anime.bangumiId == null
                            val missingTmdb = anime.tmdbId == null
                            if (missingBangumi || missingTmdb) {
                                IconButton(onClick = { viewModel.showMatchDialog() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Link,
                                        contentDescription = "匹配数据源",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            if (shareButtonEnabled) {
                                IconButton(onClick = { showShareDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = "分享",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = { viewModel.enterEditMode() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    uiState.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.error ?: "未知错误",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    uiState.anime != null -> {
                        AnimeDetailContent(
                            anime = uiState.anime!!,
                            isFetchingDetail = uiState.isFetchingDetail,
                            notesText = uiState.notesText,
                            isEditingNotes = uiState.isEditingNotes,
                            airStatusText = uiState.airStatusText,
                            onNotesChange = { viewModel.updateNotes(it) },
                            onSaveNotes = { viewModel.saveNotes() },
                            onEditNotes = { viewModel.setEditingNotes(true) },
                            onUpdateWatchedEpisodes = { viewModel.updateWatchedEpisodes(it) },
                            onAdjustWatchedEpisodes = { viewModel.adjustWatchedEpisodes(it) },
                            onStatusChange = { viewModel.updateStatus(it) },
                            onFinishDateChange = { viewModel.updateFinishDate(it) },
                            editState = editState,
                            onEditCoverSearch = { viewModel.showCoverSearch() },
                            onEditCoverUpload = { imagePickerLauncher.launch("image/*") },
                            onEditCoverSave = {
                                val coverUrl = editState.localCoverUri ?: editState.coverUrl ?: uiState.anime?.coverUrl
                                if (coverUrl != null) {
                                    scope.launch { saveCoverToGallery(context, coverUrl) }
                                } else {
                                    Toast.makeText(context, "无封面可保存", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEditTitleChange = { viewModel.updateEditTitle(it) },
                            onEditTitleStart = { viewModel.setEditingTitle(true) },
                            onEditTitleDone = { viewModel.setEditingTitle(false) },
                            onEditAirWeekdayChange = { viewModel.updateEditAirWeekday(it) },
                            onUpdateEditTotalEpisodes = { viewModel.updateEditTotalEpisodes(it) },
                            onAdjustEditTotalEpisodes = { viewModel.adjustEditTotalEpisodes(it) },
                            onEditSummaryChange = { viewModel.updateEditSummary(it) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
        }

        if (uiState.coverSearch.isVisible) {
            CoverSearchOverlay(
                query = uiState.coverSearch.query,
                results = uiState.coverSearch.results,
                isSearching = uiState.coverSearch.isSearching,
                error = uiState.coverSearch.error,
                searchSource = uiState.coverSearch.source,
                onQueryChange = { viewModel.updateCoverSearchQuery(it) },
                onSearch = { viewModel.searchCover() },
                onSelectResult = { viewModel.selectCoverResult(it) },
                onSourceChange = { viewModel.updateCoverSearchSource(it) },
                onDismiss = { viewModel.hideCoverSearch() }
            )
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("放弃修改") },
                text = { Text("你有未保存的修改，确定要放弃吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        viewModel.exitEditMode()
                    }) { Text("放弃") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) { Text("取消") }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除番剧") },
                text = { Text("确定要删除「${uiState.anime?.title}」吗？此操作不可撤销。") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAnime()
                        onNavigateBack()
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                }
            )
        }

        if (showShareDialog && uiState.anime != null) {
            ShareNotesDialog(
                initialNotes = uiState.anime!!.notes,
                onConfirm = { notes ->
                    showShareDialog = false
                    viewModel.shareAnime(context, notes)
                },
                onDismiss = { showShareDialog = false }
            )
        }

        if (showMatchDialog) {
            val missingSource = viewModel.missingSearchSource
            MatchDialog(
                searchQuery = matchSearchQuery,
                searchState = matchSearchState,
                searchSource = missingSource,
                onQueryChange = { viewModel.updateMatchSearchQuery(it) },
                onSearch = { viewModel.searchForMatch() },
                onSelectResult = { viewModel.selectMatchResult(it) },
                onDismiss = { viewModel.hideMatchDialog() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSearchOverlay(
    query: String,
    results: List<SearchResult>,
    isSearching: Boolean,
    error: String?,
    searchSource: SearchSource,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (SearchResult) -> Unit,
    onSourceChange: (SearchSource) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "搜索番剧...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { sourceExpanded = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = when (searchSource) {
                                        SearchSource.BANGUMI -> "Bangumi"
                                        SearchSource.TMDB -> "TMDB"
                                        SearchSource.ALL -> "全部"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            DropdownMenu(
                                expanded = sourceExpanded,
                                onDismissRequest = { sourceExpanded = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                SearchSource.entries.forEach { source ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = when (source) {
                                                    SearchSource.BANGUMI -> "Bangumi"
                                                    SearchSource.TMDB -> "TMDB"
                                                    SearchSource.ALL -> "全部"
                                                },
                                                fontSize = 14.sp,
                                                fontWeight = if (source == searchSource) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (source == searchSource) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onSourceChange(source)
                                            sourceExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { "${it.source}_${it.sourceId}" }) { result ->
                        CoverSearchResultItem(
                            result = result,
                            onClick = { onSelectResult(result) }
                        )
                    }
                }
            } else if (!isSearching && query.isNotBlank() && error == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "点击搜索查找封面",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverSearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resolveCoverModel(result.coverUrl),
                contentDescription = result.title,
                modifier = Modifier
                    .size(52.dp, 70.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.episodeCountText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val rating = result.rating
                    if (rating != null && rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = LocalAnimeColors.current.starFilled,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                fontSize = 12.sp,
                                color = LocalAnimeColors.current.starFilled
                            )
                        }
                    }
                    Text(
                        text = when (result.source) {
                            SearchSource.BANGUMI -> "Bangumi"
                            SearchSource.TMDB -> "TMDB"
                            SearchSource.ALL -> "全部"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnimeDetailContent(
    anime: Anime,
    isFetchingDetail: Boolean,
    notesText: String,
    isEditingNotes: Boolean,
    airStatusText: String?,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onEditNotes: () -> Unit,
    onUpdateWatchedEpisodes: (Int) -> Unit,
    onAdjustWatchedEpisodes: (Int) -> Unit,
    onStatusChange: (AnimeStatus) -> Unit,
    onFinishDateChange: (Long?) -> Unit,
    editState: EditState = EditState(),
    onEditCoverSearch: () -> Unit = {},
    onEditCoverUpload: () -> Unit = {},
    onEditCoverSave: () -> Unit = {},
    onEditTitleChange: (String) -> Unit = {},
    onEditTitleStart: () -> Unit = {},
    onEditTitleDone: () -> Unit = {},
    onEditAirWeekdayChange: (Int?) -> Unit = {},
    onUpdateEditTotalEpisodes: (Int) -> Unit = {},
    onAdjustEditTotalEpisodes: (Int) -> Unit = {},
    onEditSummaryChange: (String) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(CardCornerRadius),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .aspectRatio(2f / 3f)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "cover_${anime.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(CardCornerRadius))
                ) {
                    val displayCoverUrl = if (editState.isEditing) editState.localCoverUri ?: editState.coverUrl else anime.coverUrl

                    val coverClipShape = RoundedCornerShape(CardCornerRadius)

                    Crossfade(
                        targetState = displayCoverUrl,
                        label = "cover_crossfade"
                    ) { url ->
                        if (url != null) {
                            val context = LocalContext.current
                            val request = remember(url) {
                                ImageRequest.Builder(context)
                                    .data(resolveCoverModel(url))
                                    .crossfade(false)
                                    .build()
                            }
                            AsyncImage(
                                model = request,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            EmptyCoverPlaceholder(
                                shape = coverClipShape
                            )
                        }
                    }

                    if (editState.isEditing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(coverClipShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = onEditCoverSave,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "保存封面",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = onEditCoverSearch,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "搜索封面",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            SmallFloatingActionButton(
                                onClick = onEditCoverUpload,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "上传封面",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(top = 0.dp, bottom = 0.dp)
                        .padding(end = 4.dp)
                ) {
                    if (editState.isEditing) {
                        if (editState.isEditingTitle) {
                            val focusRequester = remember { FocusRequester() }
                            val keyboardController = LocalSoftwareKeyboardController.current

                            BasicTextField(
                                value = editState.title,
                                onValueChange = onEditTitleChange,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                singleLine = false,
                                maxLines = 3,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        onEditTitleDone()
                                        keyboardController?.hide()
                                    }
                                )
                            )

                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = editState.title,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "编辑标题",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onEditTitleStart() },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Text(
                            text = anime.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (anime.rating != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LocalAnimeColors.current.finished.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = LocalAnimeColors.current.starFilled,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", anime.rating),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LocalAnimeColors.current.starFilled
                                )
                            }
                        }
                    }

                    if (!anime.airDate.isNullOrBlank() || !airStatusText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (!anime.airDate.isNullOrBlank()) {
                            Text(
                                text = "放送: ${formatAirDate(anime.airDate)}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (anime.totalEpisodes > 0) {
                            Text(
                                text = "全 ${anime.totalEpisodes} 集",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (anime.currentEpisodes > 0) {
                            Text(
                                text = "连载中 (更新至 ${anime.currentEpisodes} 集)",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "连载中",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (editState.isEditing && !anime.isFinished && anime.status != AnimeStatus.COMPLETED) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "每周",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                WeekdayChips(
                                    selected = editState.airWeekday,
                                    onSelect = onEditAirWeekdayChange
                                )
                            }
                        } else if (!airStatusText.isNullOrBlank()) {
                            Text(
                                text = airStatusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (airStatusText.contains("已完结")) {
                                    LocalAnimeColors.current.finished
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    StatusBadge(status = anime.status)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SummaryCard(
            summary = if (editState.isEditing) editState.summary else anime.summary,
            isFetchingDetail = isFetchingDetail,
            isEditing = editState.isEditing,
            onSummaryChange = onEditSummaryChange
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProgressCard(
            anime = anime,
            onUpdateWatchedEpisodes = onUpdateWatchedEpisodes,
            onAdjustWatchedEpisodes = onAdjustWatchedEpisodes,
            isEditMode = editState.isEditing,
            editTotalEpisodes = editState.totalEpisodes,
            onUpdateEditTotalEpisodes = onUpdateEditTotalEpisodes,
            onAdjustEditTotalEpisodes = onAdjustEditTotalEpisodes
        )

        Spacer(modifier = Modifier.height(12.dp))

        NotesCard(
            notes = anime.notes,
            notesText = notesText,
            isEditingNotes = isEditingNotes,
            onNotesChange = onNotesChange,
            onSaveNotes = onSaveNotes,
            onEditNotes = onEditNotes
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusCard(
            currentStatus = anime.status,
            finishDate = anime.finishDate,
            onStatusChange = onStatusChange,
            onFinishDateChange = onFinishDateChange
        )
    }
}

@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCornerRadius),
        color = cardContainerColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SummaryCard(
    summary: String?,
    isFetchingDetail: Boolean,
    isEditing: Boolean = false,
    onSummaryChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    DetailCard(modifier = modifier) {
        Text(
            text = "简介",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isEditing) {
            OutlinedTextField(
                value = summary ?: "",
                onValueChange = onSummaryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = {
                    Text(
                        text = "添加简介...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        } else if (!summary.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (hasOverflow) isExpanded = !isExpanded
                    }
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!isExpanded) {
                            hasOverflow = result.hasVisualOverflow
                        }
                    }
                )

                if (hasOverflow && !isExpanded) {
                    Text(
                        text = "点击展开",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (isFetchingDetail) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在获取详情...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "暂无简介",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressCard(
    anime: Anime,
    onUpdateWatchedEpisodes: (Int) -> Unit,
    onAdjustWatchedEpisodes: (Int) -> Unit,
    isEditMode: Boolean = false,
    editTotalEpisodes: Int = 0,
    onUpdateEditTotalEpisodes: (Int) -> Unit = {},
    onAdjustEditTotalEpisodes: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(TextFieldValue()) }
    val focusReq = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 编辑模式下使用 editTotalEpisodes，否则使用 watchedEpisodes
    val currentDisplayValue = if (isEditMode) editTotalEpisodes else anime.watchedEpisodes
    val currentMaxValue = if (isEditMode) 9999 else anime.effectiveMaxEpisodes

    var sliderValue by remember(currentDisplayValue) {
        mutableFloatStateOf(currentDisplayValue.toFloat())
    }
    var isDragging by remember { mutableStateOf(false) }

    DetailCard(modifier = modifier) {
        Text(
            text = if (isEditMode) "总集数" else "观看进度",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AcceleratedButton(
                text = "-",
                enabled = currentDisplayValue > 0,
                hapticFeedback = hapticFeedback,
                onTap = { if (isEditMode) onAdjustEditTotalEpisodes(-1) else onAdjustWatchedEpisodes(-1) },
                onAdjust = { step -> if (isEditMode) onAdjustEditTotalEpisodes(-step) else onAdjustWatchedEpisodes(-step) }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                val displayValue = if (isDragging) sliderValue.toInt() else currentDisplayValue

                AnimatedVisibility(
                    visible = isDragging,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "${displayValue} 集",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isEditing) {
                    BasicTextField(
                        value = editValue,
                        onValueChange = { input ->
                            val filtered = input.copy(text = input.text.filter { it.isDigit() })
                            editValue = filtered
                            val num = filtered.text.toIntOrNull()
                            if (num != null && num >= 0) {
                                if (isEditMode) {
                                    onUpdateEditTotalEpisodes(num)
                                } else {
                                    onUpdateWatchedEpisodes(num.coerceAtMost(currentMaxValue))
                                }
                            }
                        },
                        modifier = Modifier
                            .widthIn(min = 60.dp, max = 120.dp)
                            .focusRequester(focusReq),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                isEditing = false
                                keyboardController?.hide()
                            }
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )

                    LaunchedEffect(isEditing) {
                        if (isEditing) {
                            val text = currentDisplayValue.toString()
                            editValue = TextFieldValue(
                                text = text,
                                selection = TextRange(text.length)
                            )
                            focusReq.requestFocus()
                        }
                    }
                } else {
                    if (isEditMode) {
                        Text(
                            text = if (editTotalEpisodes > 0) "全 ${editTotalEpisodes} 集" else "未知集数",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable {
                                isEditing = true
                                val text = editTotalEpisodes.toString()
                                editValue = TextFieldValue(
                                    text = text,
                                    selection = TextRange(text.length)
                                )
                            }
                        )
                    } else {
                        val maxEps = anime.effectiveMaxEpisodes
                        val displayMax = if (anime.totalEpisodes > 0) anime.totalEpisodes else anime.currentEpisodes
                        Text(
                            text = if (maxEps > 0) "第 ${anime.watchedEpisodes} / ${displayMax} 集" else "第 ${anime.watchedEpisodes} 集",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable {
                                isEditing = true
                                val text = anime.watchedEpisodes.toString()
                                editValue = TextFieldValue(
                                    text = text,
                                    selection = TextRange(text.length)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 编辑模式下隐藏 Slider
                if (!isEditMode) {
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    val progressColor = MaterialTheme.colorScheme.primary
                    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

                    Box {
                        val maxEps = anime.effectiveMaxEpisodes
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(20.dp)
                        ) {
                            val barHeight = 4.dp.toPx()
                            val barY = (size.height - barHeight) / 2f
                            val cornerRadius = barHeight / 2f
                            val progress = if (maxEps > 0)
                                displayValue.toFloat() / maxEps else 0f
                            val progressWidth = size.width * progress.coerceIn(0f, 1f)

                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(0f, barY),
                                size = Size(size.width, barHeight),
                                cornerRadius = CornerRadius(cornerRadius)
                            )

                            if (progressWidth > 0) {
                                drawRoundRect(
                                    color = progressColor,
                                    topLeft = Offset(0f, barY),
                                    size = Size(progressWidth, barHeight),
                                    cornerRadius = CornerRadius(cornerRadius)
                                )
                            }

                            val tickCount = minOf(maxEps, 20)
                            if (tickCount > 1) {
                                val tickSpacing = size.width / tickCount
                                for (i in 1 until tickCount) {
                                    val x = i * tickSpacing
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(x, barY - 1.dp.toPx()),
                                        end = Offset(x, barY + barHeight + 1.dp.toPx()),
                                        strokeWidth = 0.5.dp.toPx()
                                    )
                                }
                            }
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                isDragging = true
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onUpdateWatchedEpisodes(sliderValue.toInt())
                            },
                            valueRange = if (maxEps > 0) 0f..maxEps.toFloat() else 0f..100f,
                            steps = if (maxEps > 1) maxEps - 1 else 0,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(20.dp)
                                .offset(y = (-20).dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            )
                        )
                    }
                }
            }

            AcceleratedButton(
                text = "+",
                enabled = isEditMode || anime.effectiveMaxEpisodes == 0 || anime.watchedEpisodes < anime.effectiveMaxEpisodes,
                hapticFeedback = hapticFeedback,
                onTap = { if (isEditMode) onAdjustEditTotalEpisodes(1) else onAdjustWatchedEpisodes(1) },
                onAdjust = { step -> if (isEditMode) onAdjustEditTotalEpisodes(step) else onAdjustWatchedEpisodes(step) }
            )
        }
    }
}

@Composable
private fun AcceleratedButton(
    text: String,
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onTap: () -> Unit,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(1) }
    var currentDelay by remember { mutableLongStateOf(200L) }
    val pressStartTime = remember { mutableLongStateOf(0L) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "press_scale"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (enabled) Modifier.pointerInput(enabled) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown()
                            isPressed = true
                            pressStartTime.longValue = System.currentTimeMillis()
                            currentStep = 1
                            currentDelay = 200L
                            onTap()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            try {
                                waitForUpOrCancellation()
                            } finally {
                                isPressed = false
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            while (isPressed) {
                delay(currentDelay)
                if (!isPressed) break

                val elapsed = System.currentTimeMillis() - pressStartTime.longValue
                if (elapsed > 1000L) {
                    currentStep = minOf(10, ((elapsed - 1000L) / 500L + 1).toInt().coerceIn(1, 10))
                    currentDelay = maxOf(100L, 200L - (elapsed - 1000L) / 20L)
                }

                onAdjust(currentStep)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

@Composable
private fun NotesCard(
    notes: String,
    notesText: String,
    isEditingNotes: Boolean,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onEditNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    DetailCard(modifier = modifier) {
        Text(
            text = "备注",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isEditingNotes) {
            OutlinedTextField(
                value = notesText,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("添加备注...") },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onNotesChange(notes)
                        onSaveNotes()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("取消")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSaveNotes,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("保存")
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                onClick = onEditNotes
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    if (notes.isNotBlank()) {
                        Text(
                            text = notes,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "点击添加备注...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusCard(
    currentStatus: AnimeStatus,
    finishDate: Long?,
    onStatusChange: (AnimeStatus) -> Unit,
    onFinishDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }

    DetailCard(modifier = modifier) {
        Text(
            text = "状态",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        CapsuleSegmentedButton(
            options = AnimeStatus.entries,
            selectedOption = currentStatus,
            onOptionSelected = onStatusChange,
            label = { it.displayName }
        )

        AnimatedVisibility(
            visible = currentStatus == AnimeStatus.COMPLETED,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "看完日期",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (finishDate != null) {
                                com.aiexile.animetrack.util.formatDate(finishDate)
                            } else {
                                "选择日期"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (finishDate != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text("确定")
                }
            }
        ) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = finishDate ?: System.currentTimeMillis()
            )
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
            LaunchedEffect(datePickerState.selectedDateMillis) {
                datePickerState.selectedDateMillis?.let {
                    onFinishDateChange(it)
                }
            }
        }
    }
}

@Composable
private fun <T> CapsuleSegmentedButton(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                val shape = RoundedCornerShape(100.dp)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .then(
                            if (isSelected) Modifier.background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = shape
                            ) else Modifier
                        )
                        .clickable { onOptionSelected(option) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(option),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: AnimeStatus,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = status.displayName,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WeekdayChips(
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    val weekdays = listOf(
        1 to "一", 2 to "二", 3 to "三",
        4 to "四", 5 to "五", 6 to "六", 7 to "日"
    )

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        weekdays.forEach { (day, label) ->
            val isSelected = selected == day
            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(day) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isSelected) null
                    else BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun Int.toWeekdayName(): String {
    return when (this) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> ""
    }
}

@Composable
private fun ShareNotesDialog(
    initialNotes: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "分享番剧",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "添加备注（将显示在分享卡片上）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("写点什么...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(notes) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("分享")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDialog(
    searchQuery: String,
    searchState: MatchSearchState,
    searchSource: SearchSource?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (SearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val sourceName = when (searchSource) {
        SearchSource.BANGUMI -> "Bangumi"
        SearchSource.TMDB -> "TMDB"
        SearchSource.ALL -> "数据源"
        null -> "数据源"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "匹配 $sourceName",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "输入关键词搜索...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearch() }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (searchState) {
                    is MatchSearchState.Idle -> {
                        Text(
                            text = "输入关键词后点击搜索",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    is MatchSearchState.Searching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is MatchSearchState.Results -> {
                        if (searchState.results.isEmpty()) {
                            Text(
                                text = "未找到匹配结果",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchState.results, key = { "${it.source}_${it.sourceId}" }) { result ->
                                    MatchResultItem(
                                        result = result,
                                        onClick = { onSelectResult(result) }
                                    )
                                }
                            }
                        }
                    }
                    is MatchSearchState.Failed -> {
                        Text(
                            text = searchState.message,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSearch) {
                Text("搜索")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun MatchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = resolveCoverModel(result.coverUrl),
                contentDescription = result.title,
                modifier = Modifier
                    .size(52.dp, 70.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.episodeCountText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val rating = result.rating
                    if (rating != null && rating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = LocalAnimeColors.current.starFilled,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                fontSize = 12.sp,
                                color = LocalAnimeColors.current.starFilled
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun saveCoverToGallery(context: Context, coverUrl: String) = withContext(Dispatchers.IO) {
    try {
        val imageLoader = Coil.imageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(coverUrl)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)

        if (result !is SuccessResult) {
            withContext(Dispatchers.Main) { Toast.makeText(context, "无法加载封面图片", Toast.LENGTH_SHORT).show() }
            return@withContext
        }

        val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: run {
            withContext(Dispatchers.Main) { Toast.makeText(context, "无法获取图片", Toast.LENGTH_SHORT).show() }
            return@withContext
        }

        val filename = "AnimeTrack_Cover_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AnimeTrack")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: run {
            withContext(Dispatchers.Main) { Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show() }
            return@withContext
        }

        resolver.openOutputStream(uri)?.use { stream: java.io.OutputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        withContext(Dispatchers.Main) { Toast.makeText(context, "封面已保存到相册", Toast.LENGTH_SHORT).show() }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) { Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_SHORT).show() }
    }
}
