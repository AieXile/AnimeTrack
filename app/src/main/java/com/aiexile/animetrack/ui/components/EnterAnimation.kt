package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 进场动画策略参数
 */
data class EnterMotionPolicy(
    val staggerStepMs: Int,
    val maxStaggerMs: Int,
    val initialScale: Float,
    val translationFactor: Float,
    val dampingRatio: Float,
    val stiffness: Float
)

/** 完整进场动画参数（首次加载） */
private val FullEnterPolicy = EnterMotionPolicy(
    staggerStepMs = 30,
    maxStaggerMs = 200,
    initialScale = 0.9f,
    translationFactor = 1f,
    dampingRatio = 0.7f,
    stiffness = 350f
)

/** 滚入轻量动效参数（alpha 始终为 1，缩放+位移接近完整进场） */
private val ScrollEnterPolicy = EnterMotionPolicy(
    staggerStepMs = 20,
    maxStaggerMs = 120,
    initialScale = 0.9f,
    translationFactor = 0.33f,
    dampingRatio = 0.7f,
    stiffness = 350f
)

/**
 * 列表项进场动画
 *
 * @param index          列表项索引，用于计算交错延迟
 * @param key            触发重置动画的键值（传 Unit 只在首次挂载播放）
 * @param isInitialLoad  是否首次加载（true=完整波浪进场，false=滚入轻量动效）
 * @param animationEnabled 是否启用动画
 */
fun Modifier.animateEnter(
    index: Int = 0,
    key: Any? = Unit,
    isInitialLoad: Boolean = true,
    animationEnabled: Boolean = true,
    skipAnimation: Boolean = false
): Modifier = composed {
    if (!animationEnabled) return@composed this

    val motionPolicy = remember(isInitialLoad) {
        if (isInitialLoad) FullEnterPolicy else ScrollEnterPolicy
    }
    val initialOffsetY = 60f

    // 首次组合时若正处于共享元素过渡（从详情页返回主页），直接就位不播放进场，
    // 避免 graphicsLayer 的 scale/translation 与飞出动画的落点叠加导致卡片抖动
    var animationStarted by remember(key) { mutableStateOf(skipAnimation) }

    val delayMs = (index * motionPolicy.staggerStepMs).coerceAtMost(motionPolicy.maxStaggerMs)

    LaunchedEffect(key) {
        if (skipAnimation) {
            animationStarted = true
            return@LaunchedEffect
        }
        delay(delayMs.toLong())
        animationStarted = true
    }

    val progress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = motionPolicy.dampingRatio,
            stiffness = motionPolicy.stiffness
        ),
        label = "enterProgress"
    )

    this.graphicsLayer {
        // 首次加载：alpha 0→1；滚入：始终可见
        alpha = if (isInitialLoad) progress else 1f
        translationY = (initialOffsetY * motionPolicy.translationFactor) * (1f - progress)
        scaleX = motionPolicy.initialScale + (1f - motionPolicy.initialScale) * progress
        scaleY = motionPolicy.initialScale + (1f - motionPolicy.initialScale) * progress
    }
}
