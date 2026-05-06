package com.kian.khup.core.summary

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kian.khup.MainActivity
import com.kian.khup.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HourlySummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyImportant(windowStartMs: Long, summary: String, triggerPackages: List<String>): Boolean {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = triggerPackages.firstNotNullOfOrNull { TRIGGER_LABELS[it] }
            ?.let { "$it 有重要消息" }
            ?: "有重要消息"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summary.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(windowStartMs.toInt(), notification)
        return true
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "通知摘要提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "学习通 / 钉钉等关键 app 出现重要消息时提醒"
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "khup_summary"
        private val TRIGGER_LABELS = mapOf(
            "com.chaoxing.mobile" to "学习通",
            "com.alibaba.android.rimet" to "钉钉",
        )
        val TRIGGER_WHITELIST: Set<String> = TRIGGER_LABELS.keys
    }
}
