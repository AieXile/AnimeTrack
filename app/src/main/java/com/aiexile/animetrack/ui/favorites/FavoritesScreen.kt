package com.aiexile.animetrack.ui.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.components.AnimeCard
import com.aiexile.animetrack.ui.components.SharedElementSpecs
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.chrome.AppScaffoldPadding
import com.aiexile.animetrack.ui.components.rememberAdaptiveGridColumns
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.flow.first

/**
 * 收藏页：展示收藏夹与全部收藏，点击卡片进入详情。
 * 作为主界面 Pager 页使用，顶栏由 [AppScaffold] 统一提供（材质差异只存在于组件层）。
 *
 * [sharedTransitionScope] / [animatedVisibilityScope] 与主页共用同一组 Scope，
 * 卡片封面以 `cover_<id>` 与详情页封面配对，保证飞入飞出与主页一致。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onAnimeClick: (Anime, sharedKeyPrefix: String) -> Unit,
    modifier: Modifier = Modifier,
    isCapsuleNav: Boolean = false,
    canCloseFolderOnBack: Boolean = true,
    gridState: LazyGridState = rememberLazyGridState(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory())
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        FavoritesContent(
            onAnimeClick = onAnimeClick,
            modifier = modifier,
            isCapsuleNav = isCapsuleNav,
            canCloseFolderOnBack = canCloseFolderOnBack,
            gridState = gridState,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoritesContent(
    onAnimeClick: (Anime, sharedKeyPrefix: String) -> Unit,
    modifier: Modifier,
    isCapsuleNav: Boolean,
    canCloseFolderOnBack: Boolean,
    gridState: LazyGridState,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    viewModel: FavoritesViewModel
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val folderPreviews by viewModel.folderPreviews.collectAsStateWithLifecycle()
    val folderPinchGuideSeen by viewModel.folderPinchGuideSeen.collectAsStateWithLifecycle()
    val folderList = folders.orEmpty()
    val onError = rememberFolderWriteError()
    var createFolder by rememberSaveable { mutableStateOf(false) }
    var reorderFolders by rememberSaveable { mutableStateOf(false) }
    var animeToFileId by rememberSaveable { mutableStateOf<Int?>(null) }
    var requestedFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var displayedFolderId by remember { mutableStateOf(requestedFolderId) }
    var folderDialog by rememberSaveable { mutableStateOf<FavoriteFolderDialog?>(null) }
    var removingId by rememberSaveable { mutableStateOf<Int?>(null) }
    val opening = remember { Animatable(if (requestedFolderId == null) 0f else 1f) }
    val showContinuation by remember { derivedStateOf { opening.value >= FolderContinuationStart } }
    val isFolderOpen by remember { derivedStateOf { opening.value == 1f } }
    val displayedFolder = folderList.firstOrNull { it.id == displayedFolderId }
    val pinchProgress = remember(displayedFolderId) { mutableStateOf<Float?>(null) }
    val members = key(displayedFolderId) {
        displayedFolderId?.let { folderId ->
            val flow = remember(viewModel, folderId) { viewModel.observeAnimes(folderId) }
            flow.collectAsStateWithLifecycle(initialValue = null).value
        }
    }
    val loadedFolderId by rememberUpdatedState(displayedFolder?.id?.takeIf { members != null })

    LaunchedEffect(folders, requestedFolderId) {
        val folderId = requestedFolderId
        if (folderId != null && folders?.none { it.id == folderId } == true) {
            requestedFolderId = null
        }
    }
    LaunchedEffect(requestedFolderId) {
        val target = requestedFolderId
        if (displayedFolderId != target) {
            if (displayedFolderId != null) {
                opening.animateTo(0f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
            }
            folderDialog = null
            removingId = null
            displayedFolderId = target
        }
        if (target != null && opening.value < 1f) {
            // 等待夹内数据，卡片与夹面使用同一段动效。
            snapshotFlow { loadedFolderId }.first { it == target }
            opening.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
    }

    val isVisible = animatedVisibilityScope == null ||
        animatedVisibilityScope.transition.targetState == EnterExitState.Visible
    val pinchFolderId = displayedFolder?.id?.takeIf { isVisible && isFolderOpen && it == requestedFolderId }
    val showPinchGuide = folderPinchGuideSeen == false
    BackHandler(enabled = canCloseFolderOnBack && isVisible && requestedFolderId != null) {
        requestedFolderId = null
    }

    AppScaffold(
        title = stringResource(R.string.nav_app_favorites),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
            if (folderList.size > 1) {
                IconButton(onClick = { reorderFolders = true }) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.DRAG_HANDLE),
                        contentDescription = stringResource(R.string.favorite_folder_reorder)
                    )
                }
            }
            IconButton(onClick = { createFolder = true }) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ADD),
                    contentDescription = stringResource(R.string.favorite_folder_new)
                )
            }
        },
        titleContent = {
            Column {
                Text(
                    text = stringResource(R.string.nav_app_favorites),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (favorites.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.favorites_count_format, favorites.size),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    ) { chrome ->
        if (folders == null || (displayedFolder != null && members == null && showContinuation)) {
            // 等待收藏夹及恢复展开的内容，避免网格先用不完整的列表定位。
        } else if (favorites.isEmpty() && folderList.isEmpty()) {
            EmptyFavoritesContent(
                modifier = modifier
                    .fillMaxSize()
                    .then(chrome.layout)
                    .padding(chrome.content)
            )
        } else {
            val animes = members.orEmpty()
            val folderKeyPrefix = "folder_${displayedFolderId}_"
            val toggleFolder: (String) -> Unit = { folderId ->
                requestedFolderId = folderId.takeUnless { it == requestedFolderId }
            }
            val folderActions: @Composable () -> Unit = {
                if (displayedFolder != null) {
                    FavoriteFolderManagementActions(
                        displayedFolder,
                        viewModel,
                        onError,
                        onDialog = { folderDialog = it }
                    )
                }
            }
            val folderCard: @Composable (Anime, Float, Modifier) -> Unit = { anime, progress, cardModifier ->
                AnimeCard(
                    anime = anime,
                    onClick = { if (progress >= 0.92f) onAnimeClick(anime, folderKeyPrefix) },
                    onLongPress = { if (progress >= 0.92f) removingId = anime.id },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedElementKeyPrefix = folderKeyPrefix,
                    modifier = cardModifier
                )
            }
            FavoritesGrid(
                chrome,
                isCapsuleNav,
                gridState,
                modifier,
                onPinchClose = pinchFolderId?.let { folderId ->
                    {
                        if (requestedFolderId == folderId) {
                            if (folderPinchGuideSeen != true) viewModel.finishFolderPinchGuide()
                            requestedFolderId = null
                        }
                    }
                },
                onPinchProgress = if (showPinchGuide) ({ pinchProgress.value = it }) else null
            ) { columns ->
                if (folderList.isNotEmpty()) {
                    item(key = "favorite_folders_title", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.favorite_folder_folders),
                            modifier = Modifier.padding(bottom = FavoriteSectionSpacing),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    folderList.chunked(columns).forEach { row ->
                        val folderColumn = row.indexOfFirst { it.id == displayedFolder?.id }
                        val hasContinuation = folderColumn >= 0 && animes.size > columns
                        val continues = hasContinuation && showContinuation
                        item(
                            key = "folder_row_${row.first().id}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = OpenFolderContentType.takeIf { folderColumn >= 0 }
                        ) {
                            FavoriteFolderShelfRow(
                                folders = row,
                                columns = columns,
                                displayedFolderId = displayedFolderId,
                                requestedFolderId = requestedFolderId,
                                opening = opening.asState(),
                                previews = folderPreviews,
                                hasContinuation = hasContinuation,
                                viewModel = viewModel,
                                onFolderClick = toggleFolder,
                                contents = { progress ->
                                    Column(Modifier.fillMaxWidth()) {
                                        FavoriteFolderPinchGuide(
                                            visible = showPinchGuide,
                                            ready = pinchFolderId != null && canCloseFolderOnBack,
                                            pinchProgress = pinchProgress,
                                            onSkip = viewModel::finishFolderPinchGuide,
                                            modifier = Modifier.fillMaxWidth().revealInPocket(progress)
                                        )
                                        if (members != null && animes.isEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().revealInPocket(progress).padding(top = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(stringResource(R.string.favorite_folder_empty))
                                                TextButton(
                                                    onClick = { folderDialog = FavoriteFolderDialog.ADD },
                                                    enabled = progress >= 0.92f
                                                ) {
                                                    Text(stringResource(R.string.favorite_folder_add))
                                                }
                                            }
                                        } else if (animes.isNotEmpty()) {
                                            FavoriteFolderAnimeRow(animes.take(columns), columns, progress) { anime, cardModifier ->
                                                folderCard(anime, progress, cardModifier)
                                            }
                                        }
                                    }
                                },
                                actions = folderActions
                            )
                        }
                        if (continues && displayedFolder != null) {
                            val remainingRows = animes.drop(columns).chunked(columns)
                            items(
                                remainingRows,
                                key = { "$folderKeyPrefix${it.first().id}" },
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = { OpenFolderContentType }
                            ) { membersInRow ->
                                val progress = opening.value
                                val reveal = ((progress - FolderContinuationStart) / (1f - FolderContinuationStart))
                                    .coerceIn(0f, 1f)
                                FavoriteFolderContinuation(columns, folderColumn, progress) {
                                    FavoriteFolderAnimeRow(
                                        membersInRow, columns, reveal,
                                        modifier = Modifier.padding(start = 4.dp, top = FavoriteGridSpacing, end = 4.dp),
                                        fanOut = false
                                    ) { anime, cardModifier ->
                                        folderCard(anime, reveal, cardModifier)
                                    }
                                }
                            }
                            item(
                                key = "folder_flap_${displayedFolder.id}",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = OpenFolderContentType
                            ) {
                                val progress = opening.value
                                FavoriteFolderContinuation(columns, folderColumn, progress, isFlap = true) {
                                    FavoriteFolderFlap(
                                        displayedFolder,
                                        viewModel,
                                        progress,
                                        expanded = displayedFolder.id == requestedFolderId,
                                        onClick = { toggleFolder(displayedFolder.id) },
                                        actions = folderActions
                                    )
                                }
                            }
                        }
                        item(key = "folder_spacing_${row.first().id}", span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(FavoriteSectionSpacing))
                        }
                    }
                }
                item(key = "favorites_all_title", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.favorite_folder_all),
                        modifier = Modifier.padding(bottom = FavoriteSectionSpacing),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (favorites.isEmpty()) {
                    item(key = "favorites_empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyFavoritesContent(Modifier.padding(bottom = 24.dp))
                    }
                }
                items(favorites, key = { it.id }) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { onAnimeClick(anime, SharedElementSpecs.FavoritesKeyPrefix) },
                        onLongPress = { animeToFileId = anime.id },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedElementKeyPrefix = SharedElementSpecs.FavoritesKeyPrefix,
                        modifier = (if (opening.isRunning) Modifier else Modifier.animateItem())
                            .padding(bottom = FavoriteSectionSpacing)
                    )
                }
            }
        }
    }

    if (createFolder) {
        FavoriteFolderNameDialog(initialName = null, onDismiss = { createFolder = false }) { name ->
            viewModel.createFolder(name, onError)
            createFolder = false
        }
    }
    if (reorderFolders && folders != null) {
        FavoriteFolderReorderDialog(
            title = R.string.favorite_folder_reorder,
            labels = folderList.associate { it.id to it.name },
            onDismiss = { reorderFolders = false },
            onSave = {
                viewModel.reorderFolders(it, onError)
                reorderFolders = false
            }
        )
    }
    animeToFileId?.let { animeId ->
        val flow = remember(viewModel, animeId) { viewModel.observeFolderIdsForAnime(animeId) }
        val memberships by flow.collectAsStateWithLifecycle(initialValue = null)
        memberships?.let { current ->
            FavoriteFolderSelectionDialog(
                title = R.string.favorite_folder_add_to,
                emptyMessage = R.string.favorite_folder_create_first,
                choices = folderList.map { FavoriteFolderChoice(it.id, it.name, it.id in current) },
                onDismiss = { animeToFileId = null },
                onAdd = {
                    viewModel.addToFolders(animeId, it, onError)
                    animeToFileId = null
                }
            )
        }
    }
    if (displayedFolder != null && members != null && displayedFolder.id == requestedFolderId) {
        FavoriteFolderManagementDialogs(
            folder = displayedFolder,
            animes = members,
            favorites = favorites,
            viewModel = viewModel,
            dialog = folderDialog,
            removingId = removingId,
            onDismiss = { folderDialog = null },
            onDismissRemoval = { removingId = null },
            onDeleted = {
                if (requestedFolderId == displayedFolder.id) requestedFolderId = null
            },
            onError = onError
        )
    }
}

@Composable
private fun FavoritesGrid(
    chrome: AppScaffoldPadding,
    isCapsuleNav: Boolean,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    onPinchClose: (() -> Unit)?,
    onPinchProgress: ((Float?) -> Unit)?,
    content: LazyGridScope.(columns: Int) -> Unit
) {
    // 大屏适配：与主界面网格同一套列数策略，pane 压缩/展开时列数自适应
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var containerWidth by remember { mutableStateOf(configuration.screenWidthDp.dp) }
    val columns = rememberAdaptiveGridColumns(
        availableWidth = containerWidth,
        cardMinWidth = 140.dp,
        spacing = FavoriteGridSpacing,
        horizontalPadding = 24.dp,
        minColumns = 3,
        maxColumnsPortrait = 4,
        maxColumnsLandscape = 6
    )
    val containsOpenFolder = remember(gridState) {
        { position: Offset ->
            val layout = gridState.layoutInfo
            val contentY = position.y + layout.viewportStartOffset
            layout.visibleItemsInfo.any { item ->
                item.contentType == OpenFolderContentType &&
                    contentY >= item.offset.y && contentY < item.offset.y + item.size.height
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .then(chrome.layout)
            .onGloballyPositioned { coords ->
                containerWidth = with(density) { coords.size.width.toDp() }
            }
            .padding(horizontal = 12.dp)
            .closeFolderOnPinch(containsOpenFolder, onPinchClose, onPinchProgress),
        contentPadding = PaddingValues(
            top = 8.dp + chrome.content.calculateTopPadding(),
            bottom = (if (isCapsuleNav) 96.dp else 16.dp) + chrome.content.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(FavoriteGridSpacing)
    ) {
        content(columns)
    }
}

@Composable
private fun EmptyFavoritesContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.FAVORITE),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.favorites_empty_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.favorite_folder_empty_hint),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
