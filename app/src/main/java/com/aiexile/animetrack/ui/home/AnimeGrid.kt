package com.aiexile.animetrack.ui.home

import android.graphics.Bitmap
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.components.AnimeCard
import com.aiexile.animetrack.ui.components.AnimeCardStack
import com.aiexile.animetrack.ui.components.animateEnter
import com.aiexile.animetrack.ui.components.rememberAdaptiveGridColumns
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

// 季数角标形状顶层化复用：位于滞动热路径（ExpandedSeriesCard 每卡渲染），
// 提升为顶层 val 使 SquircleShape 内置 size 级缓存生效。
private val SeasonBadgeShape = SquircleShape(6.dp)

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
                painter = rememberAppIconPainter(AppIcon.LIST_ARROW),
                contentDescription = stringResource(R.string.home_filter),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = SquircleShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .padding(horizontal = 4.dp)
        ) {
            AnimeFilter.entries.forEach { filter ->
                val isSelected = filter == selectedFilter

                // 使用 Box + clickable（无涟漪）替代 DropdownMenuItem，
                // 因为 DropdownMenuItem 不支持去除涟漪，而此行已有选中背景+对勾作为视觉指示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onFilterSelected(filter)
                            expanded = false
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
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
                                    painter = rememberAppIconPainter(AppIcon.CHECK),
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
                }
            }
        }
    }
}

@Composable
internal fun EmptyAnimePlaceholder(
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
                painter = rememberAppIconPainter(AppIcon.INBOX),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.home_empty_anime_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            Text(
                text = stringResource(R.string.home_empty_anime_hint),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp
            )
        }
    }
}

internal data class AnimeGridState(
    val animeListItems: List<AnimeListItem>,
    val hasAnyAnime: Boolean = false,
    val newlyAddedAnimeId: Long?,
    val selectedAnimeId: Long?,
    val highlightedAnimeIds: Set<Long> = emptySet(),
    val selectedFilter: AnimeFilter,
    val seriesStackEnabled: Boolean = true
)

