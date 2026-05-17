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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import kotlinx.coroutines.delay

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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: AnimeDetailViewModel = viewModel(
        key = "anime_detail_$animeId",
        factory = AnimeDetailViewModel.Factory(animeId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val editState = uiState.editState
    var showDiscardDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

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
                            onEditTitleChange = { viewModel.updateEditTitle(it) },
                            onEditTitleStart = { viewModel.setEditingTitle(true) },
                            onEditTitleDone = { viewModel.setEditingTitle(false) },
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
                onQueryChange = { viewModel.updateCoverSearchQuery(it) },
                onSearch = { viewModel.searchCover() },
                onSelectResult = { viewModel.selectCoverResult(it) },
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSearchOverlay(
    query: String,
    results: List<BangumiSubject>,
    isSearching: Boolean,
    error: String?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectResult: (BangumiSubject) -> Unit,
    onDismiss: () -> Unit
) {
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
                            text = "搜索番剧封面...",
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
                    items(results, key = { it.id }) { subject ->
                        CoverSearchResultItem(
                            subject = subject,
                            onClick = { onSelectResult(subject) }
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
    subject: BangumiSubject,
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
                model = subject.coverUrl,
                contentDescription = subject.displayName,
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
                    text = subject.displayName,
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
                        text = subject.episodeCountText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val score = subject.score
                    if (score != null && score > 0) {
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
                                text = String.format("%.1f", score),
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
    onEditTitleChange: (String) -> Unit = {},
    onEditTitleStart: () -> Unit = {},
    onEditTitleDone: () -> Unit = {},
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
                        .width(120.dp)
                        .fillMaxHeight()
                ) {
                    val displayCoverUrl = if (editState.isEditing) editState.localCoverUri ?: editState.coverUrl else anime.coverUrl

                    val coverClipShape = RoundedCornerShape(CardCornerRadius)

                    val coverImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier
                                .fillMaxSize()
                                .sharedElement(
                                    rememberSharedContentState(key = "cover_${anime.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                                .clip(coverClipShape)
                        }
                    } else {
                        Modifier
                            .fillMaxSize()
                            .clip(coverClipShape)
                    }

                    Crossfade(
                        targetState = displayCoverUrl,
                        label = "cover_crossfade"
                    ) { url ->
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = coverImageModifier
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
                                    .clip(coverClipShape)
                                    .background(gradientBackground)
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
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (anime.rating != null) {
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
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f", anime.rating),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocalAnimeColors.current.starFilled
                            )
                        }
                    }
                }

                if (!anime.airDate.isNullOrBlank()) {
                    Text(
                        text = "放送: ${anime.airDate}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "全 ${anime.totalEpisodes} 集",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!airStatusText.isNullOrBlank()) {
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

                StatusBadge(status = anime.status)
            }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SummaryCard(
            summary = if (editState.isEditing) editState.summary else anime.summary,
            isFetchingDetail = isFetchingDetail
        )

        Spacer(modifier = Modifier.height(12.dp))

        ProgressCard(
            anime = anime,
            onUpdateWatchedEpisodes = onUpdateWatchedEpisodes,
            onAdjustWatchedEpisodes = onAdjustWatchedEpisodes
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

        if (isFetchingDetail) {
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
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    val focusReq = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var sliderValue by remember(anime.watchedEpisodes) {
        mutableFloatStateOf(anime.watchedEpisodes.toFloat())
    }
    var isDragging by remember { mutableStateOf(false) }

    DetailCard(modifier = modifier) {
        Text(
            text = "观看进度",
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
                enabled = anime.watchedEpisodes > 0,
                hapticFeedback = hapticFeedback,
                onTap = { onAdjustWatchedEpisodes(-1) },
                onAdjust = { step -> onAdjustWatchedEpisodes(-step) }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                val displayValue = if (isDragging) sliderValue.toInt() else anime.watchedEpisodes

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
                            val filtered = input.filter { it.isDigit() }
                            editValue = filtered
                            val num = filtered.toIntOrNull()
                            if (num != null && num in 0..anime.totalEpisodes) {
                                onUpdateWatchedEpisodes(num)
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
                            editValue = anime.watchedEpisodes.toString()
                            focusReq.requestFocus()
                        }
                    }
                } else {
                    Text(
                        text = "第 ${anime.watchedEpisodes} / ${anime.totalEpisodes} 集",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable {
                            isEditing = true
                            editValue = anime.watchedEpisodes.toString()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val progressColor = MaterialTheme.colorScheme.primary
                val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

                Box {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(20.dp)
                    ) {
                        val barHeight = 4.dp.toPx()
                        val barY = (size.height - barHeight) / 2f
                        val cornerRadius = barHeight / 2f
                        val progress = if (anime.totalEpisodes > 0)
                            displayValue.toFloat() / anime.totalEpisodes else 0f
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

                        val tickCount = minOf(anime.totalEpisodes, 20)
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
                        valueRange = 0f..anime.totalEpisodes.toFloat(),
                        steps = if (anime.totalEpisodes > 1) anime.totalEpisodes - 1 else 0,
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

            AcceleratedButton(
                text = "+",
                enabled = anime.watchedEpisodes < anime.totalEpisodes,
                hapticFeedback = hapticFeedback,
                onTap = { onAdjustWatchedEpisodes(1) },
                onAdjust = { step -> onAdjustWatchedEpisodes(step) }
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
                                java.text.SimpleDateFormat(
                                    "yyyy-MM-dd",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(finishDate))
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
