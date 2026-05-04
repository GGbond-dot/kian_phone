package com.kian.khup.core.data.repository

import com.kian.khup.core.data.db.ClassifiedEvent
import com.kian.khup.core.data.db.DerivedResultDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class MessageRepository @Inject constructor(
    private val derivedResultDao: DerivedResultDao,
) {
    fun observeMessages(classification: String, limit: Int = 200): Flow<List<ClassifiedEvent>> =
        derivedResultDao.observeClassifiedEvents(classification, limit)
}
