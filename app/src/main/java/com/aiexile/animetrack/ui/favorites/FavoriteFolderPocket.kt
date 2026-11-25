package com.aiexile.animetrack.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.favorites.FavoriteFolderSummary
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlin.math.abs
import kotlin.math.roundToInt

internal val FavoriteGridSpacing = 12.dp
internal val FavoriteSectionSpacing = 16.dp
internal const val FolderContinuationStart = 0.48f
internal const val OpenFolderContentType = "open_favorite_folder"
private val PocketShape = RoundedCornerShape(14.dp)
private val PocketTopShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
private val PocketBottomShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
private val TabShape = RoundedCornerShape(topStart = 7.dp, topEnd = 9.dp)
private val PreviewShape = RoundedCornerShape(5.dp)
private val PocketClosedHeight = 120.dp

@Composable
internal fun FavoriteFolderShelfRow(
    folders: List<FavoriteFolderSummary>,
    columns: Int,
    displayedFolderId: String?,
    requestedFolderId: String?,
    opening: State<Float>,
    previews: Map<String, List<String>>,
    hasContinuation: Boolean,
    viewModel: FavoritesViewModel,
    onFolderClick: (String) -> Unit,
    contents: @Composable (Float) -> Unit,
    actions: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val activeIndex = folders.indexOfFirst { it.id == displayedFolderId }
        val progress = if (activeIndex >= 0) opening.value else 0f
        val separateFlap = hasContinuation && activeIndex >= 0 && progress >= FolderContinuationStart

        folders.forEachIndexed { index, folder ->
            val active = index == activeIndex
            if (active || progress < FolderContinuationStart) {
                key(folder.id) {
                    val expansion = if (active) progress else 0f
                    FavoriteFolderPocket(
                        folder = folder,
                        viewModel = viewModel,
                        progress = expansion,
                        expanded = active && folder.id == requestedFolderId,
                        onClick = { onFolderClick(folder.id) },
                        previewCovers = previews[folder.id].orEmpty(),
                        separateFlap = active && separateFlap,
                        modifier = Modifier
                            .folderWidth(maxWidth, columns, index, expansion)
                            .zIndex(if (active) 1f else 0f)
                            .graphicsLayer { alpha = if (active) 1f else 1f - progress / FolderContinuationStart },
                        contents = { if (active) contents(expansion) },
                        actions = { if (active) actions() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteFolderPocket(
    folder: FavoriteFolderSummary,
    viewModel: FavoritesViewModel,
    progress: Float,
    expanded: Boolean,
    onClick: () -> Unit,
    previewCovers: List<String>,
    separateFlap: Boolean,
    modifier: Modifier = Modifier,
    contents: @Composable () -> Unit,
    actions: @Composable () -> Unit
) {
    val fullPocketClickable = progress < 0.35f
    val toggle = folderToggle(expanded, onClick)
    val shape = if (separateFlap) PocketTopShape else PocketShape
    val flapHeight = folderFlapHeight(progress)
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (fullPocketClickable) toggle else Modifier)
            .heightIn(min = if (separateFlap) PocketClosedHeight - flapHeight else PocketClosedHeight)
    ) {
        Box(
            Modifier.matchParentSize().padding(top = 10.dp)
                .background(folderBackColor(), shape)
        )
        Box(
            Modifier.padding(start = 10.dp, top = 3.dp).size(width = 40.dp, height = 15.dp)
                .background(folderBackColor(), TabShape)
        )
        if (previewCovers.isNotEmpty() && progress < FolderContinuationStart) {
            FavoriteFolderPreviewStack(
                previewCovers,
                progress,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 43.dp)
            )
        }
        Box(
            Modifier.fillMaxWidth()
                .padding(start = 4.dp, top = 16.dp, end = 4.dp, bottom = if (separateFlap) 0.dp else flapHeight)
        ) {
            contents()
        }
        if (!separateFlap) {
            FavoriteFolderFlap(
                folder, viewModel, progress, expanded,
                onClick = onClick.takeUnless { fullPocketClickable },
                modifier = Modifier.align(Alignment.BottomCenter),
                actions = actions
            )
        }
    }
}

@Composable
private fun FavoriteFolderPreviewStack(
    covers: List<String>,
    progress: Float,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val cardWidth = (maxWidth * 0.38f).coerceAtMost(43.dp)
        val step = (maxWidth * 0.17f).coerceAtMost(22.dp)
        covers.forEachIndexed { index, cover ->
            val offset = index - covers.lastIndex / 2f
            val distanceFromCenter = abs(offset)
            FavoriteFolderPreviewCover(
                coverUrl = cover,
                modifier = Modifier
                    .offset(x = step * offset, y = 5.dp * (distanceFromCenter - 1f))
                    .size(width = cardWidth, height = cardWidth * 1.5f)
                    .zIndex(-distanceFromCenter)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        rotationZ = offset * 10f
                        alpha = 1f - progress / FolderContinuationStart
                    }
                    .clip(PreviewShape)
            )
        }
    }
}

@Composable
internal fun FavoriteFolderContinuation(
    columns: Int,
    folderColumn: Int,
    progress: Float,
    isFlap: Boolean = false,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .folderWidth(maxWidth, columns, folderColumn, progress)
                .background(folderBackColor(), if (isFlap) PocketBottomShape else RoundedCornerShape(0.dp))
        ) {
            content()
        }
    }
}

