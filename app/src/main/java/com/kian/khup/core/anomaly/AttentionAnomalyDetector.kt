package com.kian.khup.core.anomaly

import android.util.Log
import com.kian.khup.collection.usage.UsageStatsCollector
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.Event
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AttentionAnomalyDetector @Inject constructor(
    private val appSessionDao: AppSessionDao,
    private val eventDao: EventDao,
    private val attentionAnomalyDao: AttentionAnomalyDao,
    private val usageStatsCollector: UsageStatsCollector,
) {
    suspend fun detectToday(): List<AttentionAnomaly> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dayStart = startOfDayMs(now)
        val anomalies = buildList {
            addAll(detectAppUsageSpikes(dayStart, now))
            addAll(detectLateAlgorithmUsage(dayStart, now))
            addAll(detectNotificationBursts(dayStart, now))
            detectRapidAppSwitching(dayStart, now)?.let(::add)
            addAll(detectRepeatedScreenInteractive(dayStart, now))
        }

        attentionAnomalyDao.deleteForDay(dayStart)
        if (anomalies.isNotEmpty()) {
            attentionAnomalyDao.upsertAll(anomalies)
        }
        Log.i(TAG, "attention anomalies refreshed: day=$dayStart count=${anomalies.size}")
        attentionAnomalyDao.loadForDay(dayStart)
    }

    private suspend fun detectAppUsageSpikes(dayStart: Long, now: Long): List<AttentionAnomaly> {
        val todayApps = appSessionDao.loadTopUsageSince(dayStart, APP_USAGE_LIMIT)
        if (todayApps.isEmpty()) return emptyList()

        val baselineStart = dayStart - TimeUnit.DAYS.toMillis(BASELINE_DAYS.toLong())
        val baselines = appSessionDao.loadUsageBaselines(
            startMs = baselineStart,
            endMs = dayStart,
            minActiveDays = MIN_BASELINE_ACTIVE_DAYS,
        ).associateBy { it.packageName }

        return todayApps.mapNotNull { usage ->
            val baseline = baselines[usage.packageName] ?: return@mapNotNull null
            val baselineMs = baseline.averageForegroundMs.toLong()
            val deltaMs = usage.foregroundMs - baselineMs
            val ratio = if (baselineMs <= 0L) 0.0 else usage.foregroundMs.toDouble() / baselineMs.toDouble()
            val isSpike = usage.foregroundMs >= MIN_SPIKE_USAGE_MS &&
                deltaMs >= MIN_SPIKE_DELTA_MS &&
                ratio >= MIN_SPIKE_RATIO

            if (!isSpike) return@mapNotNull null

            val label = usageStatsCollector.resolveAppLabel(usage.packageName)
            val severity = when {
                ratio >= HIGH_SPIKE_RATIO || deltaMs >= HIGH_SPIKE_DELTA_MS -> 3
                else -> 2
            }
            AttentionAnomaly(
                anomalyKey = "app_spike:${dayStart}:${usage.packageName}",
                dayStartMs = dayStart,
                type = TYPE_APP_USAGE_SPIKE,
                severity = severity,
                title = "$label 用时明显升高",
                detail = "今天 ${formatDuration(usage.foregroundMs)}，过去 $BASELINE_DAYS 天活跃日均 ${formatDuration(baselineMs)}，多出 ${formatDuration(deltaMs)}。",
                packageName = usage.packageName,
                metricValue = usage.foregroundMs,
                baselineValue = baselineMs,
                createdAt = now,
                ruleVersion = RULE_VERSION,
            )
        }
    }

    private fun detectLateAlgorithmUsage(dayStart: Long, now: Long): List<AttentionAnomaly> {
        val lateStart = Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (now <= lateStart) return emptyList()

        return usageStatsCollector.getTopApps(lateStart, now, APP_USAGE_LIMIT)
            .filter { it.packageName in algorithmPackages && it.foregroundMs >= MIN_LATE_ALGORITHM_USAGE_MS }
            .map { usage ->
                val severity = if (usage.foregroundMs >= HIGH_LATE_ALGORITHM_USAGE_MS) 3 else 2
                AttentionAnomaly(
                    anomalyKey = "late_algorithm:${dayStart}:${usage.packageName}",
                    dayStartMs = dayStart,
                    type = TYPE_LATE_ALGORITHM_USAGE,
                    severity = severity,
                    title = "睡前继续使用 ${usage.appLabel}",
                    detail = "22:30 后使用 ${formatDuration(usage.foregroundMs)}，这个时段更容易把入睡时间往后推。",
                    packageName = usage.packageName,
                    metricValue = usage.foregroundMs,
                    baselineValue = MIN_LATE_ALGORITHM_USAGE_MS,
                    windowStartMs = lateStart,
                    windowEndMs = now,
                    createdAt = now,
                    ruleVersion = RULE_VERSION,
                )
            }
    }

    private suspend fun detectNotificationBursts(dayStart: Long, now: Long): List<AttentionAnomaly> {
        val events = eventDao.getInWindow(EventType.NOTIFICATION_POSTED, dayStart, now)
        if (events.isEmpty()) return emptyList()

        return buildList {
            detectHourlyBurst(dayStart, now, events)?.let(::add)
            detectPackageBurst(dayStart, now, events)?.let(::add)
        }
    }

    private fun detectRapidAppSwitching(dayStart: Long, now: Long): AttentionAnomaly? {
        val events = usageStatsCollector.getForegroundSwitchEvents(dayStart, now)
        if (events.size < MIN_RAPID_APP_SWITCHES) return null

        val windowMs = TimeUnit.MINUTES.toMillis(RAPID_SWITCH_WINDOW_MINUTES)
        val peak = findPeakWindow(events.map { it.timestamp }, windowMs)
        if (peak.count < MIN_RAPID_APP_SWITCHES) return null

        val packagesInWindow = events
            .filter { it.timestamp in peak.startMs..peak.endMs }
            .map { it.appLabel }
            .distinct()
            .take(4)
        val appHint = if (packagesInWindow.isEmpty()) "" else "，涉及 ${packagesInWindow.joinToString("、")}"

        return AttentionAnomaly(
            anomalyKey = "rapid_app_switching:${dayStart}:${peak.startMs}",
            dayStartMs = dayStart,
            type = TYPE_RAPID_APP_SWITCHING,
            severity = if (peak.count >= HIGH_RAPID_APP_SWITCHES) 3 else 2,
            title = "快速切换 App",
            detail = "${RAPID_SWITCH_WINDOW_MINUTES} 分钟内切换 ${peak.count} 次 App$appHint。",
            metricValue = peak.count.toLong(),
            baselineValue = MIN_RAPID_APP_SWITCHES.toLong(),
            windowStartMs = peak.startMs,
            windowEndMs = peak.endMs.coerceAtMost(now),
            createdAt = now,
            ruleVersion = RULE_VERSION,
        )
    }

    private fun detectRepeatedScreenInteractive(dayStart: Long, now: Long): List<AttentionAnomaly> {
        val events = usageStatsCollector.getScreenInteractiveEvents(dayStart, now)
        if (events.isEmpty()) return emptyList()

        return buildList {
            detectRepeatedUnlocks(dayStart, now, events.map { it.timestamp })?.let(::add)
            detectLateRepeatedUnlocks(dayStart, now, events.map { it.timestamp })?.let(::add)
        }
    }

    private fun detectRepeatedUnlocks(
        dayStart: Long,
        now: Long,
        timestamps: List<Long>,
    ): AttentionAnomaly? {
        if (timestamps.size < MIN_REPEATED_UNLOCKS) return null

        val windowMs = TimeUnit.MINUTES.toMillis(REPEATED_UNLOCK_WINDOW_MINUTES)
        val peak = findPeakWindow(timestamps, windowMs)
        if (peak.count < MIN_REPEATED_UNLOCKS) return null

        return AttentionAnomaly(
            anomalyKey = "repeated_unlocks:${dayStart}:${peak.startMs}",
            dayStartMs = dayStart,
            type = TYPE_REPEATED_UNLOCKS,
            severity = if (peak.count >= HIGH_REPEATED_UNLOCKS) 3 else 2,
            title = "短时间反复点亮屏幕",
            detail = "${REPEATED_UNLOCK_WINDOW_MINUTES} 分钟内亮屏 ${peak.count} 次，像是在反复回到手机入口。",
            metricValue = peak.count.toLong(),
            baselineValue = MIN_REPEATED_UNLOCKS.toLong(),
            windowStartMs = peak.startMs,
            windowEndMs = peak.endMs.coerceAtMost(now),
            createdAt = now,
            ruleVersion = RULE_VERSION,
        )
    }

    private fun detectLateRepeatedUnlocks(
        dayStart: Long,
        now: Long,
        timestamps: List<Long>,
    ): AttentionAnomaly? {
        val lateStart = Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (now <= lateStart) return null

        val count = timestamps.count { it >= lateStart }
        if (count < MIN_LATE_REPEATED_UNLOCKS) return null

        return AttentionAnomaly(
            anomalyKey = "late_repeated_unlocks:${dayStart}",
            dayStartMs = dayStart,
            type = TYPE_LATE_REPEATED_UNLOCKS,
            severity = if (count >= HIGH_LATE_REPEATED_UNLOCKS) 3 else 2,
            title = "睡前反复点亮屏幕",
            detail = "22:30 后亮屏 $count 次，这通常不是一次明确使用，而是反复回到手机。",
            metricValue = count.toLong(),
            baselineValue = MIN_LATE_REPEATED_UNLOCKS.toLong(),
            windowStartMs = lateStart,
            windowEndMs = now,
            createdAt = now,
            ruleVersion = RULE_VERSION,
        )
    }

    private fun findPeakWindow(timestamps: List<Long>, windowMs: Long): PeakWindow {
        val sorted = timestamps.sorted()
        var left = 0
        var bestStart = sorted.firstOrNull() ?: 0L
        var bestCount = 0

        sorted.forEachIndexed { right, ts ->
            while (ts - sorted[left] > windowMs) {
                left += 1
            }
            val count = right - left + 1
            if (count > bestCount) {
                bestCount = count
                bestStart = sorted[left]
            }
        }

        return PeakWindow(
            startMs = bestStart,
            endMs = bestStart + windowMs,
            count = bestCount,
        )
    }

    private fun detectHourlyBurst(dayStart: Long, now: Long, events: List<Event>): AttentionAnomaly? {
        val hourMs = TimeUnit.HOURS.toMillis(1)
        val hourlyCounts = events.groupingBy { (it.timestamp / hourMs) * hourMs }.eachCount()
        val peak = hourlyCounts.maxByOrNull { it.value } ?: return null
        val otherCounts = hourlyCounts.filterKeys { it != peak.key }.values
        val baseline = otherCounts.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L
        val isBurst = peak.value >= MIN_HOURLY_NOTIFICATION_BURST &&
            (baseline == 0L || peak.value >= baseline * MIN_NOTIFICATION_BURST_RATIO)

        if (!isBurst) return null

        return AttentionAnomaly(
            anomalyKey = "notification_hour:${dayStart}:${peak.key}",
            dayStartMs = dayStart,
            type = TYPE_NOTIFICATION_BURST,
            severity = if (peak.value >= HIGH_HOURLY_NOTIFICATION_BURST) 3 else 2,
            title = "通知在 ${formatHour(peak.key)} 集中出现",
            detail = "这一小时收到 ${peak.value} 条通知，明显高于今天其他时段。",
            metricValue = peak.value.toLong(),
            baselineValue = baseline,
            windowStartMs = peak.key,
            windowEndMs = (peak.key + hourMs).coerceAtMost(now),
            createdAt = now,
            ruleVersion = RULE_VERSION,
        )
    }

    private fun detectPackageBurst(dayStart: Long, now: Long, events: List<Event>): AttentionAnomaly? {
        val hourMs = TimeUnit.HOURS.toMillis(1)
        val peak = events
            .groupingBy { event -> (event.timestamp / hourMs) * hourMs to event.packageName }
            .eachCount()
            .maxByOrNull { it.value } ?: return null

        if (peak.value < MIN_PACKAGE_NOTIFICATION_BURST) return null

        val packageName = peak.key.second
        val label = usageStatsCollector.resolveAppLabel(packageName)
        return AttentionAnomaly(
            anomalyKey = "notification_package:${dayStart}:${peak.key.first}:$packageName",
            dayStartMs = dayStart,
            type = TYPE_NOTIFICATION_SOURCE_BURST,
            severity = if (peak.value >= HIGH_PACKAGE_NOTIFICATION_BURST) 3 else 2,
            title = "$label 高频打断",
            detail = "${formatHour(peak.key.first)} 这一小时来自 $label 的通知有 ${peak.value} 条。",
            packageName = packageName,
            metricValue = peak.value.toLong(),
            baselineValue = MIN_PACKAGE_NOTIFICATION_BURST.toLong(),
            windowStartMs = peak.key.first,
            windowEndMs = (peak.key.first + hourMs).coerceAtMost(now),
            createdAt = now,
            ruleVersion = RULE_VERSION,
        )
    }

    private fun startOfDayMs(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}小时${minutes}分" else "${minutes}分钟"
    }

    private fun formatHour(timestamp: Long): String =
        "%02d:00".format(
            Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY),
        )

    companion object {
        private const val TAG = "KHUP/AttentionAnomaly"
        private const val RULE_VERSION = "attention-anomaly-v1"
        const val TYPE_APP_USAGE_SPIKE = "app_usage_spike"
        const val TYPE_LATE_ALGORITHM_USAGE = "late_algorithm_usage"
        const val TYPE_NOTIFICATION_BURST = "notification_burst"
        const val TYPE_NOTIFICATION_SOURCE_BURST = "notification_source_burst"
        const val TYPE_RAPID_APP_SWITCHING = "rapid_app_switching"
        const val TYPE_REPEATED_UNLOCKS = "repeated_unlocks"
        const val TYPE_LATE_REPEATED_UNLOCKS = "late_repeated_unlocks"

        private const val APP_USAGE_LIMIT = 20
        private const val BASELINE_DAYS = 7
        private const val MIN_BASELINE_ACTIVE_DAYS = 2
        private const val MIN_SPIKE_RATIO = 2.0
        private const val HIGH_SPIKE_RATIO = 3.0
        private val MIN_SPIKE_USAGE_MS = TimeUnit.MINUTES.toMillis(30)
        private val MIN_SPIKE_DELTA_MS = TimeUnit.MINUTES.toMillis(20)
        private val HIGH_SPIKE_DELTA_MS = TimeUnit.MINUTES.toMillis(60)

        private val MIN_LATE_ALGORITHM_USAGE_MS = TimeUnit.MINUTES.toMillis(5)
        private val HIGH_LATE_ALGORITHM_USAGE_MS = TimeUnit.MINUTES.toMillis(20)

        private const val MIN_HOURLY_NOTIFICATION_BURST = 12
        private const val HIGH_HOURLY_NOTIFICATION_BURST = 25
        private const val MIN_PACKAGE_NOTIFICATION_BURST = 6
        private const val HIGH_PACKAGE_NOTIFICATION_BURST = 12
        private const val MIN_NOTIFICATION_BURST_RATIO = 2

        private const val RAPID_SWITCH_WINDOW_MINUTES = 10L
        private const val MIN_RAPID_APP_SWITCHES = 10
        private const val HIGH_RAPID_APP_SWITCHES = 16

        private const val REPEATED_UNLOCK_WINDOW_MINUTES = 5L
        private const val MIN_REPEATED_UNLOCKS = 3
        private const val HIGH_REPEATED_UNLOCKS = 5
        private const val MIN_LATE_REPEATED_UNLOCKS = 5
        private const val HIGH_LATE_REPEATED_UNLOCKS = 9

        private val algorithmPackages = setOf(
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
        )
    }

    private data class PeakWindow(
        val startMs: Long,
        val endMs: Long,
        val count: Int,
    )
}
