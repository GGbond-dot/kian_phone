package com.kian.khup.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v16 → v17：today_narration 增加 periodDays。
 * 0 = 当日；7 / 30 / 90 = 回顾页周期故事。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE today_narration ADD COLUMN periodDays INTEGER NOT NULL DEFAULT 0")
        db.execSQL("DROP INDEX IF EXISTS index_today_narration_dayStartMs")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_today_narration_dayStartMs_periodDays " +
                "ON today_narration(dayStartMs, periodDays)"
        )
    }
}
