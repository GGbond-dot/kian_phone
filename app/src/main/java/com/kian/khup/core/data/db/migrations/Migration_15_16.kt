package com.kian.khup.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v15 → v16：daily_plan 加 sourceSuggestionId（建议→计划闭环）。
 * NULL = 用户手动添加；非空 = 由 acceptSuggestion 写入。
 * 见 ui-redesign §4.5.2。
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_plan ADD COLUMN sourceSuggestionId INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_daily_plan_sourceSuggestionId ON daily_plan(sourceSuggestionId)"
        )
    }
}
