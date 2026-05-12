package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.TodayNarration
import kotlinx.coroutines.flow.Flow

@Dao
interface TodayNarrationDao {

    @Query("SELECT * FROM today_narration WHERE dayStartMs = :dayStartMs LIMIT 1")
    fun observeForDay(dayStartMs: Long): Flow<TodayNarration?>

    @Query("SELECT * FROM today_narration WHERE dayStartMs = :dayStartMs LIMIT 1")
    suspend fun getForDay(dayStartMs: Long): TodayNarration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TodayNarration)

    @Query("DELETE FROM today_narration WHERE dayStartMs < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long)
}
