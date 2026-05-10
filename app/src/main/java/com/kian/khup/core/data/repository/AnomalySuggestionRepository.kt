package com.kian.khup.core.data.repository

import android.util.Log
import com.kian.khup.common.di.ApplicationScope
import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.anomaly.AnomalySuggestionGenerator
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.UserFeedbackDao
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.db.entities.UserFeedback
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

interface AnomalySuggestionRepository {
    fun observeTodayPending(): Flow<AnomalySuggestion?>
    fun observeRecentByStatus(status: String, limit: Int = 100): Flow<List<AnomalySuggestion>>
    suspend fun accept(suggestionId: Long)
    suspend fun postpone(suggestionId: Long)
    suspend fun reject(suggestionId: Long, reason: String? = null)
}

@Singleton
class AnomalySuggestionRepositoryImpl @Inject constructor(
    private val suggestionDao: AnomalySuggestionDao,
    private val feedbackDao: UserFeedbackDao,
    private val suggestionGenerator: AnomalySuggestionGenerator,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : AnomalySuggestionRepository {

    internal var clock: () -> Long = { System.currentTimeMillis() }

    override fun observeTodayPending(): Flow<AnomalySuggestion?> =
        suggestionDao.observeTodayPending(todayStartLocalMs())

    override fun observeRecentByStatus(status: String, limit: Int): Flow<List<AnomalySuggestion>> =
        suggestionDao.observeByStatus(status, limit)

    override suspend fun accept(suggestionId: Long) {
        val now = clock()
        suggestionDao.updateStatus(suggestionId, STATUS_ACCEPTED, now)
        feedbackDao.insert(
            UserFeedback(
                targetType = TARGET_TYPE_SUGGESTION,
                targetId = suggestionId,
                feedbackType = FEEDBACK_ACCEPT,
                createdAt = now,
            )
        )
    }

    override suspend fun postpone(suggestionId: Long) {
        val now = clock()
        val current = suggestionDao.getById(suggestionId) ?: return
        suggestionDao.updateStatus(suggestionId, STATUS_POSTPONED, now)
        feedbackDao.insert(
            UserFeedback(
                targetType = TARGET_TYPE_SUGGESTION,
                targetId = suggestionId,
                feedbackType = FEEDBACK_POSTPONE,
                createdAt = now,
            )
        )

        val patternId = current.patternId
        if (patternId == null) {
            Log.w(TAG, "postpone skipped regeneration: missing patternId for suggestion=$suggestionId")
            return
        }

        applicationScope.launch {
            try {
                suggestionGenerator.generateForPattern(
                    patternId = patternId,
                    regenerationCount = current.regenerationCount + 1,
                    parentSuggestionId = suggestionId,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "regeneration failed: ${t.javaClass.simpleName}")
            }
        }
    }

    override suspend fun reject(suggestionId: Long, reason: String?) {
        val now = clock()
        suggestionDao.updateStatus(suggestionId, STATUS_REJECTED, now)
        feedbackDao.insert(
            UserFeedback(
                targetType = TARGET_TYPE_SUGGESTION,
                targetId = suggestionId,
                feedbackType = FEEDBACK_REJECT,
                reason = reason,
                createdAt = now,
            )
        )
    }

    private companion object {
        const val TAG = "KHUP/SuggestionRepo"
        const val TARGET_TYPE_SUGGESTION = "SUGGESTION"
        const val FEEDBACK_ACCEPT = "ACCEPT"
        const val FEEDBACK_POSTPONE = "POSTPONE"
        const val FEEDBACK_REJECT = "REJECT"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_POSTPONED = "POSTPONED"
        const val STATUS_REJECTED = "REJECTED"
    }
}
