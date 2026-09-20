package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 多季堆叠卡片"微信式折叠交互"的纯逻辑状态机与运动学计算。
 *
 * 设计规格来自 https://github.com/Wren036/PhotoStack
 * （基于微信原版录屏逐帧测量逆向：堆叠层次、探边距离、峰形翻页轨迹、
 * 快甩阈值、三张守恒编排均由该库作者测量得出；PolyForm Noncommercial 许可，
 * 本项目为个人非商业用途，仅移植公开交互设计规格，代码独立编写）。
 *
 * 本文件不依赖 Compose UI（仅用 Compose runtime 的 [Easing]），
 * 全部公式与 PhotoStack JS 源码逐条对应：
 * - 探边配额 `_lr()` → [stackPeekQuota]
 * - 静止摆位（复位底座）`_apply()` → [stackStaticPlacement]
 * - 峰形轨迹擦洗 `_scrub()` → [stackLayerPlacement]
 * - 松手判定 `_release()` → [stackShouldTurnOver]
 *
 * 单位约定：内部统一使用 **px**（dp 常量由 UI 层经 density 转换后传入
 * [CardStackMotion]）；旋转角为"度"；速度为 px/ms。
 */
internal data class CardStackMotion(
    /** 第一层探边露出量（15dp） */
    val peekPx: Float,
    /** 每深一层多探出（12dp） */
    val peekStepPx: Float,
    /** 每层递进旋转角（2.2°） */
    val rotStepDeg: Float,
    /** 每层递进缩小（0.08） */
    val scaleStep: Float,
    /** 快甩判定速度阈值（0.4dp/ms 转 px） */
    val flingVelocityPxPerMs: Float,
    /** 行程分母下限（120dp） */
    val minTravelPx: Float,
    /** 边界弹性预览区间（24dp） */
    val boundaryPeekPx: Float,
    /** 边界弹性微旋（2.5°） */
    val boundaryRotDeg: Float,
    /** 边界下层联动位移 n1（8dp） */
    val boundaryNudge1Px: Float,
    /** 边界下层联动位移 n2（5dp） */
    val boundaryNudge2Px: Float
)

/** 单层卡片的完整摆放（相对静止位置的变换 + 层级 + 透明度）。 */
internal data class StackLayerPlacement(
    /** 水平位移 px（负=向左） */
    val offsetX: Float,
    /** 旋转角（度，负=逆时针） */
    val rotationDeg: Float,
    /** 缩放 */
    val scale: Float,
    /** 透明度（静止态探边为实体 1，透明度仅用于翻页动画中间态） */
    val alpha: Float,
    /** 层级（照搬 JS：顶卡 100、右探边 100-d、左探边 40-d，右侧恒高于左侧） */
    val zIndex: Float
)

/**
 * 完成动画缓动（ease-out 二次）：k → 1-(1-k)²。
 * PhotoStack `_finish()` 的进度映射 p = fromP + (1-fromP)·(1-(1-k)²)
 * 与 Compose `Animatable.animateTo(1f, tween(easing))` 的
 * value = fromP + (1-fromP)·easing(k) 完全一致。
 */
internal val TwoQuadEaseOut: Easing = Easing { k -> 1f - (1f - k) * (1f - k) }

/**
 * 探边配额（JS `_lr()`）：常态左右各 1 张，顶卡位于首/末季时
 * 配额转移到另一侧显示两层（维持三层可见）。返回 (左配额, 右配额)。
 */
internal fun stackPeekQuota(topIndex: Int, count: Int): Pair<Int, Int> {
    val leftAvail = topIndex
    val rightAvail = count - 1 - topIndex
    var left = min(leftAvail, 1)
    var right = min(rightAvail, 1)
    if (left + right < 2) {
        left = min(leftAvail, 2 - right)
        right = min(rightAvail, 2 - left)
    }
    return left to right
}

/**
 * 静止底座（JS `_apply()` 的单卡逻辑）：给定层位计算静止摆位。
 * 复位底座原则——擦洗的每一帧都先执行完整静止摆位再叠加进度插值，
 * 杜绝中间态残留累积。
 */
