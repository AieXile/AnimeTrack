package com.aiexile.animetrack.model

/** 可展示的观看记录；完成后保留记录，续播位置则由播放器仓库单独清除。 */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val completed: Boolean = false,
    val updatedAt: Long = 0L,
    val watchedMs: Long = 0L
) {
    val fraction: Float
        get() = if (completed) 1f else if (durationMs > 0)
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** 忽略迟到的落盘；片尾暂停/退出的重复采样不能覆盖已完成状态。 */
    fun merge(newer: PlaybackProgress): PlaybackProgress {
        if (newer.updatedAt < updatedAt) return this
        val keepCompletion = completed && durationMs > 0 && newer.positionMs >= durationMs * .95
        return newer.copy(completed = newer.completed || keepCompletion)
    }
}
