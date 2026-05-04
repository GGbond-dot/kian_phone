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

        return manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now,
        )
            .orEmpty()
            .asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .map { (packageName, stats) ->
                AppUsageSummary(
                    packageName = packageName,
                    appLabel = resolveAppLabel(packageName),
                    foregroundMs = stats.sumOf { it.totalTimeInForeground },
                )
            }
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
