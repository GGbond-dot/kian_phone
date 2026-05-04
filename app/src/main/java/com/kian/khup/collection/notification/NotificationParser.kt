package com.kian.khup.collection.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.kian.khup.core.data.db.EventType
import com.kian.khup.core.data.db.entities.Event
import java.security.MessageDigest

/**
 * 把系统的 StatusBarNotification 解析成应用内的 NotificationSnapshot / Event。
 *
 * 设计要点：
 * - 解析逻辑放在普通函数里，方便单测，不依赖 Service。
 * - eventId 用 SHA-256 截断 16 字符，基于 (pkg + title + text + postTime/秒)。
 *   秒级粒度避免同一逻辑通知不同毫秒戳产生不同 ID。
 */
object NotificationParser {

    fun parse(sbn: StatusBarNotification, ownPackageName: String): NotificationSnapshot? {
        // 自己发的通知不要再次入库，否则会循环放大。
        if (sbn.packageName == ownPackageName) return null

        val n = sbn.notification ?: return null
        val extras = n.extras ?: return null

        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

        // 完全空的通知（媒体控件占位之类）跳过
        if (title.isNullOrBlank() && text.isNullOrBlank() && bigText.isNullOrBlank()) {
            return null
        }

        return NotificationSnapshot(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            channelId = n.channelId,
            category = n.category,
            postTime = sbn.postTime,
            rawJson = null, // TODO Phase 2：把 extras 序列化成 JSON 兜底
        )
    }

    /** 把内存快照转成可写库的 Event。生成稳定 eventId 做 DB 层去重。 */
    fun NotificationSnapshot.toEvent(type: EventType = EventType.NOTIFICATION_POSTED): Event {
        val seed = "$packageName|${title.orEmpty()}|${text.orEmpty()}|${postTime / 1000}"
        return Event(
            eventId = sha256Short(seed),
            type = type,
            packageName = packageName,
            timestamp = postTime,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            channelId = channelId,
            category = category,
            rawJson = rawJson,
        )
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}
