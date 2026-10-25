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
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.CardInfoField
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.theme.LocalAnimeColors
import com.aiexile.animetrack.ui.theme.LocalCardInfoFields
import com.aiexile.animetrack.util.coverMemoryCacheKey
import com.aiexile.animetrack.util.formatAirDateShort
import com.aiexile.animetrack.util.isUnaired
import com.aiexile.animetrack.util.resolveCoverModel
import com.aiexile.animetrack.util.weekdayFromAirDate
import androidx.compose.foundation.layout.offset

private val CardCornerRadius = 16.dp
private val CoverAspectRatio = 2f / 3f

// SquircleShape 实例顶层化复用：避免在 Composable 函数体内反复 new，
// 使 SquircleShape 内置的 size 级 Outline 缓存能跨重组/跨 item 命中。
private val CardShape = SquircleShape(CardCornerRadius)
private val CoverTopShape = SquircleShape(topStart = CardCornerRadius, topEnd = CardCornerRadius)
private val DropdownShape = SquircleShape(16.dp)
private val BadgeShape = SquircleShape(6.dp)

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
                    
                    // 先判断两侧是否有内容：无值的一侧不占位，另一侧可占满整行；
                    // 两侧都有内容时日期省略年份，把宽度让给同行的另一个字段
                    val bothSlotsFilled = cardInfoFieldHasValue(cardInfoFields.left, anime) &&
                        cardInfoFieldHasValue(cardInfoFields.right, anime)
                    val leftInfo = cardInfoValue(cardInfoFields.left, anime, omitDateYear = bothSlotsFilled)
                    val rightInfo = cardInfoValue(cardInfoFields.right, anime, omitDateYear = bothSlotsFilled)
                    when {
                        // 两侧都有值时按内容自然宽度靠两端排布：总宽放得下就都完整显示，
                        // 放不下时由右侧先省略（左侧字段优先，通常是日期这类不该截断的信息）
                        leftInfo != null && rightInfo != null -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(contentAlignment = Alignment.CenterStart) {
                                CardInfoValueContent(leftInfo)
                            }
                            Box(contentAlignment = Alignment.CenterEnd) {
                                CardInfoValueContent(rightInfo)
                            }
                        }
                        leftInfo != null -> Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            CardInfoValueContent(leftInfo)
                        }
                        rightInfo != null -> Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            CardInfoValueContent(rightInfo)
                        }
                    }
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

/** 卡片信息栏单个槽位解析后的内容；null 表示该槽位无内容（选「不显示」或字段本身无值）。 */
private sealed interface CardInfoValue {
    /** 普通文本 */
    data class Plain(val text: String) : CardInfoValue
    /** 评分：星星以内联内容渲染，与数字共用文本基线 */
    data class Rating(val score: Float) : CardInfoValue
}

/**
 * 槽位字段在 [anime] 上是否有内容，判空口径与 [cardInfoValue] 保持一致。
 * 布局据此决定是否让另一侧占满整行、以及日期是否省略年份。
 */
private fun cardInfoFieldHasValue(field: CardInfoField, anime: Anime): Boolean = when (field) {
    CardInfoField.NONE -> false
    CardInfoField.PROGRESS -> true
    CardInfoField.RATING -> anime.rating != null
    CardInfoField.AIR_DATE -> formatAirDateShort(anime.airDate) != null
    CardInfoField.AIR_WEEKDAY -> {
        val weekday = anime.airWeekday?.takeIf { it in 1..7 }
            ?: weekdayFromAirDate(anime.airDate)
        weekday != null && shouldShowAirWeekday(anime)
    }
    CardInfoField.UNWATCHED -> anime.effectiveMaxEpisodes - anime.watchedEpisodes > 0
}

/**
 * 放送星期只对连载中/未放送的番剧有意义：
 * 完结番剧的「周几更新」已没有信息价值，一律不占卡片位置。
 * isFinished 已包含用户手动覆盖（airingStatusOverride）的结果。
 */
private fun shouldShowAirWeekday(anime: Anime): Boolean =
    !anime.isFinished || isUnaired(anime.airDate)

/**
 * 解析槽位字段在 [anime] 上的实际内容。字段无值返回 null，
 * 让布局据此决定另一侧是否需要占满整行（而不是统一按半宽裁切）。
 *
 * @param omitDateYear 两侧字段同时有内容时传 true：日期省略年份，
 *   否则两个字段并排会被卡片宽度截断。
 */
@Composable
private fun cardInfoValue(
    field: CardInfoField,
    anime: Anime,
    omitDateYear: Boolean = false
): CardInfoValue? = when (field) {
    CardInfoField.NONE -> null
    CardInfoField.PROGRESS -> CardInfoValue.Plain(
        if (anime.totalEpisodes > 0) {
            "${anime.watchedEpisodes}/${anime.totalEpisodes}"
        } else if (anime.currentEpisodes > 0) {
            "${anime.watchedEpisodes}/${anime.currentEpisodes}"
        } else {
            "${anime.watchedEpisodes}"
        }
    )
    CardInfoField.RATING -> anime.rating?.let { CardInfoValue.Rating(it) }
    CardInfoField.AIR_DATE -> formatAirDateShort(anime.airDate, omitDateYear)?.let { CardInfoValue.Plain(it) }
    CardInfoField.AIR_WEEKDAY -> {
        // airWeekday 缺失时用首播日期推算，只有年月格式的番剧推不出星期（返回 null）
        val weekday = anime.airWeekday?.takeIf { it in 1..7 }
            ?: weekdayFromAirDate(anime.airDate)
        if (weekday != null && shouldShowAirWeekday(anime)) {
            weekdayLabel(weekday)?.let { CardInfoValue.Plain(it) }
        } else {
            null
        }
    }
    CardInfoField.UNWATCHED -> {
        // 只显示「还有几集没看」；追平或未知总集数时不显示
        val remaining = anime.effectiveMaxEpisodes - anime.watchedEpisodes
        if (remaining > 0) CardInfoValue.Plain("+$remaining") else null
    }
}

