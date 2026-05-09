package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kian.khup.core.data.db.entities.ChatMessage

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_message WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun loadBySession(sessionId: Long): List<ChatMessage>

    @Query("SELECT * FROM chat_message WHERE sessionId = :sessionId ORDER BY id DESC LIMIT :limit")
    suspend fun loadRecentBySession(sessionId: Long, limit: Int): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_message WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: Long)
}
