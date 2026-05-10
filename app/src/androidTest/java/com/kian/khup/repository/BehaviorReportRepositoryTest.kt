package com.kian.khup.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kian.khup.core.anomaly.AnomalySuggestionGenerator
import com.kian.khup.core.anomaly.RegressionPatternGenerator
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.repository.BehaviorReportRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BehaviorReportRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var patternGenerator: FakeRegressionPatternGenerator
    private lateinit var suggestionGenerator: FakeAnomalySuggestionGenerator
    private lateinit var repository: BehaviorReportRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        patternGenerator = FakeRegressionPatternGenerator(patternId = 42L)
        suggestionGenerator = FakeAnomalySuggestionGenerator()
        repository = BehaviorReportRepositoryImpl(
            eventDao = db.eventDao(),
            patternGenerator = patternGenerator,
            suggestionGenerator = suggestionGenerator,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun submit_writesEventAndTriggersChain() = runBlocking {
        val eventId = repository.submit("  我又被短视频拖走了一小时  ")

        val event = db.eventDao().getById(eventId)
        assertNotNull(event)
        assertEquals(EventType.USER_REPORT, event?.type)
        assertEquals("我又被短视频拖走了一小时", event?.text)
        assertEquals(eventId, patternGenerator.lastEventId)
        assertEquals(42L, suggestionGenerator.lastPatternId)
        assertEquals(0, suggestionGenerator.lastRegenerationCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun submit_blankText_throws() = runBlocking {
        repository.submit("   ")
        Unit
    }

    @Test
    fun submit_textTruncatedTo500() = runBlocking {
        val eventId = repository.submit("x".repeat(520))

        val event = db.eventDao().getById(eventId)
        assertEquals(500, event?.text?.length)
        assertTrue(event?.text?.all { it == 'x' } == true)
    }

    private class FakeRegressionPatternGenerator(
        private val patternId: Long?,
    ) : RegressionPatternGenerator {
        var lastEventId: String? = null

        override suspend fun analyzeUserReport(eventId: String): Long? {
            lastEventId = eventId
            return patternId
        }
    }

    private class FakeAnomalySuggestionGenerator : AnomalySuggestionGenerator {
        var lastPatternId: Long? = null
        var lastRegenerationCount: Int? = null
        var lastParentSuggestionId: Long? = null

        override suspend fun generateForPattern(
            patternId: Long,
            regenerationCount: Int,
            parentSuggestionId: Long?,
        ): Long? {
            lastPatternId = patternId
            lastRegenerationCount = regenerationCount
            lastParentSuggestionId = parentSuggestionId
            return 7L
        }
    }
}