internal data class AnimeGridHeaderState(
    val isLoggedIn: Boolean = false,
    val userAvatar: String? = null,
    val hideBangumiAvatar: Boolean = false,
    val showBanner: Boolean = false,
    val todayUpdateCount: Int = 0,
    val autoSyncState: AutoSyncState = AutoSyncState.Idle,
    /** 反馈有未读回复（显示胶囊提示，无红点） */
    val hasFeedbackReply: Boolean = false
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AnimeGrid(
    state: AnimeGridState,
    headerState: AnimeGridHeaderState,
    onHighlightComplete: () -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongPress: (Anime) -> Unit,
    onStatusChange: (Anime, com.aiexile.animetrack.model.AnimeStatus) -> Unit,
    onDelete: (Anime) -> Unit,
    onTogglePin: (Anime) -> Unit,
    onFilterSelected: (AnimeFilter) -> Unit,
    onAvatarClick: () -> Unit = {},
    onDismissBanner: () -> Unit = {},
    onBannerClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    gridState: LazyGridState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    isCapsuleNav: Boolean = false,
    topContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {

    // 已展开系列 key 集合：支持多系列同时独立展开。
    // 使用 stateSaver + listSaver 让 Set 可在配置变更/进程恢复后保留。
    var expandedSeriesKeys by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet()) }

    // 堆叠卡在窗口中的位置（key=series.stableKey），作为展开卡片的平移起点
    // 使用普通 HashMap 而非 mutableStateMapOf：写入不触发重组（避免滚动时持续写入引发重组），
    // 读取处用 remember 缓存，保证 ExpandedSeriesCard 拿到 Series 渲染时最后一次布局位置。
    val seriesPositionMap = remember { HashMap<String, Offset>() }

    // 已播放展开动画的系列 key 集合：避免 LazyGrid 回收 item 后滚回时重播动画，
    // 以及进入详情页再返回时重播动画。
    // 使用 rememberSaveable + listSaver 让 Set 可在导航切换/配置变更后保留。
    var animatedSeriesKeys by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(emptySet()) }

    // 基于 expandedSeriesKeys 构建实际渲染列表：展开时将 Series 拆分为多个 ExpandedSeriesCard，
    // 每季占据一个独立网格格子，后续卡片自动顺延（真正网格回流）。
    // seriesStackEnabled=false 时，所有 Series 强制拆分为 Single，按主排序顺序排列（不按季数）。
    // seriesStackEnabled=true 时，Series 组内按季数升序排列（1,2,3...），哪怕第一季已看完也排在前面。
    val displayList = remember(state.animeListItems, expandedSeriesKeys, state.seriesStackEnabled) {
        if (!state.seriesStackEnabled) {
            // 堆叠开关关闭：所有 Series 拆分为 Single，保持主排序顺序
            buildList {
                for (item in state.animeListItems) {
                    if (item is AnimeListItem.Series) {
                        item.animeList.forEach { add(AnimeListItem.Single(it)) }
                    } else {
                        add(item)
                    }
                }
            }
        } else {
            // 堆叠开关开启：组内按季数升序排序
            val seasonSortedItems = state.animeListItems.map { item ->
                if (item is AnimeListItem.Series) {
                    item.copy(animeList = item.animeList.sortedWith(
                        compareBy<Anime> { SeriesMatcher.extractSeasonNumber(it.title) }
                            .thenBy { it.airDate ?: "" }
                            .thenBy { it.id }
                    ))
                } else {
                    item
                }
            }
            if (expandedSeriesKeys.isEmpty()) {
                seasonSortedItems
            } else {
                buildList {
                    for (item in seasonSortedItems) {
                        if (item is AnimeListItem.Series && item.stableKey in expandedSeriesKeys) {
                            item.animeList.forEachIndexed { index, anime ->
                                add(
                                    AnimeListItem.ExpandedSeriesCard(
                                        anime = anime,
                                        baseTitle = item.baseTitle,
                                        seasonIndex = index + 1,
                                        totalSeasons = item.animeList.size,
                                        seriesStableKey = item.stableKey
                                    )
                                )
                            }
                        } else {
                            add(item)
                        }
                    }
                }
            }
        }
    }

    // 首次组合时是否处于共享元素过渡（从详情页返回主页）。
    // 此时卡片是飞出动画的落点，进场动画需跳过以免与飞出叠加抖动。
    // 仅捕获首帧值：正常滚入/首次进入 app 时为 false，照常播放进场。
    val skipEnterForTransition = remember {
        sharedTransitionScope?.isTransitionActive == true
    }

    // 首次加载动画标记：初始为 true，延迟后切换为 false
    // 切换后滚入的卡片只播轻量动效（alpha=1），避免快速滑动白屏
    var isInitialLoad by remember { mutableStateOf(true) }
    LaunchedEffect(state.animeListItems) {
        if (state.animeListItems.isNotEmpty()) {
            // 等待首屏波浪动画完成（maxStaggerMs + 弹簧动画时间）
            delay(700)
            isInitialLoad = false
        }
    }
    
    // 大屏适配：实测网格容器宽度计算列数（初值取窗口宽度，首帧后由 onGloballyPositioned 矫正），
    // pane 打开压缩主界面时列数自适应减少（竖屏最多 4 列，横屏最多 6 列）
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var containerWidth by remember { mutableStateOf(configuration.screenWidthDp.dp) }
    val columns = rememberAdaptiveGridColumns(
        availableWidth = containerWidth,
        cardMinWidth = 140.dp,
        spacing = 12.dp,
        horizontalPadding = 24.dp,
        minColumns = 3,
        maxColumnsPortrait = 4,
        maxColumnsLandscape = 6
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onGloballyPositioned { coords ->
                // 实测容器宽度：pane 压缩/展开时列数随之自适应
                containerWidth = with(density) { coords.size.width.toDp() }
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        state = gridState,
        contentPadding = PaddingValues(
            // 顶栏高度预留（statusBar + 48.dp）：顶栏隐藏时内容零移动，
            // 仅首屏让出顶栏区域，滚动后内容可自然滚入顶栏背后
            top = topContentPadding,
            bottom = if (isCapsuleNav) 96.dp else 16.dp
        )
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!headerState.hideBangumiAvatar) {
                    UserAvatarButton(
                        isLoggedIn = headerState.isLoggedIn,
                        avatarUrl = headerState.userAvatar,
                        onClick = onAvatarClick,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }

                SyncBannerArea(
                    autoSyncState = headerState.autoSyncState,
                    showTodayBanner = headerState.showBanner,
                    todayUpdateCount = headerState.todayUpdateCount,
                    onBannerClick = onBannerClick,
                    showFeedbackPill = headerState.hasFeedbackReply,
                    onFeedbackClick = onFeedbackClick,
                    modifier = Modifier.align(Alignment.Center)
                )

                FilterMenu(
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = onFilterSelected,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        items(
            count = displayList.size,
            key = { index -> displayList[index].stableKey },
            contentType = { index -> when (displayList[index]) {
                is AnimeListItem.Single -> "anime_card"
                is AnimeListItem.Series -> "anime_series"
                is AnimeListItem.ExpandedSeriesCard -> "anime_card_expanded"
            }}
        ) { index ->
            val item = displayList[index]
            when (item) {
                is AnimeListItem.Single -> {
                    Box(
                        modifier = Modifier.animateEnter(
                            index = index,
                            key = Unit,
                            isInitialLoad = isInitialLoad,
                            animationEnabled = true,
                            skipAnimation = skipEnterForTransition
                        )
                    ) {
                        val anime = item.anime
                        val isNew = anime.id.toLong() == state.newlyAddedAnimeId
                        val isSelected = anime.id.toLong() == state.selectedAnimeId
                        val isHighlighted = anime.id.toLong() in state.highlightedAnimeIds

                        if (isNew) {
                            NewAnimeCardWrapper(
                                anime = anime,
                                onHighlightComplete = onHighlightComplete,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = { onAnimeLongPress(anime) },
                                isSelected = isSelected,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                onTogglePin = { onTogglePin(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        } else {
                            AnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = { onAnimeLongPress(anime) },
                                isSelected = isSelected,
                                isHighlighted = isHighlighted,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                onTogglePin = { onTogglePin(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                }
                is AnimeListItem.Series -> {
                    Box(
                        modifier = Modifier
                            .animateEnter(
                                index = index,
                                key = Unit,
                                isInitialLoad = isInitialLoad,
                                animationEnabled = true,
                                skipAnimation = skipEnterForTransition
                            )
                            .onGloballyPositioned { coords ->
                                // 记录堆叠卡在窗口中的位置，作为展开卡片的平移起点
                                seriesPositionMap[item.stableKey] = coords.positionInWindow()
                            }
                    ) {
                        AnimeCardStack(
                            baseTitle = item.baseTitle,
                            animeList = item.animeList,
                            onClick = { onAnimeClick(item.animeList.first()) },
                            onLongPress = { expandedSeriesKeys = expandedSeriesKeys + item.stableKey }
                        )
                    }
                }
                is AnimeListItem.ExpandedSeriesCard -> {
                    val anime = item.anime
                    val isNew = anime.id.toLong() == state.newlyAddedAnimeId
                    val isHighlighted = anime.id.toLong() in state.highlightedAnimeIds

                    // 从堆叠位置平移动效：
                    // 1. 通过 onGloballyPositioned 获取自身目标位置，计算与堆叠卡位置的偏移
                    // 2. enterProgress 0→1 驱动 translationX/Y 从 initialOffset 平移到 0
                    // 3. 多季按 seasonIndex 微调起点，营造从堆叠深处抽出的层次感
                    //
                    // 动画完成状态基于 seriesStableKey 持久化到 LazyGrid 外部，
                    // 避免滚动回收 item 后滚回时重播动画。
                    val seriesKey = item.seriesStableKey
                    val hasAnimated = seriesKey in animatedSeriesKeys
                    var enterStarted by remember(seriesKey) { mutableStateOf(hasAnimated) }
                    LaunchedEffect(seriesKey) {
                        if (!enterStarted) {
                            enterStarted = true
                            animatedSeriesKeys = animatedSeriesKeys + seriesKey
                        }
                    }

                    // 记录展开卡片目标位置（首次定位后）
                    var targetPosition by remember { mutableStateOf(Offset.Zero) }
                    // 读取堆叠卡位置作为平移起点：用 remember 缓存，避免每次重组查 HashMap。
                    // seriesPositionMap 已改为普通 HashMap（写入不触发重组），值在 Series 渲染期间
                    // 由 onGloballyPositioned 持续更新，展开后 Series 被移出 displayList 即冻结。
                    val seriesPosition = remember(item.seriesStableKey) {
                        seriesPositionMap[item.seriesStableKey] ?: Offset.Zero
                    }
                    // 初始偏移：从堆叠位置出发到目标位置的位移（展开卡片需平移的距离）
                    val initialOffsetX = seriesPosition.x - targetPosition.x
                    val initialOffsetY = seriesPosition.y - targetPosition.y
                    // 多季按 seasonIndex 微调起点（底层季从更深处抽出），偏移量小
                    val seasonDepthPx = with(LocalDensity.current) { (4.dp * item.seasonIndex).toPx() }

                    val enterProgress by animateFloatAsState(
                        targetValue = if (enterStarted) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "expandEnter"
                    )

                    Box(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = tween(200),
                                placementSpec = spring(
                                    dampingRatio = 0.7f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .onGloballyPositioned { coords ->
                                targetPosition = coords.positionInWindow()
                            }
                            .graphicsLayer {
                                val scale = 0.9f + 0.1f * enterProgress
                                scaleX = scale
                                scaleY = scale
                                // 从堆叠位置平移到目标位置（initialOffset → 0），叠加多季深度偏移
                                translationX = (initialOffsetX - seasonDepthPx) * (1f - enterProgress)
                                translationY = (initialOffsetY - seasonDepthPx) * (1f - enterProgress)
                                alpha = enterProgress
                            }
                    ) {
                        if (isNew) {
                            NewAnimeCardWrapper(
                                anime = anime,
                                onHighlightComplete = onHighlightComplete,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = {
                                    expandedSeriesKeys = expandedSeriesKeys - item.seriesStableKey
                                    animatedSeriesKeys = animatedSeriesKeys - item.seriesStableKey
                                },
                                isSelected = false,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                onTogglePin = { onTogglePin(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        } else {
                            AnimeCard(
                                anime = anime,
                                onClick = { onAnimeClick(anime) },
                                onLongPress = {
                                    expandedSeriesKeys = expandedSeriesKeys - item.seriesStableKey
                                    animatedSeriesKeys = animatedSeriesKeys - item.seriesStableKey
                                },
                                isHighlighted = isHighlighted,
                                onStatusChange = { onStatusChange(anime, it) },
                                onDelete = { onDelete(anime) },
                                onTogglePin = { onTogglePin(anime) },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                        // 季数角标：左上角，避免与右上角 StatusBadge 重叠
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 6.dp, y = 6.dp),
                            shape = SeasonBadgeShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.home_season_format, item.seasonIndex),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (state.hasAnyAnime && state.animeListItems.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_no_anime_in_category),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBannerArea(
    autoSyncState: AutoSyncState,
    showTodayBanner: Boolean,
    todayUpdateCount: Int,
    onBannerClick: () -> Unit,
    showFeedbackPill: Boolean = false,
    onFeedbackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 自动同步状态优先：非 Idle 时显示同步 Banner（含退出动画）
    // Idle 且有今日更新时显示今日更新 Banner
    val isAutoSyncVisible = autoSyncState !is AutoSyncState.Idle

    AnimatedVisibility(
        visible = isAutoSyncVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        modifier = modifier
    ) {
        Surface(
            color = Color.Transparent,
            shape = SquircleShape(50)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                when (autoSyncState) {
                    is AutoSyncState.Syncing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.home_syncing_data),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AutoSyncState.Completed -> {
                        Text(
                            text = stringResource(R.string.home_synced_count_format, autoSyncState.count),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is AutoSyncState.Failed -> {
                        Text(
                            text = stringResource(R.string.home_sync_failed),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is AutoSyncState.Idle -> {}
                }
            }
        }
    }

    // Banner 优先级：自动同步 > 反馈新回复 > 今日更新
    // 有未读反馈时只显示反馈胶囊；进入反馈界面（标记已读）回来后才显示今日更新
    if (!isAutoSyncVisible && showFeedbackPill) {
        Surface(
            color = Color.Transparent,
            shape = SquircleShape(50),
            modifier = modifier
                .clip(SquircleShape(50))
                .clickable { onFeedbackClick() }
        ) {
            Text(
                text = stringResource(R.string.feedback_unread_pill),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    } else if (!isAutoSyncVisible && showTodayBanner) {
        Surface(
            color = Color.Transparent,
            shape = SquircleShape(50),
            modifier = modifier
                .clip(SquircleShape(50))
                .clickable { onBannerClick() }
        ) {
            Text(
                text = stringResource(R.string.home_today_updates_format, todayUpdateCount),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
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
    onTogglePin: () -> Unit = {},
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
            onTogglePin = onTogglePin,
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
                        shape = SquircleShape(16.dp)
                    )
            )
        }
    }
}

@Composable
private fun UserAvatarButton(
    isLoggedIn: Boolean,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                color = if (isLoggedIn) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isLoggedIn && avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .bitmapConfig(Bitmap.Config.HARDWARE)
                    .build(),
                contentDescription = stringResource(R.string.home_user_avatar),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                painter = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
                contentDescription = stringResource(R.string.home_login),
                modifier = Modifier.size(18.dp),
                tint = if (isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
