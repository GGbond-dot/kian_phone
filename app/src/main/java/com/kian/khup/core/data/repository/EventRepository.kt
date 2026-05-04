package com.kian.khup.core.data.repository

import com.kian.khup.core.classification.RuleNotificationClassifier
import com.kian.khup.core.data.db.DerivedResultDao
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
    private val derivedResultDao: DerivedResultDao,
    private val classifier: RuleNotificationClassifier,
) {
    suspend fun insert(event: Event): Boolean {
        val inserted = eventDao.insert(event) != -1L
        if (inserted) derivedResultDao.upsert(classifier.classify(event))
        return inserted
    }

    suspend fun insertAll(events: List<Event>): Int {
        val rowIds = eventDao.insertAll(events)
        val insertedEvents = events.zip(rowIds)
            .filter { (_, rowId) -> rowId != -1L }
            .map { (event, _) -> event }
        derivedResultDao.upsertAll(insertedEvents.map { classifier.classify(it) })
        return insertedEvents.size
    }

    fun observeRecent(limit: Int = 200): Flow<List<Event>> = eventDao.observeRecent(limit)

    fun observeByType(type: EventType, sinceMs: Long): Flow<List<Event>> =
        eventDao.observeByType(type, sinceMs)

    suspend fun countSince(sinceMs: Long): Int = eventDao.countSince(sinceMs)

    suspend fun classifyUnprocessed(limit: Int = 300): Int {
        val events = eventDao.getUnclassified(limit)
        derivedResultDao.upsertAll(events.map { classifier.classify(it) })
        return events.size
    }

    /** 数据保留：删除 [beforeMs] 之前的事件。返回删除条数。 */
    suspend fun pruneOlderThan(beforeMs: Long): Int = eventDao.deleteOlderThan(beforeMs)
}
