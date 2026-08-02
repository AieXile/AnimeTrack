package com.aiexile.animetrack.util

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
 * 将日期字符串格式化为本地日期显示（yyyy-MM-dd 或 yyyy-MM）。
 * - ISO UTC 时间（如 "2020-01-10T16:00:00.000Z"）→ 转为本地时区日期
 * - 纯日期（如 "2020-01-10"）→ 原样返回
 * - 年月（如 "2027-10"）→ 原样返回
 * - 解析失败 → 原样返回
 */
fun formatAirDate(airDate: String?): String? {
    if (airDate.isNullOrBlank()) return null
    // 已是 yyyy-MM-dd 格式，直接返回
    if (airDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return airDate
    // 年月格式 yyyy-MM，原样返回
    if (airDate.matches(Regex("^\\d{4}-\\d{2}$"))) return airDate
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
 * 兼容 yyyy-MM-dd、ISO 8601、yyyy-MM（年月）格式。解析失败返回 false。
 */
fun isAirDateInFuture(airDate: String?): Boolean {
    val date = parseAirDateToLocalDate(airDate) ?: return false
    return date.isAfter(LocalDate.now())
}

/**
 * 将放送日期字符串解析为 [LocalDate]。
 * 支持 yyyy-MM-dd、ISO 8601（UTC）、yyyy-MM（年月，按该月 1 号）。
 * 解析失败返回 null。
 */
fun parseAirDateToLocalDate(airDate: String?): LocalDate? {
    if (airDate.isNullOrBlank()) return null
    val normalized = formatAirDate(airDate) ?: airDate
    return runCatching {
        when {
            // 年月格式 yyyy-MM → 补全为该月 1 号
            normalized.matches(Regex("^\\d{4}-\\d{2}$")) ->
                LocalDate.parse("${normalized}-01", DateTimeFormatter.ISO_LOCAL_DATE)
            else -> LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }.getOrNull()
}

/**
 * 判断番剧是否「未放送」（尚未开播）。
 * - 无放送日期 → true（视为未放送）
 * - 有日期且在当前日期之后 → true
 * - 已到/过放送日期 → false
 * - 解析失败 → true（保守视为未放送）
 */
fun isUnaired(airDate: String?): Boolean {
    if (airDate.isNullOrBlank()) return true
    val date = parseAirDateToLocalDate(airDate) ?: return true
    return date.isAfter(LocalDate.now())
}

/**
 * 从放送日期推算星期几（1=周一, 7=周日），与 Bangumi 的 airWeekday 对齐。
 * 仅对完整日期有效；年月格式返回 null。解析失败返回 null。
 */
fun weekdayFromAirDate(airDate: String?): Int? {
    if (airDate.isNullOrBlank()) return null
    // 年月格式无法推算具体星期
    if (airDate.matches(Regex("^\\d{4}-\\d{2}$"))) return null
    val date = parseAirDateToLocalDate(airDate) ?: return null
    return date.dayOfWeek.value // Monday=1 ... Sunday=7
}

/**
 * 将放送日期格式化为友好显示文本。
 * - yyyy-MM-dd → "yyyy-MM-dd"
 * - yyyy-MM → "yyyy年MM月"
 * - ISO 8601 → 转为本地日期后显示
 * - 解析失败 → 原样返回
 */
fun formatAirDateDisplay(airDate: String?): String? {
    val normalized = formatAirDate(airDate) ?: return null
    // 年月格式 → "yyyy年MM月"
    if (normalized.matches(Regex("^\\d{4}-\\d{2}$"))) {
        return runCatching {
            val parts = normalized.split("-")
            "${parts[0]}年${parts[1]}月"
        }.getOrDefault(normalized)
    }
    return normalized
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

