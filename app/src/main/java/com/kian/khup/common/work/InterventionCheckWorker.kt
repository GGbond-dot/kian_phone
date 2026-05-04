package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.collection.notification.NotificationPermissions
import com.kian.khup.core.data.repository.InterventionRepository
import com.kian.khup.core.data.repository.UsageStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class InterventionCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageStatsRepository: UsageStatsRepository,
    private val interventionRepository: InterventionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!NotificationPermissions.hasUsageAccess(applicationContext)) {
            Log.i(TAG, "skip intervention check: usage access not granted")
            return Result.success()
        }

        return runCatching {
            usageStatsRepository.syncToday()
            val triggered = interventionRepository.evaluateToday()
            Log.i(TAG, "intervention check finished: triggered=$triggered")
            Result.success()
        }.getOrElse {
            Log.w(TAG, "intervention check failed", it)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "KHUP/Intervention"
        const val UNIQUE_NAME = "khup.intervention_check"
    }
}
