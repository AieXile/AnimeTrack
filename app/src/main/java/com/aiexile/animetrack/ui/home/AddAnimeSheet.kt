package com.aiexile.animetrack.ui.home

import android.graphics.Bitmap

import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.items
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.util.resolveCoverModel
import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import com.aiexile.animetrack.ui.components.AddAnimeForm
import com.aiexile.animetrack.ui.components.AddAnimeFormState
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddAnimeBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    searchQuery: String = "",
    searchResults: List<SearchResult> = emptyList(),
    isSearching: Boolean = false,
    searchError: String? = null,
    searchSource: SearchSource = SearchSource.BANGUMI,
    onSearchQueryChange: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSearchResultSelect: (SearchResult) -> Unit = {},
    onManualAdd: () -> Unit = {},
    hasSearched: Boolean = false,
    onSearchSourceChange: (SearchSource) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 容器透明化:面板做成浮岛卡片自绘背景,
        // 周围透出被 scrim 暗化的主界面内容
        containerColor = Color.Transparent,
        shape = SquircleShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // 浮岛面板:内容少时自适应高度,搜索结果多时撑满剩余空间(fill = false)
                // 底部留 16.dp 冗余,避免面板贴死屏幕底边
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .clip(SquircleShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    // 顶部拖拽指示条（自绘，随浮岛背景）
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(SquircleShape(2.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )

                    Text(
                        text = stringResource(R.string.home_add_new_anime),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = {
                        Text(
                            stringResource(R.string.home_search_anime_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    shape = SquircleShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    leadingIcon = {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.SEARCH),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        SearchSourceDropdown(
                            selectedSource = searchSource,
                            onSourceChange = onSearchSourceChange,
                            onSearch = {
                                keyboardController?.hide()
                                onSearch()
                            },
                            onClear = { onSearchQueryChange("") },
                            hasQuery = searchQuery.isNotBlank()
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        onSearch()
                    })
                )

                if (searchError != null) {
                    Text(
                        text = searchError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                    )
                }

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val lazyListState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()
                    val flingBehavior = ScrollableDefaults.flingBehavior()

                    // 列表撑满面板剩余空间,「手动添加」按钮悬浮叠在列表底部居中,
                    // 列表内容延伸到按钮四周保持可见
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(Unit) {
                                val velocityTracker = VelocityTracker()
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        velocityTracker.resetTracking()
                                    },
                                    onDragEnd = {
                                        val velocity = velocityTracker.calculateVelocity().y
                                        coroutineScope.launch {
                                            lazyListState.scroll {
                                                with(flingBehavior) {
                                                    performFling(-velocity)
                                                }
                                            }
                                        }
                                        velocityTracker.resetTracking()
                                    },
                                    onDragCancel = {
                                        velocityTracker.resetTracking()
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        coroutineScope.launch {
                                            lazyListState.scroll { scrollBy(-dragAmount) }
                                        }
                                    }
                                )
                            }
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            userScrollEnabled = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            // 底部留出悬浮按钮高度,保证最后一项能滚出按钮遮挡区
                            contentPadding = PaddingValues(top = 4.dp, bottom = 68.dp)
                        ) {
                            items(
                                count = searchResults.size,
                                key = { "${searchResults[it].source}_${searchResults[it].sourceId}" },
                                contentType = { "search_result" }
                            ) { index ->
                                SearchResultItem(
                                    result = searchResults[index],
                                    onClick = { onSearchResultSelect(searchResults[index]) }
                                )
                            }
                        }

                        // 悬浮「手动添加」按钮:叠在列表之上,不截断列表
                        ManualAddFloatingButton(
                            onClick = onManualAdd,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }
                } else if (hasSearched && !isSearching && searchResults.isEmpty() && searchError == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_anime_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }

                // 「手动添加」按钮:无搜索结果时在面板底部正常排布;
                // 有结果时改为悬浮在列表之上(见上方),不占独立排位
                if (searchResults.isEmpty()) {
                    ManualAddFloatingButton(
                        onClick = onManualAdd,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp, bottom = 16.dp)
                    )
                }
                }
            }
        }
    }
}

/**
 * 底部「手动添加番剧」按钮(悬浮于列表之上,底部居中,包裹文字):
 * surfaceContainer 实底 + 细描边,无阴影;实底用于压住下方列表内容保证可读。
 * Dialog 窗口内玻璃/模糊的 GraphicsLayer 内容采样会触发设备级 native 崩溃
 * (主窗口正常),sheet 内不做玻璃效果。
 */
