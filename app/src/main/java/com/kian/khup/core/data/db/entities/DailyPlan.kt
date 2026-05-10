package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_plan",
    indices = [
        Index("dayStartMs"),
        Index("isDone"),
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
)
