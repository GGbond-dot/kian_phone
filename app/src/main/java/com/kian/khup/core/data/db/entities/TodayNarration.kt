package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 今日观察 narration：AI 生成的 1-2 句自然语言概述，由
 * [com.kian.khup.common.work.TodayNarrationWorker] 每 2 小时刷新一次。
 *
 * dayStartMs + periodDays UNIQUE：今天观察用 0；回顾故事用 7 / 30 / 90。
 */
@Entity(
    tableName = "today_narration",
    indices = [Index(value = ["dayStartMs", "periodDays"], unique = true)],
)
data class TodayNarration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartMs: Long,
    val periodDays: Int = 0,
    val narrationText: String,
    val generatedAt: Long,
    val modelVersion: String,
)
