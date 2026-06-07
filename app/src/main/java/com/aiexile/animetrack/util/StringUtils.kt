package com.aiexile.animetrack.util

/**
 * 清理摘要文本：去除首尾空白，将连续3个及以上换行压缩为2个
 */
fun String.cleanSummary(): String {
    return trim().replace(Regex("\n{3,}"), "\n\n")
}
