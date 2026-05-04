package com.kian.khup.collection.notification

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.kian.khup.core.data.db.entities.Event
import java.util.concurrent.ConcurrentHashMap

object NotificationLaunchRegistry {
    private const val TAG = "KHUP/Launch"

    private val intents = ConcurrentHashMap<String, PendingIntent>()

    fun register(eventId: String, contentIntent: PendingIntent?) {
        if (contentIntent != null) intents[eventId] = contentIntent
    }

    fun open(context: Context, event: Event): Boolean {
        intents[event.eventId]?.let { pendingIntent ->
            return runCatching {
                pendingIntent.send()
                true
            }.getOrElse {
                if (it is PendingIntent.CanceledException) intents.remove(event.eventId)
                Log.w(TAG, "contentIntent failed: ${event.packageName}", it)
                openApp(context, event.packageName)
            }
        }
        return openApp(context, event.packageName)
    }

    private fun openApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