internal fun stackStaticPlacement(
    index: Int,
    topIndex: Int,
    count: Int,
    motion: CardStackMotion
): StackLayerPlacement {
    val (leftQuota, rightQuota) = stackPeekQuota(topIndex, count)
    return when {
        index < topIndex -> {
            // 左侧 d 层：负向偏移、逆向旋转、按层深缩小，z=40-d
            val d = topIndex - index
            StackLayerPlacement(
                offsetX = -motion.peekPx - (d - 1) * motion.peekStepPx,
                rotationDeg = -motion.rotStepDeg * d,
                scale = 1f - motion.scaleStep * d,
                alpha = if (d > leftQuota) 0f else 1f,
                zIndex = 40f - d
            )
        }
        index == topIndex -> StackLayerPlacement(0f, 0f, 1f, 1f, 100f)
        else -> {
            // 右侧 d 层：z=100-d（右侧探边恒高于左侧，被顶卡盖住时先露右侧）
            val d = index - topIndex
            StackLayerPlacement(
                offsetX = motion.peekPx + (d - 1) * motion.peekStepPx,
                rotationDeg = motion.rotStepDeg * d,
                scale = 1f - motion.scaleStep * d,
                alpha = if (d > rightQuota) 0f else 1f,
                zIndex = 100f - d
            )
        }
    }
}

/**
 * 擦洗帧计算（JS `_scrub()` 的单卡逻辑，核心运动模型）：
 * 手指位置 = 翻页动画的进度条。
 *
 * - dir：擦洗方向。-1 = 左滑（下一季），+1 = 右滑（上一季），0 = 静止
 * - progress：p ∈ (0,1]
 *
 * 前半程（p≤0.5）：顶卡跟随手指滑出至峰值（≈半卡宽），两侧探边静止；
 * 后半程（p>0.5）：顶卡沿轨迹自行回归（缩小、微旋、落入滑动方向同侧
 * 探边位），不跟随手指反向；越过峰值瞬间顶卡层级下沉（110→102）。
 * p=1 的擦洗态与翻页结算后的静止态在数学上完全相等（结算零跳变）。
 */
