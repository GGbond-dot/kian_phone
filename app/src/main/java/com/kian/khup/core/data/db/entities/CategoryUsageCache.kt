package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "category_usage_cache",
    indices = [
        Index("dayStartMs"),
        Index("category"),
        Index(value = ["dayStartMs", "category"], unique = true),
    ],
)
data class CategoryUsageCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    val category: String,
    val foregroundMs: Long,
    val computedAt: Long,
    val ruleVersion: String,
)
