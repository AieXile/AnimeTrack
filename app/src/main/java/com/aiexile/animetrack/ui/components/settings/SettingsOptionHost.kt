package com.aiexile.animetrack.ui.components.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.aiexile.animetrack.data.ChromeStyle
import com.aiexile.animetrack.ui.components.chrome.ChromeSources
import com.aiexile.animetrack.ui.theme.LocalChromeStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 菜单状态机（规范 §3.3） */
internal enum class MenuPhase { Closed, Opening, Open, Closing }

/** 菜单渲染用的候选项快照（与泛型解耦，Host 只处理 Any?） */
internal class MenuEntry(
    val value: Any?,
    val label: String,
    val supportingText: String?,
    val enabled: Boolean,
)

/**
 * 一次打开的菜单请求：锚点、选项快照、选中值与回调。
 *
 * [onSelected] 已由行侧包好泛型转换；[onFocusReturn] 在关闭动画结束后把焦点还给触发行。
 */
internal class OptionMenuRequest(
    val owner: Any,
    val anchor: IntRect,
    val paneTitle: String,
    val entries: List<MenuEntry>,
    val selectedValue: Any?,
    val selectedIndex: Int,
    val optionsKey: List<Any?>,
    val onSelected: (Any?) -> Unit,
    val onFocusReturn: () -> Unit,
)

/** 页面级浮层状态：同一页面只允许一个菜单，行通过 [LocalSettingsOptionHost] 拿到它 */
@Stable
internal class SettingsOptionHostState {

    internal var phase by mutableStateOf(MenuPhase.Closed)
        private set

    internal var request by mutableStateOf<OptionMenuRequest?>(null)
        private set

    /** 打开后 150ms 内不响应菜单内点击与外部点击，避免双击误选 / 误关 */
    internal var armed by mutableStateOf(false)
        private set

    /** 菜单实测尺寸；0 表示尚未测量，此时菜单 alpha 为 0，避免首帧闪到错误位置 */
    internal var menuSize by mutableStateOf(IntSize.Zero)

    internal val progress = Animatable(0f)

    /** 本次菜单回调选中的值：数据回流时据此跳过「外部改变」判断 */
    private var pendingSelection: Any? = null

    internal fun <T> show(
        owner: Any,
        anchor: IntRect,
        paneTitle: String,
        options: List<SettingsOption<T>>,
        selected: T,
        onSelected: (T) -> Unit,
        onFocusReturn: () -> Unit,
    ) {
        if (phase != MenuPhase.Closed) return
        if (options.size < 2) return
        menuSize = IntSize.Zero
        pendingSelection = null
        request = OptionMenuRequest(
            owner = owner,
            anchor = anchor,
            paneTitle = paneTitle,
            entries = options.map { MenuEntry(it.value, it.label, it.supportingText, it.enabled) },
            selectedValue = selected,
            selectedIndex = options.indexOfFirst { it.value == selected },
            optionsKey = options.map { it.value },
            onSelected = { value ->
                @Suppress("UNCHECKED_CAST")
                onSelected(value as T)
            },
            onFocusReturn = onFocusReturn,
        )
        phase = MenuPhase.Opening
    }

    internal fun choose(value: Any?) {
        val current = request ?: return
        if (!armed) return
        if (phase != MenuPhase.Opening && phase != MenuPhase.Open) return
        pendingSelection = value
        current.onSelected(value)
        phase = MenuPhase.Closing
    }

    internal fun dismiss() {
        if (phase == MenuPhase.Opening || phase == MenuPhase.Open) {
            phase = MenuPhase.Closing
        }
    }

    internal fun markOpen() {
        if (phase == MenuPhase.Opening) phase = MenuPhase.Open
    }

    internal fun arm() {
        if (phase == MenuPhase.Opening || phase == MenuPhase.Open) armed = true
    }

    internal fun disarm() {
        armed = false
    }

    internal fun finishClose() {
        if (phase != MenuPhase.Closing) return
        val closed = request
        request = null
        pendingSelection = null
        phase = MenuPhase.Closed
        closed?.onFocusReturn?.invoke()
    }

    /** 触发行位置变化（页面滚动 / 回收）→ 锚点失效，直接关闭 */
    internal fun onAnchorMoved(owner: Any, anchor: IntRect) {
        val current = request ?: return
        if (current.owner !== owner) return
        if (phase != MenuPhase.Opening && phase != MenuPhase.Open) return
        if (current.anchor != anchor) dismiss()
    }

