package com.kian.khup.collection.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class AppUsageSummary(
    val packageName: String,
    val appLabel: String,
    val foregroundMs: Long,
)

@Singleton
class UsageStatsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(UsageStatsManager::class.java)

    fun getTodayTopApps(limit: Int = 5): List<AppUsageSummary> {
        val manager = usageStatsManager ?: return emptyList()
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // queryUsageStats(INTERVAL_DAILY) 返回 bucket 全段累计,会跨天泄漏。
        // 这里用 queryEvents,只统计屏幕点亮期间的当前前台 App。
        val events = manager.queryEvents(startOfDay - ONE_DAY_MS, now) ?: return emptyList()
        val event = UsageEvents.Event()
        val totals = HashMap<String, Long>()
        var screenOn = false
        var currentPackage: String? = null
        var lastTs = startOfDay

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(startOfDay - ONE_DAY_MS, now)
            if (ts >= startOfDay) {
                if (screenOn && currentPackage != null && ts > lastTs) {
                    totals[currentPackage] = (totals[currentPackage] ?: 0L) + (ts - lastTs)
                }
                lastTs = ts
            }
            val pkg = event.packageName
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> screenOn = false
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> if (pkg != null) currentPackage = pkg
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentPackage == pkg) currentPackage = null
                }
            }
        }

        if (screenOn && currentPackage != null && now > lastTs) {
            totals[currentPackage] = (totals[currentPackage] ?: 0L) + (now - lastTs)
        }

        val maxPerApp = now - startOfDay
        return totals.asSequence()
            .map { (pkg, ms) ->
                AppUsageSummary(
                    packageName = pkg,
                    appLabel = resolveAppLabel(pkg),
                    foregroundMs = ms.coerceAtMost(maxPerApp),
                )
            }
            .filter { it.foregroundMs > 0 }
            .sortedByDescending { it.foregroundMs }
            .take(limit)
            .toList()
    }

    fun getTotalForegroundMs(startMs: Long, endMs: Long): Long {
        if (endMs <= startMs) return 0L
        val manager = usageStatsManager ?: return 0L
        val events = manager.queryEvents(startMs, endMs) ?: return 0L
        val event = UsageEvents.Event()
        val activePackages = mutableSetOf<String>()
        var lastTs = startMs
        var totalMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val ts = event.timeStamp.coerceIn(startMs, endMs)
            if (activePackages.isNotEmpty() && ts > lastTs) {
                totalMs += ts - lastTs
            }
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> activePackages.add(pkg)
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> activePackages.remove(pkg)
            }
            lastTs = ts
        }

        if (activePackages.isNotEmpty() && endMs > lastTs) {
            totalMs += endMs - lastTs
        }
        return totalMs.coerceAtMost(endMs - startMs)
    }

    fun getScreenInteractiveMs(startMs: Long, endMs: Long): Long {
        if (endMs <= startMs) return 0L
        val manager = usageStatsManager ?: return 0L
        val lookbackStart = startMs - ONE_DAY_MS
        val events = manager.queryEvents(lookbackStart, endMs) ?: return 0L
        val event = UsageEvents.Event()
        var screenOn = false
        var screenOnStart: Long? = null
        var totalMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp.coerceIn(lookbackStart, endMs)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    screenOn = true
                    if (ts >= startMs && screenOnStart == null) {
                        screenOnStart = ts
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (screenOn) {
                        screenOnStart?.let { start ->
                            if (ts > start) totalMs += ts - start
                        }
                    }
                    screenOn = false
                    screenOnStart = null
                }
            }

            if (ts < startMs) {
                screenOnStart = if (screenOn) startMs else null
            }
        }

        if (screenOn) {
            val start = screenOnStart ?: startMs
            if (endMs > start) totalMs += endMs - start
        }
        return totalMs.coerceAtMost(endMs - startMs)
    }

    fun resolveAppLabel(packageName: String): String {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    fun getCurrentForegroundPackage(lookbackMs: Long = 15_000): String? {
        val manager = usageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - lookbackMs, now) ?: return null
        val event = UsageEvents.Event()
        var currentPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> currentPackage = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (currentPackage == event.packageName) currentPackage = null
                }
            }
        }

        return currentPackage
    }

    private companion object {
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}
