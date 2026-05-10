package com.kian.khup.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.migrations.MIGRATION_10_11
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v10 → v11 迁移测试（行为线 MVP 数据层）。
 *
 * 验证：
 * 1. daily_tasks 数据保留并 RENAME 为 anomaly_suggestion，status 按 isDone 回填。
 * 2. attention_anomaly 加入 5 个新列并有合理默认值。
 * 3. user_feedback 新表存在并可写入。
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate10To11_preservesDailyTaskData() {
        helper.createDatabase(testDb, 10).apply {
            execSQL(
                """
                INSERT INTO daily_tasks (title, dayStartMs, isDone, createdAt, completedAt)
                VALUES ('test task', 1700000000000, 1, 1700000000000, 1700000060000)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 11, true, MIGRATION_10_11)

        db.query(
            "SELECT title, status, costLevel, suggestionDomain FROM anomaly_suggestion WHERE title = 'test task'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("test task", c.getString(0))
            assertEquals("ACCEPTED", c.getString(1))      // isDone=1 → ACCEPTED
            assertEquals("LOW", c.getString(2))           // 默认值
            assertEquals("BEHAVIOR", c.getString(3))      // 默认值
        }
    }

    @Test
    fun migrate10To11_attentionAnomalyHasNewColumns() {
        helper.createDatabase(testDb, 10).apply {
            execSQL(
                """
                INSERT INTO attention_anomaly
                (anomalyKey, dayStartMs, type, severity, title, detail, metricValue, createdAt, ruleVersion)
                VALUES ('k1', 1700000000000, 'APP_USAGE_SPIKE', 2, 't', 'd', 100, 1700000000000, 'v1')
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 11, true, MIGRATION_10_11)
        db.query(
            "SELECT status, frequency, confidence, firstSeenAt, lastSeenAt FROM attention_anomaly WHERE anomalyKey = 'k1'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ACTIVE", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(1.0f, c.getFloat(2), 0.0001f)
            assertEquals(1700000000000L, c.getLong(3))
            assertEquals(1700000000000L, c.getLong(4))
        }
    }

    @Test
    fun migrate10To11_userFeedbackTableExists() {
        helper.createDatabase(testDb, 10).close()
        val db = helper.runMigrationsAndValidate(testDb, 11, true, MIGRATION_10_11)
        db.execSQL(
            "INSERT INTO user_feedback (targetType, targetId, feedbackType, createdAt) " +
                "VALUES ('SUGGESTION', 1, 'ACCEPT', 1700000000000)"
        )
        db.query("SELECT COUNT(*) FROM user_feedback").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }
}
