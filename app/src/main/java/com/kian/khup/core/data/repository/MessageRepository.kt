package com.kian.khup.core.data.repository

import androidx.room.withTransaction
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.ClassifiedEvent
import com.kian.khup.core.data.db.ClassificationFeedbackDao
import com.kian.khup.core.data.db.DerivedResultDao
import com.kian.khup.core.data.db.entities.ClassificationFeedback
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class MessageRepository @Inject constructor(
    private val db: AppDatabase,
    private val derivedResultDao: DerivedResultDao,
    private val classificationFeedbackDao: ClassificationFeedbackDao,
) {
    fun observeMessages(classification: String, limit: Int = 200): Flow<List<ClassifiedEvent>> =
        derivedResultDao.observeClassifiedEvents(classification, limit)

    suspend fun updateClassification(eventId: String, classification: String) {
        db.withTransaction {
            val oldClassification = derivedResultDao.getClassification(eventId) ?: return@withTransaction
            if (oldClassification == classification) return@withTransaction
            val now = System.currentTimeMillis()
            classificationFeedbackDao.insert(
                ClassificationFeedback(
                    eventId = eventId,
                    oldClassification = oldClassification,
                    newClassification = classification,
                    createdAt = now,
                )
            )
            derivedResultDao.updateClassification(
                eventId = eventId,
                classification = classification,
                processedAt = now,
            )
        }
    }
}
