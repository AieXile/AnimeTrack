package com.aiexile.animetrack.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
