package com.aiexile.animetrack.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.aiexile.animetrack.util.coverImageRequestForList

/**
 * 起播前的番剧封面占位。
 *
 * 两个作用：
 * 1. 加载态不再是纯黑一块，进入播放器先看到封面；
 * 2. 作为详情页封面 Morph 的落点内容——共享元素飞过来时这里显示的就是同一张封面，
 *    落地后淡出、交给真实视频画面。
 *
 * [shape] 用于裁切封面：共享元素飞行中内容不能被封面盖到四角之外，
 * 否则黑底的圆角会被铺满而看起来是直角。
 */
@Composable
internal fun PlayerVideoPlaceholder(
    coverUrl: String?,
    visible: Boolean,
    shape: Shape,
    instantEnter: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (coverUrl.isNullOrBlank()) return
    val context = LocalContext.current
    AnimatedVisibility(
        visible = visible,
        enter = if (instantEnter) EnterTransition.None else fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            AsyncImage(
                model = coverImageRequestForList(context, coverUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 压暗封面：避免亮部盖过加载文字的对比度
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }
    }
}
