package com.kian.khup.collection.usage

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
}
