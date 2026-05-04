package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.collection.notification.NotificationPermissions
import com.kian.khup.core.data.repository.UsageStatsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UsageStatsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageStatsRepository: UsageStatsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!NotificationPermissions.hasUsageAccess(applicationContext)) {
            Log.i(TAG, "skip sync: usage access not granted")
            return Result.success()
        }

        val synced = runCatching { usageStatsRepository.syncToday() }
            .getOrElse {
                Log.w(TAG, "usage sync failed", it)
                return Result.retry()
            }
        Log.i(TAG, "synced $synced app usage rows")
        return Result.success()
    }

    companion object {
        private const val TAG = "KHUP/UsageStats"
        const val UNIQUE_NAME = "khup.usage_stats_sync"
    }
}
