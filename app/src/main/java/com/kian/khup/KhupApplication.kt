package com.kian.khup

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kian.khup.common.work.WorkScheduler
import com.kian.khup.core.data.repository.UsageStatsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 代码生成。
 * 同时实现 [Configuration.Provider] 让 WorkManager 走 Hilt 注入的 WorkerFactory。
 */
@HiltAndroidApp
class KhupApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var usageStatsRepository: UsageStatsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("KHUP/App", "onCreate: scheduling periodic work")
        WorkScheduler.scheduleAll(this)
        appScope.launch {
            val deleted = runCatching { usageStatsRepository.cleanupAnomalousSessions() }
                .getOrDefault(0)
            if (deleted > 0) {
                android.util.Log.i("KHUP/App", "cleanup: dropped $deleted anomalous app_sessions rows")
            }
        }
    }
}
