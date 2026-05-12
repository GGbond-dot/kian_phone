package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kian.khup.core.data.db.entities.ActionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionLogDao {

    @Insert
    suspend fun insert(actionLog: ActionLog): Long

    @Query("""
        SELECT EXISTS(
            SELECT 1
            FROM actions_log
            WHERE ruleId = :ruleId
              AND actionType = :actionType
              AND triggeredAt >= :sinceMs
        )
    """)
    suspend fun hasActionSince(ruleId: String, actionType: String, sinceMs: Long): Boolean

    @Query("""
        SELECT *
        FROM actions_log
        ORDER BY triggeredAt DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int): Flow<List<ActionLog>>

    @Query("""
        SELECT *
        FROM actions_log
        WHERE triggeredAt >= :sinceMs
        ORDER BY triggeredAt DESC
        LIMIT :limit
    """)
    fun observeSince(sinceMs: Long, limit: Int): Flow<List<ActionLog>>

    @Query("""
        SELECT *
        FROM actions_log
        WHERE triggeredAt >= :sinceMs
        ORDER BY triggeredAt DESC
        LIMIT :limit
    """)
    suspend fun loadSince(sinceMs: Long, limit: Int): List<ActionLog>

    @Query("DELETE FROM actions_log WHERE triggeredAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM actions_log")
    suspend fun deleteAll(): Int
}
