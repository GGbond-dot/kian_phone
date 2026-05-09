package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 规则引擎生成的诱因标签，用于解释注意力偏离的来源。 */
@Entity(
    tableName = "trigger_tags",
    indices = [
        Index("dayStartMs"),
        Index("tag"),
        Index("sourceType", "sourceId"),
        Index(value = ["dayStartMs", "sourceType", "sourceId", "tag"], unique = true),
    ],
)
data class TriggerTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    /** notification / app_usage / action */
    val sourceType: String,
    /** notification=eventId, app_usage=packageName, action=ActionLog.id */
    val sourceId: String,
    val packageName: String? = null,
    /** algorithmic / social / promotion / task / study_work / emotion_escape */
    val tag: String,
    /** 0-100，规则判断的确定程度。 */
    val confidence: Int,
    val reason: String,
    val createdAt: Long,
    val ruleVersion: String,
)
