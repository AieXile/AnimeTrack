package com.aiexile.animetrack.ui.components

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.ui.theme.isAppDarkTheme
import com.aiexile.animetrack.util.coverImageRequestForList
import com.aiexile.animetrack.util.isUnaired
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

private val CardCornerRadius = 16.dp
private val CoverAspectRatio = 2f / 3f

// SquircleShape 实例顶层化复用（同 AnimeCard），使内置 size 级 Outline 缓存生效。
private val CardShape = SquircleShape(CardCornerRadius)
private val CoverTopShape = SquircleShape(topStart = CardCornerRadius, topEnd = CardCornerRadius)

// ── PhotoStack 逆向运动参数（dp 常量，组合时按 density 转 px）──
// 设计规格来自 https://github.com/Wren036/PhotoStack（基于微信原版录屏逐帧测量逆向）。
private val StackPeek = 15.dp                 // 第一层探边露出
private val StackPeekStep = 12.dp            // 每深一层多探出
private const val StackRotStep = 2.2f        // 每层递进旋转角（度）
private const val StackScaleStep = 0.08f     // 每层递进缩小
private val StackFlingVelocity = 0.4.dp       // 快甩判定速度（dp/ms）
private val StackMinTravel = 120.dp          // 行程分母下限
private val StackBoundaryPeek = 24.dp        // 边界弹性预览区间
private const val StackBoundaryRot = 2.5f    // 边界弹性微旋（度）
private const val StackDirectionBias = 2f    // 方向竞争偏置：|dx| ≥ 2×|dy|（与水平
                                             // 夹角约 <26.6°）才算横滑意图接管；45° 平分
                                             // 对近似垂直的斜滑太宽容，会阻断列表纵向滚动
private val StackBoundaryNudge1 = 8.dp       // 边界下层联动 n1
private val StackBoundaryNudge2 = 5.dp       // 边界下层联动 n2
private const val StackFinishMinDurationMs = 140       // 完成动画时长下限
private const val StackFinishMsPerProgress = 340       // 完成动画时长 = max(140, (1-p)×340)
private const val StackSpringBackDurationMs = 220      // 回弹时长（JS CSS transition 近似）
private val StackHorizontalInset = 3.dp       // 容器水平内缩：防边界第二层探边
                                              // 伸进相邻网格 item 被其绘制顺序遮盖
// 封面高度补偿：水平内缩（3dp×2）使 2:3 封面比普通卡片矮 6dp×1.5=9dp，
// 补到标题栏高度上（背景与封面底缘同色，视觉不可见），保证同行底边平齐
private val StackCoverHeightCompensation = StackHorizontalInset * 2f * (3f / 2f)

