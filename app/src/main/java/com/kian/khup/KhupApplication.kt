package com.kian.khup

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口。@HiltAndroidApp 触发 Hilt 代码生成。
 * 同时实现 [Configuration.Provider] 让 WorkManager 走 Hilt 注入的 WorkerFactory。
 */
@HiltAndroidApp
class KhupApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
