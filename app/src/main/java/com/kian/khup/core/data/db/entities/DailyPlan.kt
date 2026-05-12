package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "daily_plan",
    indices = [
        Index("dayStartMs"),
        Index("isDone"),
        Index("sourceSuggestionId"),
    ],
)
data class DailyPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val dayStartMs: Long,
    val isDone: Boolean = false,
    val createdAt: Long,
    val completedAt: Long? = null,
    val sortOrder: Int = 0,
    /** 来自哪条 AnomalySuggestion；null = 用户手动添加。见 ui-redesign §4.5。 */
    val sourceSuggestionId: Long? = null,
)
