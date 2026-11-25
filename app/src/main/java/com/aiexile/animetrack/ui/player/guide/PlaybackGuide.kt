package com.aiexile.animetrack.ui.player.guide

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.FeatureSpotlight
import com.aiexile.animetrack.ui.components.chrome.AppScaffold
import com.aiexile.animetrack.ui.components.chrome.AppScaffoldPadding
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PLAYBACK_GUIDE_AUTO_START_DELAY_MS = 500L

internal enum class PlaybackGuidePage(val preferenceId: String) {
    OVERVIEW("overview"),
    WEB("web"),
    LOCAL("local"),
    WEBDAV("webdav")
}

internal data class PlaybackGuideStep(@param:StringRes val title: Int, @param:StringRes val message: Int)

@OptIn(ExperimentalFoundationApi::class)
internal class PlaybackGuideState(
    private val position: MutableIntState,
    private val onStart: () -> Unit,
    private val onFinish: () -> Unit
) {
    internal data class Anchor(val requester: BringIntoViewRequester, val bounds: Rect)

    private val anchors = mutableStateMapOf<Int, Anchor>()
    val index: Int get() = position.intValue

    fun start() {
        onStart()
        position.intValue = 0
    }

    fun goTo(index: Int) {
        position.intValue = index
    }

    fun finish() {
        position.intValue = -1
        onFinish()
    }

    fun findAnchor(id: Int): Anchor? = anchors[id]

    @Composable
    fun anchor(@StringRes id: Int): Modifier {
        val requester = remember(this, id) { BringIntoViewRequester() }
        DisposableEffect(this, id, requester) {
            onDispose {
                if (anchors[id]?.requester === requester) anchors.remove(id)
            }
        }
        return Modifier.bringIntoViewRequester(requester).onGloballyPositioned {
            anchors[id] = Anchor(requester, it.boundsInWindow())
        }
    }
}

@Composable
internal fun rememberPlaybackGuide(
    page: PlaybackGuidePage,
    ready: Boolean = true,
    settingsRepository: SettingsRepository = AppContainer.getSettingsRepository()
): PlaybackGuideState {
    val position = rememberSaveable(page) { mutableIntStateOf(-1) }
    var autoOffered by rememberSaveable(page) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val guide = remember(page, settingsRepository, scope, position) {
        PlaybackGuideState(
            position = position,
            onStart = { autoOffered = true },
            onFinish = { scope.launch { settingsRepository.markPlaybackGuideSeen(page.preferenceId) } }
        )
    }
    val seenPages by settingsRepository.playbackGuideSeenPages.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(lifecycle, guide, ready, seenPages, autoOffered) {
        val seen = seenPages ?: return@LaunchedEffect
        if (!ready || autoOffered || page.preferenceId in seen) return@LaunchedEffect

        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(PLAYBACK_GUIDE_AUTO_START_DELAY_MS)
            if (!autoOffered) guide.start()
        }
    }
    return guide
}

@Composable
internal fun PlaybackGuideAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.INFO),
            contentDescription = stringResource(R.string.player_guide_title)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackGuideScaffold(
    guide: PlaybackGuideState?,
    steps: List<PlaybackGuideStep>,
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (AppScaffoldPadding) -> Unit
) {
    val index = guide?.index ?: -1
    val step = steps.getOrNull(index)
    val anchor = step?.let { guide?.findAnchor(it.title) }
    val requester = anchor?.requester
    LaunchedEffect(index, requester) {
        requester?.bringIntoView()
    }
    val bounds = anchor?.bounds?.takeIf { it.width > 0 && it.height > 0 }
    val visible = bounds != null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (visible) Modifier.clearAndSetSemantics { } else Modifier)) {
            AppScaffold(
                title = title,
                onBack = onBack,
                actions = actions,
                snackbarHost = snackbarHost,
                content = content
            )
        }
        if (guide != null && step != null && bounds != null) {
            FeatureSpotlight(
                target = bounds,
                title = stringResource(step.title),
                message = stringResource(step.message),
                step = index,
                total = steps.size,
                onDismiss = guide::finish,
                onPrevious = { guide.goTo(index - 1) },
                onNext = {
                    if (index == steps.lastIndex) guide.finish() else guide.goTo(index + 1)
                }
            )
        }
    }
}
