package com.kian.khup.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kian.khup.core.anomaly.AnomalySuggestionGenerator
import com.kian.khup.core.data.db.AppDatabase
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.core.data.repository.AnomalySuggestionRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnomalySuggestionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var generator: FakeAnomalySuggestionGenerator
    private lateinit var repository: AnomalySuggestionRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        generator = FakeAnomalySuggestionGenerator()
        repository = AnomalySuggestionRepositoryImpl(
            suggestionDao = db.anomalySuggestionDao(),
            feedbackDao = db.userFeedbackDao(),
            suggestionGenerator = generator,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun accept_marksAcceptedAndWritesFeedback() = runBlocking {
        val id = insertSuggestion(patternId = 9L)

        repository.accept(id)

        assertEquals("ACCEPTED", db.anomalySuggestionDao().getById(id)?.status)
        val feedback = db.userFeedbackDao().findByTarget("SUGGESTION", id).single()
        assertEquals("ACCEPT", feedback.feedbackType)
    }

    @Test
    fun postpone_marksPostponedAndTriggersRegenerate() = runBlocking {
        val id = insertSuggestion(patternId = 9L, regenerationCount = 2)

        repository.postpone(id)

        assertEquals("POSTPONED", db.anomalySuggestionDao().getById(id)?.status)
        val feedback = db.userFeedbackDao().findByTarget("SUGGESTION", id).single()
        assertEquals("POSTPONE", feedback.feedbackType)
        assertEquals(9L, generator.lastPatternId)
        assertEquals(3, generator.lastRegenerationCount)
        assertEquals(id, generator.lastParentSuggestionId)
    }

    @Test
    fun postpone_withoutPatternId_skipsRegeneration() = runBlocking {
        val id = insertSuggestion(patternId = null)

        repository.postpone(id)

        assertEquals("POSTPONED", db.anomalySuggestionDao().getById(id)?.status)
        assertNull(generator.lastPatternId)
    }

    @Test
    fun reject_marksRejectedNoRegeneration() = runBlocking {
        val id = insertSuggestion(patternId = 9L)

        repository.reject(id, reason = "太费劲")

        assertEquals("REJECTED", db.anomalySuggestionDao().getById(id)?.status)
        val feedback = db.userFeedbackDao().findByTarget("SUGGESTION", id).single()
        assertEquals("REJECT", feedback.feedbackType)
        assertEquals("太费劲", feedback.reason)
        assertNull(generator.lastPatternId)
    }

    private suspend fun insertSuggestion(
        patternId: Long?,
        regenerationCount: Int = 0,
    ): Long {
        val now = 1_700_000_000_000L
        return db.anomalySuggestionDao().insert(
            AnomalySuggestion(
                title = "默认路径",
                dayStartMs = now,
                createdAt = now,
                patternId = patternId,
                patternKey = patternId?.let { "pattern:$it" },
                suggestionDomain = "BEHAVIOR",
                actionText = "走到楼下，换一条路走十分钟",
                whyText = "打断惯性比证明自律更重要",
                costLevel = "LOW",
                expectedUpside = "得到一个新的身体感受",
                status = "PENDING",
                modelVersion = "test",
                regenerationCount = regenerationCount,
                updatedAt = now,
            )
        )
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
            return 100L
        }
    }
}