@Composable
private fun CardInfoValueContent(value: CardInfoValue) {
    when (value) {
        is CardInfoValue.Plain -> CardInfoText(value.text)
        is CardInfoValue.Rating -> RatingText(value.score)
    }
}

@Composable
private fun CardInfoText(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RatingText(rating: Float) {
    // 星星以内联内容嵌入文本，与数字共用同一文本基线，保证垂直对齐
    val ratingText = buildAnnotatedString {
        appendInlineContent("star", "★")
        // 窄空格（U+2009，约普通空格一半宽），星星与数字间距适中
        append(" " + String.format("%.1f", rating))
    }
    Text(
        text = ratingText,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = LocalAnimeColors.current.starFilled,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        inlineContent = mapOf(
            "star" to InlineTextContent(
                Placeholder(
                    // 略高于数字大写高度，底部压在基线上保持底部对齐
                    width = 12.5.sp,
                    height = 12.5.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline
                )
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.STAR_SHINE),
                    contentDescription = null,
                    tint = LocalAnimeColors.current.starFilled,
                    // 下移使星星相对数字上下对称超出（AboveBaseline 底部贴基线，超出量全在顶部会显偏高）
                    modifier = Modifier.fillMaxSize().offset(y = 2.dp)
                )
            }
        )
    )
}

@Composable
private fun weekdayLabel(weekday: Int?): String? = when (weekday) {
    1 -> stringResource(R.string.weekday_mon)
    2 -> stringResource(R.string.weekday_tue)
    3 -> stringResource(R.string.weekday_wed)
    4 -> stringResource(R.string.weekday_thu)
    5 -> stringResource(R.string.weekday_fri)
    6 -> stringResource(R.string.weekday_sat)
    7 -> stringResource(R.string.weekday_sun)
    else -> null
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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CoverAspectRatio)
    ) {
        // 共享元素转场时圆角随进度连续插值（底部直角→圆角），避免两侧静态
        // clip 切换导致的跳变；非转场场景保持静态 CoverTopShape
        val coverClipShape = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            rememberSharedCoverClipShape(
                animatedVisibilityScope = animatedVisibilityScope,
                cornerRadius = CardCornerRadius,
                detailSide = false
            )
        } else {
            CoverTopShape
        }
        val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier
                    .fillMaxSize()
                    .sharedElement(
                        rememberSharedContentState(key = "${sharedElementKeyPrefix}cover_${animeId}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .clip(coverClipShape)
            }
        } else {
            Modifier
                .fillMaxSize()
                .clip(coverClipShape)
        }
        
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
                modifier = sharedModifier
            )
        } else {
            EmptyCoverPlaceholder(
                shape = CoverTopShape
            )
        }
        
        StatusBadge(
            status = status,
            unaired = isUnaired(airDate),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )

        // 置顶角标：左下角小图钉，标识该卡片固定排在列表最前
        if (isPinned) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                shape = BadgeShape,
                // 黑白简洁暗色下角标 scrim（与 StatusBadge 同色），其余主题沿用 primary
                color = LocalAnimeColors.current.statusBadgeContainer
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.VERTICAL_ALIGN_TOP),
                    contentDescription = stringResource(R.string.common_pin),
                    tint = LocalAnimeColors.current.statusBadgeContent,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(12.dp)
                )
            }
        }

        // 收藏角标：右下角小心形，标识该番剧已加入收藏
        if (isFavorite) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                shape = BadgeShape,
                color = LocalAnimeColors.current.statusBadgeContainer
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.FAVORITE),
                    contentDescription = stringResource(R.string.common_favorite),
                    tint = LocalAnimeColors.current.statusBadgeContent,
                    modifier = Modifier
                        .padding(2.dp)
                        .size(12.dp)
                )
            }
        }
    }
}

@Composable
internal fun StatusBadge(
    status: AnimeStatus,
    modifier: Modifier = Modifier,
    unaired: Boolean = false
) {
    // 黑白简洁暗色下为半透明黑 scrim + 浅字（见 Theme.kt 中 AnimeColors 的计算），
    // 其余主题/明暗模式保持 primary 实底样式
    val containerColor = LocalAnimeColors.current.statusBadgeContainer
    val contentColor = LocalAnimeColors.current.statusBadgeContent
    val text = if (unaired) {
        stringResource(R.string.status_unaired)
    } else {
        status.displayName
    }
    Surface(
        modifier = modifier,
        shape = BadgeShape,
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = contentColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
