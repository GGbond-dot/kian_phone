package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_review",
    indices = [Index(value = ["dayStartMs"], unique = true)],
)
data class DailyReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    val summary: String,
    val highlights: String,
    val createdAt: Long,
    val modelVersion: String,
)
