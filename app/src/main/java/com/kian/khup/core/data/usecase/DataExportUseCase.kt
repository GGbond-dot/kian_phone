package com.kian.khup.core.data.usecase

import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.ChatMessageDao
import com.kian.khup.core.data.db.ChatSessionDao
import com.kian.khup.core.data.db.DailyPlanDao
import com.kian.khup.core.data.db.UserFeedbackDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class DataExportUseCase @Inject constructor(
    private val attentionAnomalyDao: AttentionAnomalyDao,
    private val anomalySuggestionDao: AnomalySuggestionDao,
    private val userFeedbackDao: UserFeedbackDao,
    private val chatSessionDao: ChatSessionDao,
    private val chatMessageDao: ChatMessageDao,
    private val dailyPlanDao: DailyPlanDao,
    private val appSessionDao: AppSessionDao,
) {
    suspend fun buildExportJson(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30 * 86_400_000L

        val patterns = attentionAnomalyDao.getAll()
        val suggestions = anomalySuggestionDao.getAll()
        val feedbacks = userFeedbackDao.getAll()
        val sessions = chatSessionDao.getAll()
        val messages = chatMessageDao.getAll()
        val plans = dailyPlanDao.getAll()
        val sessions30d = appSessionDao.getSince(thirtyDaysAgo)

        buildString {
            appendLine("{")
            appendLine("  \"exportedAt\": $now,")
            appendLine("  \"appVersion\": \"khup-mvp\",")
            appendLine("  \"patterns\": ${Json.encodeToString(patterns)},")
            appendLine("  \"suggestions\": ${Json.encodeToString(suggestions)},")
            appendLine("  \"feedbacks\": ${Json.encodeToString(feedbacks)},")
            appendLine("  \"chatSessions\": ${Json.encodeToString(sessions)},")
            appendLine("  \"chatMessages\": ${Json.encodeToString(messages)},")
            appendLine("  \"dailyPlans\": ${Json.encodeToString(plans)},")
            appendLine("  \"appSessions30d\": ${Json.encodeToString(sessions30d)}")
            append("}")
        }
    }
}
