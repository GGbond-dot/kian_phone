package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hourly_summary",
    indices = [Index(value = ["windowStartMs"], unique = true)],
)
data class HourlySummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val summary: String,
    val eventCount: Int,
    val topPackages: String,
    val importance: Int,
    val createdAt: Long,
    val modelVersion: String,
)
