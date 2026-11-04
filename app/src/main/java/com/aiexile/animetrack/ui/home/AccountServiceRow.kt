package com.aiexile.animetrack.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter

/**
 * 账号面板中单个服务账号行的展开操作项。
 *
 * @param destructive 使用错误色（退出登录），其余操作沿用主题色
 */
internal data class AccountRowAction(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 账号服务行：收起态是 56dp 的圆角行；展开后保持同样的容器样式，
 * 在行内向下生长出一排横向操作按钮（账号管理 / 退出登录），替代原先的二次确认弹窗。
 *
 * - 只增加内容高度，不改背景、圆角与阴影，展开前后视觉材质一致；
 * - 尾部箭头旋转 90° 指示可收起，操作按钮延迟淡入掩盖展开初期的拉伸。
 *
 * 状态只由 [expanded] 与 [onToggle] 控制；面板侧负责同一时间只展开一个账号。
 */
@Composable
internal fun AccountServiceRow(
    title: String,
    subtitle: String,
    icon: Painter,
    isConnected: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<AccountRowAction>,
    expired: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(
            durationMillis = AccountRowSpec.ExpandDurationMillis,
            easing = FastOutSlowInEasing
        ),
        label = "accountRowChevron"
    )
    val rowShape = remember { SquircleShape(AccountRowSpec.Corner) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AccountRowSpec.HeaderHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = when {
                    expired -> colorScheme.outline
                    isConnected -> colorScheme.primary
                    else -> colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (expired) colorScheme.onSurfaceVariant else colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 状态小点：已连接绿色 / 失效或未连接灰色
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        color = if (isConnected && !expired) Color(0xFF4CAF50)
                        else colorScheme.outlineVariant
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_RIGHT),
                contentDescription = null,
                tint = if (expired) colorScheme.outlineVariant else colorScheme.outline,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(
                    durationMillis = AccountRowSpec.ExpandDurationMillis,
                    easing = FastOutSlowInEasing
                ),
                expandFrom = Alignment.Top
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = AccountRowSpec.FadeInDurationMillis,
                    delayMillis = AccountRowSpec.FadeInDelayMillis
                )
            ),
            exit = shrinkVertically(
                animationSpec = tween(
                    durationMillis = AccountRowSpec.CollapseDurationMillis,
                    easing = FastOutSlowInEasing
                ),
                shrinkTowards = Alignment.Top
            ) + fadeOut(
                animationSpec = tween(durationMillis = AccountRowSpec.FadeOutDurationMillis)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AccountRowSpec.ActionHPadding,
                        end = AccountRowSpec.ActionHPadding,
                        bottom = AccountRowSpec.ActionBottomPadding
                    ),
                horizontalArrangement = Arrangement.spacedBy(AccountRowSpec.ActionGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEach { action ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(AccountRowSpec.ActionHeight)
                            .clip(rowShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = action.onClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = action.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (action.destructive) colorScheme.error
                            else colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** 账号行展开动画的视觉令牌（同一文件内唯一出处） */
private object AccountRowSpec {
    /** 与面板内其他服务行一致的圆角 */
    val Corner = 16.dp

    val HeaderHeight = 56.dp

    /** 操作按钮高度，保证 48dp 级触控目标 */
    val ActionHeight = 46.dp

    val ActionHPadding = 12.dp
    val ActionGap = 8.dp
    val ActionBottomPadding = 8.dp

    const val ExpandDurationMillis = 240
    const val CollapseDurationMillis = 200
    const val FadeInDurationMillis = 140
    const val FadeInDelayMillis = 40
    const val FadeOutDurationMillis = 100
}
