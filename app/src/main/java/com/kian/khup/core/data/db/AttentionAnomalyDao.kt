package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import kotlinx.coroutines.flow.Flow

@Dao
interface AttentionAnomalyDao {

    @Query("""
        SELECT *
        FROM attention_anomaly
        WHERE dayStartMs = :dayStartMs
        ORDER BY severity DESC, createdAt DESC
    """)
    fun observeForDay(dayStartMs: Long): Flow<List<AttentionAnomaly>>

    @Query("""
        SELECT *
        FROM attention_anomaly
        WHERE dayStartMs = :dayStartMs
        ORDER BY severity DESC, createdAt DESC
    """)
    suspend fun loadForDay(dayStartMs: Long): List<AttentionAnomaly>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(anomalies: List<AttentionAnomaly>)

    @Query("DELETE FROM attention_anomaly WHERE dayStartMs = :dayStartMs")
    suspend fun deleteForDay(dayStartMs: Long): Int
}
