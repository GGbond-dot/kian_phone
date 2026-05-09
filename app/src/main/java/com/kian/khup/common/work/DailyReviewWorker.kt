package com.kian.khup.common.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.core.summary.DailyReviewGenerator
import com.kian.khup.core.summary.DailyReviewNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyReviewWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val generator: DailyReviewGenerator,
    private val notifier: DailyReviewNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = generator.generateToday()
        return result.fold(
            onSuccess = { review ->
                notifier.notifyReview(review.dayStartMs, review.summary)
                WorkScheduler.scheduleNextDailyReview(applicationContext)
                Log.i(TAG, "daily review stored: day=${review.dayStartMs}")
                Result.success()
            },
            onFailure = {
                Log.w(TAG, "daily review failed", it)
                Result.retry()
            },
        )
    }

    companion object {
        private const val TAG = "KHUP/DailyReviewWorker"
        const val UNIQUE_NAME = "khup.daily_review"
    }
}
