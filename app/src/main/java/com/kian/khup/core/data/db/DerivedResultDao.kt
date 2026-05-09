package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.DerivedResult
import com.kian.khup.core.data.db.entities.Event
import kotlinx.coroutines.flow.Flow

data class ClassifiedEvent(
    @Embedded val event: Event,
    val classification: String,
    val priority: Int,
    val summary: String?,
    val processedAt: Long,
    val modelVersion: String,
)

data class ClassificationTotal(
    val classification: String,
    val count: Int,
)

@Dao
interface DerivedResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: DerivedResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(results: List<DerivedResult>)

    @Query("SELECT classification FROM derived_results WHERE eventId = :eventId")
    suspend fun getClassification(eventId: String): String?

    @Query("""
        UPDATE derived_results
        SET classification = :classification,
            processedAt = :processedAt,
            modelVersion = 'manual-v1'
        WHERE eventId = :eventId
    """)
    suspend fun updateClassification(
        eventId: String,
        classification: String,
        processedAt: Long,
    )

    @Query("""
        SELECT e.*, d.classification, d.priority, d.summary, d.processedAt, d.modelVersion
        FROM events e
        INNER JOIN derived_results d ON d.eventId = e.eventId
        WHERE (:classification = '全部' OR d.classification = :classification)
        ORDER BY e.timestamp DESC
        LIMIT :limit
    """)
    fun observeClassifiedEvents(classification: String, limit: Int): Flow<List<ClassifiedEvent>>

    @Query("""
        SELECT d.classification, COUNT(*) AS count
        FROM derived_results d
        INNER JOIN events e ON e.eventId = d.eventId
        WHERE e.timestamp >= :startMs AND e.timestamp < :endMs
        GROUP BY d.classification
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun loadClassificationTotalsInWindow(
        startMs: Long,
        endMs: Long,
        limit: Int,
    ): List<ClassificationTotal>
}
