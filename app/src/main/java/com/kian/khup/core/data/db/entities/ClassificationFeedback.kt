package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户手动修正通知分类的记录。
 * 后续可作为规则优化和端侧 LLM 分类评估的真实样本。
 */
@Entity(
    tableName = "classification_feedback",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("eventId"),
        Index("createdAt"),
    ],
)
data class ClassificationFeedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val oldClassification: String,
    val newClassification: String,
    val createdAt: Long,
)
