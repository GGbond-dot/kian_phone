package com.kian.khup.core.data.repository

import com.kian.khup.core.data.db.DailyTaskDao
import com.kian.khup.core.data.db.entities.DailyTask
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DailyTaskRepository @Inject constructor(
    private val dailyTaskDao: DailyTaskDao,
) {
    fun observeTodayTasks(): Flow<List<DailyTask>> =
        dailyTaskDao.observeForDay(startOfTodayMs())

    fun observeOverdueUnfinishedTasks(): Flow<List<DailyTask>> =
        dailyTaskDao.observeOverdueUnfinished(startOfTodayMs())

    suspend fun addTodayTask(title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return
        dailyTaskDao.insert(
            DailyTask(
                title = normalizedTitle,
                dayStartMs = startOfTodayMs(),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setDone(taskId: Long, isDone: Boolean) {
        dailyTaskDao.setDone(
            id = taskId,
            isDone = isDone,
            completedAt = if (isDone) System.currentTimeMillis() else null,
        )
    }

    suspend fun delete(taskId: Long) {
        dailyTaskDao.delete(taskId)
    }

    private fun startOfTodayMs(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
