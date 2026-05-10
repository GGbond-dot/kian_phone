package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.AppSession
import kotlinx.coroutines.flow.Flow

data class AppUsageTotal(
    val packageName: String,
    val foregroundMs: Long,
)

data class DailyUsageTotal(
    val dayStartMs: Long,
    val foregroundMs: Long,
)

data class AppUsageBaseline(
    val packageName: String,
    val averageForegroundMs: Double,
    val activeDays: Int,
)

@Dao
interface AppSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<AppSession>)

    @Query("""
        SELECT packageName, SUM(COALESCE(durationMs, 0)) AS foregroundMs
        FROM app_sessions
        WHERE startTime >= :sinceMs
        GROUP BY packageName
        HAVING foregroundMs > 0
        ORDER BY foregroundMs DESC
        LIMIT :limit
    """)
    fun observeTopUsageSince(sinceMs: Long, limit: Int): Flow<List<AppUsageTotal>>

    @Query("""
        SELECT packageName, SUM(COALESCE(durationMs, 0)) AS foregroundMs
        FROM app_sessions
        WHERE startTime >= :sinceMs
        GROUP BY packageName
        HAVING foregroundMs > 0
        ORDER BY foregroundMs DESC
        LIMIT :limit
    """)
    suspend fun loadTopUsageSince(sinceMs: Long, limit: Int): List<AppUsageTotal>

    @Query("""
        SELECT packageName, AVG(foregroundMs) AS averageForegroundMs, COUNT(*) AS activeDays
        FROM (
            SELECT packageName, startTime AS dayStartMs, SUM(COALESCE(durationMs, 0)) AS foregroundMs
            FROM app_sessions
            WHERE startTime >= :startMs AND startTime < :endMs
            GROUP BY packageName, startTime
            HAVING foregroundMs > 0
        )
        GROUP BY packageName
        HAVING activeDays >= :minActiveDays
    """)
    suspend fun loadUsageBaselines(
        startMs: Long,
        endMs: Long,
        minActiveDays: Int,
    ): List<AppUsageBaseline>

    @Query("""
        SELECT COALESCE(SUM(COALESCE(durationMs, 0)), 0)
        FROM app_sessions
        WHERE startTime >= :sinceMs
    """)
    fun observeTotalUsageSince(sinceMs: Long): Flow<Long>

    @Query("""
        SELECT startTime AS dayStartMs, SUM(COALESCE(durationMs, 0)) AS foregroundMs
        FROM app_sessions
        WHERE startTime >= :sinceMs
        GROUP BY startTime
        HAVING foregroundMs > 0
        ORDER BY dayStartMs ASC
    """)
    fun observeDailyUsageSince(sinceMs: Long): Flow<List<DailyUsageTotal>>

    @Query("""
        SELECT startTime AS dayStartMs, SUM(COALESCE(durationMs, 0)) AS foregroundMs
        FROM app_sessions
        WHERE startTime >= :sinceMs
        GROUP BY startTime
        HAVING foregroundMs > 0
        ORDER BY dayStartMs ASC
    """)
    suspend fun loadDailyUsageSince(sinceMs: Long): List<DailyUsageTotal>

    @Query("""
        SELECT COALESCE(SUM(COALESCE(durationMs, 0)), 0)
        FROM app_sessions
        WHERE startTime >= :sinceMs
          AND packageName IN (:packageNames)
    """)
    suspend fun getUsageForPackagesSince(packageNames: List<String>, sinceMs: Long): Long

    @Query("DELETE FROM app_sessions WHERE COALESCE(durationMs, 0) > :maxDurationMs")
    suspend fun deleteSessionsExceedingDuration(maxDurationMs: Long): Int
}
