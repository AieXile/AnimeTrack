package com.aiexile.animetrack.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.theme.LocalCardInfoFields
import com.aiexile.animetrack.ui.theme.LocalCardCoverFullyRounded
import com.aiexile.animetrack.util.coverMemoryCacheKey
import com.aiexile.animetrack.util.isUnaired
import com.aiexile.animetrack.util.resolveCoverModel

private val CardCornerRadius = 16.dp
private val CoverAspectRatio = 2f / 3f

// SquircleShape 实例顶层化复用：避免在 Composable 函数体内反复 new，
// 使 SquircleShape 内置的 size 级 Outline 缓存能跨重组/跨 item 命中。
private val CardShape = SquircleShape(CardCornerRadius)
private val CoverTopShape = SquircleShape(topStart = CardCornerRadius, topEnd = CardCornerRadius)
private val DropdownShape = SquircleShape(16.dp)

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    isSelected: Boolean = false,
    isHighlighted: Boolean = false,
    onStatusChange: (AnimeStatus) -> Unit = {},
    onDelete: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onEditProgress: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** 共享元素 key 命名空间（如收藏页前缀），空串表示主页 */
    sharedElementKeyPrefix: String = "",
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    // 信息栏左右槽位字段由全局配置下发（MainActivity），默认与历史表现一致：进度 + 评分
    val cardInfoFields = LocalCardInfoFields.current

    // 按下缩放动效：按下时缩小，松手弹回（与看板界面 CoverCard 一致）
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pressScale"
    )

    val highlightTriggerCount = remember { mutableStateOf(0) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightTriggerCount.value++
        }
    }
    
    val highlightScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(highlightTriggerCount.value) {
        if (highlightTriggerCount.value > 0) {
            highlightScale.animateTo(
                targetValue = 0.92f,
                animationSpec = tween(durationMillis = 150)
            )
            highlightScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.4f,
                    stiffness = 400f
                )
            )
        }
    }
    
    val selectScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            selectScale.animateTo(
                targetValue = 0.92f,
                animationSpec = tween(durationMillis = 150)
            )
            selectScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.4f,
                    stiffness = 400f
                )
            )
        } else {
            selectScale.snapTo(1f)
        }
    }
    
    val elevation by animateDpAsState(
        targetValue = if (isSelected || isHighlighted) 0.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 400f
        ),
        label = "elevation"
    )
    
    var showStatusMenu by remember { mutableStateOf(false) }

    // 启用共享元素过渡时，外层 Box 不应用 selectScale/highlightScale 缩放，
    // 避免与 sharedElement 动画的缩放叠加导致点击导航时闪烁。
    // 高亮/选择的视觉反馈通过 Card 内部 elevation 和 overlay 体现。
    // pressScale（按下缩放）始终应用，不受 sharedElement 限制。
    val enableScaleFeedback = sharedTransitionScope == null || animatedVisibilityScope == null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = pressScale *
                    (if (enableScaleFeedback) selectScale.value * highlightScale.value else 1f)
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            isPressed = false
                        }
                    }
                }
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    // 黑色低透明度阴影（与悬浮胶囊导航一致）：
                    // outline 着色（浅灰 20%）在白底上混色后与背景几乎无差别，肉眼不可见；
                    // spot 为键光投影（更浓、集中在底边），ambient 为环境光（四周淡晕）。
                    elevation = elevation,
                    shape = CardShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                )
                .clip(CardShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            shape = CardShape,
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            ),
            colors = CardDefaults.cardColors(
                // 亮色用最亮白（与堆叠卡片统一）；暗色 surfaceContainerLowest 过深，
                // 与网格背景几乎无区分，保持原 surfaceContainerLow
                containerColor = if (isAppDarkTheme()) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                }
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimeCoverWithStatus(
                    status = anime.status,
                    coverUrl = anime.coverUrl,
                    title = anime.title,
                    animeId = anime.id,
                    airDate = anime.airDate,
                    isPinned = anime.isPinned,
                    isFavorite = anime.isFavorite,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedElementKeyPrefix = sharedElementKeyPrefix
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = anime.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // 信息栏左右槽位：字段与判空/截断规则与堆叠卡共用 CardInfoRow
                    CardInfoRow(anime = anime, fields = cardInfoFields)
                }
            }
        }
        
        if (isSelected) {
            BoxWithConstraints(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                // 2×2 布局：按钮尺寸随卡片宽度收缩（上限 40dp），
                // 保证两列始终完整落在卡片内，不会被卡片圆角裁切成缺角
                val actionSpacing = 8.dp
                val actionButtonSize = minOf(40.dp, (maxWidth - actionSpacing) / 2f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(actionSpacing)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(actionSpacing)) {
                        // 置顶：未置顶为描边样式；已置顶为 primary 填充（图标+背景反色），再点取消置顶
                        CardOverlayButton(
                            icon = AppIcon.VERTICAL_ALIGN_TOP,
                            contentDescription = stringResource(
                                if (anime.isPinned) R.string.common_unpin else R.string.common_pin
                            ),
                            tint = if (anime.isPinned) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = if (anime.isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLowest,
                            size = actionButtonSize,
                            onClick = onTogglePin
                        )
                        // 收藏：已收藏为 primary 填充，再点取消收藏
                        CardOverlayButton(
                            icon = AppIcon.FAVORITE,
                            contentDescription = stringResource(
                                if (anime.isFavorite) R.string.common_unfavorite else R.string.common_favorite
                            ),
                            tint = if (anime.isFavorite) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                            containerColor = if (anime.isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerLowest,
                            size = actionButtonSize,
                            onClick = onToggleFavorite
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(actionSpacing)) {
                        Box {
                            IconButton(
                                onClick = { showStatusMenu = true },
                                modifier = Modifier
                                    .size(actionButtonSize)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        clip = false,
                                        ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    painter = rememberAppIconPainter(AppIcon.EDIT),
                                    contentDescription = stringResource(R.string.anime_card_change_status),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(actionButtonSize * 0.5f)
                                )
                            }

                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false },
                                shape = DropdownShape,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                AnimeStatus.entries.forEach { status ->
                                    val isSelected = status == anime.status
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
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = status.displayName,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    fontSize = 14.sp,
                                                    textAlign = TextAlign.Center,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            onStatusChange(status)
                                            showStatusMenu = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        CardOverlayButton(
                            icon = AppIcon.DELETE,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            size = actionButtonSize,
                            onClick = onDelete
                        )
                    }
                }
            }
        }
        
        if (isHighlighted && !isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            )
        }
    }
}

