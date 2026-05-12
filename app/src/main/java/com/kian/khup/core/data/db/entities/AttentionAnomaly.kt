package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "attention_anomaly",
    indices = [
        Index("dayStartMs"),
        Index("type"),
        Index("severity"),
        Index("anomalyKey", unique = true),
        Index("status"),
        Index("lastSeenAt"),
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

    // v11 新增字段
    val status: String = "ACTIVE",            // ACTIVE / COOLED_DOWN / DISPUTED / ARCHIVED
    val firstSeenAt: Long? = null,
    val lastSeenAt: Long? = null,
    val frequency: Int = 1,                   // 同 anomalyKey 命中累计次数
    val confidence: Float = 1.0f,             // 规则引擎写 1.0；LLM 判断时写实际置信度
)
