package com.kian.khup.core.ai.memory

import com.kian.khup.common.util.todayStartLocalMs
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.entities.UserMemory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortTermMemoryBuilder @Inject constructor(
    private val anomalyDao: AttentionAnomalyDao,
    private val suggestionDao: AnomalySuggestionDao,
    private val eventDao: EventDao,
) {
    suspend fun build(): UserMemory {
        val now = System.currentTimeMillis()
        val todayStart = todayStartLocalMs()
        val weekStart = now - 7 * 24 * 3600 * 1000L

        val checkInCount = eventDao.countByTypeAndRange(
            EventType.USER_REPORT.name, todayStart, now
        )
        val patterns = anomalyDao.getActivePatternsSince(weekStart, limit = 3)
        val domainCounts = suggestionDao.getDomainCountsSince(weekStart)
            .sortedByDescending { it.count }
            .take(4)
        val recentSuggestions = suggestionDao.getRecentWithStatus(weekStart)
        val accepted = recentSuggestions.count { it.status == "ACCEPTED" }
        val rejected = recentSuggestions.count { it.status == "REJECTED" }

        val content = buildString {
            if (checkInCount > 0) append("今日检入${checkInCount}次。")
            if (patterns.isNotEmpty()) {
                append("活跃模式：${patterns.joinToString("、") { it.title }}。")
            }
            if (domainCounts.isNotEmpty()) {
                val dist = domainCounts.joinToString(" ") { "${it.suggestionDomain}×${it.count}" }
                append("本周建议领域：$dist。")
            }
            if (recentSuggestions.isNotEmpty()) {
                append("接受${accepted}拒绝${rejected}条。")
            }
        }.trim()

        val tokenEst = (content.length * 1.5).toInt()

        return UserMemory(
            type = "SHORT_TERM",
            content = content.take(200),
            tokenEstimate = minOf(tokenEst, 120),
            generatedAt = now,
            expiresAt = now + 3_600_000L,
        )
    }
}
