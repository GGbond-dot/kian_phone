package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 今日观察 narration：AI 生成的 1-2 句自然语言概述，由
 * [com.kian.khup.common.work.TodayNarrationWorker] 每 2 小时刷新一次。
 *
 * dayStartMs UNIQUE：一天一行，upsert 用 REPLACE 覆盖。
 */
@Entity(
    tableName = "today_narration",
    indices = [Index(value = ["dayStartMs"], unique = true)],
)
data class TodayNarration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    val narrationText: String,
    val generatedAt: Long,
    val modelVersion: String,
)
