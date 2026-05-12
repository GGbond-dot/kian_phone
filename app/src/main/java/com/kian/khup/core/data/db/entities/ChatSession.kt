package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "chat_session",
    indices = [Index("updatedAt"), Index("linkedSuggestionId")],
)
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastMessagePreview: String? = null,
    /** 当 ChatSession 是从 [不适合] 跳转产生时，指向被拒的 AnomalySuggestion.id；否则为 null。 */
    val linkedSuggestionId: Long? = null,
)
