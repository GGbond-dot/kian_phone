package com.kian.khup.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v14 → v15：今日观察 narration 表。
 * AI Worker（每 2 小时）生成自然语言句存表，首页直接读，
 * 不再用 KV 行渲染。见 ui-redesign §4.3。
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS today_narration (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                dayStartMs INTEGER NOT NULL,
                narrationText TEXT NOT NULL,
                generatedAt INTEGER NOT NULL,
                modelVersion TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_today_narration_dayStartMs ON today_narration(dayStartMs)"
        )
    }
}
