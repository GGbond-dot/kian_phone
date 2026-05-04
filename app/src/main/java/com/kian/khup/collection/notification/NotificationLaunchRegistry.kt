package com.kian.khup.collection.notification

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.kian.khup.core.data.db.entities.Event
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationLaunchRegistry {
    private const val TAG = "KHUP/Launch"

    private val intents = ConcurrentHashMap<String, PendingIntent>()
    private val _directLaunchEventIds = MutableStateFlow<Set<String>>(emptySet())
    val directLaunchEventIds: StateFlow<Set<String>> = _directLaunchEventIds.asStateFlow()

    fun register(eventId: String, contentIntent: PendingIntent?) {
        if (contentIntent != null) {
            intents[eventId] = contentIntent
            publishDirectLaunchIds()
        }
    }

    fun launchCapability(context: Context, event: Event): LaunchCapability =
        when {
            intents.containsKey(event.eventId) -> LaunchCapability.DirectNotification
            context.packageManager.getLaunchIntentForPackage(event.packageName) != null -> LaunchCapability.App
            else -> LaunchCapability.None
        }

    fun hasDirectLaunch(eventId: String): Boolean = intents.containsKey(eventId)

    fun canOpenApp(context: Context, packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    private fun remove(eventId: String) {
        intents.remove(eventId)
        publishDirectLaunchIds()
    }

    fun open(context: Context, event: Event): Boolean {
        intents[event.eventId]?.let { pendingIntent ->
            return runCatching {
                pendingIntent.send()
                true
            }.getOrElse {
                if (it is PendingIntent.CanceledException) remove(event.eventId)
                Log.w(TAG, "contentIntent failed: ${event.packageName}", it)
                openApp(context, event.packageName)
            }
        }
        return openApp(context, event.packageName)
    }

    private fun publishDirectLaunchIds() {
        _directLaunchEventIds.value = intents.keys.toSet()
    }

    private fun openApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}

enum class LaunchCapability {
    DirectNotification,
    App,
    None,
}
