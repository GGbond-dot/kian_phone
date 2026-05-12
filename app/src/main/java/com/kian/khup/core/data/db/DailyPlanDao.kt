package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kian.khup.core.data.db.entities.DailyPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPlanDao {

    @Insert
    suspend fun insert(item: DailyPlan): Long

    @Update
    suspend fun update(item: DailyPlan)

    @Delete
    suspend fun delete(item: DailyPlan)

    @Query("SELECT * FROM daily_plan WHERE id = :id")
    suspend fun getById(id: Long): DailyPlan?

    @Query("""
        SELECT * FROM daily_plan
        WHERE dayStartMs = :dayStartMs
        ORDER BY sortOrder ASC, createdAt ASC
    """)
    fun observeByDay(dayStartMs: Long): Flow<List<DailyPlan>>

    @Query("UPDATE daily_plan SET isDone = :isDone, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: Long, isDone: Boolean, completedAt: Long?)

    @Query("UPDATE daily_plan SET title = :title, note = :note WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, note: String?)

    @Query("SELECT COUNT(*) FROM daily_plan WHERE dayStartMs = :dayStartMs")
    fun observeCountByDay(dayStartMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM daily_plan WHERE dayStartMs = :dayStartMs AND isDone = 1")
    fun observeDoneCountByDay(dayStartMs: Long): Flow<Int>

    @Query("DELETE FROM daily_plan WHERE dayStartMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM daily_plan")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM daily_plan ORDER BY dayStartMs DESC")
    suspend fun getAll(): List<DailyPlan>
}
