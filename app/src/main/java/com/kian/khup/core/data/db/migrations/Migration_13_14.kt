package com.kian.khup.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v13 → v14：分层记忆架构。
 * 新增 user_memory 表，最多 3 行（SHORT_TERM / MEDIUM_TERM / LONG_TERM），
 * 预压缩摘要存表，AI 对话时直接读取，不再实时查算。
 * 见 worklog/技术方案_行为线MVP/10_补丁_分层记忆架构.md。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_memory (
                type TEXT PRIMARY KEY NOT NULL,
                content TEXT NOT NULL,
                tokenEstimate INTEGER NOT NULL DEFAULT 0,
                generatedAt INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
