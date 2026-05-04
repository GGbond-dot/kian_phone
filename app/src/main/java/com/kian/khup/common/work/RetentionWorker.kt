package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.core.data.repository.EventRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 数据保留：删除 [RAW_EVENT_RETENTION_DAYS] 天前的原始 events。
 * derived_results / app_sessions / action_logs 体积小、有长期价值，不在这清理。
 */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val eventRepository: EventRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() -
            TimeUnit.DAYS.toMillis(RAW_EVENT_RETENTION_DAYS)
        val deleted = runCatching { eventRepository.pruneOlderThan(cutoff) }
            .getOrElse {
                Log.w(TAG, "prune failed", it)
                return Result.retry()
            }
        Log.i(TAG, "pruned $deleted events older than $RAW_EVENT_RETENTION_DAYS days")
        return Result.success()
    }

    companion object {
        private const val TAG = "KHUP/Retention"
        const val UNIQUE_NAME = "khup.retention"
        const val RAW_EVENT_RETENTION_DAYS = 7L
    }
}