internal fun stackLayerPlacement(
    index: Int,
    topIndex: Int,
    count: Int,
    dir: Int,
    progress: Float,
    stageWidthPx: Float,
    motion: CardStackMotion
): StackLayerPlacement {
    val base = stackStaticPlacement(index, topIndex, count, motion)
    if (dir == 0 || progress <= 0f) return base
    val m = motion

    // ── 边界预览分支：首张可右滑、末张可左滑，行程受限于弹性区间，
    //    下层探边卡轻微联动（模拟拽动一叠实体照片时下层被带动） ──
    val atBoundary = (dir < 0 && topIndex >= count - 1) || (dir > 0 && topIndex <= 0)
    if (atBoundary) {
        return when (index) {
            // 顶卡：24dp 弹性 + 2.5° 微旋，层级最高
            topIndex -> StackLayerPlacement(
                offsetX = dir * m.boundaryPeekPx * progress,
                rotationDeg = dir * m.boundaryRotDeg * progress,
                scale = 1f,
                alpha = 1f,
                zIndex = 110f
            )
            // n1 联动：第一层探边额外 +8dp
            topIndex + dir -> StackLayerPlacement(
                offsetX = dir * (m.peekPx + m.boundaryNudge1Px * progress),
                rotationDeg = dir * m.rotStepDeg,
                scale = 1f - m.scaleStep,
                alpha = base.alpha,
                zIndex = base.zIndex
            )
            // n2 联动：第二层探边额外 +5dp
            topIndex + dir * 2 -> StackLayerPlacement(
                offsetX = dir * (m.peekPx + m.peekStepPx + m.boundaryNudge2Px * progress),
                rotationDeg = dir * m.rotStepDeg * 2,
                scale = 1f - m.scaleStep * 2,
                alpha = base.alpha,
                zIndex = base.zIndex
            )
            else -> base
        }
    }

    // ── 顶卡：峰形轨迹（滑出→峰值→回落至滑动方向同侧探边位） ──
    if (index == topIndex) {
        val maxX = stageWidthPx * 0.52f
        val cx: Float
        val rot: Float
        val sc: Float
        if (progress <= 0.5f) {
            // 拖出段：位移 0→maxX 线性，旋转 0→8°，scale 不变
            val q = progress / 0.5f
            cx = dir * maxX * q
            rot = dir * 8f * q
            sc = 1f
        } else {
            // 回归段：位移 maxX→peek、旋转 8°→rotStep、scale 1→0.92
            val q = (progress - 0.5f) / 0.5f
            cx = dir * (maxX - (maxX - m.peekPx) * q)
            rot = dir * (8f - (8f - m.rotStepDeg) * q)
            sc = 1f - m.scaleStep * q
        }
        return StackLayerPlacement(cx, rot, sc, 1f, if (progress < 0.5f) 110f else 102f)
    }

    // ── 升顶卡（原对侧探边）：位移/旋转插值至顶位，opacity 恒 1 ──
    if (index == topIndex - dir) {
        return StackLayerPlacement(
            offsetX = -dir * m.peekPx * (1f - progress),
            rotationDeg = -dir * m.rotStepDeg * (1f - progress),
            scale = 1f - m.scaleStep + m.scaleStep * progress,
            alpha = 1f,
            zIndex = 105f
        )
    }

    // ── 新探边进场（舞台转盘）：后半程自升顶卡背后沿其边缘滑出 ──
    if (index == topIndex - dir * 2) {
        // qq = 后半程进度
        val qq = max(0f, (progress - 0.5f) / 0.5f)
        val (leftQuota, rightQuota) = stackPeekQuota(topIndex, count)
        val borrowed = if (dir < 0) 2 <= rightQuota else 2 <= leftQuota
        val z = if (dir < 0) 98f else 38f
        return if (borrowed) {
            // 边界借位：该卡本来就是可见的第二层探边——不参与进场编排，
            // 与升顶卡同步全程走位（外层探边位 → 第一层探边位），保持实体
            StackLayerPlacement(
                offsetX = -dir * (m.peekPx + m.peekStepPx * (1f - progress)),
                rotationDeg = -dir * (m.rotStepDeg * 2 - m.rotStepDeg * progress),
                scale = 1f - m.scaleStep * 2 + m.scaleStep * progress,
                alpha = 1f,
                zIndex = z
            )
        } else {
            // 普通进场：透明度 0.55→1 渐显、尺寸由小变大（近大远小）；
            // 位移自升顶卡背后沿边缘滑出（可见部分为渐宽的窄边）
            val ntx = -dir * m.peekPx * (1f - progress)
            StackLayerPlacement(
                offsetX = ntx * (1f - qq) + (-dir * m.peekPx) * qq,
                rotationDeg = -dir * (m.rotStepDeg * 2 - m.rotStepDeg * qq),
                scale = 1f - m.scaleStep * 2.5f + m.scaleStep * 1.5f * qq,
                alpha = min(1f, qq / 0.18f) * 0.55f + 0.45f * qq,
                zIndex = z
            )
        }
    }

    // ── 退场卡（原滑动方向同侧探边）：进场的时间反演镜像 ──
    if (index == topIndex + dir) {
        val qq = max(0f, (progress - 0.5f) / 0.5f)
        val newTop = if (dir < 0) min(topIndex + 1, count - 1) else max(topIndex - 1, 0)
        val (l2, r2) = stackPeekQuota(newTop, count)
        val oi = topIndex + dir
        // 结算后仍属可见集合 → 不退场，翻页过程中提前插值走位至降级后的外层探边位
        val stays = if (oi < newTop) (newTop - oi) <= l2 else (oi - newTop) <= r2
        return if (!stays) {
            // 后半程向回落顶卡背后收拢（eq 为二次加速的时间反演），
            // 尺寸与透明度同步递减，顶卡落位时恰好将其完全遮蔽
            val eq = 1f - (1f - qq) * (1f - qq)
            val maxX = stageWidthPx * 0.52f
            val cx = if (progress <= 0.5f) {
                dir * maxX * (progress / 0.5f)
            } else {
                val q = (progress - 0.5f) / 0.5f
                dir * (maxX - (maxX - m.peekPx) * q)
            }
            StackLayerPlacement(
                offsetX = dir * m.peekPx * (1f - eq) + cx * eq,
                rotationDeg = dir * m.rotStepDeg,
                scale = 1f - m.scaleStep - m.scaleStep * 1.5f * qq,
                alpha = 1f - (min(1f, qq / 0.18f) * 0.55f + 0.45f * qq),
                zIndex = base.zIndex
            )
        } else {
            // 边界例外：保持实体，插值走位至外层（15→27dp / 2.2°→4.4° / 0.92→0.84）
            StackLayerPlacement(
                offsetX = dir * (m.peekPx + m.peekStepPx * progress),
                rotationDeg = dir * (m.rotStepDeg + m.rotStepDeg * progress),
                scale = 1f - m.scaleStep - m.scaleStep * progress,
                alpha = base.alpha,
                zIndex = base.zIndex
            )
        }
    }

    // 其余层（远离滑动方向侧，正常翻页时恒不可见）：保持静止底座
    return base
}

