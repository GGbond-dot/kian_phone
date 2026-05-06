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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class UsageStatsRepository @Inject constructor(
    private val appSessionDao: AppSessionDao,
    private val usageStatsCollector: UsageStatsCollector,
) {
    fun observeTodayTotal(): Flow<Long> =
        todayMinuteFlow()
            .map { todayStart ->
                val now = System.currentTimeMillis()
                usageStatsCollector.getScreenInteractiveMs(todayStart, now)
                    .coerceAtMost(now - todayStart)
            }
            .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayTopApps(limit: Int = 5): Flow<List<AppUsageSummary>> =
        todayStartFlow().flatMapLatest { sinceMs ->
            appSessionDao.observeTopUsageSince(sinceMs, limit)
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

    fun observeDailyTotals(days: Int = 7): Flow<List<DailyUsageSummary>> =
        todayMinuteFlow()
            .map { todayStart ->
                val firstDayStart = todayStart - TimeUnit.DAYS.toMillis((days - 1).toLong())
                val dayStarts = (0 until days).map { firstDayStart + TimeUnit.DAYS.toMillis(it.toLong()) }
                val now = System.currentTimeMillis()
                dayStarts.map { dayStart ->
                    val dayEnd = (dayStart + TimeUnit.DAYS.toMillis(1)).coerceAtMost(now)
                    val maxForDay = (dayEnd - dayStart).coerceAtLeast(0L)
                    DailyUsageSummary(
                        dayStartMs = dayStart,
                        foregroundMs = usageStatsCollector.getScreenInteractiveMs(dayStart, dayEnd)
                            .coerceAtMost(maxForDay),
                    )
                }
            }
            .flowOn(Dispatchers.IO)

    suspend fun syncToday(): Int = withContext(Dispatchers.IO) {
        val startOfDay = startOfTodayMs()
        val now = System.currentTimeMillis()
        val maxDuration = now - startOfDay
        val sessions = usageStatsCollector.getTodayTopApps(limit = Int.MAX_VALUE)
            .map {
                AppSession(
                    packageName = it.packageName,
                    startTime = startOfDay,
                    endTime = now,
                    durationMs = it.foregroundMs.coerceAtMost(maxDuration),
                )
            }
        appSessionDao.upsertAll(sessions)
        sessions.size
    }

    /** 一次性清掉历史脏数据(durationMs > 24h 的行)。 */
    suspend fun cleanupAnomalousSessions(): Int = withContext(Dispatchers.IO) {
        appSessionDao.deleteSessionsExceedingDuration(TimeUnit.DAYS.toMillis(1))
    }

    private fun startOfTodayMs(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * 每分钟回看一次当天 startOfDay,变了才 emit。
     * 不能用 delay(到午夜):Android 上 kotlinx delay 走 uptimeMillis,Doze 深睡期间停摆,
     * 导致跨夜后 Flow 不重新发射,UI 看起来"要重启 app 才刷新"。
     */
    private fun todayStartFlow(): Flow<Long> = flow {
        var lastEmitted = -1L
        while (true) {
            val start = startOfTodayMs()
            if (start != lastEmitted) {
                emit(start)
                lastEmitted = start
            }
            delay(TimeUnit.MINUTES.toMillis(1))
        }
    }

    private fun todayMinuteFlow(): Flow<Long> = flow {
        while (true) {
            emit(startOfTodayMs())
            delay(TimeUnit.MINUTES.toMillis(1))
        }
    }
}

data class DailyUsageSummary(
    val dayStartMs: Long,
    val foregroundMs: Long,
)
