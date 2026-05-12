package com.kian.khup.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v12 → v13：
 * - chat_session 增加 linkedSuggestionId 列，用于把 [不适合] → 和 AI 聊聊 产生的会话
 *   反向关联到被拒的 AnomalySuggestion，便于历史建议详情展示"查看当时的讨论"。
 * 见 worklog/技术方案_行为线MVP/07_补丁_不适合后AI讨论.md §9.1。
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_session ADD COLUMN linkedSuggestionId INTEGER DEFAULT NULL")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_chat_session_linkedSuggestionId " +
                "ON chat_session(linkedSuggestionId)"
        )
    }
}
