package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import kotlinx.coroutines.flow.Flow

@Dao
interface AttentionAnomalyDao {

    @Query("""
        SELECT *
        FROM attention_anomaly
        WHERE dayStartMs = :dayStartMs
        ORDER BY severity DESC, createdAt DESC
    """)
    fun observeForDay(dayStartMs: Long): Flow<List<AttentionAnomaly>>

    @Query("""
        SELECT *
        FROM attention_anomaly
        WHERE dayStartMs = :dayStartMs
        ORDER BY severity DESC, createdAt DESC
    """)
    suspend fun loadForDay(dayStartMs: Long): List<AttentionAnomaly>

    @Query("""
        SELECT *
        FROM attention_anomaly
        WHERE dayStartMs >= :sinceMs
        ORDER BY severity DESC, createdAt DESC
        LIMIT :limit
    """)
    suspend fun loadSince(sinceMs: Long, limit: Int): List<AttentionAnomaly>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(anomalies: List<AttentionAnomaly>)

    /** 行为线 Generator：单条插入并返回新生成的主键。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(anomaly: AttentionAnomaly): Long

    @Update
    suspend fun update(anomaly: AttentionAnomaly)

    @Query("SELECT * FROM attention_anomaly WHERE id = :id")
    suspend fun getById(id: Long): AttentionAnomaly?

    /** 用 anomalyKey 命中检查（同一回归模式的累计计数入口）。 */
    @Query("SELECT * FROM attention_anomaly WHERE anomalyKey = :anomalyKey LIMIT 1")
    suspend fun findByKey(anomalyKey: String): AttentionAnomaly?

    /** 行为线 prompt 上下文：最近 N 天内、指定 type 前缀的 anomaly。 */
    @Query(
        """
        SELECT * FROM attention_anomaly
        WHERE lastSeenAt IS NOT NULL AND lastSeenAt >= :sinceMs AND type LIKE :typePrefix
        ORDER BY lastSeenAt DESC LIMIT :limit
        """
    )
    suspend fun loadRecentByTypePrefix(
        sinceMs: Long,
        typePrefix: String,
        limit: Int,
    ): List<AttentionAnomaly>

    @Query("DELETE FROM attention_anomaly WHERE dayStartMs = :dayStartMs")
    suspend fun deleteForDay(dayStartMs: Long): Int

    /** HistoryScreen 模式列表：按 status 过滤，lastSeenAt 倒序。 */
    @Query(
        """
        SELECT * FROM attention_anomaly
        WHERE status = :status
        ORDER BY lastSeenAt DESC, createdAt DESC
        """
    )
    fun observeByStatus(status: String): Flow<List<AttentionAnomaly>>

    /** Part C：最活跃的回归值模式，用于 AI 对话上下文摘要。 */
    @Query(
        """
        SELECT * FROM attention_anomaly
        WHERE status = 'ACTIVE' AND lastSeenAt >= :sinceMs
        ORDER BY frequency DESC, lastSeenAt DESC
        LIMIT :limit
        """
    )
    suspend fun getActivePatternsSince(sinceMs: Long, limit: Int): List<AttentionAnomaly>

    @Query("DELETE FROM attention_anomaly WHERE createdAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM attention_anomaly")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM attention_anomaly ORDER BY createdAt DESC")
    suspend fun getAll(): List<AttentionAnomaly>
}
