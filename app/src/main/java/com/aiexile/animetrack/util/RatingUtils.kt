package com.aiexile.animetrack.util

/**
 * 评分换算工具：外部数据源评分（10 分制）→ 本地 5 分制。
 */
object RatingUtils {

    /**
     * 将源评分（0-10，null 或 <=0 视为未评分）转为本地 5 分制 Float。
     * 有分值时除以 2 并四舍五入到 0.5 步进。
     */
    fun sourceScoreToRating(score: Double?): Float? {
        if (score == null || score <= 0.0) return null
        return roundToHalf(score.toFloat() / 2f)
    }

    /** 将浮点数四舍五入到最近的 0.5 步进值，并限制在 0-5 范围内 */
    fun roundToHalf(value: Float): Float {
        val steps = kotlin.math.round(value * 2f)
        return steps.coerceIn(0f, 10f) / 2f
    }
}
