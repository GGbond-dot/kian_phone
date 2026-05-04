package com.kian.khup.core.data.repository

import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.entities.Event
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI / 上层模块只通过 Repository 访问数据库。DAO 不直接暴露给 ViewModel。
 * Phase 2+ 会再扩 DerivedResultRepository 等。
 */
@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
) {
    suspend fun insert(event: Event): Boolean = eventDao.insert(event) != -1L

    suspend fun insertAll(events: List<Event>): Int =
        eventDao.insertAll(events).count { it != -1L }

    fun observeRecent(limit: Int = 200): Flow<List<Event>> = eventDao.observeRecent(limit)

    fun observeByType(type: EventType, sinceMs: Long): Flow<List<Event>> =
        eventDao.observeByType(type, sinceMs)

    suspend fun countSince(sinceMs: Long): Int = eventDao.countSince(sinceMs)

    /** 数据保留：删除 [beforeMs] 之前的事件。返回删除条数。 */
    suspend fun pruneOlderThan(beforeMs: Long): Int = eventDao.deleteOlderThan(beforeMs)
}
