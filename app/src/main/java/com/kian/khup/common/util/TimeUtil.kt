package com.kian.khup.common.util

import java.time.ZoneId
import java.time.ZonedDateTime

fun todayStartLocalMs(zone: ZoneId = ZoneId.systemDefault()): Long =
    ZonedDateTime.now(zone)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
