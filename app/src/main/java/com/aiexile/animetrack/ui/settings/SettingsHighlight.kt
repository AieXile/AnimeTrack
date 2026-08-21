package com.aiexile.animetrack.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiexile.animetrack.ui.components.SquircleShape
import kotlinx.coroutines.delay

/**
 * 设置搜索定位总线：搜索结果点击后暂存「目标路由 + 定位 key」，
 * 子页面进入组合时消费并高亮定位到对应设置项。
 *
 * 采用内存单例而非路由参数传递，避免查询参数侵入现有
 * pane 路由匹配与返回栈逻辑（isPaneRoute 按首段路由名匹配）。
 */
object SettingsHighlightBus {
    private var pendingRoute: String? = null
    private var pendingKey: String? = null

    /** 发起定位请求 */
    fun request(route: String, key: String?) {
        pendingRoute = route
        pendingKey = key
    }

    /** 消费定位请求：仅当目标路由匹配时返回 key 并清除，不匹配时保留请求 */
    fun consume(route: String): String? {
        if (pendingRoute != route) return null
        val key = pendingKey
        pendingRoute = null
        pendingKey = null
        return key
    }
}

/**
 * 页面级高亮 hook：进入页面首次组合时同步消费总线请求（保证同周期内
 * ExpandableSettingsGroup 等组件能依据结果计算初始展开状态），
 * 高亮持续 [durationMillis] 后自动消除。
 */
@Composable
fun rememberSettingsHighlight(route: String, durationMillis: Long = 2000L): String? {
    var highlightKey by remember { mutableStateOf(SettingsHighlightBus.consume(route)) }
    LaunchedEffect(highlightKey) {
        if (highlightKey != null) {
            delay(durationMillis)
            highlightKey = null
        }
    }
    return highlightKey
}

/**
 * 行级高亮背景：当 itemKey 与当前高亮 key 匹配时，
 * 渲染呼吸脉冲的 primaryContainer 背景，便于用户一眼定位。
 */
@Composable
fun rememberHighlightModifier(itemKey: String?, highlightKey: String?): Modifier {
    if (itemKey == null || itemKey != highlightKey) return Modifier
    val transition = rememberInfiniteTransition(label = "searchHighlight")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlightAlpha"
    )
    return Modifier.background(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha),
        shape = SquircleShape(10.dp)
    )
}
