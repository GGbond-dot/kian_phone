package com.kian.khup.core.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v10 → v11：行为线 MVP 数据层。
 *
 * 1. attention_anomaly 加 5 个新字段（status / firstSeenAt / lastSeenAt / frequency / confidence）。
 * 2. daily_tasks 重命名为 anomaly_suggestion，并加 11 个建议相关字段。
 * 3. 新建 user_feedback 表。
 *
 * 参考：worklog/技术方案_行为线MVP/01_数据层.md §4。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ===== 1. AttentionAnomaly 字段扩展 =====
        db.execSQL("ALTER TABLE attention_anomaly ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE attention_anomaly ADD COLUMN firstSeenAt INTEGER")
        db.execSQL("ALTER TABLE attention_anomaly ADD COLUMN lastSeenAt INTEGER")
        db.execSQL("ALTER TABLE attention_anomaly ADD COLUMN frequency INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE attention_anomaly ADD COLUMN confidence REAL NOT NULL DEFAULT 1.0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_attention_anomaly_status ON attention_anomaly(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_attention_anomaly_lastSeenAt ON attention_anomaly(lastSeenAt)")
        // 旧数据回填：firstSeenAt = lastSeenAt = createdAt
        db.execSQL("UPDATE attention_anomaly SET firstSeenAt = createdAt, lastSeenAt = createdAt")

        // ===== 2. daily_tasks → anomaly_suggestion =====
        // SQLite RENAME 是原子的；旧索引名仍是 index_daily_tasks_*，下面手动 DROP/CREATE
        // 以匹配 Room 启动时的 schema 校验。
        db.execSQL("ALTER TABLE daily_tasks RENAME TO anomaly_suggestion")

        // 加新列
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN patternId INTEGER")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN patternKey TEXT")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN suggestionDomain TEXT NOT NULL DEFAULT 'BEHAVIOR'")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN actionText TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN whyText TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN costLevel TEXT NOT NULL DEFAULT 'LOW'")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN expectedUpside TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN scheduledAt INTEGER")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN expiresAt INTEGER")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN modelVersion TEXT NOT NULL DEFAULT 'legacy'")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN regenerationCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN parentSuggestionId INTEGER")
        db.execSQL("ALTER TABLE anomaly_suggestion ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

        // 旧 isDone=1 数据回填为 ACCEPTED；updatedAt 用 createdAt
        db.execSQL(
            """
            UPDATE anomaly_suggestion
            SET status = CASE WHEN isDone = 1 THEN 'ACCEPTED' ELSE 'PENDING' END,
                updatedAt = createdAt
            """.trimIndent()
        )

        // 索引：先 DROP 旧名，再 CREATE 新名
        db.execSQL("DROP INDEX IF EXISTS index_daily_tasks_dayStartMs")
        db.execSQL("DROP INDEX IF EXISTS index_daily_tasks_isDone")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_suggestion_dayStartMs ON anomaly_suggestion(dayStartMs)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_suggestion_status ON anomaly_suggestion(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_anomaly_suggestion_patternId ON anomaly_suggestion(patternId)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_anomaly_suggestion_patternKey_createdAt " +
                "ON anomaly_suggestion(patternKey, createdAt)"
        )

        // ===== 3. user_feedback 新表 =====
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                targetType TEXT NOT NULL,
                targetId INTEGER NOT NULL,
                feedbackType TEXT NOT NULL,
                rating INTEGER,
                reason TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_user_feedback_targetType_targetId ON user_feedback(targetType, targetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_user_feedback_createdAt ON user_feedback(createdAt)")
    }
}
