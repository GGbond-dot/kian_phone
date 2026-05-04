package com.kian.khup.collection.notification

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/**
 * NLS / UsageStats / 悬浮窗都不能 runtime 申请，必须跳系统设置。
 * 这个文件集中放权限检查 + 跳转工具。SettingsScreen 引导页用。
 */
object NotificationPermissions {
    private const val PREFS_NAME = "khup.permission_confirmations"
    private const val KEY_MIUI_AUTOSTART_CONFIRMED = "miui_autostart_confirmed"
    private const val KEY_MIUI_BATTERY_CONFIRMED = "miui_battery_confirmed"

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
        startActivitySafely(
            context = context,
            intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            fallback = appDetailsIntent(context),
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
        startActivitySafely(
            context = context,
            intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            fallback = appDetailsIntent(context),
        )
    }

    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        startActivitySafely(
            context = context,
            intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ),
            fallback = appDetailsIntent(context),
        )
    }

    /** MIUI/HyperOS 自启动管理。系统无公开状态读取 API，只能引导用户手动确认。 */
    fun openMiuiAutostartSettings(context: Context) {
        startActivitySafely(
            context = context,
            intent = Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            ),
            fallback = appDetailsIntent(context),
        )
    }

    fun isMiuiAutostartConfirmed(context: Context): Boolean =
        confirmations(context).getBoolean(KEY_MIUI_AUTOSTART_CONFIRMED, false)

    fun setMiuiAutostartConfirmed(context: Context) {
        confirmations(context).edit().putBoolean(KEY_MIUI_AUTOSTART_CONFIRMED, true).apply()
    }

    /** MIUI/HyperOS 省电策略页面。优先跳到当前 App 的省电配置，失败则退回应用详情。 */
    fun openMiuiBatterySettings(context: Context) {
        startActivitySafely(
            context = context,
            intent = miuiAppDetailsIntent(context),
            fallback = appDetailsIntent(context),
            finalFallback = Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
            ),
        )
    }

    fun isMiuiBatteryConfirmed(context: Context): Boolean =
        confirmations(context).getBoolean(KEY_MIUI_BATTERY_CONFIRMED, false)

    fun setMiuiBatteryConfirmed(context: Context) {
        confirmations(context).edit().putBoolean(KEY_MIUI_BATTERY_CONFIRMED, true).apply()
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

    private fun startActivitySafely(
        context: Context,
        intent: Intent,
        fallback: Intent,
        finalFallback: Intent? = null,
    ) {
        if (tryStartActivity(context, intent)) return
        if (tryStartActivity(context, fallback)) return
        if (finalFallback != null) tryStartActivity(context, finalFallback)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    private fun miuiAppDetailsIntent(context: Context): Intent =
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.appmanager.ApplicationsDetailsActivity",
            )
        )
            .putExtra("package_name", context.packageName)
            .putExtra("packageName", context.packageName)
            .putExtra("pkg_name", context.packageName)

    private fun confirmations(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
