package com.aiexile.animetrack.util

object TimeZoneHelper {
    // 北京时间 → UTC（上报用）
    fun beijingHourToUtc(beijingHour: Int): Int {
        return (beijingHour - 8 + 24) % 24
    }

    // UTC → 北京时间（展示用）
    fun utcHourToBeijing(utcHour: Int): Int {
        return (utcHour + 8) % 24
    }

    // 北京时间显示格式（如 "08:00"）
    fun formatBeijingHour(hour: Int): String {
        return String.format("%02d:00", hour)
    }
}
