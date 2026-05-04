package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI（或规则引擎）对单条 Event 的衍生结果。
 * 删 Event 时级联删除（CASCADE），可以"删 derived → 重新跑全量推理"测试新模型。
 */
@Entity(
    tableName = "derived_results",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("classification"), Index("priority")],
)
data class DerivedResult(
    @PrimaryKey val eventId: String,
    /** 验证码 / 工作 / 社交 / 推广 / 算法推送 / 其他 */
    val classification: String,
    /** 0-3，数字越大越紧急 */
    val priority: Int,
    val summary: String? = null,
    /** JSON 数组：实体抽取，例如 ["张三", "明天 14:00"] */
    val entities: String? = null,
    val processedAt: Long,
    /** "rules-v1" / "gemma-4-e4b-q4-v1" 等。方便按版本 reprocess */
    val modelVersion: String,
)
