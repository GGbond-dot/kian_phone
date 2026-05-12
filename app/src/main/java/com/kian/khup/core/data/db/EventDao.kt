package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.Event
import kotlinx.coroutines.flow.Flow

data class NotificationPackageTotal(
    val packageName: String,
    val count: Int,
)

@Dao
interface EventDao {

    /** 主键冲突 = 重复事件，直接忽略（DB 层第二道去重）。返回插入的 rowId，-1 表示被忽略。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: Event): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<Event>): List<Long>

    @Query("SELECT * FROM events WHERE eventId = :eventId LIMIT 1")
    suspend fun getById(eventId: String): Event?

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<Event>>

    @Query("""
        SELECT * FROM events
        WHERE type = :type AND timestamp >= :sinceMs
        ORDER BY timestamp DESC
    """)
    fun observeByType(type: EventType, sinceMs: Long): Flow<List<Event>>

    @Query("SELECT COUNT(*) FROM events WHERE timestamp >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    @Query("""
        SELECT e.* FROM events e
        LEFT JOIN derived_results d ON d.eventId = e.eventId
        WHERE d.eventId IS NULL
        ORDER BY e.timestamp DESC
        LIMIT :limit
    """)
    suspend fun getUnclassified(limit: Int): List<Event>

    @Query("SELECT COUNT(*) FROM events WHERE type = :type AND timestamp >= :startMs AND timestamp < :endMs")
    suspend fun countByTypeAndRange(type: String, startMs: Long, endMs: Long): Int

    @Query("DELETE FROM events WHERE timestamp < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM events")
    suspend fun deleteAll(): Int

    @Query("""
        SELECT * FROM events
        WHERE type = :type AND timestamp >= :startMs AND timestamp < :endMs
        ORDER BY timestamp ASC
    """)
    suspend fun getInWindow(type: EventType, startMs: Long, endMs: Long): List<Event>

    @Query("""
        SELECT packageName, COUNT(*) AS count
        FROM events
        WHERE type = :type AND timestamp >= :startMs AND timestamp < :endMs
        GROUP BY packageName
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun loadTopPackagesInWindow(
        type: EventType,
        startMs: Long,
        endMs: Long,
        limit: Int,
    ): List<NotificationPackageTotal>
}
