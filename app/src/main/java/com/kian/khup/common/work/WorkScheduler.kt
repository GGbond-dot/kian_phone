package com.kian.khup.common.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 在 Application.onCreate 中调用一次。WorkManager 会持久化任务，
 * 即使重启手机或杀进程，下次系统起来也会按周期继续触发。
 *
 * 用 KEEP 策略：已经存在同名任务时不替换，避免每次 App 启动都重置周期。
 */
object WorkScheduler {

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)

        // NLS rebind：每 15 分钟一次（这是 PeriodicWorkRequest 允许的最小间隔）
        wm.enqueueUniquePeriodicWork(
            NlsRebindWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NlsRebindWorker>(15, TimeUnit.MINUTES).build(),
        )

        // 数据清理：每 24 小时一次
        wm.enqueueUniquePeriodicWork(
            RetentionWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(24, TimeUnit.HOURS).build(),
        )

        // UsageStats 同步：每小时把系统用机时长聚合写入 app_sessions
        wm.enqueueUniquePeriodicWork(
            UsageStatsSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UsageStatsSyncWorker>(1, TimeUnit.HOURS).build(),
        )

        // 算法 App 干预：每 15 分钟检查抖音/小红书今日用量，超过阈值提醒一次。
        wm.enqueueUniquePeriodicWork(
            InterventionCheckWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InterventionCheckWorker>(15, TimeUnit.MINUTES).build(),
        )

        // 每小时通知摘要：本地 Light，过去 1 小时通知聚合，重要项推送。
        wm.enqueueUniquePeriodicWork(
            HourlySummaryWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HourlySummaryWorker>(1, TimeUnit.HOURS).build(),
        )
    }
}
