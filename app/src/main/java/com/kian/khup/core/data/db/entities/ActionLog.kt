package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 规则引擎触发的 Action 历史。永久保留（数据量小，调试规则有用）。 */
@Entity(
    tableName = "actions_log",
    indices = [Index("ruleId", "triggeredAt"), Index("triggeredAt")],
)
data class ActionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: String,
    /** remind / block / cooldown / log */
    val actionType: String,
    val payload: String,
    val triggeredAt: Long,
    /** dismissed / acknowledged / overridden / null（未响应） */
    val userResponse: String? = null,
)
