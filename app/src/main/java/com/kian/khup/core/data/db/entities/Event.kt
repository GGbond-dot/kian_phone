package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kian.khup.core.data.db.EventType

/**
 * 不可变事件表。所有采集层数据都打成 Event 写入。
 *
 * eventId 由内容 hash 生成（pkg + title + text + postTime/1000），保证幂等：
 * 同一条逻辑通知重复回调时，PK 冲突会被 Room 的 OnConflictStrategy.IGNORE 跳过。
 */
@Entity(
    tableName = "events",
    indices = [
        Index("packageName", "timestamp"),
        Index("type", "timestamp"),
        Index("timestamp"),
    ]
)
data class Event(
    @PrimaryKey val eventId: String,
    val type: EventType,
    val packageName: String,
    val timestamp: Long,
    val title: String? = null,
    val text: String? = null,
    val subText: String? = null,
    val bigText: String? = null,
    val channelId: String? = null,
    val category: String? = null,
    /** 兜底：原始 extras 序列化，方便后续重新解析或调试 */
    val rawJson: String? = null,
)
