package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kian.khup.core.data.db.entities.DailyTask
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTaskDao {

    @Query("""
        SELECT * FROM daily_tasks
        WHERE dayStartMs = :dayStartMs
        ORDER BY isDone ASC, createdAt ASC
    """)
    fun observeForDay(dayStartMs: Long): Flow<List<DailyTask>>

    @Query("""
        SELECT * FROM daily_tasks
        WHERE dayStartMs < :todayStartMs AND isDone = 0
        ORDER BY dayStartMs DESC, createdAt ASC
    """)
    fun observeOverdueUnfinished(todayStartMs: Long): Flow<List<DailyTask>>

    @Insert
    suspend fun insert(task: DailyTask)

    @Query("""
        UPDATE daily_tasks
        SET isDone = :isDone, completedAt = :completedAt
        WHERE id = :id
    """)
    suspend fun setDone(id: Long, isDone: Boolean, completedAt: Long?)

    @Query("DELETE FROM daily_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}
