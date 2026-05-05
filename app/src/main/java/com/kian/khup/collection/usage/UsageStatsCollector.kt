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
        // 这里用 queryEvents 自己配对 RESUMED/PAUSED,严格落在 [startOfDay, now]。
        val events = manager.queryEvents(startOfDay, now) ?: return emptyList()
        val event = UsageEvents.Event()
        val foregroundStart = HashMap<String, Long>()
        val totals = HashMap<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    foregroundStart[pkg] = event.timeStamp.coerceAtLeast(startOfDay)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = foregroundStart.remove(pkg) ?: continue
                    val delta = (event.timeStamp - start).coerceAtLeast(0L)
                    totals[pkg] = (totals[pkg] ?: 0L) + delta
                }
            }
        }
        // 仍在前台、还没收到 PAUSED 的会话,按 now 截断
        foregroundStart.forEach { (pkg, start) ->
            val delta = (now - start).coerceAtLeast(0L)
            totals[pkg] = (totals[pkg] ?: 0L) + delta
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
}
