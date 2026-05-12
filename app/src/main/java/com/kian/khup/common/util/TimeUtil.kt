package com.kian.khup.common.util

import java.time.ZoneId
import java.time.ZonedDateTime

fun todayStartLocalMs(zone: ZoneId = ZoneId.systemDefault()): Long =
    ZonedDateTime.now(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h} 小时 ${m} 分"
        m > 0 -> "${m} 分钟"
        else  -> "< 1 分钟"
    }
}
