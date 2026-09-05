package com.aiexile.animetrack.ui.home

import androidx.compose.foundation.background
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.aiexile.animetrack.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.data.FabLocation
import com.aiexile.animetrack.ui.components.AdvancedBlurConfig
import com.aiexile.animetrack.ui.components.advancedHazeEffect
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/**
 * 主页顶部栏（独立 Composable，用于在 SharedTransitionLayout 外层渲染，
 * 避免转场期间被共享元素 Overlay 遮盖）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    viewModel: HomeViewModel,
    customGreeting: String,
    greetingTypingEffect: Boolean,
    showSearchButton: Boolean,
    fabLocation: FabLocation,
    isCurrentPage: Boolean,
    hasAnime: Boolean,
    hasFilteredItems: Boolean,
    onAddClick: () -> Unit,
    alwaysShowAddButton: Boolean = false,
    morphCollapse: Float = 0f,
    morphTargetColor: Color? = null,
    blurEnabled: Boolean = false,
    hazeState: HazeState? = null,
    blurConfig: AdvancedBlurConfig = AdvancedBlurConfig.DEFAULT,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isLocalSearchActive, isCurrentPage) {
        if (uiState.isLocalSearchActive && isCurrentPage) {
            focusRequester.requestFocus()
        }
    }

    // morph 收拢时背景色向 FAB 容器色插值（收拢终点与 FAB 重合，避免色块断层）；
    // 毛玻璃模式下背景由 hazeEffect 提供（底色透明），收拢全程与 FAB 毛玻璃一致
    val containerColor = if (blurEnabled) {
        Color.Transparent
    } else if (morphTargetColor != null && morphCollapse > 0f) {
        lerp(MaterialTheme.colorScheme.surface, morphTargetColor, morphCollapse)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEnabled && hazeState != null) {
                    // 顶栏毛玻璃背景：参数与 FAB 一致；收拢时被外层 morph 裁剪，
                    // 毛玻璃随几何同步收缩，与 FAB 毛玻璃无缝衔接
                    advancedHazeEffect(hazeState, blurConfig)
                } else {
                    Modifier.background(containerColor)
                }
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        if (uiState.isLocalSearchActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.SEARCH),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                BasicTextField(
                    value = TextFieldValue(
                        text = uiState.localSearchQuery,
                        selection = TextRange(uiState.localSearchQuery.length)
                    ),
                    onValueChange = { newValue ->
                        viewModel.updateLocalSearchQuery(newValue.text)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (uiState.localSearchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.home_search_anime),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (uiState.localSearchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.updateLocalSearchQuery("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = rememberAppIconPainter(AppIcon.CLOSE),
                            contentDescription = stringResource(R.string.common_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = {
                        viewModel.clearLocalSearch()
                    }
                ) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                        contentDescription = stringResource(R.string.home_close_search),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val greetingText = viewModel.resolveGreetingText(customGreeting)
                TypingGreeting(
                    greetingText = greetingText,
                    shouldAnimate = greetingTypingEffect && viewModel.shouldAnimateGreeting(greetingText),
                    onAnimated = { viewModel.onGreetingAnimated(it) },
                    // morph 收拢早期（0→0.35）问候语先行淡出，右侧按钮保留到终点与 FAB 图标交接
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - (morphCollapse / 0.35f).coerceIn(0f, 1f)
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val showSearchIcon = showSearchButton && hasAnime && hasFilteredItems
                    if (showSearchIcon) {
                        IconButton(onClick = { viewModel.startLocalSearch() }) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.SEARCH),
                                contentDescription = stringResource(R.string.common_search),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // 大屏（alwaysShowAddButton）无 FAB，顶栏始终提供添加入口。
                    // 与迁移 FAB 布局严格一致（48+1+48，无间距），morph 收拢时按钮位置零偏移
                    if (fabLocation == FabLocation.TOP_BAR || alwaysShowAddButton) {
                        if (showSearchIcon) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        IconButton(onClick = onAddClick) {
                            Icon(
                                painter = rememberAppIconPainter(AppIcon.ADD),
                                contentDescription = stringResource(R.string.home_add_anime),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
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
