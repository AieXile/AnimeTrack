package com.aiexile.animetrack.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.navigation.SIDE_PANEL_ANIM_DURATION

/**
 * 播放器抽屉的层级：[Menu] 为一级「更多」，其余为从一级进入的二级界面。
 *
 * 层级只是同一个抽屉里的内容状态：切换层级时抽屉框与遮罩保持挂载，
 * 不会被卸载重建，因此从二级返回一级不会出现「先收起再弹出」的闪动。
 */
internal sealed interface PlayerDrawerLevel {
    data object Menu : PlayerDrawerLevel
    data class Tracks(val trackType: Int) : PlayerDrawerLevel
    data object Episodes : PlayerDrawerLevel
    data object Channels : PlayerDrawerLevel
    data object Quality : PlayerDrawerLevel
    data object Speed : PlayerDrawerLevel
    data object Scale : PlayerDrawerLevel
}

@Composable
internal fun PlayerDrawerLevel.title(): String = when (this) {
    PlayerDrawerLevel.Menu -> stringResource(R.string.player_menu_more)
    is PlayerDrawerLevel.Tracks -> stringResource(
        if (trackType == C.TRACK_TYPE_TEXT) R.string.player_subtitles
        else R.string.player_audio_track
    )
    PlayerDrawerLevel.Episodes -> stringResource(R.string.player_episodes)
    PlayerDrawerLevel.Channels -> stringResource(R.string.player_channels)
    PlayerDrawerLevel.Quality -> stringResource(R.string.player_quality)
    PlayerDrawerLevel.Speed -> stringResource(R.string.player_playback_speed)
    PlayerDrawerLevel.Scale -> stringResource(R.string.player_video_scale)
}

/**
 * 播放器抽屉宿主：一级与二级共用一个抽屉框和遮罩，只在框内切换内容。
 *
 * - [onStepBack] 非空表示当前抽屉内还有上一级：点击面板外部/返回键时只回退一层；
 * - 为 null 时点击面板外部/返回键则播退场动画并关闭抽屉。
 */
@Composable
internal fun PlayerDrawerHost(
    level: PlayerDrawerLevel,
    onDismissed: () -> Unit,
    onStepBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.(PlayerDrawerLevel, requestClose: () -> Unit) -> Unit
) {
    SideDrawerPanel(
        onDismissed = onDismissed,
        stepBack = onStepBack
    ) { requestClose ->
        AnimatedContent(
            targetState = level,
            transitionSpec = {
                // 进入二级从右侧滑入，返回一级反向滑出，方向与层级关系一致
                val forward = targetState != PlayerDrawerLevel.Menu
                val spec = tween<androidx.compose.ui.unit.IntOffset>(SIDE_PANEL_ANIM_DURATION)
                val enter = slideInHorizontally(spec) { width -> if (forward) width / 4 else -width / 4 } +
                    fadeIn(tween(SIDE_PANEL_ANIM_DURATION))
                val exit = slideOutHorizontally(spec) { width -> if (forward) -width / 4 else width / 4 } +
                    fadeOut(tween(SIDE_PANEL_ANIM_DURATION * 2 / 3))
                enter togetherWith exit
            },
            modifier = Modifier.weight(1f),
            label = "playerDrawerLevel"
        ) { target ->
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = target.title(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                content(target, requestClose)
            }
        }
    }
}
