package com.kian.khup.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS daily_plan (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                note TEXT,
                dayStartMs INTEGER NOT NULL,
                isDone INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                completedAt INTEGER,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_plan_dayStartMs ON daily_plan(dayStartMs)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_plan_isDone ON daily_plan(isDone)")
    }
}