@Composable
private fun ManualAddFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = SquircleShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(colorScheme.surfaceContainer)
            .border(width = 1.dp, color = colorScheme.outline, shape = shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = stringResource(R.string.home_manual_add),
            color = colorScheme.primary,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun SearchSourceDropdown(
    selectedSource: SearchSource,
    onSourceChange: (SearchSource) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    hasQuery: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val allText = stringResource(R.string.common_all)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (hasQuery) {
            Icon(
                painter = rememberAppIconPainter(AppIcon.SEARCH),
                contentDescription = stringResource(R.string.common_search),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onSearch() },
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = rememberAppIconPainter(AppIcon.CLOSE),
                contentDescription = stringResource(R.string.common_clear),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClear() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(SquircleShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = when (selectedSource) {
                        SearchSource.BANGUMI -> "Bangumi"
                        SearchSource.TMDB -> "TMDB"
                        SearchSource.ALL -> allText
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ARROW_DROP_DOWN),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = SquircleShape(12.dp)
            ) {
                SearchSource.entries.forEach { source ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = when (source) {
                                    SearchSource.BANGUMI -> "Bangumi"
                                    SearchSource.TMDB -> "TMDB"
                                    SearchSource.ALL -> allText
                                },
                                fontSize = 14.sp,
                                fontWeight = if (source == selectedSource) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (source == selectedSource) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            onSourceChange(source)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val yearMonthFormat = stringResource(R.string.home_date_year_month_format)
    val allText = stringResource(R.string.common_all)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = SquircleShape(14.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolveCoverModel(result.coverUrl))
                .bitmapConfig(Bitmap.Config.HARDWARE)
                .build(),
            contentDescription = result.title,
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    shape = SquircleShape(8.dp)
                    clip = true
                },
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val score = result.rating
                if (score != null && score > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.STAR_SHINE),
                            contentDescription = null,
                            tint = LocalAnimeColors.current.starFilled,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = String.format("%.1f", score),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LocalAnimeColors.current.starFilled
                        )
                    }
                }
            }

            if (!result.summary.isNullOrBlank()) {
                Text(
                    text = result.summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!result.airDate.isNullOrBlank()) {
                    val formattedDate = try {
                        val parts = result.airDate.split("-")
                        if (parts.size >= 2) String.format(yearMonthFormat, parts[0], parts[1].toInt()) else result.airDate
                    } catch (_: Exception) { result.airDate }
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                Text(
                    text = result.episodeCountText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // 来源标识
                Text(
                    text = "·",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = when (result.source) {
                        SearchSource.BANGUMI -> "Bangumi"
                        SearchSource.TMDB -> "TMDB"
                        SearchSource.ALL -> allText
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
internal fun AddAnimeFormDialog(
    formState: AddAnimeFormState,
    formError: String?,
    onFormStateChange: (AddAnimeFormState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val watchedEpisodesError = if (formState.watchedEpisodes > formState.totalEpisodes) {
        stringResource(R.string.home_watched_exceeds_total)
    } else {
        null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp),
            shape = SquircleShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                FormDialogHeader(formState = formState)

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .imePadding(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    item {
                        AddAnimeForm(
                            formState = formState,
                            onFormStateChange = onFormStateChange,
                            watchedEpisodesError = watchedEpisodesError
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = SquircleShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = SquircleShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        enabled = formError == null && formState.title.isNotBlank()
                    ) {
                        Text(
                            text = stringResource(R.string.common_save),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormDialogHeader(
    formState: AddAnimeFormState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (formState.coverUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolveCoverModel(formState.coverUrl))
                    .bitmapConfig(Bitmap.Config.HARDWARE)
                    .build(),
                contentDescription = formState.title,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(2f / 3f)
                    .graphicsLayer {
                        shape = SquircleShape(10.dp)
                        clip = true
                    },
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formState.title.ifBlank { stringResource(R.string.home_new_anime) },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            val episodeText = if (formState.totalEpisodes > 0) {
                stringResource(R.string.home_total_episodes_format, formState.totalEpisodes)
            } else if (formState.currentEpisodes > 0) {
                stringResource(R.string.home_ongoing_with_eps_format, formState.currentEpisodes)
            } else {
                stringResource(R.string.home_ongoing)
            }

            Text(
                text = episodeText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (formState.airDate != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formState.airDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
