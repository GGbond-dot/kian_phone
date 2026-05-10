package com.kian.khup.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.migrations.MIGRATION_11_12
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration11To12Test {

    private val TEST_DB = "migration-12-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate11To12_dailyPlanTableExists() {
        helper.createDatabase(TEST_DB, 11).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)
        db.execSQL("""
            INSERT INTO daily_plan (title, dayStartMs, isDone, createdAt, sortOrder)
            VALUES ('test', 1700000000000, 0, 1700000000000, 0)
        """)
        db.query("SELECT title FROM daily_plan").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("test", c.getString(0))
        }
    }
}
