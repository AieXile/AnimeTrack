package com.aiexile.animetrack.data.player.service

import android.os.Bundle

/**
 * PlaybackService 与 PlayerViewModel 之间的自定义会话指令约定。
 *
 * 用于同步网页播放选集队列的上一集/下一集状态，并把系统通知栏/蓝牙的切集动作转回 ViewModel。
 */
object PlaybackSessionCommands {
    const val ACTION_SET_WEB_NAVIGATION = "com.aiexile.animetrack.player.SET_WEB_NAVIGATION"
    const val ACTION_WEB_PREVIOUS = "com.aiexile.animetrack.player.WEB_PREVIOUS"
    const val ACTION_WEB_NEXT = "com.aiexile.animetrack.player.WEB_NEXT"

    private const val EXTRA_IS_WEB_QUEUE = "is_web_queue"
    private const val EXTRA_HAS_PREVIOUS = "has_previous"
    private const val EXTRA_HAS_NEXT = "has_next"

    data class WebNavigationState(
        val isWebQueue: Boolean = false,
        val hasPrevious: Boolean = false,
        val hasNext: Boolean = false
    )

    fun buildWebNavigationArgs(
        isWebQueue: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean
    ): Bundle = Bundle().apply {
        putBoolean(EXTRA_IS_WEB_QUEUE, isWebQueue)
        putBoolean(EXTRA_HAS_PREVIOUS, hasPrevious)
        putBoolean(EXTRA_HAS_NEXT, hasNext)
    }

    fun parseWebNavigationArgs(args: Bundle): WebNavigationState = WebNavigationState(
        isWebQueue = args.getBoolean(EXTRA_IS_WEB_QUEUE, false),
        hasPrevious = args.getBoolean(EXTRA_HAS_PREVIOUS, false),
        hasNext = args.getBoolean(EXTRA_HAS_NEXT, false)
    )
}