package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 规则引擎用的 KV 存储。例如 key="douyin_today_usage_ms", valueJson="1845000"。
 * 用 JSON 存值是为了规则灵活，不绑定特定 schema。
 */
@Entity(tableName = "rule_state")
data class RuleState(
    @PrimaryKey val key: String,
    val valueJson: String,
    val updatedAt: Long,
)