/**
 * 行程分母（JS `_progress` 的方向感知适配）：手指起点到**滑动方向同侧**
 * 屏幕边缘的距离，下限 120dp。
 *
 * JS 原版固定用"起点到左缘"（聊天卡片固定侧、位置集中），网格场景下
 * 起点随卡片所在列变化，固定左缘会导致屏幕右侧卡片向右滑的可用行程
 * 远小于分母、进度永远到不了翻页阈值——故按方向选取，保证拖到屏幕
 * 边缘恰好进度 1，两侧列卡片向边缘方向滑动总能触发阈值。
 *
 * @param startXInWindowPx 手指起点在窗口坐标系中的 x
 * @param dir 滑动方向（-1 向左 / +1 向右）
 * @param screenWidthPx 窗口宽（px）
 */
internal fun stackDragDenominator(
    startXInWindowPx: Float,
    dir: Int,
    screenWidthPx: Float,
    motion: CardStackMotion
): Float = if (dir < 0) {
    max(motion.minTravelPx, startXInWindowPx)
} else {
    max(motion.minTravelPx, screenWidthPx - startXInWindowPx)
}

/** 擦洗进度：|dx| / 行程分母，上限 1。 */
internal fun stackScrubProgress(dxPx: Float, denominatorPx: Float): Float =
    (abs(dxPx) / denominatorPx).coerceAtMost(1f)

/**
 * 松手判定（JS `_release`）：满足其一即翻页——
 * ① 慢拖过半（p > 0.5）；② 快甩（速度过阈值、方向与位移一致、
 * p > 0.04 即约 10dp 防误触下限）。边界方向（无可翻页）恒回弹。
 */
internal fun stackShouldTurnOver(
    progress: Float,
    velocityPxPerMs: Float,
    dxPx: Float,
    canTurnPage: Boolean,
    motion: CardStackMotion
): Boolean {
    if (!canTurnPage) return false
    if (progress > 0.5f) return true
    val fling = abs(velocityPxPerMs) > motion.flingVelocityPxPerMs &&
        (velocityPxPerMs > 0f) == (dxPx > 0f) &&
        progress > 0.04f
    return fling
}

/** 速度指数平滑（JS `_bindGesture`：当前帧 0.7 + 历史 0.3）。 */
internal fun stackSmoothVelocity(currentPxPerMs: Float, historyPxPerMs: Float): Float =
    0.7f * currentPxPerMs + 0.3f * historyPxPerMs

/** 翻页结算：左滑（dir<0）下一季、右滑（dir>0）上一季，clamp 到合法范围。 */
internal fun stackSettleTopIndex(topIndex: Int, dir: Int, count: Int): Int =
    if (dir < 0) min(topIndex + 1, count - 1) else max(topIndex - 1, 0)
