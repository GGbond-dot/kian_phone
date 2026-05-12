package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.core.ai.LlmEngine
import com.kian.khup.core.ai.PromptRedactor
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.UserFeedbackDao
import com.kian.khup.core.data.db.UserMemoryDao
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.db.entities.UserFeedback
import com.kian.khup.core.data.db.entities.UserMemory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val MEDIUM_TERM_COMPRESSION_PROMPT = """
你是一个数据压缩助手。
将以下用户行为统计数据压缩成一段不超过 80 字的中文摘要。

要求：
- 只保留最有规律的模式，去掉偶发数字
- 保留哪类建议被接受/拒绝的规律
- 保留用户拒绝的常见原因（如果有）
- 不加标题，不加列表符号，只输出一段话
- 绝对不超过 80 字

数据：
{data_json}
"""

@HiltWorker
class WeeklyMemoryJob @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val llm: LlmEngine,
    private val userMemoryDao: UserMemoryDao,
    private val anomalyDao: AttentionAnomalyDao,
    private val suggestionDao: AnomalySuggestionDao,
    private val feedbackDao: UserFeedbackDao,
    @Suppress("UnusedPrivateMember")
    private val redactor: PromptRedactor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - 30 * 24 * 3600 * 1000L

            val patterns = anomalyDao.getActivePatternsSince(thirtyDaysAgo, limit = 10)
            val suggestions = suggestionDao.getRecentWithStatus(thirtyDaysAgo)
            val feedbacks = feedbackDao.recentByTargetType("SUGGESTION", thirtyDaysAgo, 30)

            val dataJson = buildDataJson(patterns, suggestions, feedbacks)
            val prompt = MEDIUM_TERM_COMPRESSION_PROMPT.replace("{data_json}", dataJson)
            val compressed = llm.generate(prompt).getOrThrow().trim().take(160)

            userMemoryDao.upsert(
                UserMemory(
                    type = "MEDIUM_TERM",
                    content = compressed,
                    tokenEstimate = (compressed.length * 1.5).toInt().coerceAtMost(150),
                    generatedAt = now,
                    expiresAt = Long.MAX_VALUE,
                )
            )
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "failed: ${t.javaClass.simpleName}")
            Result.retry()
        }
    }

    private fun buildDataJson(
        patterns: List<AttentionAnomaly>,
        suggestions: List<AnomalySuggestion>,
        feedbacks: List<UserFeedback>,
    ): String {
        val patternSummary = patterns.map { """{"title":"${it.title}","frequency":${it.frequency}}""" }
        val domainStats = suggestions.groupBy { it.suggestionDomain }
            .entries.joinToString(",") { (domain, list) ->
                val accepted = list.count { it.status == "ACCEPTED" }
                val rejected = list.count { it.status == "REJECTED" }
                """"$domain":{"total":${list.size},"accepted":$accepted,"rejected":$rejected}"""
            }
        val rejectReasons = feedbacks
            .filter { it.feedbackType == "REJECT" && !it.reason.isNullOrBlank() }
            .mapNotNull { it.reason }
            .groupBy { it }
            .entries.sortedByDescending { it.value.size }
            .take(3)
            .map { "\"${it.key}\"" }

        return """{"active_patterns":$patternSummary,"suggestion_by_domain":{$domainStats},"common_rejection_reasons":$rejectReasons}"""
    }

    companion object {
        private const val TAG = "KHUP/WeeklyMemoryJob"
        const val UNIQUE_NAME = "khup.weekly_memory"
    }
}
