package com.aiexile.animetrack.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 删除卡片时的像素粉尘消散特效（参考 Compose-Symphony DissolveEffect 的灭霸式消散）。
 *
 * 机制：PixelCopy 截取卡片真实像素 → 网格采样为粉尘粒子（颜色 = 原像素量化色）→
 * 从右下角向左上角波次启动（未启动粒子留在原位近似还原图像，启动后随风+重力飘散湮灭）。
 *
 * 绘制用「色板 × alpha」分桶 drawPoints 批量绘制，上万粒子单帧 ~150 次调用。
 * 覆盖层仅绘制、不含 pointerInput，不拦截下层触摸。
 */

private const val DURATION_MS = 1200
private const val TOTAL_S = DURATION_MS / 1000f

/** 波次扫过时长：右下角粒子最先启动，左上角最迟在此刻 + 抖动启动 */
private const val WAVE_S = 0.4f

/** 截图前等待选中层（按钮/遮罩）消失的重绘时间 */
private const val PREPARE_DELAY_MS = 50L

/**
 * 补位延迟：波次扫过大半、原位视觉已消散后才执行删除（触发其余卡片补位），
 * 避免补位卡片过早滑入与仍在飘散的粉尘重叠。
 */
private const val REORDER_DELAY_MS = 650L

/** 目标粒子数（采样步长按此自适应，实际约 8k~12k） */
private const val TARGET_PARTICLES = 10000

/** 色板量化：RGB 各 4 级共 64 色；alpha 分 8 档，桶 key = colorIdx * 8 + alphaIdx */
private const val ALPHA_LEVELS = 8
private const val DUST_STROKE_PX = 2.5f

/** 卡片双坐标系位置：root 用于绘制，window 用于 PixelCopy 截图 */
internal class CardPixelBounds(
    val root: Rect,
    val window: Rect
)

/** 粉尘粒子：确定性解析参数，位置随时间直接求值 */
internal class DustParticle(
    val startX: Float,          // 相对卡片左上角
    val startY: Float,
    val velocityX: Float,       // px/s
    val velocityY: Float,
    val startTime: Float,       // 波次启动时刻（秒），未到时粒子保持原位
    val colorIdx: Int,          // 量化色板索引（RGB 各 2bit）
    val noisePhase: Float       // 湍流相位
)

/** 单次消散实例 */
internal class DeletionBurst internal constructor(
    val id: Long,
    val bounds: Rect,               // root 坐标系
    val particles: List<DustParticle>
)

/** 特效状态持有者：burst 列表 + 触发入口，供列表页与覆盖层共享 */
internal class DeletionBurstState {
    val bursts = mutableStateListOf<DeletionBurst>()
    private var nextId = 0L

    fun trigger(bounds: Rect, particles: List<DustParticle>) {
        if (particles.isEmpty()) return
        bursts += DeletionBurst(nextId++, bounds, particles)
    }

    fun recycle(id: Long) {
        bursts.removeAll { it.id == id }
    }
}

@Composable
internal fun rememberDeletionBurstState(): DeletionBurstState =
    remember { DeletionBurstState() }

/**
 * 从 Context 逐层找 Activity window（PixelCopy 需要）。
 */
