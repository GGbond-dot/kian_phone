package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 异常值建议（行为线 MVP）。从原 DailyTask 表迁移而来。
 *
 * 由 [AnomalySuggestionGenerator] 基于 [AttentionAnomaly] 生成；
 * 用户反馈通过 [UserFeedback] 记录，更新 [status]。
 */
@Entity(
    tableName = "anomaly_suggestion",
    indices = [
        Index("dayStartMs"),
        Index("status"),
        Index("patternId"),
        Index("patternKey", "createdAt"),    // 24h 冷却查询
    ],
)
data class AnomalySuggestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // 来自原 DailyTask 的字段
    val title: String,
    val dayStartMs: Long,
    val createdAt: Long,
    val completedAt: Long? = null,           // 兼容字段，新代码不再写入

    // 行为线 MVP 新增字段
    val patternId: Long? = null,
    val patternKey: String? = null,          // 冗余 attention_anomaly.anomalyKey，便于 24h 冷却查询
    val suggestionDomain: String,            // BEHAVIOR / INFORMATION / SOCIAL / SPACE / BODY / CREATION
    val actionText: String,
    val whyText: String,
    val costLevel: String = "LOW",           // 强制 LOW
    val expectedUpside: String,
    val status: String = "PENDING",          // PENDING / ACCEPTED / POSTPONED / REJECTED
    val scheduledAt: Long? = null,
    val expiresAt: Long? = null,
    val modelVersion: String,
    val regenerationCount: Int = 0,          // "换一个"递增
    val parentSuggestionId: Long? = null,    // POSTPONE 链路指向上一条
    val updatedAt: Long,

    // 兼容字段（migration 自旧 daily_tasks 时填充；新代码以 status 为准）
    val isDone: Boolean = false,
)
