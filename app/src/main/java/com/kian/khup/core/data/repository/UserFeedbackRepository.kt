package com.kian.khup.core.data.repository

import com.kian.khup.core.data.db.UserFeedbackDao
import com.kian.khup.core.data.db.entities.UserFeedback
import javax.inject.Inject
import javax.inject.Singleton

interface UserFeedbackRepository {
    suspend fun record(
        targetType: String,
        targetId: Long,
        feedbackType: String,
        rating: Int? = null,
        reason: String? = null,
    )

    suspend fun recent(targetType: String, sinceMs: Long, limit: Int = 50): List<UserFeedback>
}

@Singleton
class UserFeedbackRepositoryImpl @Inject constructor(
    private val dao: UserFeedbackDao,
) : UserFeedbackRepository {

    internal var clock: () -> Long = { System.currentTimeMillis() }

    override suspend fun record(
        targetType: String,
        targetId: Long,
        feedbackType: String,
        rating: Int?,
        reason: String?,
    ) {
        dao.insert(
            UserFeedback(
                targetType = targetType,
                targetId = targetId,
                feedbackType = feedbackType,
                rating = rating,
                reason = reason,
                createdAt = clock(),
            )
        )
    }

    override suspend fun recent(targetType: String, sinceMs: Long, limit: Int): List<UserFeedback> =
        dao.recentByTargetType(targetType, sinceMs, limit)
}
