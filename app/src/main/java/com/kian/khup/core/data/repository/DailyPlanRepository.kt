package com.kian.khup.core.data.repository

import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.data.db.DailyPlanDao
import com.kian.khup.core.data.db.entities.DailyPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface DailyPlanRepository {
    fun observeToday(): Flow<List<DailyPlan>>
    fun observeTodayProgress(): Flow<Pair<Int, Int>>

    suspend fun add(title: String, note: String? = null)
    suspend fun toggleDone(id: Long)
    suspend fun updateContent(id: Long, title: String, note: String?)
    suspend fun delete(id: Long)
    suspend fun reorder(ids: List<Long>)
}

@Singleton
class DailyPlanRepositoryImpl @Inject constructor(
    private val dao: DailyPlanDao,
) : DailyPlanRepository {

    override fun observeToday(): Flow<List<DailyPlan>> =
        dao.observeByDay(todayStartLocalMs())

    override fun observeTodayProgress(): Flow<Pair<Int, Int>> {
        val day = todayStartLocalMs()
        return combine(dao.observeDoneCountByDay(day), dao.observeCountByDay(day)) { done, total ->
            done to total
        }
    }

    override suspend fun add(title: String, note: String?) {
        require(title.isNotBlank()) { "title must not be blank" }
        val now = System.currentTimeMillis()
        dao.insert(
            DailyPlan(
                title = title.trim().take(100),
                note = note?.trim()?.take(500)?.takeIf { it.isNotBlank() },
                dayStartMs = todayStartLocalMs(),
                createdAt = now,
                sortOrder = (now / 1000).toInt(),
            )
        )
    }

    override suspend fun toggleDone(id: Long) {
        val plan = dao.getById(id) ?: return
        val now = System.currentTimeMillis()
        dao.setDone(
            id = id,
            isDone = !plan.isDone,
            completedAt = if (!plan.isDone) now else null,
        )
    }

    override suspend fun updateContent(id: Long, title: String, note: String?) {
        require(title.isNotBlank()) { "title must not be blank" }
        dao.updateContent(id, title.trim().take(100), note?.trim()?.take(500)?.takeIf { it.isNotBlank() })
    }

    override suspend fun delete(id: Long) {
        val plan = dao.getById(id) ?: return
        dao.delete(plan)
    }

    override suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            val plan = dao.getById(id) ?: return@forEachIndexed
            dao.update(plan.copy(sortOrder = index))
        }
    }
}
