package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.CategoryUsageCache

@Dao
interface CategoryUsageCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CategoryUsageCache>)

    @Query("""
        SELECT *
        FROM category_usage_cache
        WHERE dayStartMs >= :startMs AND dayStartMs < :endMs
        ORDER BY dayStartMs ASC, foregroundMs DESC
    """)
    suspend fun loadInWindow(startMs: Long, endMs: Long): List<CategoryUsageCache>

    @Query("DELETE FROM category_usage_cache WHERE dayStartMs = :dayStartMs")
    suspend fun deleteForDay(dayStartMs: Long)

    @Query("DELETE FROM category_usage_cache WHERE computedAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM category_usage_cache")
    suspend fun deleteAll(): Int
}
