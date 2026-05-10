package com.kian.khup.core.data.repository

import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 旧 DailyTask 仓库的过渡封装。
 *
 * 行为线 MVP 把 daily_tasks 表迁移成了 anomaly_suggestion，但旧 DashboardScreen
 * 仍需要"今日待办"列表能编过。等 Module 4 的 TodayScreen / HistoryScreen 上线
 * 后，这个类和它的调用方一起删掉。
 */
@Suppress("DEPRECATION")
@Singleton
class DailyTaskRepository @Inject constructor(
    private val anomalySuggestionDao: AnomalySuggestionDao,
) {
    fun observeTodayTasks(): Flow<List<AnomalySuggestion>> =
        anomalySuggestionDao.observeForDayLegacy(startOfTodayMs())

    fun observeOverdueUnfinishedTasks(): Flow<List<AnomalySuggestion>> =
        anomalySuggestionDao.observeOverdueUnfinishedLegacy(startOfTodayMs())

    suspend fun addTodayTask(title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return
        val now = System.currentTimeMillis()
        anomalySuggestionDao.insert(
            AnomalySuggestion(
                title = normalizedTitle,
                dayStartMs = startOfTodayMs(),
                createdAt = now,
                suggestionDomain = "BEHAVIOR",
                actionText = "",
                whyText = "",
                expectedUpside = "",
                modelVersion = "legacy-task",
                updatedAt = now,
            )
        )
    }

    suspend fun setDone(taskId: Long, isDone: Boolean) {
        val now = System.currentTimeMillis()
        anomalySuggestionDao.setDoneLegacy(
            id = taskId,
            isDone = isDone,
            completedAt = if (isDone) now else null,
            updatedAt = now,
        )
    }

    suspend fun delete(taskId: Long) {
        anomalySuggestionDao.deleteLegacy(taskId)
    }

    private fun startOfTodayMs(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
