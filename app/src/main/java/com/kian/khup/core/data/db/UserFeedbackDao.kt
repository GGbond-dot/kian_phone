package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kian.khup.core.data.db.entities.UserFeedback

@Dao
interface UserFeedbackDao {

    @Insert
    suspend fun insert(item: UserFeedback): Long

    /** 7 天反馈历史，作为 prompt 上下文。 */
    @Query(
        """
        SELECT * FROM user_feedback
        WHERE targetType = :targetType AND createdAt >= :sinceMs
        ORDER BY createdAt DESC LIMIT :limit
        """
    )
    suspend fun recentByTargetType(targetType: String, sinceMs: Long, limit: Int = 50): List<UserFeedback>

    @Query(
        """
        SELECT * FROM user_feedback
        WHERE targetId = :targetId AND targetType = :targetType
        ORDER BY createdAt DESC
        """
    )
    suspend fun findByTarget(targetType: String, targetId: Long): List<UserFeedback>
}
