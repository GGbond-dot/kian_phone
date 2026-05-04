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

@Dao
interface DerivedResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: DerivedResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(results: List<DerivedResult>)

    @Query("""
        SELECT e.*, d.classification, d.priority, d.summary, d.processedAt, d.modelVersion
        FROM events e
        INNER JOIN derived_results d ON d.eventId = e.eventId
        WHERE (:classification = '全部' OR d.classification = :classification)
        ORDER BY e.timestamp DESC
        LIMIT :limit
    """)
    fun observeClassifiedEvents(classification: String, limit: Int): Flow<List<ClassifiedEvent>>
}
