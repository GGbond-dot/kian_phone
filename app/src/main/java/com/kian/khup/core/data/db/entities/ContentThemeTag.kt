package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "content_theme_tags",
    indices = [
        Index("dayStartMs"),
        Index("theme"),
        Index("sourceType", "sourceId"),
        Index(value = ["dayStartMs", "sourceType", "sourceId", "theme"], unique = true),
    ],
)
data class ContentThemeTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    val sourceType: String,
    val sourceId: String,
    val packageName: String?,
    val theme: String,
    val confidence: Int,
    val evidence: String,
    val createdAt: Long,
    val ruleVersion: String,
)
