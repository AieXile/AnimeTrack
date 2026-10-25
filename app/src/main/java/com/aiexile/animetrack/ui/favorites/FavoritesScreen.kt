package com.aiexile.animetrack.ui.favorites

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * 收藏页：展示已在主界面长按收藏的番剧，点击卡片进入详情。
 * 作为主界面 Pager 页使用，顶栏由 [AppScaffold] 统一提供（材质差异只存在于组件层）。
 *
 * [sharedTransitionScope] / [animatedVisibilityScope] 与主页共用同一组 Scope，
 * 卡片封面以 `cover_<id>` 与详情页封面配对，保证飞入飞出与主页一致。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FavoritesScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    isCapsuleNav: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory())
) {
    val favorites by viewModel.favorites.collectAsState()

    AppScaffold(
        title = stringResource(R.string.nav_app_favorites),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
        if (favorites.isEmpty()) {
            EmptyFavoritesContent(
                modifier = modifier
                    .fillMaxSize()
                    .then(chrome.layout)
                    .padding(chrome.content)
            )
        } else {
            FavoritesGrid(
                favorites = favorites,
                onAnimeClick = onAnimeClick,
                isCapsuleNav = isCapsuleNav,
                chrome = chrome,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun FavoritesGrid(
    favorites: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    isCapsuleNav: Boolean,
    chrome: AppScaffoldPadding,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier
) {
    // 大屏适配：与主界面网格同一套列数策略，pane 压缩/展开时列数自适应
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
            .then(chrome.layout)
            .onGloballyPositioned { coords ->
                containerWidth = with(density) { coords.size.width.toDp() }
            }
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(
            top = 8.dp + chrome.content.calculateTopPadding(),
            bottom = (if (isCapsuleNav) 96.dp else 16.dp) + chrome.content.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(favorites, key = { it.id }) { anime ->
            AnimeCard(
                anime = anime,
                onClick = { onAnimeClick(anime) },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                // 与主页卡片区分 key：Pager 滑动时两页可能同时组合
                sharedElementKeyPrefix = SharedElementSpecs.FavoritesKeyPrefix,
                // 详情页取消收藏后返回时，剩余卡片平滑补位而不是硬跳
                modifier = Modifier.animateItem()
            )
        }
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
                text = stringResource(R.string.favorites_empty_hint),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
