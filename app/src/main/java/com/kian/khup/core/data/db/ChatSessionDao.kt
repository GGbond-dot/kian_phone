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

    /** 历史建议详情：找到由该建议（被拒后）跳转到 AI 聊聊产生的会话。 */
    @Query("SELECT * FROM chat_session WHERE linkedSuggestionId = :suggestionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByLinkedSuggestion(suggestionId: Long): ChatSession?

    /** 同上的 Flow 版本，供 HistoryViewModel 在多条建议上批量观察使用。 */
    @Query("SELECT * FROM chat_session WHERE linkedSuggestionId IS NOT NULL")
    fun observeLinkedSessions(): Flow<List<ChatSession>>

    @Query("DELETE FROM chat_session WHERE updatedAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM chat_session")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM chat_session ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ChatSession>
}
