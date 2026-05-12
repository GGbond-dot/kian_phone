package com.kian.khup.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kian.khup.core.data.db.entities.ContentThemeTag

data class ContentThemeTotal(
    val theme: String,
    val count: Int,
    val averageConfidence: Double,
)

data class DailyContentThemeTotal(
    val dayStartMs: Long,
    val theme: String,
    val count: Int,
    val averageConfidence: Double,
)

@Dao
interface ContentThemeTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tags: List<ContentThemeTag>)

    @Query("DELETE FROM content_theme_tags WHERE dayStartMs = :dayStartMs")
    suspend fun deleteForDay(dayStartMs: Long)

    @Query("""
        SELECT *
        FROM content_theme_tags
        WHERE dayStartMs = :dayStartMs
        ORDER BY confidence DESC, createdAt DESC
    """)
    suspend fun loadForDay(dayStartMs: Long): List<ContentThemeTag>

    @Query("""
        SELECT theme, COUNT(*) AS count, AVG(confidence) AS averageConfidence
        FROM content_theme_tags
        WHERE dayStartMs = :dayStartMs
        GROUP BY theme
        ORDER BY count DESC, averageConfidence DESC
    """)
    suspend fun loadThemeTotalsForDay(dayStartMs: Long): List<ContentThemeTotal>

    @Query("""
        SELECT theme, COUNT(*) AS count, AVG(confidence) AS averageConfidence
        FROM content_theme_tags
        WHERE dayStartMs >= :sinceMs
        GROUP BY theme
        ORDER BY count DESC, averageConfidence DESC
        LIMIT :limit
    """)
    suspend fun loadThemeTotalsSince(sinceMs: Long, limit: Int): List<ContentThemeTotal>

    @Query("""
        SELECT dayStartMs, theme, COUNT(*) AS count, AVG(confidence) AS averageConfidence
        FROM content_theme_tags
        WHERE dayStartMs >= :sinceMs
        GROUP BY dayStartMs, theme
        ORDER BY dayStartMs ASC, count DESC, averageConfidence DESC
    """)
    suspend fun loadDailyThemeTotalsSince(sinceMs: Long): List<DailyContentThemeTotal>

    @Query("DELETE FROM content_theme_tags WHERE createdAt < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM content_theme_tags")
    suspend fun deleteAll(): Int
}
