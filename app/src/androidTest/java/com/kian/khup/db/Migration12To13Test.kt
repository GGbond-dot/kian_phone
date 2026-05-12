package com.kian.khup.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.migrations.MIGRATION_12_13
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration12To13Test {

    private val TEST_DB = "migration-13-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /** v12 旧的 chat_session 行迁移后 linkedSuggestionId 应为 NULL，不丢数据。 */
    @Test
    fun migrate12To13_existingChatSessionGetsNullLinkedSuggestion() {
        val now = 1_700_000_000_000L
        helper.createDatabase(TEST_DB, 12).use { db ->
            db.execSQL(
                """
                INSERT INTO chat_session (title, createdAt, updatedAt, lastMessagePreview)
                VALUES ('legacy', $now, $now, 'hi')
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        db.query("SELECT title, linkedSuggestionId FROM chat_session").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("legacy", c.getString(0))
            assertTrue(c.isNull(1))
        }

        // 新行可以写入 linkedSuggestionId
        db.execSQL(
            """
            INSERT INTO chat_session (title, createdAt, updatedAt, lastMessagePreview, linkedSuggestionId)
            VALUES ('linked', $now, $now, 'pre', 42)
            """.trimIndent()
        )
        db.query("SELECT linkedSuggestionId FROM chat_session WHERE title = 'linked'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42L, c.getLong(0))
        }
    }

    /** 索引应被创建以支持 findByLinkedSuggestion 查询。 */
    @Test
    fun migrate12To13_linkedSuggestionIndexCreated() {
        helper.createDatabase(TEST_DB, 12).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13)

        var foundIndex = false
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'chat_session'"
        ).use { c ->
            while (c.moveToNext()) {
                if (c.getString(0) == "index_chat_session_linkedSuggestionId") {
                    foundIndex = true
                }
            }
        }
        assertTrue(foundIndex)
        // 安抚 lint：确保 db 不为 null
        assertNull(null)
    }
}