@Composable
internal fun FavoriteFolderFlap(
    folder: FavoriteFolderSummary,
    viewModel: FavoritesViewModel,
    progress: Float,
    expanded: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val frontColor = lerp(colors.tertiaryContainer, colors.tertiary, 0.12f)
    val labelColor = if (folder.hasCover) Color.White else colors.onTertiaryContainer
    Box(modifier.fillMaxWidth().height(folderFlapHeight(progress))) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    cameraDistance = 420.dp.toPx()
                    rotationX = -45f * progress
                    clip = true
                    shape = PocketShape
                }
                .background(frontColor)
        ) {
            if (folder.hasCover) {
                FavoriteFolderCover(folder, viewModel, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.54f)))
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f)
                    .then(if (onClick != null) folderToggle(expanded, onClick) else Modifier)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        color = labelColor,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.favorites_count_format, folder.animeCount),
                        color = labelColor.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress > 0.85f) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_DOWN),
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(20.dp).rotate(180f)
                    )
                }
            }
            if (expanded && progress > 0.92f) actions()
        }
    }
}

@Composable
internal fun Modifier.closeFolderOnPinch(
    contains: (Offset) -> Boolean,
    onClose: (() -> Unit)?,
    onProgress: ((Float?) -> Unit)?
): Modifier {
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnProgress by rememberUpdatedState(onProgress)
    return pointerInput(contains) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val close = currentOnClose ?: return@awaitEachGesture
            if (!contains(first.position)) return@awaitEachGesture

            val touchSlop = viewConfiguration.touchSlop
            var event = awaitPointerEvent(PointerEventPass.Initial)
            var pressed = event.changes.filter { it.pressed }
            while (pressed.size == 1) {
                val change = pressed.single()
                if (change.id != first.id || (change.position - first.position).getDistance() > touchSlop) {
                    return@awaitEachGesture
                }
                event = awaitPointerEvent(PointerEventPass.Initial)
                pressed = event.changes.filter { it.pressed }
            }
            if (pressed.size != 2 || pressed.any { !contains(it.position) }) return@awaitEachGesture

            val initialDistance = (pressed[0].position - pressed[1].position).getDistance()
            val closeThreshold = maxOf(initialDistance * 0.2f, touchSlop * 2f)
            val reportProgress = currentOnProgress
            var canClose = true

            // 两指落下后接管整段触摸，收起动画开始后也要等最后一指抬起。
            try {
                while (true) {
                    event.changes.forEach { it.consume() }
                    if (pressed.isEmpty()) break
                    canClose = canClose && pressed.size == 2
                    if (canClose) {
                        val distance = (pressed[0].position - pressed[1].position).getDistance()
                        val inwardDistance = initialDistance - distance
                        reportProgress?.invoke((inwardDistance / closeThreshold).coerceIn(0f, 1f))
                        if (inwardDistance >= closeThreshold) {
                            canClose = false
                            close()
                        }
                    }
                    event = awaitPointerEvent(PointerEventPass.Initial)
                    pressed = event.changes.filter { it.pressed }
                }
            } finally {
                reportProgress?.invoke(null)
            }
        }
    }
}

@Composable
private fun folderBackColor(): Color {
    val colors = MaterialTheme.colorScheme
    return lerp(colors.surfaceContainerHighest, colors.tertiaryContainer, 0.5f)
}

@Composable
private fun folderToggle(expanded: Boolean, onClick: () -> Unit): Modifier = Modifier.clickable(
    onClickLabel = stringResource(if (expanded) R.string.favorite_folder_close else R.string.favorite_folder_open),
    interactionSource = null,
    indication = null,
    role = Role.Button,
    onClick = onClick
)

private fun folderFlapHeight(progress: Float): Dp = lerp(69.dp, 72.dp, progress)

private fun Modifier.folderWidth(maxWidth: Dp, columns: Int, index: Int, progress: Float): Modifier {
    val cellWidth = ((maxWidth - FavoriteGridSpacing * (columns - 1)) / columns).coerceAtLeast(0.dp)
    return offset(x = (cellWidth + FavoriteGridSpacing) * (index * (1f - progress)))
        .width(lerp(cellWidth, maxWidth, progress))
}

@Composable
internal fun FavoriteFolderAnimeRow(
    animes: List<Anime>,
    columns: Int,
    progress: Float,
    modifier: Modifier = Modifier,
    fanOut: Boolean = true,
    card: @Composable (Anime, Modifier) -> Unit
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Row(
        modifier = modifier.fillMaxWidth().revealInPocket(progress, minHeight = if (fanOut) 0.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(FavoriteGridSpacing)
    ) {
        animes.forEachIndexed { index, anime ->
            key(anime.id) {
                val delay = 0.13f + index * 0.035f
                val reveal = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
                val stagger = if (fanOut) (animes.lastIndex / 2f - index) * direction else 0f
                val hidden = if (progress < 0.9f) Modifier.clearAndSetSemantics { } else Modifier
                card(
                    anime,
                    Modifier.weight(1f)
                        .then(hidden)
                        .graphicsLayer {
                            val folded = 1f - reveal
                            translationX = stagger * size.width * folded * 0.72f
                            translationY = 44.dp.toPx() * folded
                            rotationZ = -stagger * folded * 11f
                            scaleX = 0.7f + 0.3f * reveal
                            scaleY = scaleX
                            alpha = (reveal * 2.5f).coerceAtMost(1f)
                        }
                )
            }
        }
        repeat(columns - animes.size) { Spacer(Modifier.weight(1f)) }
    }
}

internal fun Modifier.revealInPocket(progress: Float, minHeight: Dp = 0.dp): Modifier = clipToBounds().layout { measurable, constraints ->
    val content = measurable.measure(constraints.copy(minHeight = 0))
    val height = (content.height * progress).roundToInt().coerceAtLeast(minHeight.roundToPx().coerceAtMost(content.height))
    layout(content.width, height) {
        content.placeRelative(0, 0)
    }
}
