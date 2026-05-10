package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.TriggerTag
import kotlinx.coroutines.flow.Flow

data class TriggerTagTotal(
    val tag: String,
    val count: Int,
    val averageConfidence: Double,
)

data class DailyTriggerTagTotal(
    val dayStartMs: Long,
    val tag: String,
    val count: Int,
    val averageConfidence: Double,
)

@Dao
interface TriggerTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<TriggerTag>)

    @Query("DELETE FROM trigger_tags WHERE dayStartMs = :dayStartMs")
    suspend fun deleteForDay(dayStartMs: Long)

    @Query("""
        SELECT *
        FROM trigger_tags
        WHERE dayStartMs = :dayStartMs
        ORDER BY confidence DESC, createdAt DESC
    """)
    suspend fun loadForDay(dayStartMs: Long): List<TriggerTag>

    @Query("""
        SELECT tag, COUNT(*) AS count, AVG(confidence) AS averageConfidence
        FROM trigger_tags
        WHERE dayStartMs = :dayStartMs
        GROUP BY tag
        ORDER BY count DESC, averageConfidence DESC
    """)
    suspend fun loadTagTotalsForDay(dayStartMs: Long): List<TriggerTagTotal>

    @Query("""
        SELECT tag, COUNT(*) AS count, AVG(confidence) AS averageConfidence
        FROM trigger_tags
        WHERE dayStartMs >= :sinceMs
        GROUP BY tag
        ORDER BY count DESC, averageConfidence DESC
        LIMIT :limit
    """)
    suspend fun loadTagTotalsSince(sinceMs: Long, limit: Int): List<TriggerTagTotal>

    @Query("""
        SELECT dayStartMs, tag, COUNT(*) AS count, AVG(confidence) AS averageConfidence
        FROM trigger_tags
        WHERE dayStartMs >= :sinceMs
        GROUP BY dayStartMs, tag
        ORDER BY dayStartMs ASC, count DESC, averageConfidence DESC
    """)
    suspend fun loadDailyTagTotalsSince(sinceMs: Long): List<DailyTriggerTagTotal>

    @Query("""
        SELECT tag, COUNT(*) AS count, AVG(confidence) AS averageConfidence
        FROM trigger_tags
        WHERE dayStartMs = :dayStartMs
        GROUP BY tag
        ORDER BY count DESC, averageConfidence DESC
    """)
    fun observeTagTotalsForDay(dayStartMs: Long): Flow<List<TriggerTagTotal>>
}
