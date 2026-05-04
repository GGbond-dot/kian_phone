package com.kian.khup.collection.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kian.khup.collection.notification.NotificationParser.toEvent
import com.kian.khup.core.data.repository.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知监听服务。系统在用户授权后主动 bind 这个 Service，
 * onNotificationPosted/Removed 跑在 **Binder 线程**，绝对不能阻塞。
 *
 * 流程：
 *   onNotificationPosted (Binder)  ──→  解析 + 内存去重  ──→  Channel
 *                                                                │
 *                                                                ↓
 *                                              IO 协程消费 ──→  Repository.insert
 *
 * AI 推理、规则评估都不在这里做，由 WorkManager 批量调度。
 */
@AndroidEntryPoint
class MessageListener : NotificationListenerService() {

    @Inject lateinit var repository: EventRepository

    private val dedupe = DedupeCache()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 用 DROP_OLDEST 防止 IO 慢于推送时无界堆积。容量 256 足够日常爆量。 */
    private val channel = Channel<NotificationSnapshot>(
        capacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
        scope.launch {
            for (snap in channel) {
                runCatching {
                    val event = snap.toEvent()
                    if (repository.insert(event)) {
                        NotificationLaunchRegistry.register(event.eventId, snap.contentIntent)
                    }
                }
                    .onFailure { Log.w(TAG, "insert event failed: ${snap.packageName}", it) }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected (will be rebound by system)")
        // 不要在这里 rebind，由 WorkManager 周期检查统一处理（避免抖动）
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val snap = NotificationParser.parse(sbn, packageName) ?: return
        if (!dedupe.acceptIfNew(snap.key, snap.contentHash)) return
        // 非阻塞投递：满了就丢最老的（DROP_OLDEST）
        channel.trySend(snap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        dedupe.remove(sbn.key)
        // TODO Phase 2：也写一条 NOTIFICATION_REMOVED 事件，方便追溯"消息已读"行为
    }

    override fun onDestroy() {
        super.onDestroy()
        channel.close()
        scope.cancel()
    }

    companion object {
        private const val TAG = "KHUP/NLS"
    }
}
