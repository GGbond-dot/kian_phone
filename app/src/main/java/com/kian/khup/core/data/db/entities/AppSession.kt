package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一次 App 前台会话。endTime/durationMs 在 END 事件到来时回填。 */
@Entity(
    tableName = "app_sessions",
    indices = [Index("packageName", "startTime"), Index("startTime")],
)
data class AppSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long? = null,
)
