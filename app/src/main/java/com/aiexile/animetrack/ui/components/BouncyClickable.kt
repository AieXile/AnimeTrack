package com.aiexile.animetrack.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Q 弹点击效果：按压缩放，松手带回弹过冲（iOS 风格按压手感）。
 *
 * 抽取自开源库 Compose-Symphony（MIT 协议）：
 * https://github.com/jay3-yy/Compose-Symphony
 *
 * 使用无涟漪反馈（indication = null）的独立小元素场景；
 * 已有按压反馈的组件（如共享元素卡片、液态玻璃 FAB、胶囊导航）不应叠加使用，
 * 避免与既有 graphicsLayer 缩放冲突。
 *
 * @param scaleDown 按压时的缩放比例，默认 0.90
 * @param onClick 点击回调
 */
fun Modifier.bouncyClickable(
    scaleDown: Float = 0.90f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BouncyScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
