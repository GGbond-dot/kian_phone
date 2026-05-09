package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kian.khup.core.data.db.entities.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_session ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_session ORDER BY updatedAt DESC")
    suspend fun loadAll(): List<ChatSession>

    @Query("SELECT * FROM chat_session WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ChatSession?

    @Insert
    suspend fun insert(session: ChatSession): Long

    @Query("UPDATE chat_session SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE chat_session SET updatedAt = :updatedAt, lastMessagePreview = :preview WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long, preview: String?)

    @Query("DELETE FROM chat_session WHERE id = :id")
    suspend fun deleteById(id: Long)
}
