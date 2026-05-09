package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attention_anomaly",
    indices = [
        Index("dayStartMs"),
        Index("type"),
        Index("severity"),
        Index("anomalyKey", unique = true),
    ],
)
data class AttentionAnomaly(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val anomalyKey: String,
    val dayStartMs: Long,
    val type: String,
    val severity: Int,
    val title: String,
    val detail: String,
    val packageName: String? = null,
    val metricValue: Long,
    val baselineValue: Long? = null,
    val windowStartMs: Long? = null,
    val windowEndMs: Long? = null,
    val createdAt: Long,
    val ruleVersion: String,
)
