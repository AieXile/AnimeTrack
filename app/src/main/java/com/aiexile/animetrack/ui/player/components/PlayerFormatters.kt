package com.aiexile.animetrack.ui.player.components

fun formatDuration(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/** 控制条短标签：竖屏视频按短边计算；无法确定尺寸时交由界面显示「自动」。 */
internal fun formatVideoResolution(width: Int, height: Int): String? {
    if (height <= 0) return null
    val resolution = if (width > 0) minOf(width, height) else height
    return when (resolution) {
        1440 -> "2K"
        2160 -> "4K"
        4320 -> "8K"
        else -> "${resolution}P"
    }
}