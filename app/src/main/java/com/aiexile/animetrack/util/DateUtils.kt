package com.aiexile.animetrack.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 获取今天是周几（1=周一, 7=周日），与 Bangumi 的 airWeekday 对齐
 */
fun getCurrentWeekday(): Int {
    return Calendar.getInstance().get(Calendar.DAY_OF_WEEK).let {
        when (it) {
            Calendar.SUNDAY -> 7
            else -> it - 1
        }
    }
}

// 日期格式化工具，避免各处重复创建 SimpleDateFormat

/** 格式：yyyy-MM-dd */
fun formatDate(date: Date): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
}

/** 格式：yyyy-MM-dd，接收时间戳 */
fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
}

/** 格式：MM月dd日 */
fun formatDateMonthDay(timestamp: Long): String {
    return SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(timestamp))
}

/** 格式：yyyy年MM月 */
fun formatDateYearMonth(timestamp: Long): String {
    return SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(Date(timestamp))
}

/** 格式：yyyy.MM.dd */
fun formatDateDotSeparated(date: Date): String {
    return SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(date)
}

/** 格式：yyyy-MM-dd HH:mm:ss */
fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

/**
 * 将日期字符串格式化为本地日期显示（yyyy-MM-dd）。
 * - ISO UTC 时间（如 "2020-01-10T16:00:00.000Z"）→ 转为本地时区日期
 * - 纯日期（如 "2020-01-10"）→ 原样返回
 * - 解析失败 → 原样返回
 */
fun formatAirDate(airDate: String?): String? {
    if (airDate.isNullOrBlank()) return null
    // 已是 yyyy-MM-dd 格式，直接返回
    if (airDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return airDate
    return try {
        // 兼容带时区的 ISO 8601（如 2020-01-10T16:00:00.000Z）
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(airDate) ?: return airDate
        formatDate(date)
    } catch (e: Exception) {
        try {
            // 兼容不带毫秒的 ISO 8601（如 2020-01-10T16:00:00Z）
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(airDate) ?: return airDate
            formatDate(date)
        } catch (e2: Exception) {
            airDate
        }
    }
}

/**
 * 判断开播日期是否晚于今天（尚未开播）。
 * 解析失败返回 false。
 */
fun isAirDateInFuture(airDate: String?): Boolean {
    if (airDate.isNullOrBlank()) return false
    return runCatching {
        java.time.LocalDate.parse(airDate).isAfter(java.time.LocalDate.now())
    }.getOrDefault(false)
}

/**
 * 将 yyyy-MM-dd 日期字符串解析为本地时间戳（毫秒）。
 * 解析失败返回 null。
 */
fun parseDateToTimestamp(dateStr: String?): Long? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        sdf.parse(dateStr)?.time
    } catch (e: Exception) {
        null
    }
}

