package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.DailyReview
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReviewDao {

    @Query("SELECT * FROM daily_review WHERE dayStartMs = :dayStartMs LIMIT 1")
    fun observeForDay(dayStartMs: Long): Flow<DailyReview?>

    @Query("SELECT * FROM daily_review WHERE dayStartMs >= :sinceDayStartMs ORDER BY dayStartMs DESC")
    suspend fun loadSince(sinceDayStartMs: Long): List<DailyReview>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: DailyReview): Long
}