    /** 选项 / 选中值被外部改变 → 关闭，避免菜单与数据不一致 */
    internal fun onDataChanged(owner: Any, selected: Any?, optionsKey: List<Any?>) {
        val current = request ?: return
        if (current.owner !== owner) return
        if (phase != MenuPhase.Opening && phase != MenuPhase.Open) return
        if (selected == pendingSelection) return
        if (current.optionsKey != optionsKey || current.selectedValue != selected) dismiss()
    }

    internal fun onRowDisposed(owner: Any) {
        val current = request ?: return
        if (current.owner === owner) dismiss()
    }
}

/** 页面内访问浮层状态；未包裹 [SettingsOptionHost] 时为 null（行静默不可打开） */
internal val LocalSettingsOptionHost = staticCompositionLocalOf<SettingsOptionHostState?> { null }

/**
 * 设置项选项弹层的同层宿主。
 *
 * 必须包裹**页面内容**，并把 `chrome.layout`（玻璃档的采样源）挂到内部的内容根上：
 * 菜单是 Host 的直接子节点、位于采样源子树之外，否则玻璃菜单会采样到自己。
 *
 * ```kotlin
 * AppScaffold(...) { chrome ->
 *     SettingsOptionHost(
 *         modifier = Modifier.fillMaxSize(),
 *         topInset = chrome.topInset,
 *         bottomInset = chrome.bottomInset,
 *         sources = chrome.sources,
 *     ) {
 *         LazyColumn(
 *             modifier = Modifier.fillMaxSize().then(chrome.layout),
 *             contentPadding = chrome.content,
 *         ) { /* SettingsOptionRow(...) */ }
 *     }
 * }
 * ```
 *
 * @param topInset 顶栏 + 状态栏内边距，菜单不进入该区域
 * @param bottomInset 底栏 + 导航栏内边距，菜单不进入该区域
 * @param sources 页面采样源；为空或标准档时菜单用实色卡片
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsOptionHost(
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    sources: ChromeSources? = null,
    content: @Composable () -> Unit,
) {
    val state = remember { SettingsOptionHostState() }
    var hostOrigin by remember { mutableStateOf(IntOffset.Zero) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    val style = LocalChromeStyle.current
    val layoutDirection = LocalLayoutDirection.current
    // 窗口尺寸是 observable state：旋转、分屏拖拽、折叠展开都会触发重组
    val windowSize = LocalWindowInfo.current.containerSize

    // 窗口尺寸 / 分屏 / 方向 / 材质 / RTL 变化：锚点语义失效，关闭即可
    LaunchedEffect(windowSize, layoutDirection, style) {
        if (state.phase != MenuPhase.Closed) state.dismiss()
    }

    // 输入法弹出优先关闭，不做重新定位
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible) state.dismiss()
    }

    // 入场 / 退场动画（退场期间所有交互由状态机忽略）
    LaunchedEffect(state.phase) {
        when (state.phase) {
            MenuPhase.Opening -> {
                state.progress.snapTo(0f)
                state.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                )
                state.markOpen()
            }

            MenuPhase.Closing -> {
                state.progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
                )
                state.finishClose()
            }

            else -> Unit
        }
    }

    // 打开后 150ms 才接受选择 / 外部点击
    LaunchedEffect(state.phase) {
        when (state.phase) {
            MenuPhase.Opening -> {
                state.disarm()
                delay(150)
                state.arm()
            }

            MenuPhase.Open -> state.arm()
            else -> state.disarm()
        }
    }

    BackHandler(enabled = state.phase != MenuPhase.Closed) { state.dismiss() }

    CompositionLocalProvider(LocalSettingsOptionHost provides state) {
        Box(
            modifier = modifier
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    hostOrigin = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                    hostSize = coordinates.size
                }
        ) {
            content()
            OptionMenuOverlay(
                state = state,
                hostOrigin = hostOrigin,
                hostSize = hostSize,
                topInset = topInset,
                bottomInset = bottomInset,
                style = style,
                sources = sources
            )
        }
    }
}

@Composable
private fun OptionMenuOverlay(
    state: SettingsOptionHostState,
    hostOrigin: IntOffset,
    hostSize: IntSize,
    topInset: Dp,
    bottomInset: Dp,
    style: ChromeStyle,
    sources: ChromeSources?,
) {
    val request = state.request ?: return
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val marginPx = with(density) { SettingsOptionSpec.ScreenMargin.roundToPx() }
    val topInsetPx = with(density) { topInset.roundToPx() }
    val bottomInsetPx = with(density) { bottomInset.roundToPx() }
    // Safe 区在 Host 局部坐标中计算（topInset 是窗口坐标，需减去 Host 原点）
    val safeTop = (topInsetPx - hostOrigin.y + marginPx).coerceIn(0, hostSize.height)
    val safeBottom = (hostSize.height - bottomInsetPx - marginPx)
        .coerceIn(safeTop, hostSize.height)
    val availableHeight = with(density) { (safeBottom - safeTop).toDp() }
    // Host 首帧尺寸未知时先按上限测量，避免菜单被压成 0 高后再重排
    val maxHeight = if (hostSize == IntSize.Zero) {
        SettingsOptionSpec.MenuMaxHeight
    } else {
        minOf(availableHeight, SettingsOptionSpec.MenuMaxHeight)
    }

    val menuSize = state.menuSize
    val measured = menuSize.width > 0 && menuSize.height > 0
    val menuOffset = if (hostSize != IntSize.Zero && measured) {
        placeOptionMenu(
            anchorInWindow = request.anchor,
            hostOrigin = hostOrigin,
            containerSize = hostSize,
            menuSize = menuSize,
            safeTop = safeTop,
            safeBottom = safeBottom,
            rtl = layoutDirection == LayoutDirection.Rtl,
            margin = marginPx,
            overlap = with(density) { SettingsOptionSpec.AnchorOverlap.roundToPx() },
            gap = with(density) { SettingsOptionSpec.MenuGap.roundToPx() }
        )
    } else {
        IntOffset.Zero
    }
    // Morph 起始矩形：触发行（含图标）在 Host 局部坐标中的边界，外框从它展开到菜单边界
    val anchorInHost = IntRect(
        left = request.anchor.left - hostOrigin.x,
        top = request.anchor.top - hostOrigin.y,
        right = request.anchor.right - hostOrigin.x,
        bottom = request.anchor.bottom - hostOrigin.y
    )

    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
        // 透明 scrim：不压暗背景，只吃掉菜单外的点击。按下菜单内部的指针完全不消费，
        // 避免把菜单项的手势取消掉。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.armed, menuOffset, menuSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val insideMenu = menuSize != IntSize.Zero &&
                            down.position.x >= menuOffset.x &&
                            down.position.x <= menuOffset.x + menuSize.width &&
                            down.position.y >= menuOffset.y &&
                            down.position.y <= menuOffset.y + menuSize.height
                        if (insideMenu) {
                            // 交给菜单自己处理，只等手势结束
                            var pressed = true
                            while (pressed) {
                                pressed = awaitPointerEvent().changes.any { it.pressed }
                            }
                        } else {
                            down.consume()
                            if (state.armed) state.dismiss()
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                pressed = event.changes.any { it.pressed }
                            }
                        }
                    }
                }
        )

        SettingsOptionMenu(
            request = request,
            style = style,
            sources = sources,
            progress = { state.progress.value },
            maxHeight = maxHeight,
            modifier = Modifier
                .offset { menuOffset }
                .onSizeChanged { state.menuSize = it }
                .graphicsLayer {
                    // Morph：外框矩形每帧在「触发行边界 → 菜单边界」之间线性插值。
                    // 围绕左上角做非均匀 scale + translation，四条边的轨迹与真实
                    // 布局 morph 完全一致；菜单内容自身延迟淡入掩盖拉伸形变。
                    if (!measured) {
                        alpha = 0f
                    } else {
                        val fraction = state.progress.value
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = lerp(anchorInHost.width / menuSize.width.toFloat(), 1f, fraction)
                        scaleY = lerp(anchorInHost.height / menuSize.height.toFloat(), 1f, fraction)
                        translationX = lerp((anchorInHost.left - menuOffset.x).toFloat(), 0f, fraction)
                        translationY = lerp((anchorInHost.top - menuOffset.y).toFloat(), 0f, fraction)
                        alpha = 1f
                    }
                },
            onSelect = { value -> state.choose(value) },
            onDismiss = { state.dismiss() }
        )
    }
}