internal fun findActivityWindow(context: Context): Window? {
    var ctx: Context = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx.window
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 截图卡片像素并触发消散特效，随后回调 [onCaptured]（执行真正的删除）。
 *
 * 时序：[onPrepare]（清选中态隐藏按钮）→ 等待重绘 → PixelCopy 截图 →
 * 触发特效 + [onBurstStarted]（隐藏原卡片，粒子层接管显示）→
 * 延迟 [REORDER_DELAY_MS]（原位消散大半后再补位）→ [onCaptured]。
 * 任何失败：跳过特效直接 [onCaptured]。延迟仅 UI 层，不影响数据层（Room/墓碑照常）。
 */
internal fun launchDeletionBurst(
    state: DeletionBurstState,
    window: Window?,
    bounds: CardPixelBounds,
    density: Density,
    onPrepare: () -> Unit,
    onBurstStarted: () -> Unit,
    onCaptured: () -> Unit
) {
    if (window == null) {
        onCaptured()
        return
    }
    // 先清选中层（按钮/遮罩），等待重绘完成后再截图，避免按钮被拍进粉尘
    onPrepare()
    Handler(Looper.getMainLooper()).postDelayed({
        captureAndBurst(state, window, bounds, density, onBurstStarted, onCaptured)
    }, PREPARE_DELAY_MS)
}

private fun captureAndBurst(
    state: DeletionBurstState,
    window: Window,
    bounds: CardPixelBounds,
    density: Density,
    onBurstStarted: () -> Unit,
    onCaptured: () -> Unit
) {
    // 截图区 clamp 到窗口可绘制区内（卡片部分滚出屏幕时只截可见部分）
    val decorWidth = window.decorView.width
    val decorHeight = window.decorView.height
    if (decorWidth <= 0 || decorHeight <= 0) {
        onCaptured()
        return
    }
    val cardLeft = bounds.window.left.toInt()
    val cardTop = bounds.window.top.toInt()
    val left = bounds.window.left.toInt().coerceIn(0, decorWidth)
    val top = bounds.window.top.toInt().coerceIn(0, decorHeight)
    val right = bounds.window.right.toInt().coerceIn(0, decorWidth)
    val bottom = bounds.window.bottom.toInt().coerceIn(0, decorHeight)
    if (right - left <= 0 || bottom - top <= 0) {
        onCaptured()
        return
    }
    val capture = AndroidRect(left, top, right, bottom)
    val bitmap = try {
        Bitmap.createBitmap(capture.width(), capture.height(), Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        onCaptured()
        return
    }
    try {
        PixelCopy.request(
            window, capture, bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    // bitmap(0,0) 相对卡片左上角的偏移（clamp 可能为负，即截掉了滚出屏幕的部分）
                    state.trigger(
                        bounds = bounds.root,
                        particles = generateDustParticles(
                            bitmap = bitmap,
                            offsetX = left - cardLeft.toFloat(),
                            offsetY = top - cardTop.toFloat(),
                            density = density
                        )
                    )
                    // 隐藏原卡片，粒子层（点阵还原图）无缝接管显示
                    onBurstStarted()
                    // 原位消散大半后再执行删除，避免补位卡片与飘散粉尘重叠
                    Handler(Looper.getMainLooper()).postDelayed(onCaptured, REORDER_DELAY_MS)
                } else {
                    bitmap.recycle()
                    onCaptured()
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (e: Exception) {
        bitmap.recycle()
        onCaptured()
    }
}

/**
 * 覆盖层：纯绘制无 pointerInput，不拦截下层触摸；恒挂载以稳定自身坐标系。
 */
@Composable
internal fun DeletionBurstOverlay(
    state: DeletionBurstState,
    modifier: Modifier = Modifier
) {
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            overlayOrigin = coords.positionInRoot()
        }
    ) {
        state.bursts.forEach { burst ->
            key(burst.id) {
                DeletionBurstCanvas(
                    burst = burst,
                    overlayOrigin = overlayOrigin,
                    onFinished = { state.recycle(burst.id) },
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
private fun DeletionBurstCanvas(
    burst: DeletionBurst,
    overlayOrigin: Offset,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frameBuckets = remember(burst.id) { FrameBuckets() }
    // 池化 native 画笔：nativeCanvas.drawPoints 批量绘制，每桶仅改色
    val dustPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            style = AndroidPaint.Style.STROKE
            strokeWidth = DUST_STROKE_PX
            strokeCap = AndroidPaint.Cap.ROUND
        }
    }
    val progress = remember(burst.id) { Animatable(0f) }

    LaunchedEffect(burst.id) {
        // 物理求值需要线性时间，波次层次由各粒子 startTime 营造
        progress.animateTo(1f, tween(DURATION_MS, easing = LinearEasing))
        onFinished()
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        drawBurst(burst, t * TOTAL_S, overlayOrigin, frameBuckets, dustPaint)
    }
}

/**
 * 采样 Bitmap 像素生成粉尘粒子：颜色取原像素量化色板，波次按到右下角的距离排布
 * （右下先散、左上后散，未启动粒子原位显示近似还原图像）。
 * 生成完毕立即 recycle bitmap。
 */
private fun generateDustParticles(
    bitmap: Bitmap,
    offsetX: Float,
    offsetY: Float,
    density: Density
): List<DustParticle> = with(density) {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return emptyList()

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    bitmap.recycle()

    // 采样步长：按面积自适应，控制粒子数在目标量级（透明像素跳过后更少）
    val step = ceil(sqrt(width * height / TARGET_PARTICLES.toFloat())).toInt().coerceIn(2, 6)
    val maxDist = sqrt((width * width + height * height).toFloat())
    val random = Random(width * 31 + height)

    val result = ArrayList<DustParticle>(width * height / (step * step) + 16)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = pixels[y * width + x]
            if (((pixel ushr 24) and 0xFF) > 20) {
                // 波次启动：右下角近的先散，加随机抖动避免整齐的波前
                val dist = sqrt(
                    ((width - x).toFloat() * (width - x) + (height - y).toFloat() * (height - y)).toDouble()
                ).toFloat()
                // 初速偏左上（灭霸风），幅度按 dp 归一避免不同密度差异
                result += DustParticle(
                    startX = offsetX + x,
                    startY = offsetY + y,
                    velocityX = (random.nextFloat() - 0.7f) * 55.dp.toPx(),
                    velocityY = (random.nextFloat() - 0.7f) * 37.dp.toPx(),
                    startTime = dist / maxDist * WAVE_S + random.nextFloat() * 0.1f,
                    colorIdx = ((pixel ushr 16 and 0xFF) shr 6) shl 4 or
                            (((pixel ushr 8 and 0xFF) shr 6) shl 2) or
                            ((pixel and 0xFF) shr 6),
                    noisePhase = random.nextFloat()
                )
            }
            x += step
        }
        y += step
    }
    result
}

private fun DrawScope.drawBurst(
    burst: DeletionBurst,
    time: Float,
    overlayOrigin: Offset,
    buckets: FrameBuckets,
    paint: AndroidPaint
) {
    // 物理常量（Compose-Symphony ParticleRenderer 同款曲线，按 dp 归一）
    val gravity = 200.dp.toPx()          // px/s²
    val wind = (-55).dp.toPx()           // px/s，向左
    val noiseAmp = 7.dp.toPx()           // 湍流幅度 × 时间
    val twoPi = (2f * PI).toFloat()

    buckets.beginFrame()
    val particles = burst.particles

    translate(
        left = burst.bounds.left - overlayOrigin.x,
        top = burst.bounds.top - overlayOrigin.y
    ) {
        // 按量化色 + 量化 alpha 分桶
        for (i in particles.indices) {
            val p = particles[i]
            val elapsed = time - p.startTime
            if (elapsed <= 0f) {
                // 未启动：原位原色显示（点阵近似还原图像）
                buckets.add(p.colorIdx * ALPHA_LEVELS + (ALPHA_LEVELS - 1), p.startX, p.startY)
                continue
            }
            val alpha = 1f - elapsed / (TOTAL_S - p.startTime)
            if (alpha <= 0f) continue
            val alphaIdx = (alpha * ALPHA_LEVELS).toInt().coerceAtMost(ALPHA_LEVELS - 1)
            val x = p.startX + (p.velocityX + wind) * elapsed +
                    sin(twoPi * (3f * elapsed + p.noisePhase)) * noiseAmp * elapsed
            val y = p.startY + p.velocityY * elapsed + 0.5f * gravity * elapsed * elapsed
            buckets.add(p.colorIdx * ALPHA_LEVELS + alphaIdx, x, y)
        }

        // 批量绘制：每桶一次 nativeCanvas.drawPoints（FloatArray 零装箱直绘）
        buckets.forEach { key, bag ->
            if (bag.size == 0) return@forEach
            val colorIdx = key / ALPHA_LEVELS
            val alpha = ((key % ALPHA_LEVELS) + 1) * 255 / ALPHA_LEVELS
            // 还原量化色：各通道 4 级（0, 85, 170, 255）
            val r = ((colorIdx shr 4) and 0x3) * 85
            val g = ((colorIdx shr 2) and 0x3) * 85
            val b = (colorIdx and 0x3) * 85
            paint.color = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPoints(bag.data, 0, bag.size, paint)
            }
        }
    }
}

/** 可增长 float 数组（避免装箱），data 前 size 位即有效点坐标 */
private class FloatBag {
    var data = FloatArray(128)
    var size = 0

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
}

/** 每帧分桶容器：bag 池化复用容量，避免每帧分配 */
private class FrameBuckets {
    private val buckets = HashMap<Int, FloatBag>(256)
    private val pool = ArrayList<FloatBag>(256)
    private var poolCursor = 0

    fun beginFrame() {
        buckets.clear()
        poolCursor = 0
    }

    fun add(key: Int, x: Float, y: Float) {
        val bag = buckets.getOrPut(key) {
            if (poolCursor < pool.size) pool[poolCursor++].also { it.size = 0 }
            else FloatBag().also { pool.add(it); poolCursor++ }
        }
        bag.add(x)
        bag.add(y)
    }

    fun forEach(action: (Int, FloatBag) -> Unit) {
        for ((k, bag) in buckets) action(k, bag)
    }
}
