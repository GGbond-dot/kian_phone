package com.kian.khup.core.intervention

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
import kotlin.math.absoluteValue

@Singleton
class InterventionNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyThresholdExceeded(
        ruleId: String,
        appLabel: String,
        usedText: String,
        thresholdText: String,
    ): Boolean {
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
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$appLabel 今天已经 $usedText")
            .setContentText("超过设定阈值 $thresholdText。先停一下，回到今日主线。")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(ruleId.hashCode().absoluteValue, notification)
        return true
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "使用提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "算法 App 超过今日阈值时提醒"
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "khup_intervention"
    }
}
