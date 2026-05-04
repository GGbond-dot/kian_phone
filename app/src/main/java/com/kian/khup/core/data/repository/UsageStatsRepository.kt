package com.kian.khup.core.data.repository

import com.kian.khup.collection.usage.AppUsageSummary
import com.kian.khup.collection.usage.UsageStatsCollector
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.entities.AppSession
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class UsageStatsRepository @Inject constructor(
    private val appSessionDao: AppSessionDao,
    private val usageStatsCollector: UsageStatsCollector,
) {
    fun observeTodayTotal(): Flow<Long> =
        appSessionDao.observeTotalUsageSince(startOfTodayMs())

    fun observeTodayTopApps(limit: Int = 5): Flow<List<AppUsageSummary>> {
        return appSessionDao.observeTopUsageSince(startOfTodayMs(), limit)
            .map { rows ->
                rows.map {
                    AppUsageSummary(
                        packageName = it.packageName,
                        appLabel = usageStatsCollector.resolveAppLabel(it.packageName),
                        foregroundMs = it.foregroundMs,
                    )
                }
            }
    }

    fun observeDailyTotals(days: Int = 7): Flow<List<DailyUsageSummary>> {
        val todayStart = startOfTodayMs()
        val firstDayStart = todayStart - TimeUnit.DAYS.toMillis((days - 1).toLong())
        val dayStarts = (0 until days).map { firstDayStart + TimeUnit.DAYS.toMillis(it.toLong()) }

        return appSessionDao.observeDailyUsageSince(firstDayStart)
            .map { rows ->
                val byDay = rows.associateBy { it.dayStartMs }
                dayStarts.map { dayStart ->
                    DailyUsageSummary(
                        dayStartMs = dayStart,
                        foregroundMs = byDay[dayStart]?.foregroundMs ?: 0L,
                    )
                }
            }
    }

    suspend fun syncToday(): Int = withContext(Dispatchers.IO) {
        val startOfDay = startOfTodayMs()
        val now = System.currentTimeMillis()
        val sessions = usageStatsCollector.getTodayTopApps(limit = Int.MAX_VALUE)
            .map {
                AppSession(
                    packageName = it.packageName,
                    startTime = startOfDay,
                    endTime = now,
                    durationMs = it.foregroundMs,
                )
            }
        appSessionDao.upsertAll(sessions)
        sessions.size
    }

    private fun startOfTodayMs(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}

data class DailyUsageSummary(
    val dayStartMs: Long,
    val foregroundMs: Long,
)