/** 长按选中态的操作按钮：圆形、投影底、无涟漪（与卡片状态/置顶按钮样式一致） */
@Composable
private fun CardOverlayButton(
    icon: AppIcon,
    contentDescription: String,
    tint: Color,
    containerColor: Color,
    size: Dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = 4.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                spotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            .background(containerColor, CircleShape)
            .clip(CircleShape)
    ) {
        Icon(
            painter = rememberAppIconPainter(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AnimeCoverWithStatus(
    status: AnimeStatus,
    coverUrl: String?,
    title: String,
    animeId: Int,
    airDate: String? = null,
    isPinned: Boolean = false,
    isFavorite: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedElementKeyPrefix: String = "",
    modifier: Modifier = Modifier
) {
    val enableSharedElement = sharedTransitionScope != null && animatedVisibilityScope != null
    val coverContentState = if (enableSharedElement) {
        with(sharedTransitionScope!!) {
            rememberSharedContentState(key = "${sharedElementKeyPrefix}cover_${animeId}")
        }
    } else {
        null
    }
    // 封面圆角风格由设置下发：四角全圆时卡面与详情封面静止形态一致，
    // 共享元素转场无需再做底部圆角插值，直接用统一静态形状即可
    val coverFullyRounded = LocalCardCoverFullyRounded.current

    // 共享元素转场时圆角随进度连续插值（底部直角→圆角），避免两侧静态
    // clip 切换导致的跳变；非转场场景保持静态 CoverTopShape。
    // 只有确认配对了对端封面、且共享转场确实在进行时才读取进度：
    // 飞行途中切页会打断转场，进度可能残留在中间值上，静止态若不
    // 回落到固定圆角，封面底部会一直保留圆角直到下一次完整转场。
    val coverClipShape = when {
        coverFullyRounded -> CardShape
        enableSharedElement -> rememberSharedCoverClipShape(
            animatedVisibilityScope = animatedVisibilityScope!!,
            cornerRadius = CardCornerRadius,
            detailSide = false,
            animationActive = {
                sharedTransitionScope!!.isTransitionActive && coverContentState!!.isMatchFound
            }
        )
        else -> CoverTopShape
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CoverAspectRatio)
            // 封面共享元素：置顶/收藏小角标随其内容一起飞行；状态角标按设置
            // 走"随封面同层"或"独立飞向详情页标题行"两种策略之一
            .then(
                if (enableSharedElement) {
                    with(sharedTransitionScope!!) {
                        Modifier.sharedElement(
                            coverContentState!!,
                            animatedVisibilityScope = animatedVisibilityScope!!
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // 先解析封面模型：空串脏数据/本地文件缺失均解析为 null，走占位图分支
        val resolvedModel = remember(coverUrl) { resolveCoverModel(coverUrl) }
        if (resolvedModel != null) {
            val context = LocalContext.current
            val request = remember(coverUrl) {
                ImageRequest.Builder(context)
                    .data(resolvedModel)
                    .memoryCacheKey(coverMemoryCacheKey(coverUrl))
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(coverClipShape)
            )
        } else {
            EmptyCoverPlaceholder(
                shape = coverClipShape
            )
        }

        // 置顶/收藏小角标：没有详情页对应落点，随封面共享元素一起飞行
        CoverCornerBadges(
            isPinned = isPinned,
            isFavorite = isFavorite,
            modifier = Modifier.matchParentSize()
        )

        // 观看状态角标：两种转场策略由设置项切换——
        // FOLLOW_COVER 作为封面内容随封面飞行；FLY_TO_TITLE 独立飞向标题行
        CardStatusBadge(
            status = status,
            unaired = isUnaired(airDate),
            animeId = animeId,
            sharedElementKeyPrefix = sharedElementKeyPrefix,
            sharedTransitionScope = sharedTransitionScope.takeIf { enableSharedElement },
            animatedVisibilityScope = animatedVisibilityScope.takeIf { enableSharedElement },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )
    }
}
