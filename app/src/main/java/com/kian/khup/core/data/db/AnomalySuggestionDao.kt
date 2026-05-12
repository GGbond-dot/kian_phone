package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import kotlinx.coroutines.flow.Flow

data class DomainCount(val suggestionDomain: String, val count: Int)

@Dao
interface AnomalySuggestionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: AnomalySuggestion): Long

    @Update
    suspend fun update(item: AnomalySuggestion)

    @Query("SELECT * FROM anomaly_suggestion WHERE id = :id")
    suspend fun getById(id: Long): AnomalySuggestion?

    /** TodayScreen 主卡片：今天最新的 PENDING 建议（最多一条）。 */
    @Query(
        """
        SELECT * FROM anomaly_suggestion
        WHERE dayStartMs = :dayStartMs AND status = 'PENDING'
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    fun observeTodayPending(dayStartMs: Long): Flow<AnomalySuggestion?>

    /** 24h 冷却检查：同 patternKey 在 since 之后的所有 suggestion。 */
    @Query(
        """
        SELECT * FROM anomaly_suggestion
        WHERE patternKey = :patternKey AND createdAt >= :sinceMs
        ORDER BY createdAt DESC
        """
    )
    suspend fun findByPatternKeySince(patternKey: String, sinceMs: Long): List<AnomalySuggestion>

    /** HistoryScreen 建议列表：按 status 分组。 */
    @Query(
        """
        SELECT * FROM anomaly_suggestion
        WHERE status = :status
        ORDER BY createdAt DESC LIMIT :limit
        """
    )
    fun observeByStatus(status: String, limit: Int = 100): Flow<List<AnomalySuggestion>>

    @Query(
        """
        UPDATE anomaly_suggestion SET status = :status, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(id: Long, status: String, updatedAt: Long)

    // ===== 兼容 DailyTaskRepository / DashboardScreen 的过渡查询 =====
    // 行为线 UI 切换完成后（Module 4），下个迭代删除以下方法。

    @Deprecated("旧 DailyTask 兼容；用 observeTodayPending 或 observeByStatus 取代")
    @Query(
        """
        SELECT * FROM anomaly_suggestion
        WHERE dayStartMs = :dayStartMs
        ORDER BY isDone ASC, createdAt ASC
        """
    )
    fun observeForDayLegacy(dayStartMs: Long): Flow<List<AnomalySuggestion>>

    @Deprecated("旧 DailyTask 兼容")
    @Query(
        """
        SELECT * FROM anomaly_suggestion
        WHERE dayStartMs = :dayStartMs
        ORDER BY isDone ASC, createdAt ASC
        """
    )
    suspend fun loadForDayLegacy(dayStartMs: Long): List<AnomalySuggestion>

    @Deprecated("旧 DailyTask 兼容")
    @Query(
        """
        SELECT * FROM anomaly_suggestion
        WHERE dayStartMs < :todayStartMs AND isDone = 0
        ORDER BY dayStartMs DESC, createdAt ASC
        """
    )
    fun observeOverdueUnfinishedLegacy(todayStartMs: Long): Flow<List<AnomalySuggestion>>

    @Deprecated("旧 DailyTask 兼容；新代码用 updateStatus")
    @Query(
        """
        UPDATE anomaly_suggestion
        SET isDone = :isDone, completedAt = :completedAt, updatedAt = :updatedAt,
            status = CASE WHEN :isDone = 1 THEN 'ACCEPTED' ELSE 'PENDING' END
        WHERE id = :id
        """
    )
    suspend fun setDoneLegacy(id: Long, isDone: Boolean, completedAt: Long?, updatedAt: Long)

    @Deprecated("旧 DailyTask 兼容；新代码用 updateStatus 到 ARCHIVED")
    @Query("DELETE FROM anomaly_suggestion WHERE id = :id")
    suspend fun deleteLegacy(id: Long)

    /** Part A：7 天内各 domain 的建议数量，用于 domain 轮转。 */
    @Query(
        """
        SELECT suggestionDomain, COUNT(*) as count
        FROM anomaly_suggestion
        WHERE createdAt >= :sinceMs
        GROUP BY suggestionDomain
        """
    )
    suspend fun getDomainCountsSince(sinceMs: Long): List<DomainCount>

    /** Part C：7 天内所有建议（含 status），用于上下文摘要。 */
    @Query(
        """
        SELECT * FROM anomaly_suggestion
        WHERE createdAt >= :sinceMs
        ORDER BY createdAt DESC
        """
    )
    suspend fun getRecentWithStatus(sinceMs: Long): List<AnomalySuggestion>

    @Query("DELETE FROM anomaly_suggestion WHERE createdAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM anomaly_suggestion")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM anomaly_suggestion ORDER BY createdAt DESC")
    suspend fun getAll(): List<AnomalySuggestion>
}
