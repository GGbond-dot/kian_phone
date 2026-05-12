package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.TodayNarration
import kotlinx.coroutines.flow.Flow

@Dao
interface TodayNarrationDao {

    @Query("SELECT * FROM today_narration WHERE dayStartMs = :dayStartMs AND periodDays = :periodDays LIMIT 1")
    fun observeForPeriod(dayStartMs: Long, periodDays: Int): Flow<TodayNarration?>

    fun observeForDay(dayStartMs: Long): Flow<TodayNarration?> = observeForPeriod(dayStartMs, 0)

    @Query("SELECT * FROM today_narration WHERE dayStartMs = :dayStartMs AND periodDays = :periodDays LIMIT 1")
    suspend fun getForPeriod(dayStartMs: Long, periodDays: Int): TodayNarration?

    suspend fun getForDay(dayStartMs: Long): TodayNarration? = getForPeriod(dayStartMs, 0)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TodayNarration)

    @Query("DELETE FROM today_narration WHERE dayStartMs < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long)
}
