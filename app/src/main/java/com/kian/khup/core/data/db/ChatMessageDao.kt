package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kian.khup.core.data.db.entities.ChatMessage

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_message ORDER BY id ASC")
    suspend fun loadAll(): List<ChatMessage>

    @Query("SELECT * FROM chat_message ORDER BY id DESC LIMIT :limit")
    suspend fun loadRecent(limit: Int): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_message")
    suspend fun clear()
}
