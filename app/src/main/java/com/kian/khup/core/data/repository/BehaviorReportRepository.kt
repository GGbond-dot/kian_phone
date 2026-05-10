package com.kian.khup.core.data.repository

import android.util.Log
import com.kian.khup.common.di.ApplicationScope
import com.kian.khup.common.util.sha256
import com.kian.khup.core.anomaly.AnomalySuggestionGenerator
import com.kian.khup.core.anomaly.RegressionPatternGenerator
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.entities.Event
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface BehaviorReportRepository {
    /**
     * 用户提交一段行为处境。
     *
     * @return 新写入的 Event.eventId。生成链路在 application scope 异步执行。
     */
    suspend fun submit(text: String): String
}

@Singleton
class BehaviorReportRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val patternGenerator: RegressionPatternGenerator,
    private val suggestionGenerator: AnomalySuggestionGenerator,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : BehaviorReportRepository {

    override suspend fun submit(text: String): String {
        val sanitized = text.trim().take(MAX_REPORT_CHARS)
        require(sanitized.isNotBlank()) { "report text must not be blank" }

        val now = System.currentTimeMillis()
        val eventId = sha256("$USER_REPORT_PACKAGE|$sanitized|${now / 1000}")
        eventDao.insert(
            Event(
                eventId = eventId,
                type = EventType.USER_REPORT,
                packageName = USER_REPORT_PACKAGE,
                timestamp = now,
                title = null,
                text = sanitized,
            )
        )

        applicationScope.launch {
            try {
                val patternId = patternGenerator.analyzeUserReport(eventId) ?: return@launch
                suggestionGenerator.generateForPattern(patternId, regenerationCount = 0)
            } catch (t: Throwable) {
                Log.w(TAG, "generation chain failed: ${t.javaClass.simpleName}")
            }
        }

        return eventId
    }

    private companion object {
        const val TAG = "KHUP/BehaviorReport"
        const val MAX_REPORT_CHARS = 500
        const val USER_REPORT_PACKAGE = "com.kian.khup.user_report"
    }
}
