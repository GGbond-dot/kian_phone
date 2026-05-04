package com.kian.khup.collection.notification

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * NLS / UsageStats / 悬浮窗都不能 runtime 申请，必须跳系统设置。
 * 这个文件集中放权限检查 + 跳转工具。SettingsScreen 引导页用。
 */
object NotificationPermissions {

    /** 检查通知监听服务是否被授权。 */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val expected = ComponentName(context, MessageListener::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    /** 跳到「通知使用权」设置页。MIUI 上还有二次确认开关，引导页要截图说明。 */
    fun openNotificationListenerSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** UsageStats 权限检查（属于 AppOps）。 */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * MIUI/HyperOS NLS 假死时的标准重启大法：
     * 切换 component enable 状态，强制系统重新评估并 rebind。
     * 在 WorkManager 周期任务里调，不要在主线程频繁调。
     */
    fun rebindListener(context: Context) {
        val cn = ComponentName(context, MessageListener::class.java)
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            cn,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            cn,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
    }
}
