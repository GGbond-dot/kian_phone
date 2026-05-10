package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通用用户反馈表。承载行为线 / 信息线 / 复盘 / 分类 / AI 消息等多种反馈，
 * 由 [targetType] + [targetId] 寻址；不设外键，避免跨表删除联动。
 */
@Entity(
    tableName = "user_feedback",
    indices = [
        Index("targetType", "targetId"),
        Index("createdAt"),
    ],
)
data class UserFeedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: String,        // SUGGESTION / PATTERN / DAILY_REVIEW / CLASSIFICATION / AI_MESSAGE
    val targetId: Long,            // 对应表的 PK，无 FK 约束
    val feedbackType: String,      // ACCEPT / REJECT / POSTPONE / TOO_SHARP / TOO_SOFT / INACCURATE
    val rating: Int? = null,
    val reason: String? = null,
    val createdAt: Long,
)
