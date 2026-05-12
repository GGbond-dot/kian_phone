package com.kian.khup.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_memory")
data class UserMemory(
    @PrimaryKey val type: String,       // "SHORT_TERM" / "MEDIUM_TERM" / "LONG_TERM"
    val content: String,
    val tokenEstimate: Int,
    val generatedAt: Long,
    val expiresAt: Long,
)