/**
 * 多季番剧堆叠卡片（仅收起态）：微信"合并发图"式折叠交互。
 *
 * 恒定三层可见（顶卡 + 左右各一张探边，左=上一季、右=下一季，边界时配额
 * 转移到另一侧）；左右滑动对预定义的峰形翻页动画做擦洗（scrub）预览——
 * 前半程顶卡跟手滑出，后半程自行回归落入滑动方向同侧探边位，全程可逆。
 * 翻页编排遵循"三张守恒"：升顶/新探边/退场卡通过遮挡与纵深完成进退场。
 *
 * 运动参数与插值公式移植自 PhotoStack（微信原版逐帧测量逆向规格），
 * 详见 [CardStackScrubState]。
 *
 * @param baseTitle 合并后的主标题（顶层卡片显示）
 * @param animeList 该系列所有季（组内已按季数升序），滑动切换顶层
 * @param onAnimeClick 单击堆叠卡片（打开当前顶层季，滑动后跟随）
 * @param onExpand 长按堆叠卡片触发展开
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AnimeCardStack(
    baseTitle: String,
    animeList: List<Anime>,
    onAnimeClick: (Anime) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    // 持久化的顶层页码（冷启动恢复）；翻页结算时回调持久化
    initialTopIndex: Int = 0,
    onTopIndexChange: (Int) -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 运动参数（dp→px 一次转换，密度变化时重算）
    val motion = remember(density) {
        with(density) {
            CardStackMotion(
                peekPx = StackPeek.toPx(),
                peekStepPx = StackPeekStep.toPx(),
                rotStepDeg = StackRotStep,
                scaleStep = StackScaleStep,
                flingVelocityPxPerMs = StackFlingVelocity.toPx(),
                minTravelPx = StackMinTravel.toPx(),
                boundaryPeekPx = StackBoundaryPeek.toPx(),
                boundaryRotDeg = StackBoundaryRot,
                boundaryNudge1Px = StackBoundaryNudge1.toPx(),
                boundaryNudge2Px = StackBoundaryNudge2.toPx()
            )
        }
    }

    // 屏幕宽（px）：方向感知行程分母——向右滑时计算"起点到右缘"剩余行程用
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    // 顶层页码：rememberSaveable 挂 LazyGrid item key，回收/滚动/进程重建后恢复；
    // 列表变化（同步增删季）时 clamp 到合法范围。
    var topIndex by rememberSaveable {
        mutableIntStateOf(initialTopIndex.coerceIn(0, (animeList.size - 1).coerceAtLeast(0)))
    }
    if (topIndex > animeList.lastIndex) topIndex = animeList.lastIndex

    // 冷启动时持久化值可能晚于首次组合到达（DataStore 异步首读），用户未交互前
    // 同步一次；用户交互后不再自动覆盖，避免打断操作
    var userInteracted by remember { mutableStateOf(false) }
    LaunchedEffect(initialTopIndex) {
        if (!userInteracted && initialTopIndex != topIndex) {
            topIndex = initialTopIndex.coerceIn(0, animeList.lastIndex)
        }
    }

    // 擦洗状态：progress 为擦洗进度（0=静止，1=翻页完成点），拖拽中逐帧直写、
    // 松手后由帧循环动画驱动（等价 JS 的 rAF + 直写双模式，见 withFrameNanos）；
    // scrubDir 为当前擦洗方向（0=静止）；pendingDir 为完成动画未结算标记（连甩判定）。
    val progress = remember { mutableFloatStateOf(0f) }
    val scrubDir = remember { mutableIntStateOf(0) }
    val pendingDir = remember { mutableIntStateOf(0) }
    var scrubAnimJob by remember { mutableStateOf<Job?>(null) }
    // 卡片实际宽（内缩后），峰形轨迹 maxX = 0.52×卡宽 的基准
    val stageWidthPx = remember { mutableFloatStateOf(0f) }
    // 手势区域原点在窗口坐标系中的 x：用于把手指局部坐标换算成窗口坐标
    // （行程分母 = 手指起点到屏幕左缘距离，等价 JS 的 clientX）
    val stackOriginWindowX = remember { mutableFloatStateOf(0f) }

    // 完成动画（JS _finish 的 rAF 帧循环）：沿同一条峰形轨迹播完剩余行程
    // （非跳终态），进度 p = fromP + (1-fromP)·(1-(1-k)²)，
    // 时长 max(140, (1-fromP)×340) ms。
    fun startFinish(dir: Int, fromP: Float) {
        scrubAnimJob?.cancel()
        pendingDir.intValue = dir
        scrubDir.intValue = dir
        scrubAnimJob = scope.launch {
            val durMs = max(
                StackFinishMinDurationMs,
                ((1f - fromP) * StackFinishMsPerProgress).toInt()
            )
            val startTime = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val k = (((frame - startTime) / 1_000_000f) / durMs).coerceAtMost(1f)
                progress.floatValue = fromP + (1f - fromP) * (1f - (1f - k) * (1f - k))
                if (k >= 1f) break
            }
            // 结算：p=1 擦洗态与翻页后静止态数学相等，零跳变。
            // 被手势取消（连甩）时协程在挂起点终止，不会走到这里二次结算。
            if (pendingDir.intValue == dir) {
                topIndex = stackSettleTopIndex(topIndex, dir, animeList.size)
                pendingDir.intValue = 0
                scrubDir.intValue = 0
                progress.floatValue = 0f
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                // 翻页结算：持久化顶层页码
                onTopIndexChange(topIndex)
            }
        }
    }

    // 回弹：沿轨迹可逆回放至静止（JS CSS transition 回弹，TwoQuad ease-out）
    fun springBack() {
        scrubAnimJob?.cancel()
        val fromP = progress.floatValue
        if (fromP <= 0f) {
            scrubDir.intValue = 0
            return
        }
        scrubAnimJob = scope.launch {
            val startTime = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val k = (((frame - startTime) / 1_000_000f) / StackSpringBackDurationMs)
                    .coerceAtMost(1f)
                progress.floatValue = fromP * (1f - k) * (1f - k)
                if (k >= 1f) break
            }
            scrubDir.intValue = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = StackHorizontalInset)
            .onGloballyPositioned { coords ->
                // 卡片实际宽（内缩后）作为峰形轨迹 maxX = 0.52×卡宽 的基准；
                // 顺带记录手势区域原点的窗口 x（局部坐标 → 窗口坐标换算基准）
                stageWidthPx.floatValue = coords.size.width.toFloat()
                stackOriginWindowX.floatValue = coords.positionInWindow().x
            }
            .pointerInput(motion, animeList.size, screenWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 手指起点窗口 x（手势区域原点窗口 x + 局部 x，等价 JS 的 clientX）。
                    // 行程分母按滑动方向选取（方向感知）：向左 = 起点到左缘距离、
                    // 向右 = 起点到右缘距离——拖到屏幕边缘恰好进度 1，
                    // 保证屏幕两侧列的卡片向边缘方向滑动也总能触发翻页阈值。
                    val startXInWindow = stackOriginWindowX.floatValue + down.position.x
                    // 接管判定阈值：平台 touchSlop（约 8dp，与 JS 8px 测量值一致）
                    val slop = viewConfiguration.touchSlop
                    var dragging = false
                    var lastX = down.position.x
                    var lastTime = down.uptimeMillis
                    var velocity = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        // 系统手势取消（pointercancel 等价场景）：立即回弹归位，
                        // 绝不停留在中间态（Compose 无 Cancel 事件类型，经底层
                        // MotionEvent 识别）
                        if (event.motionEvent?.actionMasked == MotionEvent.ACTION_CANCEL) {
                            if (dragging) springBack()
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            // 事件被父级消费（如网格纵向滚动锁定）：
                            // 未接管则安静放弃（纵向滚动归网格），已接管则回弹
                            if (dragging) springBack()
                            break
                        }
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        // 方向竞争：横向分量需显著占优（StackDirectionBias）才接管，
                        // 近似垂直的斜滑留给网格纵向滚动
                        if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * StackDirectionBias) {
                            dragging = true
                            userInteracted = true
                            // 打断进行中的完成/回弹动画：完成动画先立即结算页码
                            // （连甩规则：每次甩动翻且仅翻一页），回弹动画直接作废
                            if (scrubAnimJob?.isActive == true) {
                                scrubAnimJob?.cancel()
                                val pending = pendingDir.intValue
                                if (pending != 0) {
                                    topIndex = stackSettleTopIndex(
                                        topIndex, pending, animeList.size
                                    )
                                    pendingDir.intValue = 0
                                    // 连甩结算：立即持久化，进程若在此刻被杀也不丢页码
                                    onTopIndexChange(topIndex)
                                }
                                scrubDir.intValue = 0
                                progress.floatValue = 0f
                            }
                        }
                        if (dragging) {
                            // 速度指数平滑（0.7 当前帧 + 0.3 历史），避免单帧抖动误触发
                            val dt = change.uptimeMillis - lastTime
                            if (dt > 0) {
                                velocity = stackSmoothVelocity(
                                    (change.position.x - lastX) / dt, velocity
                                )
                            }
                            lastX = change.position.x
                            lastTime = change.uptimeMillis
                            // 拖拽中逐帧直写（跟手，无动画）；
                            // 复位底座由渲染侧每帧先摆静止位再叠加插值保证
                            val dir = if (dx < 0f) -1 else 1
                            val denominator = stackDragDenominator(
                                startXInWindow, dir, screenWidthPx, motion
                            )
                            scrubDir.intValue = dir
                            progress.floatValue = stackScrubProgress(dx, denominator)
                            // 消费事件防止网格纵向滚动抢占
                            change.consume()
                        }
                        if (!change.pressed) {
                            if (dragging) {
                                // 松手判定（JS _release）：慢拖过半或快甩 → 翻页；否则回弹
                                val dir = if (dx < 0f) -1 else 1
                                val p = progress.floatValue
                                val canTurn = if (dir < 0) {
                                    topIndex < animeList.size - 1
                                } else {
                                    topIndex > 0
                                }
                                if (stackShouldTurnOver(p, velocity, dx, canTurn, motion)) {
                                    startFinish(dir, p)
                                } else {
                                    springBack()
                                }
                            }
                            break
                        }
                    }
                }
            }
    ) {
        // 点击层放最底（先组合）：卡片本身无 clickable，触摸穿透至此；
        // 展开按钮在顶卡内、绘制于其上，命中优先于本层。
        // 拖动超 slop 时 combinedClickable 自动取消单击/长按，天然互斥。
        Box(
            modifier = Modifier
                .matchParentSize()
                .combinedClickable(
                    onClick = { onAnimeClick(animeList[topIndex]) },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExpand()
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        )

        // 最多组合 5 张卡（topIndex±2），静止态仅 3 张可见（alpha=0 的卡不组合）
        val rangeStart = (topIndex - 2).coerceAtLeast(0)
        val rangeEnd = (topIndex + 2).coerceAtMost(animeList.lastIndex)
        for (i in rangeStart..rangeEnd) {
            key(i) {
                StackCardSlot(
                    anime = animeList[i],
                    title = if (i == topIndex) baseTitle else animeList[i].title,
                    depth = abs(i - topIndex),
                    seasonIndex = i + 1,
                    totalSeasons = animeList.size,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    placement = {
                        stackLayerPlacement(
                            index = i,
                            topIndex = topIndex,
                            count = animeList.size,
                            dir = scrubDir.intValue,
                            progress = progress.floatValue,
                            stageWidthPx = stageWidthPx.floatValue,
                            motion = motion
                        )
                    }
                )
            }
        }
    }
}

/**
 * 堆叠中的单张卡槽：每帧擦洗只触发 graphicsLayer 重绘（draw 阶段直读
 * 擦洗状态），zIndex/可见性经 derivedStateOf 只在跨阈值时重组。
 * 不可见卡（alpha=0，如超出探边配额、尚未进场的退场卡）不参与组合。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StackCardSlot(
    anime: Anime,
    title: String,
    depth: Int,
    seasonIndex: Int,
    totalSeasons: Int,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    placement: () -> StackLayerPlacement
) {
    // zIndex 与可见性计算不依赖卡片宽度，且取值仅跨进度阈值（0.5/进场起点）时
    // 变化 → derivedStateOf 每帧重算但结果不变时不通知，重组只在阈值处发生
    val zIndex by remember(placement) { derivedStateOf { placement().zIndex } }
    val visible by remember(placement) { derivedStateOf { placement().alpha > 0.01f } }
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(zIndex)
            .graphicsLayer {
                // draw 阶段逐帧读取擦洗状态，无重组开销
                val pl = placement()
                translationX = pl.offsetX
                rotationZ = pl.rotationDeg
                scaleX = pl.scale
                scaleY = pl.scale
                alpha = pl.alpha
            }
    ) {
        StackCardLayer(
            anime = anime,
            title = title,
            depth = depth,
            seasonIndex = seasonIndex,
            totalSeasons = totalSeasons,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

/**
 * 堆叠中的单层卡片：完整渲染封面+标题（复用原有结构），变换由外层
 * [StackCardSlot] 的 graphicsLayer（位移/旋转/缩放/透明度，中心锚点
 * 与 CSS transform 一致）与 zIndex 驱动。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StackCardLayer(
    anime: Anime,
    title: String,
    depth: Int,
    seasonIndex: Int,
    totalSeasons: Int,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    // 与普通卡片（AnimeCard）底部保持同色：亮色用最亮白，暗色 surfaceContainerLowest
    // 过深、与网格背景几乎无区分，改用 surfaceContainerLow
    val containerColor = if (isAppDarkTheme()) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                // 与 AnimeCard 一致的黑色低透明度阴影，白底上边缘可见
                elevation = if (depth == 0) 2.dp else (depth + 1).dp,
                shape = CardShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clip(CardShape)
            .background(containerColor)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CoverAspectRatio)
        ) {
            if (anime.coverUrl != null) {
                val coverUrl = anime.coverUrl
                val context = LocalContext.current
                val coverRequest = remember(coverUrl) {
                    coverImageRequestForList(context, coverUrl)
                }
                // 共享元素过渡：仅顶卡（depth==0）参与，key 与详情页封面配对实现
                // 飞入飞出；探边卡带旋转/缩放变换，参与会导致飞行起点歪斜。
                // 顶卡静止态 graphicsLayer 全 identity，bounds 干净
                val coverModifier = if (depth == 0 && sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier
                            .fillMaxSize()
                            .sharedElement(
                                rememberSharedContentState(key = "cover_${anime.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .clip(CoverTopShape)
                    }
                } else {
                    Modifier
                        .fillMaxSize()
                        .clip(CoverTopShape)
                }
                AsyncImage(
                    model = coverRequest,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = coverModifier
                )
            } else {
                EmptyCoverPlaceholder(
                    shape = CoverTopShape
                )
            }

            // 观看状态徽章（与普通卡片 AnimeCoverWithStatus 一致：右上角）
            StatusBadge(
                status = anime.status,
                unaired = isUnaired(anime.airDate),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }

        // 标题区域（含封面高度补偿，见 StackCoverHeightCompensation 注释）：
        // 封面矮 9dp 使标题栏顶比普通卡片高 9dp，补偿空间放顶部 padding 把
        // 文本下推，标题/季数与普通卡片的位置精确一致
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp + StackCoverHeightCompensation)
                .background(containerColor)
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = 6.dp + StackCoverHeightCompensation,
                    bottom = 6.dp
                )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 季数进度：与普通卡片集数进度同款样式（左下角），
            // 前为当前季序、后为总季数，滑动翻页后跟随顶层变化
            Text(
                text = "$seasonIndex/$totalSeasons",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
