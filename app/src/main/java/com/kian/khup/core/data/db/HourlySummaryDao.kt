package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.HourlySummary
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlySummaryDao {

    @Query("SELECT * FROM hourly_summary ORDER BY windowStartMs DESC LIMIT 1")
    fun observeLatest(): Flow<HourlySummary?>

    @Query("SELECT * FROM hourly_summary WHERE windowStartMs = :windowStartMs LIMIT 1")
    suspend fun findByWindow(windowStartMs: Long): HourlySummary?

    @Query("""
        SELECT * FROM hourly_summary
        WHERE windowStartMs >= :startMs AND windowStartMs < :endMs
        ORDER BY windowStartMs ASC
    """)
    suspend fun loadInWindow(startMs: Long, endMs: Long): List<HourlySummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: HourlySummary): Long
}
