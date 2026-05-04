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
}
