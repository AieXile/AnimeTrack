package com.aiexile.animetrack.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.StatusBadgeTransitionMode
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.settings.optionRowSubtitleStyle
import com.aiexile.animetrack.ui.components.settings.optionRowTitleStyle

/**
 * 状态角标转场设置项：标题/说明 + 两张并排的动态示意卡。
 *
 * 两个示意都由同一份循环进度驱动（只读 State 的 draw 阶段，不逐帧重组），
 * 离开屏幕（LazyColumn 回收）后不再播放。
 */
@Composable
internal fun StatusBadgeTransitionSetting(
    selected: StatusBadgeTransitionMode,
    onSelected: (StatusBadgeTransitionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "statusBadgeTransitionPreview")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "statusBadgeTransitionProgress"
    )

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.features_status_badge_transition),
            style = optionRowTitleStyle(),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.features_status_badge_transition_desc),
            style = optionRowSubtitleStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusBadgeTransitionModeCard(
                mode = StatusBadgeTransitionMode.FOLLOW_COVER,
                label = stringResource(R.string.features_status_badge_transition_follow_cover),
                description = stringResource(R.string.features_status_badge_transition_follow_cover_desc),
                selected = selected == StatusBadgeTransitionMode.FOLLOW_COVER,
                progress = progress,
                onClick = { onSelected(StatusBadgeTransitionMode.FOLLOW_COVER) },
                modifier = Modifier.weight(1f)
            )
            StatusBadgeTransitionModeCard(
                mode = StatusBadgeTransitionMode.FLY_TO_TITLE,
                label = stringResource(R.string.features_status_badge_transition_fly_to_title),
                description = stringResource(R.string.features_status_badge_transition_fly_to_title_desc),
                selected = selected == StatusBadgeTransitionMode.FLY_TO_TITLE,
                progress = progress,
                onClick = { onSelected(StatusBadgeTransitionMode.FLY_TO_TITLE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 单个模式示意卡：标签在上、动态示意居中、简短说明在下，选中态用主色描边标识。 */
@Composable
private fun StatusBadgeTransitionModeCard(
    mode: StatusBadgeTransitionMode,
    label: String,
    description: String,
    selected: Boolean,
    progress: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.RadioButton
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(SquircleShape(16.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = SquircleShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            StatusBadgeTransitionPreview(
                mode = mode,
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
        )
    }
}
