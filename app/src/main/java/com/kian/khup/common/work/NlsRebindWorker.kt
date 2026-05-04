package com.kian.khup.common.work

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kian.khup.collection.notification.MessageListener
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 周期性把 NLS 组件 disable→enable 切一下，强制系统重新评估 binding。
 * MIUI 会把 NLS 杀成"假死"——服务对象还在，但系统不再回调 onNotificationPosted。
 * 切 component 状态是绕过这种假死的标准操作（架构文档 §5.1 服务保活）。
 */
@HiltWorker
class NlsRebindWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cn = ComponentName(applicationContext, MessageListener::class.java)
        val pm = applicationContext.packageManager
        runCatching {
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure {
            Log.w(TAG, "rebind toggle failed", it)
            return Result.retry()
        }
        Log.i(TAG, "NLS component toggled for rebind")
        return Result.success()
    }

    companion object {
        private const val TAG = "KHUP/NlsRebind"
        const val UNIQUE_NAME = "khup.nls_rebind"
    }
}
