package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.core.data.db.ActionLogDao
import com.kian.khup.core.data.db.AnomalySuggestionDao
import com.kian.khup.core.data.db.AppSessionDao
import com.kian.khup.core.data.db.AttentionAnomalyDao
import com.kian.khup.core.data.db.CategoryUsageCacheDao
import com.kian.khup.core.data.db.ChatMessageDao
import com.kian.khup.core.data.db.ChatSessionDao
import com.kian.khup.core.data.db.ContentThemeTagDao
import com.kian.khup.core.data.db.DailyPlanDao
import com.kian.khup.core.data.db.DailyReviewDao
import com.kian.khup.core.data.db.DerivedResultDao
import com.kian.khup.core.data.db.EventDao
import com.kian.khup.core.data.db.HourlySummaryDao
import com.kian.khup.core.data.db.TriggerTagDao
import com.kian.khup.core.data.db.UserFeedbackDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val eventDao: EventDao,
    private val appSessionDao: AppSessionDao,
    private val derivedResultDao: DerivedResultDao,
    private val hourlySummaryDao: HourlySummaryDao,
    private val triggerTagDao: TriggerTagDao,
    private val contentThemeTagDao: ContentThemeTagDao,
    private val categoryUsageCacheDao: CategoryUsageCacheDao,
    private val chatMessageDao: ChatMessageDao,
    private val chatSessionDao: ChatSessionDao,
    private val anomalySuggestionDao: AnomalySuggestionDao,
    private val attentionAnomalyDao: AttentionAnomalyDao,
    private val userFeedbackDao: UserFeedbackDao,
    private val dailyPlanDao: DailyPlanDao,
    private val dailyReviewDao: DailyReviewDao,
    private val actionLogDao: ActionLogDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        var failed = false

        val tasks: List<Pair<String, suspend () -> Int>> = listOf(
            "events"               to { eventDao.deleteOlderThan(cutoff(now, 3)) },
            "app_sessions"         to { appSessionDao.deleteOlderThan(cutoff(now, 90)) },
            "derived_results"      to { derivedResultDao.deleteOlderThan(cutoff(now, 90)) },
            "hourly_summary"       to { hourlySummaryDao.deleteOlderThan(cutoff(now, 30)) },
            "trigger_tags"         to { triggerTagDao.deleteOlderThan(cutoff(now, 30)) },
            "content_theme_tags"   to { contentThemeTagDao.deleteOlderThan(cutoff(now, 30)) },
            "category_usage_cache" to { categoryUsageCacheDao.deleteOlderThan(cutoff(now, 7)) },
            "chat_message"         to { chatMessageDao.deleteOlderThan(cutoff(now, 90)) },
            "chat_session"         to { chatSessionDao.deleteOlderThan(cutoff(now, 90)) },
            "anomaly_suggestion"   to { anomalySuggestionDao.deleteOlderThan(cutoff(now, 180)) },
            "attention_anomaly"    to { attentionAnomalyDao.deleteOlderThan(cutoff(now, 180)) },
            "user_feedback"        to { userFeedbackDao.deleteOlderThan(cutoff(now, 180)) },
            "daily_plan"           to { dailyPlanDao.deleteOlderThan(cutoff(now, 30)) },
            "daily_review"         to { dailyReviewDao.deleteOlderThan(cutoff(now, 90)) },
            "actions_log"          to { actionLogDao.deleteOlderThan(cutoff(now, 30)) },
        )

        for ((table, action) in tasks) {
            try {
                val n = action()
                Log.i(TAG, "pruned $n rows from $table")
            } catch (e: Exception) {
                Log.w(TAG, "prune failed: $table", e)
                failed = true
            }
        }

        return if (failed) Result.retry() else Result.success()
    }

    private fun cutoff(now: Long, days: Long) = now - TimeUnit.DAYS.toMillis(days)

    companion object {
        private const val TAG = "KHUP/Retention"
        const val UNIQUE_NAME = "khup.retention"
    }
}
